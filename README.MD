# GemmaGuard 🛡️

**Zero-Trust, On-Device Media Sanitization for the Creator Economy**

GemmaGuard is a completely offline, privacy-first Android application that empowers parents to sanitize the "wild west" of digital media—home videos, downloaded podcasts, screen recordings, and social media clips—without ever sending personal data to a cloud server. 

By leveraging **Gemma 4 E2B (2 Billion Parameter)** running locally via **LiteRT**, GemmaGuard provides contextual, nuanced content moderation directly on mobile silicon.

## The Problem
The modern media landscape isn't just Hollywood movies with standardized PG ratings; it is dominated by User-Generated Content (UGC), independent creators, and shared personal clips. Parents need a way to filter this media, but uploading personal home videos or downloaded files to a centralized corporate cloud API for moderation is a massive privacy violation.

## Why Gemma 4? (Beyond the Blocklist)
We initially considered using a standard regex parser and a hardcoded blocklist. However, static word lists are fundamentally broken for child safety. We utilized Gemma 4 E2B because true safety requires understanding meaning, not just matching text.

1. **Zero-Profanity Toxicity (Thematic Risks):** A traditional blocklist is blind to dangerous ideas. It will pass phrases like *"nobody likes you, jump off a bridge"* or *"you should starve yourself"* because the vocabulary is technically clean. Gemma 4 provides semantic reasoning to flag bullying, violence, and self-harm based on intent, acting as a thematic safety engine.
2. **Algorithmic Slang ("Algo-Speak"):** The internet moves too fast for static dictionaries. Creators constantly invent new words to bypass standard platform filters (e.g., using "unalive" instead of "kill"). Because Gemma 4 is a foundational model trained on the modern internet, it inherently understands algorithmic evasion.
3. **Granular Severity Scoring:** Parenting is not binary. A sarcastic insult that flies over a 4-year-old's head might be immediately repeated by a 6-year-old or 7-year-old sibling. Gemma evaluates the context and assigns a dynamic Severity Score (1-5), allowing the UI to provide age-appropriate, sliding-scale filtration rather than a rigid block/allow system.

## The GemmaGuard Solution
GemmaGuard acts as a secure, neutral sandbox. Users can select any local `.mp4` video on their device. The app asynchronously processes the video in the background, flags contextual risks with customized severity scores, and allows the parent to automatically mute the offending audio blocks—all while in Airplane Mode.

### Why Edge AI? 
We explicitly chose to run a 4-bit quantized Gemma model on the edge rather than calling a massive cloud API for three strategic reasons:
1. **Absolute Privacy:** The user's media never leaves the device. Complete peace of mind for family recordings.
2. **Infinite Scalability at Zero Cost:** By utilizing the user's local Snapdragon processor (via XNNPACK), we eliminate recurring cloud token costs, allowing the app to be financially sustainable.
3. **Air-Gapped Reliability:** GemmaGuard works in cellular dead zones, on road trips, and at 30,000 feet. 

## Technical Architecture: The Dual-Pipeline

Running generative AI on mobile hardware presents severe constraints—specifically, the high computational cost of text decoding (generation) and strict KV cache memory limits. To achieve rapid processing times without Out-Of-Memory (OOM) crashes, GemmaGuard utilizes a highly optimized **Neuro-Symbolic Architecture**.

### 1. Offline Speech-to-Text (The Pre-Processor)
Large Language Models are not optimized for raw audio extraction on mobile. Instead of forcing Gemma to process audio tensors, GemmaGuard utilizes Android's native `SpeechRecognizer` (with `EXTRA_PREFER_OFFLINE = true`) to transcribe the video's audio into text blocks with timestamps in real-time, utilizing zero battery-draining cloud APIs.

### 2. Neuro-Symbolic Inference (The Cognitive Engine)
The generated text chunks are passed sequentially to the **Gemma 4 E2B LiteRT** model. 
To bypass the severe latency of JSON serialization on mobile chips (which often causes 30+ second execution times and model hallucinations), we constrain the LLM's output. The model is prompted using a strict few-shot template to act purely as a semantic classifier, outputting a hyper-minimal, 3-token piped string:
`word|category|severity` (or `CLEAN`).

### 3. Deterministic Kotlin Guardrails (The Structural Engine)
The Kotlin `MainViewModel` acts as the structural safety net. It catches the LLM's piped output in under 7 seconds per chunk, parsing it deterministically. Kotlin then merges this semantic data with the timestamp data from the STT engine to construct the `FlaggedItem` state models. This completely eliminates UI-blocking hallucinations and native C++ memory leaks.

### 4. FFmpeg Audio Manipulation
Once the parent reviews the Human-In-The-Loop (HITL) dashboard and approves the moderation, GemmaGuard uses an embedded FFmpeg binary to execute precise block-level audio muting on the flagged timestamps, generating a clean, safe copy of the media file directly in the Android cache.

## Tech Stack
* **UI:** Jetpack Compose (Material 3 Dark Mode)
* **AI Model:** Gemma 4 E2B (CPU INT-4 Quantized)
* **Inference Engine:** Google MediaPipe `tasks-genai` (LiteRT)
* **Transcription:** Android Native Offline `SpeechRecognizer`
* **Media Processing:** FFmpeg-Kit Android