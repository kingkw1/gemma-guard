# GemmaGuard Project Roadmap

## Phase 1: Core Inference (COMPLETE ✅)
- [x] **LiteRT Integration**: Loaded Gemma 2B LiteRT model on S23.
- [x] **Neuro-Symbolic Classifier**: Implemented `PipedStringParser` for deterministic `word|category|severity` parsing.

## Phase 2: Dual-Pipeline & Audio Processing (COMPLETE ✅)
- [x] **FFmpeg Integration**: Integrated community-maintained `antonkarpenko:ffmpeg-kit-full:2.1.0`.
- [x] **Audio Muting**: Implemented `FFmpegWrapper` for silent block-level muting.
- [x] **Offline STT**: Built `SpeechToTextEngine.kt` wrapping Android's native offline `SpeechRecognizer`.

## Phase 3: "Bring Your Own File" (BYOF) Integration (COMPLETE ✅)
- [x] **File Resolver**: Implemented `FileResolver` to copy gallery videos to app cache.
- [x] **Pipeline Wiring**: Full orchestration in `MainViewModel`: Extract WAV -> Run STT -> Gemma Loop -> FFmpeg Mute.
- [x] **Native Picker**: Added `ActivityResultContracts.OpenDocument` in `MainActivity`.

## Phase 4: UI/UX Polish & Hackathon Deliverables (IN PROGRESS 🏗️)
- [ ] **Aesthetics**: Apply Glassmorphism and vibrant dark mode themes.
- [ ] **HITL Dashboard**: Polish contextual preview player and manual override toggles.
- [ ] **Video Pitch**: Prepare for final screen recording demo in Airplane Mode.
