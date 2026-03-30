#!/bin/bash
#
# open2jam-modern - Launch script for macOS (Apple Silicon / Intel)
#
# This script launches open2jam-modern with appropriate JVM settings.
# Supports both Apple Silicon (M1/M2/M3) and Intel Macs.
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_NAME="open2jam-modern"

# Find the JAR file
JAR_FILE="$(find "$SCRIPT_DIR/.." -maxdepth 1 -name '*-all.jar' | head -n 1)"

if [ -z "$JAR_FILE" ]; then
    echo "Error: Could not find application JAR file in $SCRIPT_DIR/.."
    echo "Expected file: *-all.jar"
    exit 1
fi

# Detect architecture
ARCH=$(uname -m)
if [ "$ARCH" = "arm64" ]; then
    ARCH_NAME="Apple Silicon ($ARCH)"
else
    ARCH_NAME="Intel ($ARCH)"
fi

# Check for Java
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed or not in PATH"
    echo "Please install Java 21+ for $ARCH."
    echo ""
    echo "Using Homebrew (recommended):"
    echo "  brew install openjdk@21"
    echo ""
    if [ "$ARCH" = "arm64" ]; then
        echo "For Apple Silicon, ensure you're using ARM64 Java:"
        echo "  arch -arm64 brew install openjdk@21"
    fi
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
JAVA_ARCH=$(java -d64 2>&1 | grep -q "64-Bit" && echo "64-bit" || echo "32-bit")

echo "Starting $APP_NAME..."
echo "Architecture: $ARCH_NAME"
echo "Java: $JAVA_VERSION ($JAVA_ARCH)"
echo "Heap size: 30% of system RAM (dynamic)"

# Set JVM options with dynamic heap sizing (30% of system RAM)
# Capped at 16GB max RAM to prevent excessive heap on high-memory systems
# Supports both Apple Silicon (M1/M2/M3) and Intel Macs
JVM_OPTS=(
    "-XX:MaxRAM=16G"
    "-XX:MinRAMPercentage=30.0"
    "-XX:MaxRAMPercentage=30.0"
    "-XX:+UseZGC"
    "-XX:+AlwaysPreTouch"
    "-XX:+ExplicitGCInvokesConcurrent"
    "-XX:ZAllocationSpikeTolerance=2"
    "-Djava.awt.headless=false"
    "-Dfile.encoding=UTF-8"
    "-Xdock:name=$APP_NAME"
)

# Launch the application
exec java "${JVM_OPTS[@]}" -jar "$JAR_FILE" "$@"
