#!/bin/bash
set -e

# Build script for MedWatchoor Watch App

# Set environment variables
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME="$HOME/Library/Android/sdk"

echo "🔧 Building MedWatchoor Watch App..."
echo "================================"

# Check if Android SDK is available
if [ ! -d "$ANDROID_HOME" ]; then
    echo "❌ Error: Android SDK not found at $ANDROID_HOME"
    exit 1
fi

# Check if Java is available
if [ ! -d "$JAVA_HOME" ]; then
    echo "❌ Error: Java 17 not found at $JAVA_HOME"
    exit 1
fi

echo "✓ Android SDK: $ANDROID_HOME"
echo "✓ Java Home: $JAVA_HOME"
echo ""

# Build the release APK (already configured to sign automatically)
echo "🛠 Building signed release APK..."
./gradlew assembleRelease

RELEASE_APK="app/build/outputs/apk/release/app-release.apk"

if [ -f "$RELEASE_APK" ]; then
    echo ""
    echo "✅ Build successful!"
    echo "📦 APK location: $RELEASE_APK"
    echo ""
    echo "To install on your watch:"
    echo "  1. Enable Developer Mode on Galaxy Watch Ultra"
    echo "  2. Enable ADB debugging"
    echo "  3. Connect via WiFi: adb connect <watch-ip>:5555"
    echo "  4. Install: adb install -r $RELEASE_APK"
else
    echo "❌ Error: APK not found at $RELEASE_APK"
    exit 1
fi
