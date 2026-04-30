# GemmaGuard Functional Specification

## 1. Introduction
This functional specification details the user journey and Human-in-the-Loop (HITL) dashboard for GemmaGuard, an offline-first Android application that intelligently sanitizes media using a local Gemma 4 LiteRT model.

## 2. User Journey

### 2.1. App Launch & Media Selection
Upon launching the application, the user is presented with a sandbox environment designed for a seamless, offline demo.
* **Pre-loaded Assets:** The user can select from three pre-packaged 30-second clips:
  1. A clip containing explicit profanity.
  2. A clip with intense/frightening thematic elements (no explicit profanity).
  3. A borderline clip with mild language/insults.
* **Bring Your Own File:** Alternatively, the user can tap the "Load Local File" intent to select an `.mp4` and corresponding `.vtt`/`.srt` transcript from their device storage.

### 2.2. Profile Selection
Before processing, the user selects a developmental age profile, which dictates the strictness of the AI filtering:
* **Age 4 Profile:** Strict lockdown (blocks everything).
* **Age 6 Profile:** Allows very mild slapstick/action; no cursing or mean insults.
* **Age 7 Profile:** Allows mild, non-aggressive words; strictly blocks heavy swearing and intense thematic scenes.

### 2.3. Model Inference
Once the media and profile are selected, the Gemma 4 LiteRT model processes the text transcript locally. A debug overlay displays real-time performance metrics to the user (Time to First Token, Total Inference Time, Tokens/Sec, Memory Usage). The model generates a JSON manifest of flagged timestamps with severity scores and reasoning.

### 2.4. Human-in-the-Loop (HITL) Review Dashboard
The UI transitions to the HITL Dashboard, presenting the AI's findings.
* **Auto-Flagging:** The app automatically checks (flags for cutting) any item where the AI's severity score exceeds the selected profile's `ToleranceConfig`.
* **Contextual Previews:** The user can tap any flagged item in the list to instantly play the corresponding video clip in a localized mini-player (using the `timestamp_start` and `timestamp_end` from the JSON). This allows the user to verify the AI's reasoning contextually before making an approval decision.
* **Manual Override:** After reviewing, the user can manually uncheck an item to override the AI and allow the content.
* **Accessibility:** The dashboard is fully navigable via Android TalkBack, ensuring semantic modifiers are present on all interactive elements.

### 2.5. Execution & Playback
After the user approves the final manifest, the app executes the local FFmpeg pipeline. Instead of muting the audio, the app uses a local Text-to-Speech library to inject comedic substitute phrases (e.g., "Oh biscuits!") over the flagged timestamps. The sanitized video is then presented for playback.
