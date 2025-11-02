#!/bin/bash
set -e

echo "🔧 Setting up Gymoor Watch App project..."
echo "=========================================="

# Check if gradle is installed
if command -v gradle &> /dev/null; then
    echo "✓ Gradle found, initializing wrapper..."
    gradle wrapper --gradle-version 8.2
else
    echo "⚠ Gradle not found in PATH"
    echo "  Downloading gradle wrapper manually..."

    # Create wrapper directory
    mkdir -p gradle/wrapper

    # Download gradle wrapper jar
    curl -L https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar \
        -o gradle/wrapper/gradle-wrapper.jar

    echo "✓ Gradle wrapper downloaded"
fi

# Make scripts executable
chmod +x gradlew 2>/dev/null || true
chmod +x build_watch_app.sh

echo ""
echo "✅ Setup complete!"
echo ""
echo "Next steps:"
echo "  1. Review sample_stevie.json and create your version at:"
echo "     https://www.radig.com/gymoor/stevie.json"
echo ""
echo "  2. Build the app:"
echo "     ./build_watch_app.sh"
echo ""
echo "  3. Or build manually:"
echo "     ./gradlew assembleRelease"
