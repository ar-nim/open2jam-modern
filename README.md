# open2jam-modern

[![Java 21+](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.java.net/)
[![LWJGL 3](https://img.shields.io/badge/LWJGL-3.4.1-blue.svg)](https://www.lwjgl.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.4.0-green.svg)](https://gradle.org/)
[![Build Status](https://img.shields.io/badge/build-successful-brightgreen.svg)]()

**open2jam-modern** is a comprehensive modernization of [open2jam](https://github.com/open2jamorg/open2jam), an open-source emulator of the rhythm game [O2Jam](http://o2jam.wikia.com/wiki/O2Jam).

This project represents **13+ years of evolution** from the last commit in September 2013 to the present day, migrating from legacy Java 6/LWJGL 2/FMOD Ex to **modern Java 21+**, **Gradle 9.4.0**, **LWJGL 3.4.1**, and **OpenAL**, while fixing critical native crashes on Linux/Wayland and removing all NetBeans-specific dependencies.

## ✅ Build Status: SUCCESSFUL

```bash
$ ./gradlew clean build
BUILD SUCCESSFUL in 8s
10 actionable tasks: 9 executed, 1 from cache
```

## From 2013 to 2026: A 13-Year Journey

**Last commit in 2013**: September 14, 2013 (`11384b3` - "Should fix fractional time in issue #18")  
**First modernization commit**: March 12, 2026 (`c8479cd` - "Complete modernization of the open2jam codebase")  
**Current status**: Active development (March 2026)

### Original Project (2010-2013)
- **Java 6** with Swing GUI
- **LWJGL 2.9.0** for OpenGL rendering
- **FMOD Ex** (proprietary) for audio
- **Ant** build system
- **NetBeans** IDE project files
- X11-only Linux support
- Basic keyboard input handling

### Modern Project (2026)
- **Java 21+** with modern language features
- **LWJGL 3.4.1** with GLFW window management
- **OpenAL** (open source) audio system
- **Gradle 9.4.0** build system
- Cross-platform (Windows, Linux, macOS, Apple Silicon)
- Various under-the-hood improvements for modern display technology (Wayland on Linux, HiDPI, etc.)
- Advanced keyboard configuration with auto-save

## What's New for Players

### Gameplay Enhancements - More Faithful to Original O2Jam

The modernization isn't just about technology - it's about making the game **feel like the original O2Jam** you remember:

- **Authentic Judgment Timing** - BeatJudgement windows recalibrated to match original O2Jam behavior
- **Original Lifebar Behavior** - HP increase/decrease values restored to match the classic game
- **Per-Note Volume & Positioning** - Accurate audio placement for OJN charts with individual note volume
- **Hi-Speed & xR-Speed Accuracy** - Original speed modifiers working as intended

### Quality of Life Improvements

- **5-Second Result Screen** - See your final score after songs complete naturally
- **Instant Exit** - Press ESC to quit immediately without waiting
- **Auto-Save Settings** - Volume, modifiers, and key bindings save automatically - no more lost preferences

## Modernization Highlights

### Technology Updates (Under the Hood)

These improvements ensure the game runs smoothly on modern systems:

- **Modern Rendering Backend** - LWJGL 3.4.1 with efficient object pooling for consistent frame times
- **OpenAL Audio** - Open source audio with 200-source pool (no more audio dropout)
- **FPS Limiter** - Hybrid spin-wait timing saves battery on laptops while maintaining smooth gameplay (±0.1ms accuracy)
- **Pure Letterboxing** - Fullscreen renders at exact user resolution with HiDPI support
- **Java 21+** - Better performance and stability on modern systems
- **Logging System** - Clean runtime with optional `-debug` flag for troubleshooting

## Requirements

- **Java 21+** or higher (LTS recommended)
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
- `config.vl` - Keyboard mappings and directory cache (binary format)
- `game-options.xml` - Game options (display, speed, volume)

**Note:** Old configuration files from previous versions will be regenerated.

## Features

- **Chart Support:** OJN (O2Jam format only)
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
├── lib/                  # Third-party JARs (partytime)
└── docs/                 # Documentation
```

## Key Changes from Original (2013 → 2026)

| Component | 2013 (Last Commit) | 2026 (Modern) | Impact |
|-----------|-------------------|---------------|--------|
| **Build System** | Ant + NetBeans | Gradle 9.4.0 | Reproducible builds, cross-platform distribution |
| **Java Version** | Java 6 | Java 21+ (LTS) | Modern language features, better performance |
| **Windowing** | LWJGL 2 `Display` | LWJGL 3 `GLFW` | Multi-monitor, Wayland, Apple Silicon support |
| **Audio** | FMOD Ex (proprietary) | OpenAL (LGPL) | Open source, 200-source pool, no exhaustion |
| **Input** | LWJGL 2 `Keyboard` | GLFW + bridge | Symbol keys, auto-save, ESC to unbind |
| **GUI** | NetBeans Forms + beansbinding | Standard Swing | No IDE dependency, maintainable code |
| **Linux Support** | X11 only | X11 + Wayland | Modern display server support |
| **Frame Timing** | `Thread.sleep()` | Hybrid spin-wait | ±0.1ms accuracy, avoids Windows timer trap |
| **Fullscreen** | OS stretching | Pure letterboxing | Exact user resolution, HiDPI support |
| **Performance** | GC during gameplay | Object pooling | Zero GC, consistent frame times |
| **Config Saving** | Manual save button | Auto-save on change | Debounced writes, no lost settings |
| **Keyboard Binding** | Basic rebind | Transfer + ESC unbind | Intuitive UI, proper key management |
| **Display Config** | Basic resolution list | Monitor enumeration with presets for modern compositors (Wayland, macOS) | Better compatibility |
| **Logging** | Always verbose | `-debug` flag | Clean runtime, debug when needed |
| **Cross-Platform** | Windows + Linux | + macOS (Intel + ARM) | Universal binaries, native launchers |

**Total lines changed**: ~2,500+ lines rewritten/added since 2013  
**New files created**: 15+ modern Java classes  
**Files removed**: 20+ legacy files (.form, build.xml, nbproject/)

## Troubleshooting

### Fullscreen Letterboxing
If you see black borders around the game in fullscreen:
- This is **intentional** - the game renders at your selected resolution
- To fill the entire monitor, select your monitor's native resolution
- Letterboxing preserves aspect ratio and prevents GPU stretching

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
- **LWJGL 3.4.1** (BSD 3-Clause) - OpenGL, GLFW, OpenAL, STB
- **OpenAL** (LGPL/GPL via LWJGL) - 3D audio with 200-source pool
- **Partytime** (MIT) - Local multiplayer networking

### Removed Dependencies

**Dropped for Security & Simplicity:**
- **Voile** (binary serialization) - Binary files can be tampered with to execute malicious code. Switched to safer text-based formats.
- **VLCJ** (video playback) - Dropped BMS format support to focus on OJN (O2Jam chart format) only. Reduces complexity and potential security risks.
- **JNA** - Only needed for VLCJ, no longer required.

**Replaced with Modern Alternatives:**
- **LWJGL 2.x** → LWJGL 3.4.1 (modern graphics library)
- **FMOD Ex** → OpenAL (open source, no licensing restrictions)
- **NetBeans GUI Builder** → Standard Swing (no IDE lock-in)
- **Ant** → Gradle 9.4.0 (modern build system)

## License

Core code remains under the **Artistic License 2.0**.

New dependencies have their respective licenses:
- LWJGL 3: BSD 3-Clause License
- OpenAL: LGPL/GPL licenses

See individual dependency licenses for details.

## Credits

### Original Project (2010-2013)
- **open2jam founders**: [open2jamorg](https://github.com/open2jamorg/open2jam)
- **Last 2013 commit**: September 14, 2013 - fractional time fix for issue #18
- **Community contributors**: @dtinth, and many others from the original project

### Modernization (2026)
- **Project revival & lead**: @ar-nim
- **Core modernization**: @ar-nim (Java 21+, LWJGL 3, OpenAL migration)
- **FPS Limiter & Letterboxing**: @ar-nim
- **Gameplay Enhancements**: @ar-nim (BeatJudgement, Lifebar restoration)
- **Performance Optimizations**: @ar-nim (object pooling, entity matrix)
- **Modern Display Support**: @ar-nim (Wayland, HiDPI, etc.)
- **Auto-Save System**: @ar-nim (MusicSelection, Configuration)
- **Keyboard Configuration**: @ar-nim (key binding fixes, ESC to unbind)
- **Cross-Platform Build**: @ar-nim (6-platform distribution)
- **Logging System**: @ar-nim (`-debug` flag implementation)

### Technology Providers
- **LWJGL**: [lwjgl.org](https://www.lwjgl.org/) - Lightweight Java Game Library
- **OpenAL**: [openal.org](https://www.openal.org/) - Cross-platform 3D audio API
- **GLFW**: [glfw.org](https://www.glfw.org/) - Multi-platform window management
- **Gradle**: [gradle.org](https://gradle.org/) - Build automation system

## Links

- [LWJGL 3 Documentation](https://www.lwjgl.org/guide)
- [OpenAL Specification](https://www.openal.org/documentation/)
- [GLFW Documentation](https://www.glfw.org/docs/latest/)
- [Original open2jam Repository](https://github.com/open2jamorg/open2jam)
- [FPS Limiter Documentation](docs/fps-limiter-feature.md)
- [Wayland Window Close Fix](docs/wayland-window-close-fix.md)
- [Render Loop Refactor](docs/render-loop-refactor.md)
