#!/bin/bash
#
# Build script for open2jam-modern
#
# This script is designed for CI/CD integration (GitHub Actions, GitLab CI, etc.)
# It builds the project and creates distribution packages.
#
# Usage:
#   ./build.sh [command]
#
# Commands:
#   build           - Build the project (default)
#   dist            - Create distribution for current platform
#   dist-all        - Create distributions for all platforms (cross-compile)
#   dist-platform   - Create distribution for specific platform
#   clean           - Clean build artifacts
#   test            - Run tests
#   all             - Clean, build, test, and create distribution
#   help            - Show this help message
#

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

show_help() {
    echo "Build script for open2jam-modern"
    echo ""
    echo "Usage: ./build.sh [command] [options]"
    echo ""
    echo "Commands:"
    echo "  build           - Build the project (default)"
    echo "  dist            - Create distribution for current platform"
    echo "  dist-all        - Create distributions for all platforms (cross-compile)"
    echo "  dist-platform   - Create distribution for specific platform"
    echo "  clean           - Clean build artifacts"
    echo "  test            - Run tests"
    echo "  all             - Clean, build, test, and create distribution"
    echo "  help            - Show this help message"
    echo ""
    echo "Platforms (for dist-platform):"
    echo "  windows-x86_64  - Windows 64-bit (Intel/AMD)"
    echo "  windows-arm64   - Windows ARM64 (Snapdragon, Surface Pro X)"
    echo "  linux-x86_64    - Linux 64-bit (Intel/AMD)"
    echo "  linux-arm64     - Linux ARM64 (Raspberry Pi, AWS Graviton)"
    echo "  macos-x86_64    - macOS Intel (64-bit)"
    echo "  macos-arm64     - macOS Apple Silicon (M1/M2/M3)"
    echo ""
    echo "Examples:"
    echo "  ./build.sh                          # Build the project"
    echo "  ./build.sh dist                     # Create distribution for current platform"
    echo "  ./build.sh dist-all                 # Create all platform distributions"
    echo "  ./build.sh dist-platform macos-arm64 # Create macOS Apple Silicon dist"
    echo "  ./build.sh all                      # Full build pipeline"
}

check_java() {
    if ! command -v java &> /dev/null; then
        log_error "Java is not installed or not in PATH"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    JAVA_MAJOR_VERSION=$(echo "$JAVA_VERSION" | cut -d'.' -f1)
    
    if [ "$JAVA_MAJOR_VERSION" -lt 21 ]; then
        log_warning "Java version $JAVA_VERSION detected. Java 21+ is recommended."
    else
        log_info "Java version: $JAVA_VERSION"
    fi
}

check_gradle() {
    if [ ! -f "gradlew" ]; then
        log_error "Gradle wrapper not found. Make sure you're in the project root."
        exit 1
    fi
    chmod +x gradlew
}

cmd_build() {
    log_info "Building project..."
    ./gradlew clean build fatJar
    log_success "Build completed!"
    log_info "JAR file: build/libs/open2jam-modern-*-all.jar"
}

cmd_dist() {
    log_info "Creating distribution for current platform..."
    ./gradlew distZipCurrent
    
    # Find the created ZIP
    ZIP_FILE=$(find build/libs -name "open2jam-modern-*-x86_64.zip" -type f | head -n 1)
    
    if [ -n "$ZIP_FILE" ]; then
        log_success "Distribution created: $ZIP_FILE"
        
        # Show contents
        log_info "Contents:"
        unzip -l "$ZIP_FILE" | head -20
    else
        log_error "Distribution ZIP not found!"
        exit 1
    fi
}

cmd_dist_all() {
    log_info "Creating distributions for all platforms (cross-compilation)..."
    log_info "Note: The fat JAR is platform-independent (pure Java bytecode)"
    log_info "Launch scripts are shell/batch scripts that work on all platforms"
    log_info "Users need to install Java 21+ for their specific platform"
    
    ./gradlew distZipAll
    
    log_success "Distributions created in build/libs/"
    log_info "Available packages:"
    ls -lh build/libs/*-x86_64.zip build/libs/*-arm64.zip 2>/dev/null || log_warning "No ZIP files found"
}

cmd_dist_platform() {
    local platform="$1"
    
    if [ -z "$platform" ]; then
        log_error "Platform not specified!"
        echo "Available platforms: windows-x86_64, windows-arm64, linux-x86_64, linux-arm64, macos-x86_64, macos-arm64"
        exit 1
    fi
    
    # Validate platform
    case "$platform" in
        windows-x86_64|windows-arm64|linux-x86_64|linux-arm64|macos-x86_64|macos-arm64)
            ;;
        *)
            log_error "Unknown platform: $platform"
            echo "Available platforms: windows-x86_64, windows-arm64, linux-x86_64, linux-arm64, macos-x86_64, macos-arm64"
            exit 1
            ;;
    esac
    
    log_info "Creating distribution for platform: $platform"
    
    # Use Gradle task for consistency
    ./gradlew distZipAll --rerun-tasks 2>/dev/null || {
        # Fallback: manual packaging
        log_warning "Gradle task failed, using manual packaging..."
        
        local platformDir="build/dist/${platform}"
        local libsDir="build/libs"
        mkdir -p "$platformDir"
        
        # Build fatJar if needed
        if [ ! -f "$libsDir/open2jam-modern-${VERSION}-all.jar" ]; then
            log_info "Building fatJar..."
            ./gradlew fatJar
        fi
        
        # Copy fatJar
        cp "$libsDir"/*-all.jar "$platformDir/"
        
        # Copy launch scripts
        cp -r "scripts/${platform}/"* "$platformDir/" 2>/dev/null || {
            log_warning "No launch scripts found for $platform, using generic"
        }
        
        # Copy README and LICENSE
        cp README.md LICENSE "$platformDir/" 2>/dev/null || true
        
        # Create ZIP using Java (more portable than zip command)
        (cd "$platformDir" && jar cf "../libs/open2jam-modern-${VERSION}-${platform}.zip" .)
        
        log_success "Distribution created: $libsDir/open2jam-modern-${VERSION}-${platform}.zip"
        return
    }
    
    log_success "Distributions created in build/libs/"
    ls -lh "$libsDir"/open2jam-modern-*-${platform}.zip 2>/dev/null || log_warning "ZIP not found"
}

cmd_clean() {
    log_info "Cleaning build artifacts..."
    ./gradlew clean
    log_success "Clean completed!"
}

cmd_test() {
    log_info "Running tests..."
    ./gradlew test
    log_success "Tests completed!"
}

cmd_all() {
    log_info "Running full build pipeline..."
    cmd_clean
    cmd_build
    cmd_test
    cmd_dist
    log_success "Full build pipeline completed!"
}

# Main
case "${1:-build}" in
    build)
        check_java
        check_gradle
        cmd_build
        ;;
    dist)
        check_java
        check_gradle
        cmd_dist
        ;;
    dist-all)
        check_java
        check_gradle
        cmd_dist_all
        ;;
    dist-platform)
        check_java
        check_gradle
        cmd_dist_platform "$2"
        ;;
    clean)
        check_gradle
        cmd_clean
        ;;
    test)
        check_java
        check_gradle
        cmd_test
        ;;
    all)
        check_java
        check_gradle
        cmd_all
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        log_error "Unknown command: $1"
        show_help
        exit 1
        ;;
esac
