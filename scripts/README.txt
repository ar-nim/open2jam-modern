================================================================================
                        open2jam-modern - Launch Instructions
================================================================================

IMPORTANT: Always use the launch script for optimal performance!

The launch script configures critical JVM settings for rhythm gameplay:
  - Dynamic heap sizing (30% of system RAM, max 16GB)
  - Z Garbage Collector for sub-millisecond GC pauses
  - Pre-touch memory to prevent page faults during gameplay
  - Explicit concurrent GC at song boundaries

DO NOT run the JAR directly with: java -jar open2jam-modern-*-all.jar
This bypasses the JVM tuning and may cause audio crackling and stutters.


LAUNCHING
---------

Linux:
  ./open2jam-modern

macOS:
  ./open2jam-modern.command    (Terminal)
  Double-click open2jam-modern.command    (Finder)

Windows:
  open2jam-modern.bat


COMMAND-LINE OPTIONS
--------------------

-debug          Enable debug logging (detailed initialization logs)


REQUIREMENTS
------------

- Java 25 (LTS) or higher
- OpenGL 3.2+ compatible graphics drivers
- Minimum 8GB system RAM (16GB recommended for long songs)


TROUBLESHOOTING
---------------

If you experience performance issues:

1. Verify you're using the launch script (not running JAR directly)
2. Check that Java 21+ is installed: java -version
3. Close other memory-intensive applications
4. For debugging, run with: ./open2jam-modern -debug


JVM TUNING DETAILS
------------------

The launch script applies these JVM flags:

  -XX:MaxRAM=16G                  # Cap system memory detection at 16GB
  -XX:MinRAMPercentage=30.0       # Initial heap = 30% of detected RAM
  -XX:MaxRAMPercentage=30.0       # Max heap = 30% of detected RAM
  -XX:+UseZGC                     # Z Garbage Collector
  -XX:+AlwaysPreTouch             # Pre-allocate heap at startup
  -XX:+ExplicitGCInvokesConcurrent # System.gc() triggers concurrent GC
  -XX:ZAllocationSpikeTolerance=2 # Handle allocation spikes

Heap size by system RAM:
  - 8 GB RAM  → 2.4 GB heap
  - 16 GB RAM → 4.8 GB heap
  - 32 GB RAM → 4.8 GB heap (capped)
  - 64 GB RAM → 4.8 GB heap (capped)


================================================================================
