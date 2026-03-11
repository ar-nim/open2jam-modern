open2jam-modern
===============
A modernized open source emulator of [O2Jam](http://o2jam.wikia.com/wiki/O2Jam).

Originally written in Java with LWJGL 2 and FMOD Ex, this project has been modernized to use **Java 25**, **Gradle 9.4.0**, **LWJGL 3.4.1**, and **OpenAL** for cross-platform rhythm game gameplay.

## Modernization Changes

### Build System
- **Migrated from Ant/NetBeans to Gradle 9.4.0** - Modern build system with dependency management
- **Java 25** - Updated to the latest Java LTS version
- **Removed NetBeans dependencies** - All `.form` files and `org.jdesktop` dependencies replaced with standard Swing

### Graphics & Windowing
- **LWJGL 2 → LWJGL 3.4.1** - Complete migration to modern LWJGL
- **Display → GLFW** - Window management migrated from LWJGL 2 `Display` to GLFW
- **Wayland support** - Proper detection and handling of Wayland sessions (no window positioning on Wayland)
- **Robust display mode enumeration** - Fallback to standard resolutions when hardware enumeration fails

### Audio System
- **FMOD Ex → OpenAL** - Replaced proprietary FMOD Ex with open-source OpenAL (included with LWJGL 3)
- **Source pooling** - Implemented 200-source pool with ticket system to prevent audio exhaustion
- **Proper cleanup order** - OpenAL resources released before GLFW window destruction to prevent native crashes

### Bug Fixes
- **Native crash fix** - Fixed `SIGSEGV` in `libc.so.6` during song transitions by ensuring proper OpenGL/OpenAL context lifecycle
- **Audio exhaustion fix** - Keysounds no longer stop after 30 seconds due to OpenAL source exhaustion
- **Keyboard mapping** - Added support for symbol keys (`;`, `'`, `[`, `]`, `-`, `=`, `,`, `.`, `/`)
- **Duplicate key handling** - Rebinding a key automatically removes old mapping

### GUI Improvements
- **Display options refactored** - Separated into three dropdowns: Resolution, Refresh Rate, Color Depth
- **Standard Swing layouts** - Removed `beansbinding` and `org.jdesktop.layout` dependencies

Current Features
----------------

* Supports OJN/OJM files and BMS files.
    * Partially supports BGA for BMS files. (Image backgrounds and movie files using VLC)
* Works on Windows, Mac and Linux.
    * Tested on Windows 10/11 with Java 25.
    * Tested on Linux (Wayland/X11) with Java 25.
    * Tested on Mac OS X with Java 25.
* Music directory selection
    * You can put songs in multiple directories. open2jam keeps track of each of them separately.
* Adjustable KEY/BGM/Master volume.
* Auto-play and auto-sound modes.
* Display and audio latency compensation. [Howto](https://github.com/open2jamorg/open2jam/blob/master/docs/autosync.md)
    * Related discussions:
        * [Audio Latency and Autosyncing](https://github.com/open2jamorg/open2jam/pull/20)
        * [Display lag and audio latency - Some information and problems](https://github.com/open2jamorg/open2jam/issues/8)
* Optional, configurable alternative judgment method: "Timed Judgment," which judges notes by milliseconds rather than beats.
* Local matching - play with friends (powered by [partytime](https://github.com/dtinth/partytime)). [Demo Video](http://www.youtube.com/watch?v=UaZu2jVOdS8)
* Speed type: Hi-Speed, xR-Speed, W-Speed, Regul-Speed
* Channel modifiers: Mirror, Shuffle, Random


License
-------

All the code here is distributed under the terms of the Artistic License 2.0.
For more details, see the full text of the license in the file LICENSE.

LWJGL 3 is distributed under the BSD 3-Clause License.
OpenAL is distributed under the LGPL/GPL licenses.


Building from Source
--------------------

### Prerequisites
- **Java 25** or later
- **Git** (for cloning the repository)

### Build Instructions
```bash
# Clone the repository
git clone https://github.com/open2jamorg/open2jam-modern.git
cd open2jam-modern

# Build the project with Gradle
./gradlew build fatJar

# The runnable JAR will be in build/libs/
java -jar build/libs/open2jam-modern-1.0-SNAPSHOT-all.jar
```

### Using an IDE
This project can be imported into any IDE that supports Gradle:
- **IntelliJ IDEA**: File → Open → Select the project directory
- **Eclipse**: File → Import → Gradle → Existing Gradle Project
- **NetBeans**: File → Open Project → Select the project directory
