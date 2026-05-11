#!/bin/bash
# ============================================================================
# push_media.sh — Push all media files to GemmaGuard's on-device media folder
# ============================================================================
#
# Usage:
#   ./push_media.sh              # Push from ./media/ (default)
#   ./push_media.sh /path/to/dir # Push from a custom directory
#
# Target: /storage/emulated/0/Android/data/com.gemmaguard.app/files/media/
# The app scans this folder at runtime for .mp4 and .vtt files.
# ============================================================================

set -euo pipefail

PACKAGE="com.gemmaguard.app"
DEVICE_DIR="/storage/emulated/0/Android/data/${PACKAGE}/files/media"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SOURCE_DIR="${1:-${SCRIPT_DIR}/media}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No color

echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "${CYAN}  GemmaGuard — Media Push Script${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"

# Verify source directory
if [ ! -d "$SOURCE_DIR" ]; then
    echo -e "${RED}ERROR: Source directory not found: ${SOURCE_DIR}${NC}"
    exit 1
fi

# Count files
FILE_COUNT=$(find "$SOURCE_DIR" -maxdepth 1 -type f \( -name "*.mp4" -o -name "*.vtt" -o -name "*.srt" -o -name "*.mkv" \) | wc -l)
if [ "$FILE_COUNT" -eq 0 ]; then
    echo -e "${YELLOW}WARNING: No media files (.mp4, .vtt, .srt, .mkv) found in ${SOURCE_DIR}${NC}"
    exit 0
fi

echo -e "${GREEN}Source:${NC}  ${SOURCE_DIR}"
echo -e "${GREEN}Target:${NC}  ${DEVICE_DIR}"
echo -e "${GREEN}Files:${NC}   ${FILE_COUNT} media files found"
echo ""

# Verify ADB connection
if ! adb devices | grep -q "device$"; then
    echo -e "${RED}ERROR: No ADB device connected. Connect your phone and enable USB debugging.${NC}"
    exit 1
fi

# Create target directory on device
echo -e "${YELLOW}Creating device directory...${NC}"
adb shell "mkdir -p '${DEVICE_DIR}' && chmod 777 '${DEVICE_DIR}'"

# Push each media file
PUSHED=0
for file in "$SOURCE_DIR"/*; do
    [ -f "$file" ] || continue
    filename=$(basename "$file")
    ext="${filename##*.}"

    # Only push recognized media types
    case "$ext" in
        mp4|mkv|vtt|srt)
            echo -e "  ${GREEN}↑${NC} ${filename} ($(du -h "$file" | cut -f1))"
            adb push "$file" "${DEVICE_DIR}/" 2>/dev/null
            PUSHED=$((PUSHED + 1))
            ;;
        *)
            echo -e "  ${YELLOW}⊘${NC} Skipping ${filename} (not a media/transcript file)"
            ;;
    esac
done

echo ""
echo -e "${GREEN}Pushed ${PUSHED} files.${NC}"
echo ""

# Show what's on the device
echo -e "${CYAN}Files on device:${NC}"
adb shell "ls -la '${DEVICE_DIR}/'" 2>/dev/null || echo "  (empty)"
echo ""
echo -e "${GREEN}Done. Launch the app and select a clip to process.${NC}"
