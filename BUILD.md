# Build System Documentation

This document describes the build system for open2jam-modern, including how to create distribution packages for different platforms.

## Quick Start

### Local Development

```bash
# Build the project
./build.sh

# Create distribution for your current platform
./build.sh dist

# Full build pipeline (clean, build, test, dist)
./build.sh all
```

### Windows

```cmd
REM Build the project
build.bat

REM Create distribution
build.bat dist
```

## Build Commands

| Command | Description |
|---------|-------------|
| `build` | Build the project (default) |
| `dist` | Create distribution ZIP for current platform |
| `dist-all` | Create distribution ZIPs for all platforms |
| `clean` | Clean build artifacts |
| `test` | Run tests |
| `all` | Full pipeline: clean → build → test → dist |
| `help` | Show help message |

## Gradle Tasks

### Core Tasks

```bash
# Build the project
./gradlew build

# Create fat JAR (all dependencies included)
./gradlew fatJar

# Install distribution (for testing)
./gradlew installDist
```

### Distribution Tasks

```bash
# Create distribution for current platform only
./gradlew distZipCurrent

# Create distributions for all platforms (Windows, Linux, macOS)
./gradlew distZipAll
```

## Distribution Packages

### Output Location

Distribution ZIPs are created in:
```
build/libs/open2jam-modern-<version>-<platform>.zip
```

### Platform Identifiers

| Platform | Identifier | Launcher |
|----------|------------|----------|
| Windows 64-bit | `windows-x86_64` | `open2jam-modern.bat` |
| Linux 64-bit | `linux-x86_64` | `open2jam-modern` (shell script) |
| macOS 64-bit | `macos-x86_64` | `open2jam-modern` (shell script) |

### Package Contents

Each distribution ZIP contains:
```
<platform>/
├── open2jam-modern-<version>-all.jar    # Fat JAR with all dependencies
├── <platform>/                          # Platform-specific launcher
│   ├── open2jam-modern                  # Linux/macOS shell script
│   └── open2jam-modern.bat              # Windows batch script
├── README.md                            # User documentation
└── LICENSE                              # License file
```

### Usage

After extracting the ZIP:

**Linux/macOS:**
```bash
cd linux-x86_64/
./open2jam-modern
```

**Windows:**
```cmd
cd windows-x86_64
open2jam-modern.bat
```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Build Distribution

on:
  push:
    branches: [ master ]
  pull_request:
    branches: [ master ]

jobs:
  build:
    runs-on: ${{ matrix.os }}
    
    strategy:
      matrix:
        os: [ubuntu-latest, windows-latest, macos-latest]
        java-version: [21]
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up Java
      uses: actions/setup-java@v4
      with:
        java-version: ${{ matrix.java-version }}
        distribution: 'temurin'
        cache: gradle
    
    - name: Build with Gradle
      run: ./gradlew clean build fatJar
    
    - name: Create Distribution
      run: ./gradlew distZipCurrent
    
    - name: Upload Artifact
      uses: actions/upload-artifact@v4
      with:
        name: open2jam-modern-${{ runner.os }}-x86_64
        path: build/libs/open2jam-modern-*-x86_64.zip
```

### GitLab CI Example

```yaml
stages:
  - build
  - package

build:
  stage: build
  image: eclipse-temurin:21-jdk
  script:
    - ./gradlew clean build fatJar
  artifacts:
    paths:
      - build/libs/*.jar

package:
  stage: package
  image: eclipse-temurin:21-jdk
  script:
    - ./gradlew distZipCurrent
  artifacts:
    paths:
      - build/libs/*-x86_64.zip
    expire_in: 1 week
```

### Build Script for CI

For CI/CD pipelines, you can use the build scripts directly:

```bash
# Full build pipeline
./build.sh all

# Or individual steps
./build.sh clean
./build.sh build
./build.sh dist
```

## Requirements

### Build Requirements

- **Java 21+** (JDK required for compilation)
- **Gradle 9.4.0+** (wrapper included)

### Runtime Requirements

- **Java 21+** (JRE sufficient for running)
- **VLC** (for BMS video background support)
- **OpenGL 3.2+** compatible graphics drivers

## Customization

### Changing Version

Edit `build.gradle`:
```gradle
version = '1.0.0'  // Change version here
```

### Adding Platform-Specific Files

Add files to the appropriate platform directory:
```
scripts/
├── windows-x86_64/
│   └── your-file.dll
├── linux-x86_64/
│   └── your-lib.so
└── macos-x86_64/
    └── your-lib.dylib
```

Then update `build.gradle` to include them in the distribution.

### JVM Options

Edit the launcher scripts to customize JVM options:

**Linux/macOS** (`scripts/linux-x86_64/open2jam-modern`):
```bash
JVM_OPTS=(
    "-Xmx2G"           # Max heap size
    "-XX:+UseG1GC"     # GC algorithm
    # Add your options here
)
```

**Windows** (`scripts/windows-x86_64/open2jam-modern.bat`):
```batch
set "JVM_OPTS=-Xmx2G -XX:+UseG1GC"
REM Add your options here
```

## Troubleshooting

### Build Fails with "Java not found"

Ensure Java 21+ is installed and in PATH:
```bash
java -version
```

### Distribution ZIP Not Created

Check that `fatJar` task runs successfully:
```bash
./gradlew clean fatJar distZipCurrent
```

### Launcher Script Permission Denied (Linux/macOS)

Make the launcher executable:
```bash
chmod +x linux-x86_64/open2jam-modern
```

### Out of Memory During Build

Increase Gradle heap size in `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g
```

## Release Checklist

1. Update version in `build.gradle`
2. Update version in `README.md`
3. Run full build: `./build.sh all`
4. Test distribution on each platform
5. Create distributions for all platforms: `./gradlew distZipAll`
6. Verify all ZIPs are created in `build/libs/`
7. Commit and tag release

## License

This build system is part of open2jam-modern. See LICENSE for details.
