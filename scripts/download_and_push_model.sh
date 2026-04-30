#!/bin/bash

# Gemma 4 E2B LiteRT Download & Push Script
# Make sure your Samsung Galaxy S23 is connected via USB and USB Debugging is enabled.

echo "Downloading Gemma 4 E2B LiteRT model from HuggingFace..."
# Note: HuggingFace models are often gated behind a license agreement. 
# You may need to use 'huggingface-cli login' before running this if it fails.
curl -L -o gemma.tflite https://huggingface.co/google/gemma-4-E2B-it/resolve/main/gemma-4-e2b-it-cpu-int4.tflite

echo "Waiting for Android device (Samsung Galaxy S23)..."
adb wait-for-device

echo "Pushing model to local device storage..."
# We push to /data/local/tmp/ so the app can read it without inflating the APK size to 2GB.
adb push gemma.tflite /data/local/tmp/gemma.tflite

echo "Model successfully transferred! You can now run the app."
