# open2jam-modern

[![Java 21+](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.java.net/)
[![LWJGL 3](https://img.shields.io/badge/LWJGL-3.4.1-blue.svg)](https://www.lwjgl.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.4.0-green.svg)](https://gradle.org/)
[![Build Status](https://img.shields.io/badge/build-successful-brightgreen.svg)]()

**open2jam-modern** is a modernized fork of [open2jam](https://github.com/open2jamorg/open2jam), an open-source emulator of the rhythm game [O2Jam](http://o2jam.wikia.com/wiki/O2Jam).

This version migrates from the legacy Java/LWJGL 2/FMOD Ex stack to **modern Java 21+**, **Gradle 9.4.0**, **LWJGL 3.4.1**, and **OpenAL**, removing all NetBeans-specific dependencies and fixing critical native crashes on Linux/Wayland.

## ✅ Build Status: SUCCESSFUL

```bash
$ ./gradlew clean build
BUILD SUCCESSFUL in 8s
10 actionable tasks: 9 executed, 1 from cache
```

## Modernization Highlights

### Build System
- **Gradle 9.4.0** build system with wrapper for reproducible builds
- Java 25 toolchain configuration
- LWJGL 3.4.1 BOM with automatic platform-specific natives download
- `fatJar` task for creating runnable JAR with all dependencies
- Removed Ant and NetBeans project files

### Graphics & Windowing
- **LWJGL 2 → LWJGL 3** migration
- **Display → GLFW** for window management
- **LWJGL 2 keyboard → GLFW** key codes with compatibility bridge
- OpenGL bindings updated to LWJGL 3
- Texture loading via LWJGL 3 STB image loading

### Audio System
- **FMOD Ex (proprietary) → OpenAL** (open source)
- 200-source pool with ticket system prevents audio exhaustion
- Proper OpenAL context lifecycle management
- Fixed keysound dropout after 30 seconds of gameplay

### Linux/Wayland Support
- Multi-method Wayland detection (`XDG_SESSION_TYPE`, `WAYLAND_DISPLAY`, etc.)
- Skip `glfwSetWindowPos()` on Wayland sessions (not supported)
- Proper cleanup order: OpenAL resources released **before** GLFW window destruction
- Fixed `SIGSEGV` in `libc.so.6` during song transitions

### GUI Refactoring
- Removed NetBeans `.form` files and beansbinding dependencies
- Refactored to standard Swing with `GroupLayout`
- Display mode selection refactored to separate dropdowns (Resolution, Refresh Rate, Color Depth)

### Input System
- Added symbol key support: `;`, `'`, `[`, `]`, `-`, `=`, `,`, `.`, `/`
- Duplicate key rebinding removes old mapping automatically
- Fixed keyboard configuration not listening to changes

## Requirements

- **Java 25** or higher
- **VLC** (for BMS video background support)
- **Graphics drivers** with OpenGL 3.2+ support

## Building

### Using Gradle Wrapper (Recommended)

```bash
# Build the project
./gradlew build

# Create runnable JAR with all dependencies
./gradlew fatJar

# Run the application
./gradlew run
```

### Using System Gradle

```bash
gradle build
gradle fatJar
```

## Running

After building, the runnable JAR will be in `build/libs/`:

```bash
java -jar build/libs/open2jam-modern-1.0-SNAPSHOT-all.jar
```

### Command-Line Options

| Option | Description |
|--------|-------------|
| `-debug` | Enable debug logging. Shows detailed initialization logs for OpenAL, GLFW, OpenGL, and game lifecycle events. |

**Example:**
```bash
# Run with debug logging enabled
java -jar build/libs/open2jam-modern-1.0-SNAPSHOT-all.jar -debug

# Run normally (minimal logging)
java -jar build/libs/open2jam-modern-1.0-SNAPSHOT-all.jar
```

**Debug logs include:**
- OpenAL device/context creation
- OpenGL capabilities and vendor info
- GLFW window lifecycle events
- Game loop start/exit
- Music timing and audio tail waiting

## Configuration

Configuration files are stored in the application directory:
- `config.vl` - Keyboard mappings and directory cache (binary Voile format)
- `game-options.xml` - Game options (display, speed, volume)

**Note:** Old configuration files from previous versions will be regenerated.

## Features

- **Chart Support:** OJN, OJM, BMS (with partial BGA), SM files
- **Cross-Platform:** Windows 10/11, Linux (X11/Wayland), macOS
- **Speed Modifiers:** Hi-Speed, xR-Speed, W-Speed, Regul-Speed
- **Judgment Systems:** Beat-based and Time-based (millisecond)
- **Local Multiplayer:** Via Partytime networking
- **Audio/Display Sync:** Configurable latency compensation
- **Key Modes:** 4K through 8K keyboard configurations

## Project Structure

```
open2jam-modern/
├── build.gradle          # Gradle build configuration
├── settings.gradle       # Project settings
├── gradle.properties     # Gradle properties
├── gradlew               # Gradle wrapper script
├── gradle/wrapper/       # Gradle wrapper files
├── src/
│   └── org/open2jam/
│       ├── Main.java           # Application entry point
│       ├── Config.java         # Configuration management
│       ├── GameOptions.java    # Game settings
│       ├── render/             # LWJGL 3 rendering
│       ├── sound/              # OpenAL audio
│       ├── gui/                # Swing GUI
│       └── game/               # Game logic
├── parsers/              # Chart file parsers (submodule)
├── lib/                  # Third-party JARs (partytime, voile, vlcj)
└── docs/                 # Documentation
```

## Key Changes from Original

| Component | Before | After |
|-----------|--------|-------|
| Build System | Ant + NetBeans | Gradle 9.4.0 |
| Java Version | Java 6 | Java 25 |
| Windowing | LWJGL 2 Display | LWJGL 3 GLFW |
| Audio | FMOD Ex (proprietary) | OpenAL (LGPL) |
| Input | LWJGL 2 Keyboard | GLFW + compatibility bridge |
| GUI | NetBeans Forms + beansbinding | Standard Swing |
| Linux Support | X11 only | X11 + Wayland |

## Troubleshooting

### Wayland Issues
If you encounter `GLFW_FEATURE_UNAVAILABLE` errors:
- The application automatically detects Wayland and skips unsupported features
- Ensure `XDG_SESSION_TYPE=wayland` or `WAYLAND_DISPLAY` is set

### Audio Crashes
- OpenAL sources are now properly managed with a 200-source pool
- If you still experience issues, try increasing buffer size in Advanced Options

### Display Mode Selection
- Display modes are now enumerated as separate dropdowns:
  - **Resolution** (Width × Height)
  - **Refresh Rate** (Hz)
  - **Color Depth** (bpp)
- Falls back to standard 4:3 resolutions if enumeration fails

## Development

### IDE Support
- **IntelliJ IDEA** (recommended): Import as Gradle project
- **Eclipse**: Use Buildship Gradle integration
- **VS Code**: Use Gradle for Java extension

**Note:** NetBeans is no longer supported due to removal of `.form` files.

### Building from Source

```bash
# Clone the repository
git clone https://github.com/open2jamorg/open2jam-modern.git
cd open2jam-modern

# Build
./gradlew build

# Run tests (if available)
./gradlew test
```

## Dependencies

### Runtime
- **LWJGL 3.4.1** (BSD 3-Clause)
- **OpenAL** (LGPL/GPL via LWJGL)
- **JNA 5.14.0** (LGPL/Apache 2.0)
- **VLCJ 4.8.2** (GPL)
- **Partytime** (MIT)
- **Voile** (Apache 2.0)

### Removed Dependencies
- LWJGL 2.x
- FMOD Ex (proprietary)
- NetBeans GUI Builder

## License

Core code remains under the **Artistic License 2.0**.

New dependencies have their respective licenses:
- LWJGL 3: BSD 3-Clause License
- OpenAL: LGPL/GPL licenses

See individual dependency licenses for details.

## Credits

- Original open2jam: [open2jamorg](https://github.com/open2jamorg/open2jam)
- Modernization by: open2jam-modern contributors
- LWJGL: [lwjgl.org](https://www.lwjgl.org/)
- OpenAL: [openal.org](https://www.openal.org/)

## Links

- [LWJGL 3 Documentation](https://www.lwjgl.org/guide)
- [OpenAL Specification](https://www.openal.org/documentation/)
- [GLFW Documentation](https://www.glfw.org/docs/latest/)
- [Original open2jam Repository](https://github.com/open2jamorg/open2jam)
