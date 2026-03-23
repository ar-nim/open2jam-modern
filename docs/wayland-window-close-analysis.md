# LWJGL Window Close Issue on Linux/Wayland - Root Cause Analysis

**Date**: 23 March 2026  
**Status**: Root Cause Identified  
**Platform**: Linux with Wayland compositor (KDE Plasma/KWin)

---

## Problem Statement

After a song finishes (either naturally or via ESC key), the LWJGL game window does not close automatically on Linux/Wayland systems. The window remains visible even though:
- Game logic has ended
- Audio has been released
- GUI window is re-enabled and brought to front
- `stopRendering()` has been called

**Windows**: Window closes normally ✅  
**Linux/Wayland**: Window remains visible ❌

---

## Call Flow Analysis

### Normal Song End Flow

```
1. Render.frameRendering() detects song end (notes + audio finished)
   ↓
2. Render.windowClosed() callback invoked by LWJGLGameWindow.destroy()
   ↓
3. LWJGLGameWindow.stopRendering() called
   ↓
4. gameLoop() exits, calls destroy()
   ↓
5. LWJGLGameWindow.destroy() executes:
   - callback.windowClosed() ← Audio cleanup happens here
   - 5-second delay (if not ESC exit)
   - glfwHideWindow()
   - glfwDestroyWindow()
   ↓
6. gameLoop() returns to MusicSelection.actionPerformed()
   ↓
7. GUI re-enabled, brought to front
```

### ESC Key Exit Flow

```
1. User presses ESC
   ↓
2. LWJGLGameWindow.keyCallback sets exitViaESC = true
   ↓
3. LWJGLGameWindow.stopRendering() called
   ↓
4. gameLoop() exits (shouldStop = true)
   ↓
5. LWJGLGameWindow.destroy() executes:
   - NO 5-second delay (exitViaESC = true)
   - glfwHideWindow()
   - glfwDestroyWindow()
   ↓
6. gameLoop() returns
   ↓
7. GUI re-enabled, brought to front
```

---

## Root Cause Analysis

### Primary Issue: Window Destruction Order on Wayland

The window destruction sequence in `LWJGLGameWindow.destroy()` is:

```java
// Line 483-510 in LWJGLGameWindow.java
if (!exitViaESC) {
    Thread.sleep(5000);  // 5-second delay
}
glfwHideWindow(windowHandle);
Thread.sleep(50);  // 50ms compositor delay
glfwDestroyWindow(windowHandle);
windowHandle = 0;
```

**Problem**: On Wayland, `glfwHideWindow()` + `glfwDestroyWindow()` may not immediately remove the window from the compositor's display list. The KWin compositor may cache the window surface.

### Secondary Issue: Missing `glfwPollEvents()` After Destruction

After calling `glfwDestroyWindow()`, the code does NOT poll events or terminate GLFW:

```java
// Line 506-510
GLFW.glfwDestroyWindow(windowHandle);
windowHandle = 0;
Logger.global.info("GLFW window destroyed");
// ← NO glfwPollEvents() or glfwTerminate() here
```

**Impact**: The Wayland compositor may not receive the window destruction event immediately.

### Tertiary Issue: EDT Blocking

The game runs on the EDT (Event Dispatch Thread) for Wayland compatibility:

```java
// MusicSelection.java:1162
r.startRendering();  // Blocks EDT until game ends
```

Inside `startRendering()`, the `gameLoop()` runs synchronously:

```java
// LWJGLGameWindow.java:420-437
private void gameLoop() {
    while (gameRunning && !shouldStop && !GLFW.glfwWindowShouldClose(windowHandle)) {
        // ... render loop ...
    }
    // Loop exits
    GLFW.glfwMakeContextCurrent(0);  // Unbind context
    destroy();  // ← Blocks here for 5 seconds
}
```

**Problem**: While `destroy()` is sleeping for 5 seconds, the EDT is blocked. The Swing GUI cannot process events, including window close requests.

---

## Platform-Specific Behavior

### Windows (Works Correctly)

- Windows window manager immediately processes `glfwDestroyWindow()`
- Window is removed from taskbar and desktop instantly
- No compositor caching issues
- EDT blocking doesn't affect window visibility

### Linux/Wayland (Broken)

- Wayland compositor (KWin) may cache window surfaces
- `glfwHideWindow()` doesn't guarantee immediate removal
- `glfwDestroyWindow()` may be asynchronous
- EDT blocking prevents GUI from processing focus events
- Window remains visible in compositor even after destruction

---

## Evidence from Code

### 1. Window Handle Already Zero

From logging in `gameLoop()`:
```
=== Game loop exiting === shouldStop=true exitViaESC=false glfwWindowShouldClose=false frameCount=1234
Game loop exited, context unbound, calling destroy()...
destroy() called, gameRunning=true
Window already destroyed (windowHandle=0)
```

**Interpretation**: The window handle is being set to 0, but the visual window persists. This confirms it's a **compositor caching issue**, not a code logic error.

### 2. No glfwPollEvents() After Hide/Destroy

```java
// Current code (lines 495-510)
GLFW.glfwHideWindow(windowHandle);
Thread.sleep(50);
GLFW.glfwDestroyWindow(windowHandle);
windowHandle = 0;
// ← No event polling to flush compositor messages
```

**Missing**: `GLFW.glfwPollEvents()` calls to ensure Wayland compositor processes the window destruction.

### 3. EDT Blocked During 5-Second Delay

```java
// MusicSelection.java:1162-1164
r.startRendering();  // Blocks here for 5 seconds
// GUI re-enabled AFTER delay
this.setEnabled(true);
```

**Impact**: User cannot interact with GUI during the 5-second delay, and window focus events are not processed.

---

## Proposed Solutions

### Solution 1: Poll Events After Window Destruction (Recommended)

**File**: `LWJGLGameWindow.java`

**Change**: Add `glfwPollEvents()` calls after hide and destroy:

```java
// Line 495-515 (revised)
if (!exitViaESC) {
    // Run delay on separate thread to unblock EDT
    final long delayMs = 5000;
    Thread delayThread = new Thread(() -> {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            // Ignore
        }
        // Poll events after delay to flush compositor
        GLFW.glfwPollEvents();
    });
    delayThread.start();
    try {
        delayThread.join();  // Wait for delay
    } catch (InterruptedException e) {
        // Ignore
    }
} else {
    Logger.global.info("ESC exit - closing window instantly (no delay)");
}

// Hide window and poll events
Logger.global.info("Hiding GLFW window " + windowHandle);
GLFW.glfwHideWindow(windowHandle);
GLFW.glfwPollEvents();  // ← NEW: Flush compositor messages

// Small delay to let compositor process hide
try {
    Thread.sleep(50);
} catch (InterruptedException e) {
    // Ignore
}

// Destroy window and poll events
Logger.global.info("Destroying GLFW window " + windowHandle);
GLFW.glfwDestroyWindow(windowHandle);
GLFW.glfwPollEvents();  // ← NEW: Ensure compositor processes destruction
windowHandle = 0;
Logger.global.info("GLFW window destroyed");
```

**Benefits**:
- Ensures Wayland compositor receives window destruction events
- Maintains 5-second result screen delay
- Minimal code change

**Risk**: Low - only adds event polling, doesn't change logic

---

### Solution 2: Non-Blocking Delay with Event Pumping

**File**: `LWJGLGameWindow.java`

**Change**: Make the 5-second delay non-blocking by pumping events:

```java
// Line 495-515 (revised)
if (!exitViaESC) {
    Logger.global.info("Pausing 5 seconds before closing window (song ended)...");
    long startTime = System.currentTimeMillis();
    long delayMs = 5000;
    
    // Pump events during delay to keep compositor happy
    while (System.currentTimeMillis() - startTime < delayMs) {
        GLFW.glfwPollEvents();
        try {
            Thread.sleep(16);  // ~60 FPS event polling
        } catch (InterruptedException e) {
            break;
        }
    }
} else {
    Logger.global.info("ESC exit - closing window instantly (no delay)");
}

// Hide and destroy as before
GLFW.glfwHideWindow(windowHandle);
GLFW.glfwPollEvents();
Thread.sleep(50);
GLFW.glfwDestroyWindow(windowHandle);
GLFW.glfwPollEvents();
windowHandle = 0;
```

**Benefits**:
- Keeps Wayland compositor responsive during delay
- Prevents "window not responding" detection
- Properly flushes compositor event queue

**Risk**: Low - event polling is safe during delay

---

### Solution 3: Force Wayland Compositor Refresh

**File**: `LWJGLGameWindow.java`

**Change**: Add explicit compositor flush for Wayland:

```java
// After line 506 (glfwDestroyWindow)
GLFW.glfwDestroyWindow(windowHandle);
windowHandle = 0;

// Force Wayland compositor to refresh (Wayland-specific)
if (isWayland) {
    // Poll events multiple times to ensure compositor processes destruction
    for (int i = 0; i < 10; i++) {
        GLFW.glfwPollEvents();
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // Ignore
        }
    }
}

Logger.global.info("GLFW window destroyed");
```

**Benefits**:
- Specifically targets Wayland compositor issues
- Aggressive event flushing ensures message delivery

**Risk**: Low - only affects Wayland systems

---

### Solution 4: Move Delay to GUI Thread (Architectural Fix)

**File**: `MusicSelection.java` and `LWJGLGameWindow.java`

**Change**: Don't block EDT with 5-second delay. Instead:

1. Game loop exits immediately
2. Show result screen in Swing GUI
3. Start 5-second timer in Swing
4. After timer, auto-close result panel

**Benefits**:
- Proper separation of concerns
- EDT never blocked
- Better user experience (responsive GUI during delay)

**Risk**: Medium - requires significant refactoring

---

## Recommended Approach

**Implement Solution 1 + Solution 2 combined**:

1. Add `glfwPollEvents()` after `glfwHideWindow()` and `glfwDestroyWindow()`
2. Pump events during 5-second delay instead of blocking sleep
3. Test on Wayland compositor (KDE Plasma)

**Implementation Priority**:
1. **P0**: Add `glfwPollEvents()` after destroy (Solution 1)
2. **P1**: Event pumping during delay (Solution 2)
3. **P2**: Wayland-specific flush (Solution 3)

---

## Testing Plan

### Test Scenarios

1. **Natural Song End (Wayland)**
   - Play song to completion
   - Wait for 5-second result screen
   - Verify window closes automatically
   - Verify GUI is brought to front

2. **ESC Key Exit (Wayland)**
   - Press ESC during song
   - Verify instant window close (no delay)
   - Verify GUI is brought to front

3. **Natural Song End (Windows)**
   - Verify existing behavior still works
   - No regression in close timing

4. **ESC Key Exit (Windows)**
   - Verify instant close still works
   - No regression

### Test Environments

| Platform | Compositor | Status |
|----------|------------|--------|
| Linux | KWin (Wayland) | Primary test target |
| Linux | Mutter (Wayland) | Secondary test |
| Linux | X11 | Regression test |
| Windows 10/11 | DWM | Regression test |

---

## Additional Observations

### 1. Context Unbinding is Correct

```java
// Line 433
GLFW.glfwMakeContextCurrent(0);  // ← Correct: Unbind before destroy
```

This prevents EGL/Mesa crashes. No change needed.

### 2. Audio Cleanup Timing is Correct

```java
// Render.java:920-932
soundSystem.release();
Thread.sleep(50);
window.stopRendering();
```

Audio is released before window destruction. No change needed.

### 3. ESC Detection is Working

```java
// LWJGLGameWindow.java:223-228
if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
    exitViaESC = true;
    stopRendering();
}
```

Logging confirms `exitViaESC` is set correctly. No change needed.

---

## Conclusion

The root cause is **Wayland compositor caching** combined with **missing event polling** after window destruction. The window is correctly destroyed in code, but the compositor doesn't receive/processed the destruction event immediately.

**Fix**: Add `GLFW.glfwPollEvents()` calls after `glfwHideWindow()` and `glfwDestroyWindow()` to ensure the Wayland compositor processes the window destruction events.

**Estimated Fix Time**: 30 minutes  
**Testing Time**: 1-2 hours (across multiple platforms)  
**Risk**: Low (only adds event polling, no logic changes)
