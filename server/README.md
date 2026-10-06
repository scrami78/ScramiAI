# S.AI Core 7.0

Free/local-first backend for S.AI.

Stack:
- Python + PyTorch + Transformers: local LLM inference.
- Diffusers + PyTorch: local image generation.
- CUDA: automatic GPU acceleration through PyTorch plus an optional custom kernel.
- Go: HTTP gateway between clients and the AI engine.
- Rust: deterministic memory/index key primitive.
- Android Kotlin + C++: offline phone client and native local inference.
- React/Next.js: web client can use the same gateway.

Run the AI engine:
1. cd server/ai
2. python -m venv .venv
3. source .venv/bin/activate
4. pip install -r requirements.txt
5. uvicorn main:app --host 0.0.0.0 --port 8000

Optional model variables:
SAI_CHAT_MODEL=Qwen/Qwen2.5-3B-Instruct
SAI_IMAGE_MODEL=stabilityai/stable-diffusion-2-1-base

No paid API key is required. Models are downloaded from their public model repositories on first use. GPU is optional; CUDA is used automatically when NVIDIA CUDA is available.

Run the gateway:
1. cd server/go-gateway
2. SAI_AI_URL=http://127.0.0.1:8000 go run .
3. Gateway listens on http://127.0.0.1:8787.

The Android client can use the gateway as its S.AI Core endpoint. The bundled Android model remains available as a private offline fallback.
