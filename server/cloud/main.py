import json
import os
import urllib.parse
import urllib.request
from html.parser import HTMLParser

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="S.AI Cloud Core", version="1.01")
API_BASE = os.getenv("SAI_API_BASE", "").rstrip("/")
API_KEY = os.getenv("SAI_API_KEY", "")
MODEL = os.getenv("SAI_CLOUD_MODEL", "openai/gpt-oss-20b:free")


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=20000)
    history: str = ""
    memory: str = ""
    mode: str = "SMART"
    use_web: bool = True
    max_tokens: int = Field(default=900, ge=64, le=4096)


class SearchRequest(BaseModel):
    query: str = Field(min_length=1, max_length=1000)


class SearchItem(BaseModel):
    title: str
    url: str
    snippet: str = ""


class SearchResponse(BaseModel):
    query: str
    results: list[SearchItem]


class Result(BaseModel):
    text: str
    model: str
    device: str = "cloud"
    language: str = "unknown"
    sources: list[SearchItem] = []


class DDGParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.results = []
        self.title = ""
        self.url = ""
        self.snippet = ""
        self.mode = ""

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        cls = a.get("class", "")
        if tag == "a" and "result__a" in cls:
            if self.title.strip() and self.url:
                self.results.append(SearchItem(title=self.title.strip(), url=self.url, snippet=self.snippet.strip()))
            self.title, self.url, self.snippet, self.mode = "", a.get("href", ""), "", "title"
        elif "result__snippet" in cls:
            self.mode = "snippet"

    def handle_data(self, data):
        if self.mode == "title":
            self.title += data
        elif self.mode == "snippet":
            self.snippet += data

    def handle_endtag(self, tag):
        if tag == "a" and self.mode == "title":
            self.mode = ""

    def close(self):
        super().close()
        if self.title.strip() and self.url:
            self.results.append(SearchItem(title=self.title.strip(), url=self.url, snippet=self.snippet.strip()))


def web_search(query: str, limit: int = 6):
    url = "https://html.duckduckgo.com/html/?q=" + urllib.parse.quote_plus(query[:500])
    request = urllib.request.Request(url, headers={"User-Agent": "S.AI/1.01"})
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            html = response.read().decode("utf-8", "ignore")
        parser = DDGParser()
        parser.feed(html)
        return parser.results[:limit]
    except Exception:
        return []


def language_name(text: str) -> str:
    if any(c in text for c in "їієґЇІЄҐ"):
        return "Ukrainian"
    if any("А" <= c <= "я" or c in "Ёё" for c in text):
        return "Russian"
    if any("\u4e00" <= c <= "\u9fff" for c in text):
        return "Chinese"
    return "English"


@app.get("/health")
def health():
    return {"ok": True, "service": "sai-cloud-core", "provider_configured": bool(API_BASE and API_KEY), "model": MODEL}


@app.post("/v1/chat", response_model=Result)
def chat(req: ChatRequest):
    if not API_BASE or not API_KEY:
        raise HTTPException(status_code=503, detail="Cloud provider is not configured. Set SAI_API_BASE and SAI_API_KEY.")
    lang = language_name(req.message)
    sources = web_search(req.message) if req.use_web and any(w in req.message.lower() for w in ("сегодня", "сейчас", "последн", "новост", "цена", "курс", "погода", "найди", "поищи", "today", "latest", "news", "price", "weather", "search")) else []
    source_text = "\n".join(f"[{i+1}] {s.title} — {s.url}\n{s.snippet}" for i, s in enumerate(sources))
    system = (
        "You are S.AI, a helpful general-purpose AI assistant. Be accurate and natural. "
        f"Answer only in {lang}; match the user's language and never switch without being asked. "
        "Do not invent facts, tool use, or sources. Cite supplied web sources as [1], [2], etc. "
        f"Mode: {req.mode}. Memory: {req.memory[-6000:]}\nWeb sources:\n{source_text}"
    )
    messages = [{"role": "system", "content": system}]
    if req.history.strip():
        messages.append({"role": "user", "content": "Earlier conversation context; use only as context:\n" + req.history[-10000:]})
    messages.append({"role": "user", "content": req.message})
    payload = json.dumps({"model": MODEL, "messages": messages, "max_tokens": req.max_tokens, "temperature": 0.7}).encode()
    request = urllib.request.Request(API_BASE + "/chat/completions", data=payload, method="POST", headers={
        "Authorization": "Bearer " + API_KEY, "Content-Type": "application/json", "User-Agent": "SAI-Core/1.01"
    })
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            data = json.loads(response.read().decode())
        answer = data["choices"][0]["message"]["content"]
        if isinstance(answer, list):
            answer = "".join(str(p.get("text", "")) for p in answer if isinstance(p, dict))
        if not str(answer).strip():
            raise ValueError("Provider returned an empty answer")
        return Result(text=str(answer).strip(), model=str(data.get("model", MODEL)), device="cloud", language=lang, sources=sources)
    except Exception as exc:
        raise HTTPException(status_code=502, detail="Cloud provider request failed: " + str(exc)[:250]) from exc


@app.post("/v1/search", response_model=SearchResponse)
def search(req: SearchRequest):
    return SearchResponse(query=req.query, results=web_search(req.query, 8))


@app.post("/v1/image")
def image():
    raise HTTPException(status_code=501, detail="Image generation is not configured on this cloud deployment yet.")
