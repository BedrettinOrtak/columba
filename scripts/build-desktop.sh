#!/bin/bash
# Build Columba Desktop Application

set -e

cd "$(dirname "$0")/.."

VERSION="${VERSION:-0.7.3}"

echo "🔨 Building Columba Desktop v$VERSION..."

# Build distributables for the current platform
./gradlew :desktop:createDistributable

echo "✅ Build complete!"
echo "Output: desktop/build/compose/binaries/"
