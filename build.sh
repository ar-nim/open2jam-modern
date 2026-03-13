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
#   build       - Build the project (default)
#   dist        - Create distribution for current platform
#   dist-all    - Create distributions for all platforms
#   clean       - Clean build artifacts
#   test        - Run tests
#   all         - Clean, build, test, and create distribution
#   help        - Show this help message
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
    echo "Usage: ./build.sh [command]"
    echo ""
    echo "Commands:"
    echo "  build       - Build the project (default)"
    echo "  dist        - Create distribution for current platform"
    echo "  dist-all    - Create distributions for all platforms"
    echo "  clean       - Clean build artifacts"
    echo "  test        - Run tests"
    echo "  all         - Clean, build, test, and create distribution"
    echo "  help        - Show this help message"
    echo ""
    echo "Examples:"
    echo "  ./build.sh              # Build the project"
    echo "  ./build.sh dist         # Create distribution ZIP"
    echo "  ./build.sh all          # Full build pipeline"
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
    log_info "Creating distributions for all platforms..."
    log_warning "Note: This creates ZIPs for all platforms but only includes native launchers."
    log_warning "For true cross-compilation, use jpackage on each target platform."
    
    ./gradlew distZipAll
    
    log_success "Distributions created in build/libs/"
    ls -lh build/libs/*-x86_64.zip 2>/dev/null || log_warning "No ZIP files found"
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
