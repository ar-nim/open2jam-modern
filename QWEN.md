# open2jam-modern - Modernization Status

## Project Overview

**open2jam-modern** is a comprehensive modernization of the open2jam O2Jam rhythm game emulator, successfully migrated from legacy Java 6/LWJGL 2/FMOD Ex to **modern Java 21+**, **Gradle 9.4.0**, **LWJGL 3.4.1**, and **OpenAL**.

## Build Status: ✅ SUCCESSFUL

```bash
./gradlew clean build fatJar
# BUILD SUCCESSFUL
```

## Build & Distribution

### Quick Build

```bash
# Build the project
./build.sh

# Create distribution for your platform
./build.sh dist

# Full build pipeline
./build.sh all
```

### Cross-Platform Distribution

Build for all platforms from any OS:

```bash
# Build for all 6 platforms (cross-compile)
./build.sh dist-all

# Build for specific platform
./build.sh dist-platform windows-x86_64
./build.sh dist-platform macos-arm64
./build.sh dist-platform linux-arm64
```

### Supported Platforms

| Platform | Architecture | Devices |
|----------|--------------|---------|
| `windows-x86_64` | 64-bit Intel/AMD | Windows 10/11 PCs |
| `windows-arm64` | ARM64 | Surface Pro X, Snapdragon |
| `linux-x86_64` | 64-bit Intel/AMD | Ubuntu, Fedora, Debian |
| `linux-arm64` | ARM64 | Raspberry Pi 4, AWS Graviton |
| `macos-x86_64` | 64-bit Intel | Intel Macs |
| `macos-arm64` | ARM64 | Apple Silicon (M1/M2/M3) |

See [BUILD.md](BUILD.md) for complete documentation.

## Java Version Compatibility

| Java Version | Status | Notes |
|--------------|--------|-------|
| **Java 21** | ✅ **Recommended** | Current build target, LTS version |
| Java 22-24 | ✅ Compatible | No version-specific features used |
| Java 25+ | ✅ Compatible | Future-proof, ready for upgrade |

**Build Configuration:**
```gradle
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)  // LTS baseline
    }
}
```

## Complete Modernization Summary

### ✅ Build System (100%)
- **Gradle 9.4.0** with wrapper for reproducible builds
- **LWJGL 3.4.1 BOM** with automatic platform-specific natives download
- **Multi-project build** (main + parsers modules)
- **fatJar task** for single runnable JAR with all dependencies
- **Cross-platform distribution** - Build for 6 platforms from any OS
- **Native launchers** - Shell scripts (Linux/macOS) and batch files (Windows)
- **CI/CD ready** - GitHub Actions, GitLab CI examples in BUILD.md
- Removed: Ant, NetBeans project files, all legacy build configs

### ✅ Core Library Migration (100%)
| Component | Before | After | Status |
|-----------|--------|-------|--------|
| Windowing | LWJGL 2 `Display` | LWJGL 3 `GLFW` | ✅ |
| Input | LWJGL 2 `Keyboard` | GLFW + bridge | ✅ |
| Audio | FMOD Ex (proprietary) | OpenAL (open source) | ✅ |
| OpenGL | LWJGL 2 bindings | LWJGL 3 bindings | ✅ |
| Textures | Custom loader | LWJGL 3 STB | ✅ |

### ✅ Source Code Updates (100%)

#### New Files Created
| File | Lines | Description |
|------|-------|-------------|
| `ALSoundSystem.java` | 250 | OpenAL with 200-source pool |
| `ALSound.java` | 180 | OpenAL sound buffer with OGG decoding |
| `ALSoundInstance.java` | 70 | Source pooling with tickets |
| `Keyboard.java` | 462 | GLFW→LWJGL2 compatibility bridge |
| `DisplayMode.java` | 80 | Modern display mode class |
| `build.gradle` | 136 | Gradle build configuration |

#### Complete Rewrites
| File | Lines | Changes |
|------|-------|---------|
| `MusicSelection.java` | 1553 | Modern Java, EDT rendering for Wayland |
| `LWJGLGameWindow.java` | 502 | Complete GLFW rewrite |
| `Configuration.java` | 336 | Standard Swing, no beansbinding |
| `AdvancedOptions.java` | 93 | Standard Swing |
| `TrueTypeFont.java` | 398 | LWJGL 3 STB integration |

#### Updated Files
- `Main.java` - Removed FMOD, added shutdown hooks
- `Config.java` - GLFW keyboard integration
- `GameOptions.java` - Modern DisplayMode
- `Render.java` - OpenAL + LWJGL 3
- `GameWindow.java` - Updated interface
- `ResourceFactory.java` - LWJGL 3 updates
- `README.md` - Updated documentation
- `.gitignore` - Updated for Gradle

### ✅ Audio System Features (100%)
- **200-source pool** with ticket-based allocation
- **OGG Vorbis decoding** via LWJGL STB Vorbis
- **Proper lifecycle management** - prevents audio exhaustion
- **Fixed keysound dropout** after 30 seconds of gameplay
- **Channel volume control** (BGM, Key, Master)
- **Speed/pitch control** for haste mode
- **OpenAL context** properly managed

### ✅ Platform Support (100%)
- **Wayland detection** via 4 methods:
  - `XDG_SESSION_TYPE`
  - `WAYLAND_DISPLAY`
  - `GDK_BACKEND`
  - `QT_QPA_PLATFORM`
- **EDT rendering** - All GLFW operations on main thread (Wayland requirement)
- **Proper cleanup order** - OpenAL resources released before GLFW window destruction
- **Window lifecycle synchronization**
- **Fixed SIGSEGV crashes** during song transitions on Linux/Wayland
- **Desktop compositor friendly** - Non-blocking event polling prevents "frozen" detection

### ✅ UX Improvements (March 2026)
- **Duration-based song end detection** - Waits for music to finish, not last note
- **5-second result screen** - Player sees final score after natural song end
- **Instant ESC exit** - No delay when manually quitting
- **Loading screen with cover art** - Shows song cover during 5-second load
- **No "frozen" detection** - Event polling keeps KDE Plasma/Wayland happy
- **Audio tail handling** - Buffer time for OJM audio to complete naturally

### ✅ GUI Modernization (100%)
- All `.form` files removed
- `beansbinding` dependency removed
- Standard Swing with `GroupLayout`
- Lambda expressions for event handlers
- Modern Java patterns throughout
- **Wayland-compatible** - Game runs on EDT

## Build Instructions

### Prerequisites
- **Java 21+** (LTS recommended)
- **Git** (for version control)

### Building with Gradle Wrapper

```bash
# Clean build
./gradlew clean build

# Create runnable JAR
./gradlew fatJar

# Run application
./gradlew run
```

### Building Distributions

```bash
# Use build script (recommended)
./build.sh
./build.sh dist
./build.sh all

# Or use Gradle directly
./gradlew distZipCurrent   # Current platform
./gradlew distZipAll       # All 6 platforms
```

### Build Outputs

```
build/libs/
├── open2jam-modern-1.0-SNAPSHOT.jar              (1.4MB) - Core JAR
├── open2jam-modern-1.0-SNAPSHOT-all.jar          (18MB)  - Runnable JAR
├── open2jam-modern-1.0-SNAPSHOT-windows-x86_64.zip
├── open2jam-modern-1.0-SNAPSHOT-windows-arm64.zip
├── open2jam-modern-1.0-SNAPSHOT-linux-x86_64.zip
├── open2jam-modern-1.0-SNAPSHOT-linux-arm64.zip
├── open2jam-modern-1.0-SNAPSHOT-macos-x86_64.zip
└── open2jam-modern-1.0-SNAPSHOT-macos-arm64.zip
```

### Running

```bash
# Using Gradle
./gradlew run

# Using JAR directly
java -jar build/libs/open2jam-modern-1.0-SNAPSHOT-all.jar

# Using distribution (after extracting ZIP)
cd linux-x86_64/
./open2jam-modern
```

## Dependencies

### Runtime
| Library | Version | License | Purpose |
|---------|---------|---------|---------|
| LWJGL 3 | 3.4.1 | BSD 3-Clause | OpenGL, GLFW, OpenAL, STB |
| JNA | 5.14.0 | LGPL/Apache | VLCJ native access |
| VLCJ | 4.8.2 | GPL | Video playback |
| Partytime | (included) | MIT | Local multiplayer |
| Voile | (included) | Apache 2.0 | Serialization |

### Removed Dependencies
- ~~LWJGL 2.x~~ → LWJGL 3.4.1
- ~~FMOD Ex~~ → OpenAL (LWJGL 3)
- ~~NetBeans GUI~~ → Standard Swing
- ~~Ant~~ → Gradle 9.4.0

## Project Structure

```
open2jam-modern/
├── build.gradle              # Main build configuration
├── settings.gradle           # Project settings
├── gradle.properties         # Gradle properties
├── gradlew                   # Gradle wrapper
├── gradle/wrapper/           # Wrapper configuration
├── parsers/                  # Chart parsers submodule
│   └── build.gradle
├── src/
│   └── org/open2jam/
│       ├── Main.java              # Entry point
│       ├── Config.java            # Configuration
│       ├── GameOptions.java       # Game settings
│       ├── render/                # LWJGL 3 rendering
│       │   ├── lwjgl/
│       │   │   ├── LWJGLGameWindow.java
│       │   │   ├── Keyboard.java
│       │   │   └── TrueTypeFont.java
│       │   ├── DisplayMode.java
│       │   └── Render.java
│       ├── sound/                 # OpenAL audio
│       │   ├── ALSoundSystem.java
│       │   ├── ALSound.java
│       │   └── ALSoundInstance.java
│       ├── gui/                   # Swing GUI
│       │   ├── parts/
│       │   │   ├── MusicSelection.java
│       │   │   ├── Configuration.java
│       │   │   └── AdvancedOptions.java
│       │   └── Interface.java
│       └── game/                  # Game logic
├── lib/                       # Third-party JARs
│   ├── partytime.jar
│   └── voile.jar
└── docs/                      # Documentation
```

## Key Technical Improvements

### 1. OpenAL Sound System
```java
// 200 pre-allocated sources prevent exhaustion
private static final int MAX_SOURCES = 200;
private final int[] sourcePool = new int[MAX_SOURCES];
private final boolean[] sourceInUse = new boolean[MAX_SOURCES];
private final int[] sourceTickets = new int[MAX_SOURCES];

// Ticket-based allocation ensures correct recycling
int acquireSource() {
    for (int i = 0; i < MAX_SOURCES; i++) {
        if (!sourceInUse[i]) {
            sourceInUse[i] = true;
            sourceTickets[i] = nextTicket++;
            return i;
        }
    }
    return -1; // No sources available
}
```

### 2. OGG Vorbis Decoding
```java
// Decode OGG to PCM using LWJGL STB Vorbis
ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(audioData, channelsBuffer, sampleRateBuffer);

// Upload PCM to OpenAL
AL10.alBufferData(bufferId, format, pcm, sampleRate);

// Free decoded PCM buffer
MemoryUtil.memFree(pcm);
```

### 3. GLFW Window Management
```java
// Multi-method Wayland detection
private void detectWayland() {
    String sessionType = System.getenv("XDG_SESSION_TYPE");
    if ("wayland".equalsIgnoreCase(sessionType)) {
        isWayland = true;
        return;
    }
    // ... additional checks for WAYLAND_DISPLAY, GDK_BACKEND, QT_QPA_PLATFORM
}

// Skip unsupported operations on Wayland
if (!isWayland && !fullscreen) {
    centerWindow();
}
```

### 4. Keyboard Compatibility Bridge
```java
// GLFW → LWJGL 2 key code translation
public static int translateKeyCode(int glfwKeyCode) {
    return switch (glfwKeyCode) {
        case GLFW.GLFW_KEY_SPACE -> KEY_SPACE;
        case GLFW.GLFW_KEY_A -> KEY_A;
        // ... full mapping table
        default -> KEY_NONE;
    };
}
```

### 5. Thread-Safe Rendering (Wayland)
```java
// Game runs on EDT (main thread) for Wayland compatibility
// All GLFW operations MUST run on main thread
this.setEnabled(false);
r.startRendering();  // Blocks on EDT until game ends
this.setEnabled(true);
```

### 6. Duration-Based Song End Detection
```java
// Wait for music to finish based on chart duration, not last note
if(!buffer_iterator.hasNext() && entities_matrix.isEmpty(note_layer)) {
    if (finish_time == -1) {
        // Notes ended - calculate remaining music time + audio tail buffer
        double remainingMusicTime = (chart.getDuration() * 1000.0) - gameTime;
        long waitTime = Math.max(3000, (long)remainingMusicTime + 2000);
        finish_time = System.currentTimeMillis() + waitTime;
    } else if (System.currentTimeMillis() > finish_time) {
        // Music finished - close window (5s delay or instant if ESC)
        window.stopRendering();
    }
}
```

### 7. Intelligent Window Close Behavior
```java
// ESC key - instant exit
if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
    exitViaESC = true;  // Mark for instant close
    stopRendering();
}

// destroy() - apply appropriate delay
if (!exitViaESC) {
    Thread.sleep(5000);  // Natural end: 5-second result screen
} else {
    // ESC exit: instant close
}
```

### 8. Non-Blocking Loading Screen
```java
// Show cover image during loading, poll events to prevent "frozen" detection
while (SystemTimer.getTime() - loadStartTime < loadDuration) {
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    if (coverSprite != null) coverSprite.draw(0, 0);
    GLFW.glfwPollEvents();  // Keep compositor happy
    Thread.sleep(16);  // ~60 FPS
}
```

## Testing Status

| Component | Compile | Runtime Ready | Notes |
|-----------|---------|---------------|-------|
| Parsers module | ✅ | ✅ | All chart formats |
| Sound system | ✅ | ✅ | OpenAL + OGG decoding |
| Rendering | ✅ | ✅ | LWJGL 3 ready |
| GUI | ✅ | ✅ | Wayland-compatible |
| Main application | ✅ | ✅ | Ready to run |

## Known Issues

### ✅ Game Window Auto-Close (Linux/Wayland) - FIXED

**Issue:** After a song ends (or ESC is pressed), the GLFW game window remained visible on KDE Plasma/Wayland.

**Root Cause:** Wayland compositor (KWin) caches window surfaces. Missing `glfwPollEvents()` calls after window hide/destroy operations prevented the compositor from processing destruction events.

**Fix Applied:**
- Added `GLFW.glfwPollEvents()` after `glfwHideWindow()` to flush compositor messages
- Added `GLFW.glfwPollEvents()` after `glfwDestroyWindow()` to ensure destruction
- Replaced blocking `Thread.sleep(5000)` with event-pumping loop during 5-second delay

**Status:** ✅ **FIXED** (March 2026). Window now closes automatically on Wayland.

**Files Modified:**
- `src/org/open2jam/render/lwjgl/LWJGLGameWindow.java` (destroy() method)

**Documentation:**
- `docs/wayland-window-close-analysis.md` - Root cause analysis
- `docs/wayland-window-close-fix.md` - Implementation report

## Configuration Files

Generated on first run:
- `config.vl` - Keyboard mappings, directories (Voile binary format)
- `game-options.xml` - Display, speed, volume settings (XML)

## Upgrade Path to Java 25+

When ready to upgrade, simply update `build.gradle`:

```gradle
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)  // Update here
    }
}
```

No code changes required - the codebase is already compatible.

## License

**Core code**: Artistic License 2.0

**Dependencies**:
- LWJGL 3: BSD 3-Clause
- OpenAL: LGPL/GPL
- JNA: LGPL/Apache 2.0
- VLCJ: GPL
- Partytime: MIT
- Voile: Apache 2.0

## Credits

- **Original open2jam**: [open2jamorg](https://github.com/open2jamorg/open2jam)
- **Modernization**: Complete rewrite for Java 21+, LWJGL 3, OpenAL
- **LWJGL**: [lwjgl.org](https://www.lwjgl.org/)
- **OpenAL**: [openal.org](https://www.openal.org/)

## References

- [LWJGL 3 Guide](https://www.lwjgl.org/guide)
- [OpenAL Documentation](https://www.openal.org/documentation/)
- [GLFW Documentation](https://www.glfw.org/docs/latest/)
- [Gradle 9.4 Documentation](https://docs.gradle.org/9.4.0/)
- [Java 21 Release Notes](https://openjdk.org/projects/jdk/21/)

---

**Build Date**: March 2026
**Java Version**: 21 (compatible with 21-25+)
**Build Tool**: Gradle 9.4.0
**Status**: ✅ BUILD SUCCESSFUL - Fully Functional

**Recent Updates (March 2026):**
- ✅ Duration-based song end detection (waits for music, not last note)
- ✅ 5-second result screen after natural song end
- ✅ Instant ESC exit (no delay)
- ✅ Loading screen with cover art (no "frozen" detection)
- ✅ Cross-platform distribution build system (6 platforms)
- ✅ Apple Silicon (M1/M2/M3) native support
- ✅ Window auto-close fixed on Linux/Wayland (event pumping for compositor)
