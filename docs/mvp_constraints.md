# MVP Constraints: GemmaGuard (Hackathon Submission)

## 1. Primary Objective
Deliver a functional, offline-first Android application demonstrating edge-based contextual media sanitization utilizing the Gemma 4 LLM. The MVP must satisfy the requirements for the "Gemma for Good" hackathon, specifically targeting the **Safety & Trust** and **LiteRT** prize tracks within a 19-day development window.

## 2. The Critical Path (Fail-Fast Protocol)
The highest risk to this project is the hardware execution layer. UI development is secondary until the following architectural spikes are validated on target consumer hardware (specifically a Samsung Galaxy S23):
* **Constraint 2.1 (Model Loading):** The application must successfully load the Gemma 4 E2B (or smallest viable variant) LiteRT quantized model into memory on the device without triggering an Out-Of-Memory (OOM) crash.
* **Constraint 2.2 (Inference Latency):** The model must parse a 30-second text transcript chunk and output a structured JSON severity manifest within an acceptable user-waiting threshold (under 15 seconds). 
* **Constraint 2.3 (Audio Fallback):** While Gemma 4 supports native audio processing, running audio-inferences on mobile edge hardware is highly experimental and compute-intensive. **For the MVP, audio-processing is designated as a secondary stretch goal.** The primary inference pipeline *must* rely on reading `.vtt` / `.srt` text transcripts to guarantee a successful demo. 

## 3. Hardware & Architecture Constraints
* **Zero Cloud Dependency:** The application must execute 100% locally. Any API calls to Gemini, OpenAI, or external cloud inference engines are strictly forbidden. The system must function entirely offline (Airplane Mode).
* **Orchestration:** Data routing between the model inference, UI state, and audio execution must be managed by a lightweight local agentic framework or deterministic state machine.
* **Execution:** Audio manipulation (muting or overdubbing) must utilize a locally compiled `FFmpeg` binary or equivalent lightweight Android media framework. 

## 4. Feature Scope (The Sandbox Demo)
To avoid copyright issues and unpredictable file-parsing bugs during the hackathon judging, the live demo will operate as a tightly controlled sandbox.
* **Pre-loaded Media:** The app will ship with three pre-packaged 30-second clips in the `assets` folder:
    1.  A clip containing explicit profanity (e.g., *Cosmos Laundromat* open-source film).
    2.  A clip with intense/frightening thematic elements but no explicit profanity.
    3.  A borderline clip with mild language/insults.
* **Developmental Toggles:** The Human-in-the-Loop (HITL) UI must feature three distinct age profiles (Ages 4, 6, and 7) to demonstrate how the LLM dynamically adjusts its severity flagging and contextual reasoning based on the specific developmental stage of the user. 
* **The "Bring Your Own File" Feature:** The UI must contain an intent button allowing judges to load a local `.mp4` and `.vtt` file from their device storage to prove the architecture is not hardcoded.

## 5. Audio Dubbing (The "Wow" Factor)
* **No Voice Cloning:** Attempting seamless, exact-match voice cloning (zero-shot TTS) requires excessive compute and introduces copyright liability. This is strictly out of scope.
* **Comedic Replacement:** The MVP will utilize a lightweight Android Text-to-Speech library (or a fast, tiny edge TTS model) to inject highly distinct, comedic substitute phrases (e.g., "oh biscuits!", "fudge!") over flagged timestamps.

## 6. Out of Scope for MVP
* User accounts, authentication, or cloud-syncing profiles.
* Live YouTube URL fetching (`yt-dlp` integration) via the mobile app, to maintain the strict offline-first narrative. 
* Real-time live streaming sanitization (the app will process static, pre-downloaded files).