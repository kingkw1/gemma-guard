#!/bin/bash
set -e
echo "--- Clearing Logcat ---"
adb logcat -c
echo "--- Building and Running GPU Smoke Test ---"
adb logcat native:E tflite:E GemmaGuard-LiteRT:D GpuSmokeTest:D *:S > diagnostic_log.txt &
LOGCAT_PID=$!
./gradlew :gemmacore-sanitizer:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.gemmaguard.sanitizer.GpuSmokeTest || true
kill $LOGCAT_PID
echo "--- Native Log Snippet ---"
grep -E "INVALID_ARGUMENT|INTERNAL|RET_CHECK|Failed to create Delegate" diagnostic_log.txt | tail -n 20
echo "--- Diagnostic Complete ---"
