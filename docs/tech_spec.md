# GemmaGuard Technical Specification

## 1. Architecture Overview
GemmaGuard adheres to a strict offline-first, multi-module Android architecture utilizing Kotlin, Jetpack Compose, and an MVVM design pattern.

### 1.1. Multi-Module Project Structure
The repository is split into two primary modules:
* **`:app` (Application Module):** Contains the Jetpack Compose UI, Navigation, and GemmaGuard-specific ViewModels. Manages state via `StateFlow` and handles the HITL dashboard. Absolutely no Java or XML layouts.
* **`:gemmacore-sanitizer` (Library Module):** A standalone Android library housing the LiteRT C++ bindings, the FFmpeg execution wrapper, and LLM prompt formatting. The `:app` module depends on this library.

### 1.2. Build & Dependency Management
* **Build System:** Kotlin DSL (`build.gradle.kts`).
* **Dependency Management:** Version Catalogs (`libs.versions.toml`) to avoid hardcoded version numbers.

### 1.3. File & URI Resolution
The `:gemmacore-sanitizer` module MUST contain a robust resolver capable of handling both internal APK assets/streams (for the pre-loaded demo) and external `content://` URIs (from the user intent). It must convert or copy these into absolute file paths or valid file descriptors before passing them to the native C++ LiteRT layer and the FFmpeg binary.

## 2. LiteRT C++ Bindings
The core AI execution relies on running the Gemma 4 E2B LiteRT quantized model on-device.
* **JNI & CMake:** All native code interfacing with the model uses CMake and JNI.
* **Memory Management:** The C++ layer is responsible for aggressive memory management to prevent Out-Of-Memory (OOM) exceptions when passing strings (like the JSON manifest) back to the Kotlin layer.
* **Data Contracts:** The C++ layer returns a strictly formatted JSON string to Kotlin, adhering to the defined schema (`timestamp_start`, `timestamp_end`, `text`, `category`, `severity`, `reasoning`).

## 3. Local FFmpeg Execution Pipeline
Audio manipulation is executed via a local FFmpeg binary.
* **Transcript-First:** The primary inference pipeline reads `.vtt` or `.srt` text transcripts rather than raw audio to ensure latency remains under 15 seconds. (Native audio processing is a secondary stretch goal.)
* **Comedic Overdubbing:** Instead of silent muting, the FFmpeg wrapper coordinates with a local Android Text-to-Speech library (or a fast edge TTS model) to inject distinct, comedic replacements (e.g., "fudge!") over the flagged timestamps. Exact-match voice cloning is out of scope.
* **Execution Constraint:** Zero cloud dependencies. No external APIs (Gemini, OpenAI, etc.) are permitted. The entire execution, including TTS generation and FFmpeg muxing, runs locally.

## 4. UI & State Management
* **State Contracts:** The UI relies on predefined data classes (`UserProfile`, `ToleranceConfig`, `InferenceMetrics`).
* **MVVM & StateFlow:** ViewModels parse the JSON manifest against the active `ToleranceConfig` to determine auto-flagged states, hoisting the state to the Compose UI.
* **Contextual Previews:** Requires `androidx.media3:media3-exoplayer` to handle the inline video playback. The Compose UI must seamlessly seek between `timestamp_start` and `timestamp_end` when a user taps a flagged item.
* **Performance Metrics:** A debug overlay observes the `InferenceMetrics` state object populated by the C++ LiteRT layer.
* **Accessibility:** All Jetpack Compose interactive elements MUST include semantic modifiers (e.g., `contentDescription`, `stateDescription`). The HITL dashboard must be fully navigable via Android TalkBack.
