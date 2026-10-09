# S.AI 1.01 — rebuild plan

## Product target
S.AI 1.01 is an internet-only Android AI assistant. The app must not download or bundle a local language model; chat inference is performed by a remote provider through a backend gateway. The mobile app must never contain provider API keys.

## Visual contract
- Strict monochrome dark interface: black background, white text/icons, restrained charcoal surfaces.
- Header: hamburger/history on the left, centered S.AI wordmark, new-chat icon (chat bubble with plus) on the right.
- Empty state: large S.AI wordmark and the short tagline “Твой ИИ-помощник. Задавай вопросы, получай ответы, создавай, думай.”
- Composer: plus attachment button, placeholder “Напиши сообщение…”, microphone, white send button.
- Attachment menu only: Камера → Фото → Файлы. No “Создать изображение” entry in this menu.
- History drawer: only S.AI heading and chat history list, plus clear-history action; no unrelated sections.
- No bottom tab bar, decorative suggestion cards, fake status labels, or unnecessary account button.

## Functional requirements
- Online-only assistant with clear network/offline error and retry; do not silently fall back to a tiny local model.
- Fast response experience: streaming output when the backend supports it, cancel generation, clear loading states, bounded retries and request timeouts.
- Persistent conversation history, new chat, open prior chat, rename/delete chat.
- Camera capture, photo picker, document picker; image understanding and generation/editing are separate backend capabilities.
- Speech-to-text and optional text-to-speech with permission handling.
- All controls must have real behavior or must not be shown.
- Respect selected UI language; default to Russian based on device/app language.
- Accessibility labels, responsive layouts, secure HTTPS transport, no secrets in APK.

## Important constraints
“Knows everything” and “zero bugs” cannot be guaranteed. Quality must be improved with tests and real-device checks. Free access depends on the chosen inference/image providers and their quotas; the app can be free to download, but unlimited inference and image generation cannot be promised without a funded backend.