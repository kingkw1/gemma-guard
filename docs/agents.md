# Agent Directives & Architecture Standards

## 1. Tech Stack & Language 
* **Primary Language:** Kotlin (Latest stable version). Absolutely no Java.
* **UI Framework:** Jetpack Compose exclusively. Do not generate any XML layout files.
* **Architecture:** MVVM (Model-View-ViewModel). State should be hoisted and managed via StateFlow.
* **Project Structure (Multi-Module):** The Android project MUST be structured as a multi-module repository.
    * :app (Contains the Jetpack Compose UI, Navigation, and GemmaGuard specific ViewModels).
    * :gemmacore-sanitizer (A standalone Android Library module containing the LiteRT C++ bindings, the FFmpeg execution wrapper, and the LLM prompt formatting). The :app module will depend on this library.

## 2. Dependencies & Build Tools
* Use `build.gradle.kts` (Kotlin DSL) for all Gradle scripts.
* Use Version Catalogs (`libs.versions.toml`) for all dependency management. Do not hardcode version numbers in the build scripts.

## 3. C++ / LiteRT Bindings (Crucial)
* All native code for the Gemma 4 E2B LiteRT model must use CMake and JNI. 
* Prioritize aggressive memory management in the C++ layer to prevent Out-Of-Memory (OOM) exceptions when passing strings back to the Kotlin layer.

## 4. Workflow Rules
* **No Ghost Code:** Do not write placeholder functions or `// TODO: Implement later` comments without explicit permission. Write complete, functional slices.
* **File Scoping:** Keep composables modular. Do not write UI files exceeding 200 lines; break them down into smaller components.
* **Accessibility by Default:** All Jetpack Compose interactive elements MUST include semantic modifiers (e.g., contentDescription, stateDescription). The HITL dashboard must be fully navigable via Android TalkBack.