import base64
import io
import json
import os
import re
import urllib.parse
import urllib.request
from html.parser import HTMLParser
from functools import lru_cache

import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="S.AI Core", version="7.2")

CHAT_MODEL = os.getenv("SAI_CHAT_MODEL", "Qwen/Qwen2.5-3B-Instruct")
IMAGE_MODEL = os.getenv("SAI_IMAGE_MODEL", "stabilityai/stable-diffusion-2-1-base")
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
DTYPE = torch.float16 if DEVICE == "cuda" else torch.float32


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=20000)
    history: str = ""
    memory: str = ""
    mode: str = "SMART"
    use_web: bool = True
    max_tokens: int = Field(default=900, ge=64, le=4096)


class SearchItem(BaseModel):
    title: str
    url: str
    snippet: str = ""


class ChatResponse(BaseModel):
    text: str
    model: str
    device: str
    language: str = "unknown"
    sources: list[SearchItem] = Field(default_factory=list)


class SearchResponse(BaseModel):
    query: str
    results: list[SearchItem]


class ImageRequest(BaseModel):
    prompt: str = Field(min_length=1, max_length=4000)
    width: int = Field(default=768, ge=256, le=1536)
    height: int = Field(default=768, ge=256, le=1536)
    steps: int = Field(default=25, ge=5, le=80)


class ImageResponse(BaseModel):
    image_base64: str
    model: str
    device: str


class DDGParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.results = []
        self._title = ""
        self._url = ""
        self._snippet = ""
        self._mode = None

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        cls = attrs.get("class", "")
        if tag == "a" and "result__a" in cls:
            if self._title.strip() and self._url:
                self.results.append(SearchItem(title=self._title.strip(), url=self._url, snippet=self._snippet.strip()))
            self._title, self._url, self._snippet = "", attrs.get("href", ""), ""
            self._mode = "title"
        elif "result__snippet" in cls:
            self._mode = "snippet"

    def handle_data(self, data):
        if self._mode == "title":
            self._title += data
        elif self._mode == "snippet":
            self._snippet += data

    def handle_endtag(self, tag):
        if tag == "a" and self._mode == "title":
            self._mode = None

    def close(self):
        super().close()
        if self._title.strip() and self._url:
            self.results.append(SearchItem(title=self._title.strip(), url=self._url, snippet=self._snippet.strip()))


def detect_language(text: str) -> str:
    if re.search(r"[ЇїІіЄєҐґ]", text): return "Ukrainian"
    if re.search(r"[А-Яа-яЁё]", text): return "Russian"
    if re.search(r"[一-鿿]", text): return "Chinese"
    if re.search(r"[ぁ-ゟァ-ヿ]", text): return "Japanese"
    if re.search(r"[가-힣]", text): return "Korean"
    return "English"


def web_search(query: str, limit: int = 5) -> list[SearchItem]:
    q = urllib.parse.quote_plus(query[:500])
    req = urllib.request.Request(
        "https://html.duckduckgo.com/html/?q=" + q,
        headers={"User-Agent": "S.AI/7.1 (local AI assistant)"},
    )
    try:
        with urllib.request.urlopen(req, timeout=8) as resp:
            html = resp.read().decode("utf-8", errors="ignore")
        parser = DDGParser()
        parser.feed(html)
        return parser.results[:limit]
    except Exception:
        return []


def cloud_chat(req: ChatRequest, sources: list[SearchItem], language: str) -> ChatResponse | None:
    """Use an optional OpenAI-compatible cloud endpoint when configured; otherwise use local inference."""
    api_base = os.getenv("SAI_API_BASE", "").strip().rstrip("/")
    api_key = os.getenv("SAI_API_KEY", "").strip()
    if not api_base or not api_key:
        return None

    model_name = os.getenv(
        "SAI_CLOUD_MODEL",
        os.getenv("SAI_FAST_MODEL", "openai/gpt-oss-20b:free") if req.mode.upper() == "FAST"
        else os.getenv("SAI_CODE_MODEL", "openai/gpt-oss-20b:free") if req.mode.upper() == "CODE"
        else os.getenv("SAI_SMART_MODEL", "openai/gpt-oss-20b:free"),
    )
    source_text = "\\n".join(
        f"[{i+1}] {s.title} — {s.url}\\n{s.snippet}" for i, s in enumerate(sources)
    )
    system = (
        "You are S.AI, a helpful general-purpose assistant. Be accurate and natural. "
        f"Answer only in {language}, matching the user's language. Never switch languages without being asked. "
        "Never invent facts, sources, or tool results. If web sources are supplied, cite them as [1], [2], etc. "
        f"Mode: {req.mode}. Memory: {req.memory[-6000:]}\\n"
        f"Web sources:\\n{source_text}"
    )
    messages = [{"role": "system", "content": system}]
    if req.history.strip():
        messages.append({"role": "user", "content": "Earlier conversation context (use as context, do not answer it again):\\n" + req.history[-12000:]})
    messages.append({"role": "user", "content": req.message})
    payload = json.dumps({
        "model": model_name,
        "messages": messages,
        "max_tokens": req.max_tokens,
        "temperature": 0.7,
    }).encode("utf-8")
    request = urllib.request.Request(
        api_base + "/chat/completions",
        data=payload,
        headers={
            "Authorization": "Bearer " + api_key,
            "Content-Type": "application/json",
            "User-Agent": "SAI-Core/1.01",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            data = json.loads(response.read().decode("utf-8"))
        answer = data["choices"][0]["message"]["content"]
        if isinstance(answer, list):
            answer = "".join(str(part.get("text", "")) for part in answer if isinstance(part, dict))
        answer = str(answer).strip()
        if not answer:
            raise ValueError("Cloud provider returned an empty answer")
        return ChatResponse(text=answer, model=str(data.get("model", model_name)), device="cloud", language=language, sources=sources)
    except Exception as exc:
        # Do not silently fall back to a local model after a configured cloud provider fails:
        # make the configuration/network error visible instead of unexpectedly loading gigabytes.
        raise HTTPException(status_code=502, detail="Cloud AI request failed: " + str(exc)[:300]) from exc


def needs_web(text: str) -> bool:
    t = text.lower()
    triggers = ("сегодня", "сейчас", "последн", "новост", "цена", "курс", "погода", "найди", "поищи",
                "кто сейчас", "что произошло", "latest", "today", "now", "news", "price", "weather", "search", "find")
    return any(x in t for x in triggers) or "http://" in t or "https://" in t


@lru_cache(maxsize=1)
def chat_pipeline(model_name: str = CHAT_MODEL):
    from transformers import AutoModelForCausalLM, AutoTokenizer
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModelForCausalLM.from_pretrained(
        model_name,
        torch_dtype=DTYPE,
        device_map="auto" if DEVICE == "cuda" else None,
    )
    if DEVICE != "cuda":
        model.to(DEVICE)
    return tokenizer, model


@lru_cache(maxsize=1)
def image_pipeline():
    from diffusers import StableDiffusionPipeline
    pipe = StableDiffusionPipeline.from_pretrained(
        IMAGE_MODEL,
        torch_dtype=DTYPE,
        use_safetensors=True,
    )
    pipe = pipe.to(DEVICE)
    if DEVICE == "cuda":
        pipe.enable_attention_slicing()
    return pipe


@app.get("/health")
def health():
    return {
        "ok": True,
        "service": "sai-core",
        "device": DEVICE,
        "cuda": torch.cuda.is_available(),
        "chat_model": CHAT_MODEL,
        "image_model": IMAGE_MODEL,
    }


@app.post("/v1/chat", response_model=ChatResponse)
def chat(req: ChatRequest):
    selected_model = os.getenv("SAI_CODE_MODEL" if req.mode.upper() == "CODE" else "SAI_FAST_MODEL" if req.mode.upper() == "FAST" else "SAI_SMART_MODEL", CHAT_MODEL)
    tokenizer, model = chat_pipeline(selected_model)
    language = detect_language(req.message)
    sources = web_search(req.message) if req.use_web and needs_web(req.message) else []
    cloud_result = cloud_chat(req, sources, language)
    if cloud_result is not None:
        return cloud_result
    source_text = "\n".join(f"[{i+1}] {s.title} — {s.url}\n{s.snippet}" for i, s in enumerate(sources))
    language_rule = (
        f"Answer ONLY in {language}, matching the user's wording and script. "
        "Do not switch to Chinese, English, or another language unless explicitly asked. "
        "Never output Chinese characters accidentally. " if language == "Russian" else
        f"Answer ONLY in {language}, matching the user's wording and script. "
    )
    system = (
        "You are S.AI, a private general-purpose assistant. Be accurate, useful and natural. "
        "Never invent tool access or sources. " + language_rule +
        f"Mode: {req.mode}. Memory: {req.memory[-6000:]}\n"
        "If web sources are provided, use them for current facts and cite them as [1], [2], etc. "
        "Do not claim you searched if the source list is empty.\nWEB SOURCES:\n" + source_text
    )
    messages = [{"role": "system", "content": system}]
    if req.history:
        messages.append({"role": "user", "content": req.history[-12000:]})
    messages.append({"role": "user", "content": req.message})
    prompt = tokenizer.apply_chat_template(
        messages, tokenize=False, add_generation_prompt=True
    )
    inputs = tokenizer(prompt, return_tensors="pt").to(model.device)
    with torch.inference_mode():
        output = model.generate(
            **inputs,
            max_new_tokens=req.max_tokens,
            do_sample=True,
            temperature=0.7,
            top_p=0.9,
            repetition_penalty=1.05,
        )
    generated = output[0][inputs["input_ids"].shape[1]:]
    text = tokenizer.decode(generated, skip_special_tokens=True).strip()
    return ChatResponse(text=text, model=selected_model, device=DEVICE, language=language, sources=sources)


@app.post("/v1/image", response_model=ImageResponse)
def image(req: ImageRequest):
    try:
        pipe = image_pipeline()
        result = pipe(
            req.prompt,
            width=req.width,
            height=req.height,
            num_inference_steps=req.steps,
        ).images[0]
        buffer = io.BytesIO()
        result.save(buffer, format="PNG", optimize=True)
        encoded = base64.b64encode(buffer.getvalue()).decode("ascii")
        return ImageResponse(
            image_base64=encoded, model=IMAGE_MODEL, device=DEVICE
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@app.post("/v1/search", response_model=SearchResponse)
def search(req: dict):
    query = str(req.get("query", "")).strip()
    if not query:
        return SearchResponse(query="", results=[])
    return SearchResponse(query=query, results=web_search(query, 8))
