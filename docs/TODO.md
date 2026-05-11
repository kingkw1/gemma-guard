# GemmaGuard Comprehensive Roadmap & TODO

## Phase 1: Core AI Inference & Feasibility Validation (PRIORITY 1)
*Goal: Prove that the S23 can load and execute Gemma 2B LiteRT on a transcript chunk under 15 seconds without OOM.*
- [x] **Procure Model**: Download the `gemma-1.1-2b-it-cpu-int4` or `gemma-2-2b-it-cpu-int4` (4-bit quantized) LiteRT `.tflite` model.
- [x] **Load Model**: Push `.tflite` model to the Android device and implement model loading using the official `com.google.mediapipe:tasks-genai` Kotlin API.
- [x] **Benchmark Inference**: Feed a 30-second `.vtt` transcript into the model on the S23.
- [x] **Validation Metric**: Output time-to-first-token, generation speed (tokens/sec), and RAM usage. 
- [x] **Neuro-Symbolic Architecture Pivot**: Shift from LLM-generated JSON to minimal piped string (`word|category|severity`) for deterministic Kotlin parsing, drastically reducing inference latency.

## Phase 2: FFmpeg Audio Processing Pipeline
*Goal: Successfully splice out bad words and overdub comedic TTS without cloud APIs.*
- [ ] **Android TTS Integration**: Finalize `FFmpegWrapper.kt` to generate temporary `.wav` files of comedic phrases (e.g., "Oh biscuits").
- [ ] **Compile/Include FFmpeg**: Integrate a lightweight, pre-compiled Android FFmpeg library (e.g., `com.arthenica:ffmpeg-kit-video` or similar).
- [ ] **Audio Splicing Command**: Write the complex FFmpeg command to:
  1. Extract audio from `.mp4`.
  2. Mute specific timestamp windows (`timestamp_start` to `timestamp_end`).
  3. Overlay the TTS `.wav` files at those exact timestamps.
  4. Mux the new audio track back with the original video.
- [ ] **Muxing Performance Test**: Benchmark how long FFmpeg takes to mux a 30-second clip on the S23.

## Phase 3: "Bring Your Own File" (BYOF) & File Management
*Goal: Allow users to select arbitrary video files.*
- [ ] **Intent Implementation**: Add `ACTION_OPEN_DOCUMENT` intent for selecting `.mp4` and `.vtt` pairs.
- [ ] **File Resolver Upgrade**: Use `ContentResolver` to copy selected files into the app's cache directory, as FFmpeg native binaries cannot read directly from Android `content://` URIs securely.
- [ ] **Chunking Logic**: Implement a Kotlin utility to break long `.vtt` files into 30-second semantic chunks to feed into Gemma incrementally (preventing context bloat and long inference delays).

## Phase 4: UI/UX Polish & HITL Dashboard
*Goal: Finalize the app's rich aesthetics and accessibility.*
- [ ] **Dashboard Styling**: Apply the modern, dark-mode design system to the `HitlDashboardScreen`.
- [ ] **ExoPlayer Synchronization**: Ensure the Contextual Preview player seamlessly seeks to the flagged timestamp and loops the specific 3-second window for the reviewer.
- [ ] **Performance Overlay**: Display the live `InferenceMetrics` prominently during the "Processing" state to impress hackathon judges.

## Phase 5: Automated Testing & Demo Preparation
*Goal: Ensure the demo runs flawlessly offline for the judges.*
- [ ] **Write Unit Tests**: Test the deterministic string parsing logic in `MainViewModel` and `VttParser` for edge cases.
- [ ] **Airplane Mode Verification**: Conduct a full end-to-end run on the S23 with WiFi/Cellular completely disabled.
