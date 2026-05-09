#!/bin/bash

# Clear the phone's log to prevent reading old data
adb logcat -c

echo "Compiling and running hardware benchmark on Galaxy S23..."
# This command runs ONLY our specific test file, skipping everything else
./gradlew :gemmacore-sanitizer:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.gemmaguard.sanitizer.LiteRTBenchmarkTest

echo "------------------------------------------------"
echo "BENCHMARK RESULTS:"
# Dump the logcat and filter only for our specific benchmark tag
adb logcat -d | grep "\[GEMMAGUARD_BENCHMARK\]"
echo "------------------------------------------------"