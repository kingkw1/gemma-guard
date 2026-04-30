# Project Mission: GemmaGuard
**Target:** Gemma for Good Hackathon 
**Tracks:** Safety & Trust ($10k) | LiteRT Special Technology ($10k)

## The Core Concept
GemmaGuard is a sovereign, offline-first Android application designed to intelligently sanitize media (video/audio) for households. It uses a quantized Gemma 4 LiteRT model running locally on the device to analyze text transcripts contextually. Instead of blindly blocking a hardcoded list of words, it understands the difference between aggressive profanity, frightening thematic elements, and mild language, adjusting its severity based on the user's developmental age profile (e.g., Ages 4, 6, or 7).

## The "Wow" Factor (Hackathon Narrative)
We are proving that frontier-level multimodal AI can run in a completely privacy-preserving, network-denied environment (like a tablet in the back of a car). 
Instead of just muting flagged audio—which ruins the narrative pacing of a movie—the app uses a local Text-to-Speech library to overdub comedic replacements (e.g., replacing an F-bomb with a goofy voice saying "Oh biscuits!"). 

## The Agent Directives (Antigravity Code Rules)
When generating specifications or writing code for this workspace, all agents MUST adhere to the following rules:
1. **Privacy Absolute:** No code may utilize external cloud APIs (no Gemini, no OpenAI, no network telemetry). Execution must be 100% local.
2. **Track Compliance:** The architecture must prioritize the deployment of the Gemma 4 E2B LiteRT model on Android. Code must reflect C++/Kotlin bindings for local inference.
3. **The HITL Interface:** The UI must be a Human-in-the-Loop (HITL) dashboard. The AI does not make the final cut; it generates a structured JSON manifest of flagged timestamps with its reasoning, which the human user reviews and approves before FFmpeg execution.
4. **Demo Readiness:** The app will ship with 3 pre-loaded media clips in the `assets` folder to ensure a frictionless, offline live demo for the judges, alongside a "Load Local File" intent. 

## Reference Material
Agents must cross-reference all architectural decisions with:
* `hackathon_rubric.md` (For scoring optimization and constraints)
* `mvp_constraints.md` (For hardware limitations and fail-fast technical spikes)