
# Data Schema & State Contracts: GemmaGuard

## 1. Overview
This document defines the strict data contracts between the Kotlin UI layer, the LiteRT C++ bindings, and the Gemma 4 LLM. To prevent serialization errors, all agents must adhere to these exact structures when generating data classes and parsing logic.

## 2. Profile & State Models
The application must support dynamic state switching based on user profiles. The UI should initialize with three default profiles tailored to specific developmental stages (Ages 4, 6, and 7).

### 2.1 User Profile Structure (Kotlin Data Class Reference)
```kotlin
data class UserProfile(
    val id: String,
    val name: String,
    val age: Int,
    val toleranceConfig: ToleranceConfig
)

data class ToleranceConfig(
    val maxProfanitySeverity: Int,     // Range 1-5
    val maxViolenceSeverity: Int,      // Range 1-5
    val maxThematicSeverity: Int,      // Range 1-5
    val blockMeanLanguage: Boolean     // e.g., "stupid", "idiot"
)
```

### 2.2 Default Profile Thresholds (For the Demo)
* **Age 4 Profile:** `maxProfanitySeverity = 0`, `maxViolenceSeverity = 0`, `maxThematicSeverity = 0`, `blockMeanLanguage = true`. (Strict lockdown, blocks everything).
* **Age 6 Profile:** `maxProfanitySeverity = 1`, `maxViolenceSeverity = 1`, `maxThematicSeverity = 1`, `blockMeanLanguage = true`. (Allows very mild slapstick/action, but no cursing or mean insults).
* **Age 7 Profile:** `maxProfanitySeverity = 2`, `maxViolenceSeverity = 2`, `maxThematicSeverity = 2`, `blockMeanLanguage = false`. (Allows mild, non-aggressive words like "crap" or "idiot", but strictly blocks heavy swearing and intense thematic scenes).

### 2.3 Performance Metrics State (Debug Overlay)
The Kotlin UI must maintain a state object to display hardware performance to the judges, populated by the C++ LiteRT layer after every inference.
```kotlin
data class InferenceMetrics(
    val timeToFirstTokenMs: Long,
    val totalInferenceTimeMs: Long,
    val tokensPerSecond: Float,
    val memoryUsageMb: Int
)
```

## 3. Gemma 4 Output Schema (The JSON Manifest)
When the Gemma 4 LiteRT model processes a transcript chunk, it MUST return a strictly formatted JSON array of flagged items. The C++ JNI layer will pass this string to Kotlin for parsing.

### 3.1 JSON Structure
```json
[
  {
    "timestamp_start": 12500,
    "timestamp_end": 14200,
    "text": "What the hell is this?",
    "category": "Profanity",
    "severity": 2,
    "reasoning": "Mild profanity used as an exclamation."
  },
  {
    "timestamp_start": 45000,
    "timestamp_end": 48500,
    "text": "You're such an ugly idiot.",
    "category": "Mean Language",
    "severity": 1,
    "reasoning": "Aggressive, insulting language directed at a character."
  }
]
```

### 3.2 Schema Rules for the LLM Prompt
* `timestamp_start` and `timestamp_end`: Must be integers representing milliseconds.
* `category`: Must be one of `["Profanity", "Violence", "Thematic", "Mean Language"]`.
* `severity`: Must be an integer from `1` to `5` (1 = Mildest, 5 = Most Severe).
* `reasoning`: A concise (under 15 words) explanation of why the context triggered the flag. This will be displayed on the Human-in-the-Loop review dashboard.

## 4. UI Filtering Logic (The HITL Dashboard)
When a user selects a profile, the Kotlin ViewModel parses the JSON manifest against the active `ToleranceConfig`. 
* Any JSON item where the `severity` exceeds the profile's `max[Category]Severity` is automatically checked (flagged for cutting).
* The user is presented with the dashboard and can manually uncheck an item to override the profile's default logic before execution.

