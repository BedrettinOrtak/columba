#!/bin/bash
# Run Columba Desktop Application

set -e

cd "$(dirname "$0")/.."

echo "🚀 Starting Columba Desktop..."
./gradlew :desktop:run
