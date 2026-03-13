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

## Cross-Compilation

The build system supports **cross-compilation** - you can build distributions for all platforms from a single machine:

```bash
# Build for all platforms (Windows, Linux, macOS - x86_64 and ARM64)
./build.sh dist-all

# Build for specific platform
./build.sh dist-platform macos-arm64
./build.sh dist-platform windows-x86_64
./build.sh dist-platform linux-arm64
```

**How it works:**
- The fat JAR is **platform-independent** (pure Java bytecode)
- Launch scripts are shell/batch scripts that work on all platforms
- Users need to install Java 21+ for their specific platform

This means you can build all distributions from Linux, Windows, or macOS!

## Supported Platforms

| Platform | Architecture | Devices |
|----------|--------------|---------|
| `windows-x86_64` | 64-bit Intel/AMD | Windows 10/11 PCs |
| `windows-arm64` | ARM64 | Surface Pro X, Snapdragon PCs |
| `linux-x86_64` | 64-bit Intel/AMD | Ubuntu, Fedora, Debian, etc. |
| `linux-arm64` | ARM64 | Raspberry Pi 4, AWS Graviton |
| `macos-x86_64` | 64-bit Intel | Intel Macs |
| `macos-arm64` | ARM64 | Apple Silicon (M1/M2/M3) |

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
| Windows ARM64 | `windows-arm64` | `open2jam-modern.bat` |
| Linux 64-bit | `linux-x86_64` | `open2jam-modern` (shell script) |
| Linux ARM64 | `linux-arm64` | `open2jam-modern` (shell script) |
| macOS Intel | `macos-x86_64` | `open2jam-modern` (shell script) |
| macOS Apple Silicon | `macos-arm64` | `open2jam-modern` (shell script) |

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
unzip open2jam-modern-*-linux-x86_64.zip
cd linux-x86_64/
chmod +x open2jam-modern
./open2jam-modern
```

**Windows:**
```cmd
Expand-Archive open2jam-modern-*-windows-x86_64.zip
cd windows-x86_64
open2jam-modern.bat
```

**Apple Silicon Macs:**
```bash
unzip open2jam-modern-*-macos-arm64.zip
cd macos-arm64/
chmod +x open2jam-modern
./open2jam-modern
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
    runs-on: ubuntu-latest  # Cross-compile from Linux
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up Java
      uses: actions/setup-java@v4
      with:
        java-version: 21
        distribution: 'temurin'
        cache: gradle
    
    - name: Build with Gradle
      run: ./gradlew clean build fatJar
    
    - name: Create all platform distributions
      run: ./gradlew distZipAll
    
    - name: Upload all artifacts
      uses: actions/upload-artifact@v4
      with:
        name: open2jam-modern-all-platforms
        path: build/libs/*-x86_64.zip
        retention-days: 7

  # Optional: Build native packages per platform
  native-build:
    runs-on: ${{ matrix.os }}
    strategy:
      matrix:
        os: [ubuntu-latest, windows-latest, macos-latest, macos-14]  # macos-14 is Apple Silicon
        java-version: [21]
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up Java
      uses: actions/setup-java@v4
      with:
        java-version: ${{ matrix.java-version }}
        distribution: 'temurin'
        cache: gradle
    
    - name: Build distribution for current platform
      run: ./gradlew distZipCurrent
    
    - name: Upload Artifact
      uses: actions/upload-artifact@v4
      with:
        name: open2jam-modern-${{ runner.os }}-${{ runner.arch }}
        path: build/libs/*-${{ runner.os }}*.zip
```

### GitHub Actions Matrix for All Platforms

```yaml
# Build for all 6 platforms using matrix
build-matrix:
  runs-on: ubuntu-latest  # Cross-compile everything
  
  strategy:
    matrix:
      platform:
        - windows-x86_64
        - windows-arm64
        - linux-x86_64
        - linux-arm64
        - macos-x86_64
        - macos-arm64
  
  steps:
    - uses: actions/checkout@v4
    
    - name: Set up Java
      uses: actions/setup-java@v4
      with:
        java-version: 21
        distribution: 'temurin'
    
    - name: Build for ${{ matrix.platform }}
      run: ./build.sh dist-platform ${{ matrix.platform }}
    
    - name: Upload ${{ matrix.platform }}
      uses: actions/upload-artifact@v4
      with:
        name: open2jam-${{ matrix.platform }}
        path: build/libs/*-${{ matrix.platform }}.zip
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
  - **Apple Silicon Macs**: Use ARM64 Java (e.g., `arch -arm64 brew install openjdk@21`)
  - **Windows ARM64**: Use ARM64 Java from Adoptium
  - **Linux ARM64**: Use ARM64 OpenJDK (`apt install openjdk-21-jdk`)
- **VLC** (for BMS video background support)
- **OpenGL 3.2+** compatible graphics drivers

### Apple Silicon (M1/M2/M3) Notes

For macOS on Apple Silicon:

1. **Install ARM64 Java:**
   ```bash
   # Using Homebrew (recommended)
   arch -arm64 brew install openjdk@21
   
   # Or download from Adoptium
   # https://adoptium.net/temurin/releases/?os=mac&arch=aarch64
   ```

2. **Verify Java architecture:**
   ```bash
   java -XshowSettings:properties -version 2>&1 | grep os.arch
   # Should show: os.arch = aarch64
   ```

3. **Run the game:**
   ```bash
   unzip open2jam-modern-*-macos-arm64.zip
   cd macos-arm64/
   chmod +x open2jam-modern
   ./open2jam-modern
   ```

The game runs natively on Apple Silicon with full performance!

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
