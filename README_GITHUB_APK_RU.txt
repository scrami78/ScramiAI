SCRAMI AI — сборка APK через GitHub Actions

1. Создай новый репозиторий на GitHub, например ScramiAI.
2. Распакуй этот ZIP.
3. Загрузи ВСЕ файлы и папки проекта в корень репозитория.
   В корне должны быть settings.gradle.kts, build.gradle.kts и папка app.
4. В GitHub открой вкладку Actions.
5. Выбери "Build SCRAMI AI APK".
6. Нажми "Run workflow".
7. После окончания зелёной сборки открой результат запуска и скачай artifact
   "SCRAMI-AI-debug". Внутри будет app-debug.apk.

Важно: API-ключ OpenAI НЕ добавляй в GitHub и НЕ записывай в workflow.
Ключ вводится уже внутри приложения SCRAMI AI.

Workflow использует GitHub Actions, Java 17, Android SDK 35 и Gradle 8.10.
