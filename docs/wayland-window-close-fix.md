# Wayland Window Close Fix - Implementation Report

**Date**: 23 March 2026  
**Status**: ✅ IMPLEMENTED  
**Build**: SUCCESSFUL

---

## Changes Made

### File: `src/org/open2jam/render/lwjgl/LWJGLGameWindow.java`

#### Change 1: Event Pumping During 5-Second Delay (Lines 483-495)

**Before**:
```java
if (!exitViaESC) {
    Logger.global.info("Pausing 5 seconds before closing window (song ended)...");
    try {
        Thread.sleep(5000);
    } catch (InterruptedException e) {
        // Ignore
    }
}
```

**After**:
```java
if (!exitViaESC) {
    Logger.global.info("Pausing 5 seconds before closing window (song ended)...");
    // Pump events during delay to keep Wayland compositor responsive
    long startTime = System.currentTimeMillis();
    long delayMs = 5000;
    while (System.currentTimeMillis() - startTime < delayMs) {
        GLFW.glfwPollEvents();
        try {
            Thread.sleep(16);  // ~60 FPS event polling
        } catch (InterruptedException e) {
            break;
        }
    }
}
```

**Impact**: 
- Keeps Wayland compositor responsive during result screen delay
- Prevents "window not responding" detection by KWin
- Processes window close events during delay

---

#### Change 2: Event Flush After Hide Window (Line 502)

**Before**:
```java
Logger.global.info("Hiding GLFW window " + windowHandle);
GLFW.glfwHideWindow(windowHandle);

// Small delay to let compositor process hide
try {
    Thread.sleep(50);
} catch (InterruptedException e) {
    // Ignore
}
```

**After**:
```java
Logger.global.info("Hiding GLFW window " + windowHandle);
GLFW.glfwHideWindow(windowHandle);
GLFW.glfwPollEvents();  // Flush compositor messages

// Small delay to let compositor process hide
try {
    Thread.sleep(50);
} catch (InterruptedException e) {
    // Ignore
}
```

**Impact**: 
- Ensures Wayland compositor receives hide event immediately
- Flushes event queue before destruction

---

#### Change 3: Event Flush After Destroy Window (Line 514)

**Before**:
```java
Logger.global.info("Destroying GLFW window " + windowHandle);
GLFW.glfwDestroyWindow(windowHandle);
windowHandle = 0;
Logger.global.info("GLFW window destroyed");
```

**After**:
```java
Logger.global.info("Destroying GLFW window " + windowHandle);
GLFW.glfwDestroyWindow(windowHandle);
GLFW.glfwPollEvents();  // Ensure compositor processes destruction
windowHandle = 0;
Logger.global.info("GLFW window destroyed");
```

**Impact**: 
- Forces Wayland compositor to process window destruction
- Prevents window surface caching by KWin

---

## Build Status

```bash
$ ./gradlew clean build

BUILD SUCCESSFUL in 7s
10 actionable tasks: 9 executed, 1 from cache
```

✅ No compilation errors  
✅ All tests pass  
✅ JAR generated successfully

---

## Testing Checklist

### Pending Tests (User to Verify)

- [ ] **Natural song end on Wayland**: Play song to completion, verify window closes after 5-second delay
- [ ] **ESC key exit on Wayland**: Press ESC during song, verify instant window close
- [ ] **GUI brought to front**: After window closes, verify Swing GUI is visible and focused
- [ ] **No compositor freezing**: KDE Plasma should not show "window not responding"
- [ ] **X11 regression**: Test on X11 session to ensure no breaking changes

---

## Expected Behavior

### Natural Song End (Wayland)

1. Song notes end → music continues playing
2. After music finishes: 5-second result screen with window visible
3. During delay: Window remains responsive (compositor events pumped)
4. After 5 seconds: Window hides → destroyed → compositor flushes
5. Swing GUI brought to front automatically

### ESC Key Exit (Wayland)

1. User presses ESC
2. `exitViaESC = true` → skip 5-second delay
3. Window hides → destroyed → compositor flushes immediately
4. Swing GUI brought to front automatically

---

## Technical Details

### Event Pumping Loop

```java
while (System.currentTimeMillis() - startTime < delayMs) {
    GLFW.glfwPollEvents();
    try {
        Thread.sleep(16);  // ~60 FPS
    } catch (InterruptedException e) {
        break;
    }
}
```

**Why 16ms?**: Matches typical display refresh rate (60 Hz), provides smooth event processing without excessive CPU usage.

### Event Flush After Hide/Destroy

```java
GLFW.glfwHideWindow(windowHandle);
GLFW.glfwPollEvents();  // ← Critical for Wayland

GLFW.glfwDestroyWindow(windowHandle);
GLFW.glfwPollEvents();  // ← Critical for Wayland
```

**Why poll after each operation?**: Wayland compositor processes events asynchronously. Flushing after each operation ensures:
1. Hide event is processed before destroy
2. Destroy event is processed before GUI restore
3. No event coalescing/dropping by compositor

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| X11 regression | Low | Medium | Test on X11 session |
| Event loop infinite loop | Very Low | High | Timeout-based loop exit |
| CPU spike during delay | Low | Low | 16ms sleep limits CPU usage |
| Audio glitch during delay | Very Low | Medium | Audio already released before delay |

**Overall Risk**: **LOW** - Changes are minimal and targeted at event flushing.

---

## Next Steps

1. **User Testing**: Run on Linux/Wayland (KDE Plasma) and verify:
   - Window closes after natural song end
   - Window closes instantly on ESC
   - GUI is brought to front

2. **If Issues Persist**:
   - Check logs for `GLFW window destroyed` message
   - Verify `glfwPollEvents()` is being called
   - Try Solution 3 (aggressive Wayland flush) from analysis doc

3. **If Successful**:
   - Update QWEN.md with fix status
   - Mark issue as resolved in project docs

---

## Log Messages to Watch For

```
INFO: Pausing 5 seconds before closing window (song ended)...
INFO: Hiding GLFW window 12345678
INFO: Destroying GLFW window 12345678
INFO: GLFW window destroyed
INFO: destroy() complete
INFO: Game ended, GUI re-enabled and brought to front
```

If window still doesn't close, check for:
- Missing `GLFW.glfwPollEvents()` calls in log
- Compositor-specific issues (KWin vs Mutter vs Weston)
- Wayland protocol version compatibility

---

## Commit Message Draft

```
Fix: LWJGL window not closing on Linux/Wayland

- Add glfwPollEvents() after glfwHideWindow() to flush compositor
- Add glfwPollEvents() after glfwDestroyWindow() to ensure destruction
- Replace blocking sleep with event-pumping loop during 5-second delay
- Keeps Wayland compositor responsive and prevents window caching

Fixes issue where game window remained visible after song end on
KDE Plasma/Wayland systems. Window now closes automatically after
natural song end (5-second delay) or instantly on ESC key.

Build: SUCCESSFUL
Tested: Pending user verification on Linux/Wayland
```

---

**Implementation Time**: ~15 minutes  
**Lines Changed**: 3 locations in 1 file  
**Build Status**: ✅ SUCCESSFUL  
**Testing Status**: ⏳ PENDING USER VERIFICATION
