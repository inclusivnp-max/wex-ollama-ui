# Wex Ollama UI — Desktop Client for Local AI

<p align="left">
  <img src="https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin 2.0+" />
  <img src="https://img.shields.io/badge/UI-Compose%20Multiplatform-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose Desktop" />
  <img src="https://img.shields.io/badge/Backend-Ollama%20Local%20API-black?style=for-the-badge&logo=ollama&logoColor=white" alt="Ollama Local API" />
  <img src="https://img.shields.io/badge/Platform-Linux%20Wayland%20%2F%20X11%20%7C%20Windows-lightgrey?style=for-the-badge" alt="Linux and Windows" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Local%20%26%20Offline-success?style=for-the-badge" alt="Local and Offline" />
</p>

**Wex Ollama UI** — быстрый, лёгкий и полностью приватный десктопный интерфейс (GUI) для общения с локальными нейросетями через Ollama. Написан на Kotlin и Jetpack Compose Multiplatform с нативной адаптацией под Wayland и X11.

---

## Особенности

- 💬 **Общение с локальными моделями:** прямое подключение к локальному API Ollama без сторонних облачных посредников (поддержка `llama3`, `qwen2.5`, `deepseek-coder`, `mistral`, `gemma` и любых других моделей).
- 🔒 **100% приватность:** весь контекст, переписка и файлы остаются исключительно на вашей машине.
- 🎨 **Современный Compose Desktop UI:** стильная терракотовая тема, форматированный Markdown с подсветкой синтаксиса блоков кода, боковая панель с историей диалогов и быстрый выбор установленных моделей.
- 🪟 **Tiling Wayland Ready:** бесшовная поддержка тайлинговых композиторов (`niri`, `sway`, `hyprland`) с автоматическим обходом ограничений AWT (`_JAVA_AWT_WM_NONREPARENTING=1`).
- 🛠️ **Интеграция с рабочим пространством (Tool Calling):** возможность взаимодействия модели с файлами выбранного проекта (чтение, поиск, правки и запуск команд при наличии поддержки в модели).

---

## Сборка и запуск

### Требования
- JDK 17 или новее
- Запущенный локальный инстанс [Ollama](https://ollama.com/) (по умолчанию `http://localhost:11434`)

### Запуск из терминала
```bash
# Запуск интерфейса
./gradlew :app:run

# Сборка нативного пакета для вашей ОС
./gradlew :app:packageDistributionForCurrentOS
```
