#!/bin/bash
# deploy_and_test.sh
# Builds, deploys, and runs the GemmaGuard media pipeline on the connected Samsung S23.
# Captures a logcat session and saves it to logs/ for post-mortem review.
# Usage: bash deploy_and_test.sh [/path/to/video.mp4]

set -euo pipefail

# ─────────────────────────────────────────────────────────────
# Config
# ─────────────────────────────────────────────────────────────
PACKAGE="com.gemmaguard.app"
ACTIVITY="com.gemmaguard.app.MainActivity"
DEVICE_MEDIA_DIR="/storage/emulated/0/Movies/GemmaGuard"
LOCAL_MEDIA_DIR="./media"
LOG_DIR="./logs"
APK_PATH="./app/build/outputs/apk/debug/app-debug.apk"

# Log file stamped with device info + timestamp
DEVICE_MODEL=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r' | tr ' ' '-')
DEVICE_ANDROID=$(adb shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
TIMESTAMP=$(date +"%Y-%m-%d_%H%M%S")
LOG_FILE="$LOG_DIR/${DEVICE_MODEL}-Android-${DEVICE_ANDROID}_${TIMESTAMP}.log"

# Test video (arg or default)
TEST_VIDEO="${1:-$LOCAL_MEDIA_DIR/iasip_boatClip.mp4}"
TEST_VIDEO_NAME=$(basename "$TEST_VIDEO")

# ─────────────────────────────────────────────────────────────
# Banner
# ─────────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════"
echo "  GemmaGuard — Deploy & Pipeline Test Script"
echo "═══════════════════════════════════════════════════"
echo "  Device : $DEVICE_MODEL (Android $DEVICE_ANDROID)"
echo "  Video  : $TEST_VIDEO_NAME"
echo "  Log    : $LOG_FILE"
echo "═══════════════════════════════════════════════════"
echo ""

# ─────────────────────────────────────────────────────────────
# 1. Pre-flight checks
# ─────────────────────────────────────────────────────────────
echo "[ 1/6 ] Pre-flight checks..."

if ! adb devices | grep -q "device$"; then
    echo "  ✗ No ADB device found. Connect the S23 and try again."
    exit 1
fi
echo "  ✓ ADB device connected."

if [ ! -f "$TEST_VIDEO" ]; then
    echo "  ✗ Test video not found: $TEST_VIDEO"
    exit 1
fi
echo "  ✓ Test video found."

mkdir -p "$LOG_DIR"

# ─────────────────────────────────────────────────────────────
# 2. Build
# ─────────────────────────────────────────────────────────────
echo ""
echo "[ 2/6 ] Building debug APK..."
./gradlew :app:assembleDebug --quiet
echo "  ✓ Build complete."

# ─────────────────────────────────────────────────────────────
# 3. Install
# ─────────────────────────────────────────────────────────────
echo ""
echo "[ 3/6 ] Installing APK on device..."
adb install -r "$APK_PATH"
echo "  ✓ APK installed."

# ─────────────────────────────────────────────────────────────
# 4. Push test video to app-private storage
# ─────────────────────────────────────────────────────────────
echo ""
echo "[ 4/6 ] Pushing test video to app storage..."
APP_PRIVATE_DIR="/sdcard/Android/data/$PACKAGE/files"
adb shell mkdir -p "$APP_PRIVATE_DIR"
adb push "$TEST_VIDEO" "$APP_PRIVATE_DIR/$TEST_VIDEO_NAME"
REMOTE_VIDEO_PATH="$APP_PRIVATE_DIR/$TEST_VIDEO_NAME"

VTT_NAME="${TEST_VIDEO_NAME%.mp4}_transcript.vtt"
VTT_PATH="$LOCAL_MEDIA_DIR/$VTT_NAME"
if [ -f "$VTT_PATH" ]; then
    adb push "$VTT_PATH" "$APP_PRIVATE_DIR/$VTT_NAME"
    echo "  ✓ Pushed $TEST_VIDEO_NAME + sidecar $VTT_NAME"
else
    echo "  ✓ Pushed $TEST_VIDEO_NAME (no sidecar found)"
fi

# ─────────────────────────────────────────────────────────────
# 5. Launch app with automation intent
# ─────────────────────────────────────────────────────────────
echo ""
echo "[ 5/6 ] Launching app and auto-triggering pipeline..."
adb shell am force-stop "$PACKAGE"
adb logcat -c
sleep 1
adb shell am start -n "$PACKAGE/$ACTIVITY" --es "AUTO_VIDEO_PATH" "$REMOTE_VIDEO_PATH"

echo "  ✓ App launched with auto-trigger. Monitoring logs..."
echo ""

# ─────────────────────────────────────────────────────────────
# 6. Stream and Analyze logcat (Robust Version)
# ─────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

dump_context() {
    echo ""
    echo "══════════════ LAST 60 LOG LINES (for diagnosis) ══════════════"
    tail -60 "$LOG_FILE" 2>/dev/null || echo "(log file not yet written)"
    echo "════════════════════════════════════════════════════════════════"
    echo "Full log: $LOG_FILE"
}

# Clear log file
cat /dev/null > "$LOG_FILE"

# Background logcat
LOGCAT_PID=""
cleanup_test() {
    [ -n "$LOGCAT_PID" ] && kill "$LOGCAT_PID" 2>/dev/null || true
}
trap cleanup_test EXIT

adb logcat \
    GemmaGuard-STT:D \
    GemmaGuard-LiteRT:D \
    GemmaGuard-VM:D \
    GemmaGuard-Parser:W \
    GemmaGuard-FFmpeg:D \
    AndroidRuntime:E \
    DEBUG:F \
    native:E \
    tflite:E \
    "*:S" >> "$LOG_FILE" &
LOGCAT_PID=$!

START_TIME=$(date +%s)
TIMEOUT=600
LAST_CHUNK_LOG=""
LAST_ANALYZE_LOG=""

echo "  Monitoring logstream... (Timeout: $TIMEOUT s total)"
echo "───────────────────────────────────────────────────"

while true; do
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    
    if [ "$ELAPSED" -gt "$TIMEOUT" ]; then
        echo -e "\n${RED}✗✗✗✗✗ TEST FAILED: Global Timeout ($TIMEOUT s) reached ✗✗✗✗✗${NC}"
        dump_context
        exit 1
    fi

    # 1. Check for HARD FAILURES
    if grep -q "GemmaGuard-VM: Pipeline failed\|CalculatorGraph::Run() failed\|INTERNAL: RET_CHECK\|INVALID_ARGUMENT\|FATAL SIGNAL\|SIGSEGV" "$LOG_FILE"; then
        echo -e "\n${RED}✗✗✗✗✗ TEST FAILED: Pipeline or Engine Error detected ✗✗✗✗✗${NC}"
        dump_context
        exit 1
    fi

    if grep -q "GemmaGuard-Parser: Malformed piped output" "$LOG_FILE"; then
        echo -e "\n${RED}✗✗✗✗✗ TEST FAILED: Model returned malformed output ✗✗✗✗✗${NC}"
        dump_context
        exit 1
    fi

    # 2. Progress display
    ANALYZE_LINE=$(grep "analyze:" "$LOG_FILE" | tail -n 1 || true)
    if [ -n "$ANALYZE_LINE" ] && [ "$ANALYZE_LINE" != "$LAST_ANALYZE_LOG" ]; then
        echo -e "${GREEN}    ✓ $ANALYZE_LINE${NC}"
        LAST_ANALYZE_LOG="$ANALYZE_LINE"
    fi

    # 3. Check for success
    if grep -q "Gemma Inference Complete" "$LOG_FILE"; then
        echo -e "\n${GREEN}★★★★★ TEST PASSED: Gemma Inference Complete ★★★★★${NC}"
        exit 0
    fi
    if grep -q "Sanitization Complete\|MutingComplete" "$LOG_FILE"; then
        echo -e "\n${GREEN}★★★★★ TEST PASSED: Pipeline finished successfully ★★★★★${NC}"
        exit 0
    fi

    # Progress display
    LATEST=$(tail -n 2 "$LOG_FILE")
    CHUNK_LINE=$(echo "$LATEST" | grep "Analyzing chunk" | tail -n 1 || true)
    if [ -n "$CHUNK_LINE" ] && [ "$CHUNK_LINE" != "$LAST_CHUNK_LOG" ]; then
        echo -e "${YELLOW}  → $CHUNK_LINE${NC}"
        LAST_CHUNK_LOG="$CHUNK_LINE"
    fi

    sleep 1
done
