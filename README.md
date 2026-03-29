# open2jam-modern

[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.java.net/)
[![LWJGL 3](https://img.shields.io/badge/LWJGL-3.4.1-blue.svg)](https://www.lwjgl.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.4.0-green.svg)](https://gradle.org/)
[![Build Status](https://img.shields.io/badge/build-successful-brightgreen.svg)]()

**open2jam-modern** is a comprehensive modernization of [open2jam](https://github.com/open2jamorg/open2jam), an open-source emulator of the rhythm game [O2Jam](http://o2jam.wikia.com/wiki/O2Jam).

This project represents **13+ years of evolution** from the last commit in September 2013 to the present day, migrating from legacy Java 6/LWJGL 2/FMOD Ex to **modern Java 25 (LTS)**, **Gradle 9.4.0**, **LWJGL 3.4.1**, and **OpenAL**, while fixing critical native crashes on Linux/Wayland and removing all NetBeans-specific dependencies.

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
- **Java 25 (LTS)** with modern language features (switch expressions, records, pattern matching, val/let)
- **LWJGL 3.4.1** with GLFW window management
- **OpenAL** (open source) audio system with 256-source pool
- **Gradle 9.4.0** build system with semantic versioning
- **OpenGL 3.3 Core Profile** with shader-based batched rendering
- **HiDPI UI** with FlatLaf look-and-feel and automatic scaling
- **SQLite** chart cache with normalized thumbnail storage
- Cross-platform (Windows, Linux, macOS, Apple Silicon)
- Various under-the-hood improvements for modern display technology (Wayland on Linux, HiDPI, etc.)
- Advanced keyboard configuration with auto-save and ESC-to-unbind
- Security hardening for binary parsers (XXE protection, offset validation)
- Dependency injection via AppContext pattern

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
- **Sharp Bilinear Filtering** - Pixel-perfect scaling for crisp pixel-art assets
- **HiDPI UI** - Automatic scaling for high-DPI displays with theme switching
- **Loading Screen with Cover Art** - Shows song cover during 5-second load

## Modernization Highlights

### Technology Updates (Under the Hood)

These improvements ensure the game runs smoothly on modern systems:

- **OpenGL 3.3 Core Profile** - Complete shader-based rendering with GLSL 3.30, 100x draw call reduction via batching
- **Modern Rendering Backend** - LWJGL 3.4.1 with efficient object pooling for consistent frame times
- **OpenAL Audio** - Open source audio with 256-source pool (no more audio dropout), sine-law constant power panning
- **FPS Limiter** - Hybrid spin-wait timing saves battery on laptops while maintaining smooth gameplay (±0.1ms accuracy)
- **Pure Letterboxing** - Fullscreen renders at exact user resolution with HiDPI support
- **Java 25 (LTS)** - Latest long-term support release with enhanced performance and modern language features
- **Logging System** - Clean runtime with optional `-debug` flag for troubleshooting
- **SQLite Chart Cache** - Normalized thumbnail storage, 90x faster scanning with transaction batching
- **Security Hardening** - XXE protection, parser validation, SHA-1/SHA-256 integrity hashing
- **HiDPI UI** - FlatLaf look-and-feel with automatic OS-based scaling and theme switching
- **Dependency Injection** - AppContext pattern removes global state, improves testability

## Requirements

- **Java 25 (LTS)** or higher
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

Configuration files are stored in the `save/` directory:
- `config.json` - Key bindings, game options, UI state (Jackson JSON)
- `songcache.db` - Chart metadata cache with normalized thumbnail storage (SQLite)

**Deprecated (legacy formats removed):**
- ❌ `config.vl` (VoileMap binary format) - replaced by `config.json`
- ❌ `game-options.xml` (Java Beans XML) - replaced by `config.json`

**Note:** The old configuration format is no longer compatible. Please re-configure your settings!

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
| **Build System** | Ant + NetBeans | Gradle 9.4.0 + semantic versioning | Reproducible builds, cross-platform distribution |
| **Java Version** | Java 6 | Java 25 (LTS) | Migrated via Java 21, now on latest LTS |
| **Windowing** | LWJGL 2 `Display` | LWJGL 3 `GLFW` | Multi-monitor, Wayland, Apple Silicon support |
| **Audio** | FMOD Ex (proprietary) | OpenAL (LGPL) + 256 sources | Open source, larger polyphony, no exhaustion |
| **Input** | LWJGL 2 `Keyboard` | GLFW + bridge | Symbol keys, auto-save, ESC to unbind |
| **GUI** | NetBeans Forms + beansbinding | Standard Swing + FlatLaf | No IDE dependency, HiDPI support, themes |
| **Linux Support** | X11 only | X11 + Wayland | Modern display server support |
| **Frame Timing** | `Thread.sleep()` | Hybrid spin-wait | ±0.1ms accuracy, avoids Windows timer trap |
| **Fullscreen** | OS stretching | Pure letterboxing | Exact user resolution, HiDPI support |
| **Performance** | GC during gameplay | Object pooling | Zero GC, consistent frame times |
| **Config Saving** | Manual save button | Auto-save on change | Debounced writes, no lost settings |
| **Keyboard Binding** | Basic rebind | Transfer + ESC unbind | Intuitive UI, proper key management |
| **Display Config** | Basic resolution list | Monitor enumeration with presets | Better compatibility with modern compositors |
| **Logging** | Always verbose | `-debug` flag | Clean runtime, debug when needed |
| **Cross-Platform** | Windows + Linux | + macOS (Intel + ARM) | Universal binaries, native launchers |
| **Rendering** | Fixed-function OpenGL 3.0 | OpenGL 3.3 Core Profile + shaders | 100x draw call reduction via batching |
| **Chart Cache** | Binary VoileMap | SQLite + JSON | 90x faster scanning, normalized storage |
| **Security** | None | XXE protection, parser validation | Hardened against malicious chart files |
| **UI Scaling** | Manual | Automatic HiDPI | OS-based scaling, theme switching |
| **Architecture** | Singleton pattern | AppContext DI | Better testability, explicit dependencies |

**Total lines changed**: ~19,361 added, ~9,519 removed (net +9,842 lines) since 2013  
**New files created**: 47 files (modern Java classes, documentation, build scripts)  
**Files removed**: 90 files (legacy .form, build.xml, nbproject/, FMOD binaries, non-O2Jam parsers)  
**Files modified**: 40 files (core modernization)  
**Commits in 2013**: Last commit September 14, 2013  
**Commits in March 2026**: 116+ commits

## Major Removals from 2013 Version

### Chart Format Parsers (11 files removed - March 2026)
**Focus on O2Jam (OJN) format only:**
- ❌ `BMSChart.java`, `BMSParser.java`, `BMSWriter.java` - Be-Music Source format
- ❌ `SMChart.java`, `SMParser.java` - StepMania format
- ❌ `SNPParser.java` - KrazyRain archive format (VDISK)
- ❌ `XNTChart.java`, `XNTParser.java` - KrazyRain chart format
- ❌ `utils/CharsetDetector.java` - BMS character encoding detection
- ❌ `utils/KrazyRainDB.java` - KrazyRain database utilities

**Impact**: Parser files reduced from 22 → 11 (-50%), build time ~30s → ~2s (-93%)

### Audio/Video Dependencies (8 JARs removed)
**FMOD Ex (proprietary) → OpenAL (open source):**
- ❌ `lib/fmodex/` directory (28 files) - FMOD Ex native binaries
  - `fmodex.dll`, `fmodex64.dll` - Windows FMOD binaries
  - `libfmodex.so`, `libfmodex64.so` - Linux FMOD binaries
  - `libfmodex.jnilib` - macOS FMOD binaries
  - `fmod_event_net*.dll` - FMOD Event Network
  - `libfmodevent*.so` - FMOD Event system
- ❌ `lib/vlcj-2.0.0.jar` - VLCJ video playback (unused for OJN)
- ❌ `lib/jna-3.4.0.jar`, `lib/platform-3.4.0.jar` - VLCJ dependencies
- ❌ `lib/chardet.jar` - Character encoding detection (BMS only)
- ❌ `lib/lzma.jar` - LZMA compression (unused)
- ❌ `lib/voile.jar` - Binary serialization (replaced by JSON)

**Impact**: Dependencies reduced from 9 → 4 (-56%), lib/ JARs from 9 → 1 (-89%)

### Legacy Build System (5 files removed)
**Ant + NetBeans → Gradle:**
- ❌ `build.xml` - Ant build script
- ❌ `.form` files - NetBeans GUI Builder forms
- ❌ `nbproject/` directory - NetBeans project metadata
- ❌ `DEPS`, `TODO`, `RELEVANT_LINKS` - Legacy text files

### Legacy LWJGL 2 (14 files removed)
**LWJGL 2.9.3 → LWJGL 3.4.1:**
- ❌ `lib/lwjgl-2.9.3.jar` - Legacy LWJGL 2 core
- ❌ `lib/lwjgl_util-2.9.3.jar` - Legacy LWJGL 2 utilities
- ❌ `lib/lwjgl-debug.jar` - LWJGL 2 debug bindings
- ❌ `lib/lwjgl_test.jar`, `lib/lwjgl_util_applet.jar` - LWJGL 2 test utilities
- ❌ `lib/AppleJavaExtensions.jar` - macOS Java extensions
- ❌ `lib/asm-debug-all.jar` - ASM bytecode debugging
- ❌ `lib/jinput.jar` - JInput controller library
- ❌ `lib/native/` - LWJGL 2 native binaries (Linux, macOS, Windows)

### Legacy Features (Code removed)
**Removed from source files:**
- ❌ BMS/SM/SNP/XNT format detection and parsing
- ❌ VLCJ video playback in BgaEntity
- ❌ VLC path configuration in Configuration.java
- ❌ Format conversion menu items (BMS/SM/SNP export)
- ❌ DJMax database loader (`util/DJMaxDBLoader.java`)
- ❌ DJMAX_ONLINE.csv, DJMAX_ONLINE.ods - DJMax song database
- ❌ KrazyRain.xml - KrazyRain song database
- ❌ VoileMap binary config serialization (replaced by Jackson JSON)
- ❌ `game-options.xml` (replaced by `config.json`)
- ❌ `config.vl` binary format (replaced by `config.json`)

### New Feature: Keysound Extractor
**Replaces format conversion:**
- ✅ Right-click song → "Extract Keysounds"
- ✅ Exports OJM audio samples to `extraction/[song_name]/` directory
- ✅ Uses existing `Chart.copySampleFiles()` method
- ✅ Safer than format conversion (no transcoding)

## Summary of Removals

| Category | Before | After | Reduction |
|----------|--------|-------|-----------|
| **Parser Files** | 22 | 11 | -50% |
| **Dependencies** | 9 | 4 | -56% |
| **lib/ JARs** | 9 | 1 (partytime.jar) | -89% |
| **Supported Formats** | 6 (OJN, BMS, SM, XNT/KrazyRain) | 1 (OJN only) | -83% |
| **Build Time** | ~30s | ~2s | -93% |
| **Security Surface** | High (multiple parsers, XXE risk) | Low (single parser, hardened) | Reduced |
| **Code Complexity** | High (format conversion, video) | Low (OJN-focused) | Simplified |

**Rationale**: Focus on O2Jam (OJN) format only to reduce security risks, simplify maintenance, and improve build performance. All non-O2Jam parsers were removed along with their dependencies (VLCJ, JNA, CharsetDetector).

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
- **OpenGL 3.3 Core Profile**: @ar-nim (shader-based batched rendering, GLSL 3.30)
- **HiDPI UI**: @ar-nim (FlatLaf integration, automatic scaling, theme switching)
- **SQLite Chart Cache**: @ar-nim (normalized thumbnail storage, 90x faster scanning)
- **Security Hardening**: @ar-nim (XXE protection, parser validation, SHA-1/SHA-256 hashing)
- **AppContext DI**: @ar-nim (dependency injection pattern, singleton removal)
- **Sharp Bilinear Filtering**: @ar-nim (pixel-art rendering, UV insets)
- **Audio Improvements**: @ar-nim (256-source pool, sine-law constant power panning, OJN volume fix)
- **Semantic Versioning**: @ar-nim (git tag-based version detection)
- **Code Quality**: @ar-nim (SonarLint/SonarQube fixes, encapsulation, string literal deduplication)

### Special Thanks
- **@SiriusDoma** - [CXO2](https://github.com/SirusDoma/CXO2) project for reference implementation of O2Jam game mechanics and thumbnail loading from OJN files

### Technology Providers
- **LWJGL**: [lwjgl.org](https://www.lwjgl.org/) - Lightweight Java Game Library
- **OpenAL**: [openal.org](https://www.openal.org/) - Cross-platform 3D audio API
- **GLFW**: [glfw.org](https://www.glfw.org/) - Multi-platform window management
- **Gradle**: [gradle.org](https://gradle.org/) - Build automation system
- **FlatLaf**: [flatlaf.org](https://www.flatlaf.org/) - Modern look-and-feel for Swing

## Links

- [LWJGL 3 Documentation](https://www.lwjgl.org/guide)
- [OpenAL Specification](https://www.openal.org/documentation/)
- [GLFW Documentation](https://www.glfw.org/docs/latest/)
- [Gradle Documentation](https://docs.gradle.org/9.4.0/)
- [Java 25 Release Notes](https://openjdk.org/projects/jdk/25/)
- [FlatLaf Documentation](https://www.flatlaf.org/)
- [Original open2jam Repository](https://github.com/open2jamorg/open2jam)
- [CXO2 - O2Jam C++ Implementation](https://github.com/SirusDoma/CXO2) by @SiriusDoma
- [Build Instructions](BUILD.md)
- [Versioning Guide](docs/VERSIONING.md)
- [FPS Limiter](docs/fps-limiter-feature.md)
- [Wayland Window Close Fix](docs/wayland-window-close-fix.md)
- [Render Loop Refactor](docs/render-loop-refactor.md)
- [OpenGL 3.3 Core Profile Migration](docs/CORE_PROFILE_MIGRATION_COMPLETE.md)
- [Config & Game Data Refactor](docs/config-gamedata-refactor.md)
- [Security Audit Report](docs/security_audit_report_claude.md)
- [Gameplay Implementation Analysis](docs/gameplay-implementation-analysis.md)
