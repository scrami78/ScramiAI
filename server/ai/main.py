import base64
import io
import os
from functools import lru_cache

import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="S.AI Core", version="7.0")

CHAT_MODEL = os.getenv("SAI_CHAT_MODEL", "Qwen/Qwen2.5-3B-Instruct")
IMAGE_MODEL = os.getenv("SAI_IMAGE_MODEL", "stabilityai/stable-diffusion-2-1-base")
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
DTYPE = torch.float16 if DEVICE == "cuda" else torch.float32


class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=20000)
    history: str = ""
    memory: str = ""
    mode: str = "SMART"
    max_tokens: int = Field(default=900, ge=64, le=4096)


class ChatResponse(BaseModel):
    text: str
    model: str
    device: str


class ImageRequest(BaseModel):
    prompt: str = Field(min_length=1, max_length=4000)
    width: int = Field(default=768, ge=256, le=1536)
    height: int = Field(default=768, ge=256, le=1536)
    steps: int = Field(default=25, ge=5, le=80)


class ImageResponse(BaseModel):
    image_base64: str
    model: str
    device: str


@lru_cache(maxsize=1)
def chat_pipeline():
    from transformers import AutoModelForCausalLM, AutoTokenizer
    tokenizer = AutoTokenizer.from_pretrained(CHAT_MODEL)
    model = AutoModelForCausalLM.from_pretrained(
        CHAT_MODEL,
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
    tokenizer, model = chat_pipeline()
    system = (
        "You are S.AI, a private general-purpose assistant. "
        "Be accurate, useful, concise when possible, and never invent tool access. "
        f"Mode: {req.mode}. Memory: {req.memory[-6000:]}"
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
    return ChatResponse(text=text, model=CHAT_MODEL, device=DEVICE)


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
