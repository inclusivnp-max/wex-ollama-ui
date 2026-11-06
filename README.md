# WexCoder — Local AI Coding Assistant & Agent Workbench

<p align="left">
  <img src="https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin 2.0+" />
  <img src="https://img.shields.io/badge/UI-Compose%20Multiplatform-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose Desktop" />
  <img src="https://img.shields.io/badge/AI%20Engine-Ollama%20Local-black?style=for-the-badge&logo=ollama&logoColor=white" alt="Ollama Local AI" />
  <img src="https://img.shields.io/badge/Platform-Linux%20Wayland%20%2F%20X11%20%7C%20Windows-lightgrey?style=for-the-badge" alt="Linux and Windows" />
  <img src="https://img.shields.io/badge/Privacy-100%25%20Offline%20%26%20Local-success?style=for-the-badge" alt="Local and Offline" />
</p>

**WexCoder** — лёгкий и полностью локальный автономный AI-ассистент для программирования на базе Jetpack Compose Multiplatform и Ollama. Обеспечивает автономное выполнение задач в проекте (tool calling), контекстный поиск по коду и адаптивный интерфейс с поддержкой тайлинговых Wayland-композиторов (niri, sway).

---

## Возможности

- 🤖 **Автономный агент (Tool Calling):** модель может исследовать кодовую базу, читать файлы, искать код, применять точечные diff-правки, создавать файлы и выполнять безопасные shell-команды внутри корня проекта.
- 🔒 **100% приватность:** работает напрямую с локальным инстансом Ollama (`qwen2.5-coder`, `deepseek-coder`, `llama3.1`, `codellama` и др.). Никаких скрытых облачных запросов и передачи телеметрии.
- 🎨 **Современный Compose Desktop UI:** тёплая терракотовая тема, форматированный Markdown с подсветкой синтаксиса кода, боковая панель истории диалогов и быстрое переключение моделей.
- 🪟 **Tiling Wayland Ready:** встроенный механизм адаптации под Wayland-композиторы (`niri`, `sway`, `hyprland`) с автоматической передачей `_JAVA_AWT_WM_NONREPARENTING=1` для бесшовного ресайза окон.
- 📁 **Контекст проекта:** автоматическое индексирование файлов проекта с фильтрацией кэшей (`.git`, `build`, `node_modules`, `.gradle`) для мгновенного добавления релевантных файлов в промпт.

## Доступные инструменты модели

| Инструмент | Назначение |
| :--- | :--- |
| `list_files` | Просмотр дерева файлов проекта или указанной подпапки |
| `read_file` | Чтение содержимого файла с лимитом размера |
| `write_file` | Создание или перезапись файла |
| `edit_file` | Точечная замена блоков кода (поиск и замена фрагмента) |
| `search_files` | Поиск по содержимому файлов с регулярными выражениями |
| `run_command` | Выполнение сборочных или проверочных команд в корне проекта |

---

## Сборка и запуск

### Требования
- JDK 17 или новее
- Запущенный локальный сервер [Ollama](https://ollama.com/) (по умолчанию `http://localhost:11434`)

### Запуск из терминала
```bash
# Запуск приложения
./gradlew :app:run

# Сборка дистрибутива
./gradlew :app:packageDistributionForCurrentOS
```
