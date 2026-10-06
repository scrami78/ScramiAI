# S.AI native architecture

S.AI uses a Kotlin + native C++ architecture.

- Kotlin owns the Android UI, lifecycle, storage, speech, and application logic.
- The bundled llama Android runtime performs local GGUF inference through a native C/C++ backend.
- app/src/main/cpp contains the S.AI native bridge used for low-level prompt preparation and engine diagnostics.
- JNI keeps the UI independent from the native implementation.

Python is reserved for backend/services and model tooling.
