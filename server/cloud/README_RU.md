# S.AI Cloud Core 1.01

Это лёгкий облачный API без PyTorch и скачивания большой локальной модели. Он подходит для развёртывания на обычном Python/Docker-хостинге и проксирует запросы в OpenAI-compatible API.

## Переменные окружения

- `SAI_API_BASE` — адрес провайдера, например `https://openrouter.ai/api/v1`.
- `SAI_API_KEY` — секретный API-ключ провайдера. Добавляй его только в секреты хостинга, не в приложение и не в Git.
- `SAI_CLOUD_MODEL` — доступный модели ID у выбранного провайдера. По умолчанию `openai/gpt-oss-20b:free`, но наличие бесплатной модели и лимиты определяет провайдер.

## Запуск

```bash
cd server/cloud
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export SAI_API_BASE=https://openrouter.ai/api/v1
export SAI_API_KEY=your-secret-key
export SAI_CLOUD_MODEL=openai/gpt-oss-20b:free
uvicorn main:app --host 0.0.0.0 --port 8000
```

Проверка: `GET /health`. Android-клиент использует `POST /v1/chat`, `POST /v1/search` и `POST /v1/image`. Генерация изображений здесь пока возвращает 501, пока отдельный image provider не настроен. Бесплатные лимиты не гарантированы и могут меняться.
