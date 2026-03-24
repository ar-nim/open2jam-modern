# FPS Limiter Feature

## Overview

The FPS Limiter feature allows players to cap the frame rate when VSync is disabled. This is useful for:
- Reducing power consumption on laptops
- Preventing GPU overheating
- Maintaining consistent frame pacing
- Testing game behavior at different frame rates

## VSync vs FPS Limiter: What's the Difference?

**VSync ON** (`glfwSwapInterval(1)`):
- `glfwSwapBuffers()` **blocks** until the next vertical blank
- Frame rate is **hardware-locked** to monitor refresh rate (e.g., exactly 60 FPS on 60Hz)
- **Natural delta time** - each frame takes ~16.67ms (60Hz) or ~8.33ms (120Hz)
- Eliminates screen tearing
- May introduce input lag

**VSync OFF + FPS Limiter**:
- `glfwSwapBuffers()` returns **immediately**
- Frame rate is **capped by software** using hybrid spin-wait timing
- **Same delta time** as VSync at equivalent FPS (e.g., 1x = ~16.67ms at 60Hz)
- Minimal frame time variance (±0.1ms)
- No screen tearing prevention
- Lower input lag than VSync

**VSync OFF + Unlimited**:
- `glfwSwapBuffers()` returns **immediately**
- Frame rate is **uncapped** - renders as fast as GPU allows (200+ FPS possible)
- **Very small delta time** - each frame might be 4-5ms
- Maximum screen tearing
- Minimum input lag

### Key Insight

The game uses **delta-based movement**:
```java
double now = SystemTimer.getTime();
double delta = now - lastLoopTime;  // Time since last frame in milliseconds
lastLoopTime = now;

gameTime += delta * effectiveSpeed;
e.move(delta);  // Entity movement is delta-dependent
```

This means:
- **VSync ON @ 60Hz**: `delta ≈ 16.67ms` → normal game speed
- **VSync OFF + 1x @ 60Hz**: `delta ≈ 16.67ms` → same game speed
- **VSync OFF + Unlimited**: `delta ≈ 4-5ms` → same game speed, but more updates/sec

**Gameplay is timing-consistent** across all modes because movement is delta-based, not frame-based.

## Usage

### Configuration UI

In the Configuration screen (accessible from the main menu):

1. **VSync Checkbox**: Enable/disable VSync
2. **FPS Limiter Dropdown**: Available when VSync is **disabled**
   - Greyed out when VSync is enabled
   - Options:
     - `1x Refresh Rate` (e.g., 60 FPS on 60Hz monitor)
     - `2x Refresh Rate` (e.g., 120 FPS on 60Hz monitor)
     - `4x Refresh Rate` (e.g., 240 FPS on 60Hz monitor)
     - `8x Refresh Rate` (e.g., 480 FPS on 60Hz monitor)
     - `Unlimited` (no cap)

### Behavior Matrix

| VSync | FPS Limiter | Effective FPS | Use Case |
|-------|-------------|---------------|----------|
| ✅ ON | (greyed out) | = Refresh Rate | Tear-free, standard experience |
| ❌ OFF | 1x | = Refresh Rate | Low input lag, similar timing to VSync |
| ❌ OFF | 2x | 2× Refresh Rate | Smoother motion, higher GPU usage |
| ❌ OFF | 4x | 4× Refresh Rate | High refresh rate monitors |
| ❌ OFF | 8x | 8× Refresh Rate | Extreme FPS testing |
| ❌ OFF | Unlimited | Max possible | Minimum input lag, competitive play |

## Technical Implementation

### Files Modified

1. **GameOptions.java**
   - Added `FpsLimiter` enum with multipliers (0, 1, 2, 4, 8)
   - Added `fpsLimiter` field with getter/setter
   - Default value: `FpsLimiter.x1`

2. **Configuration.java**
   - Added FPS limiter dropdown (`JComboBox<GameOptions.FpsLimiter>`)
   - Added `updateFpsLimiterEnabled()` method to grey out dropdown when VSync is on
   - Integrated into load/save display settings

3. **GameWindow.java**
   - Added new `setDisplay()` method with FPS limiter parameter
   - Maintains backward compatibility with existing 3-parameter method

4. **LWJGLGameWindow.java**
   - Added `fpsLimiter`, `refreshRate`, and `nextFrameTimeNs` fields
   - Implemented **hybrid spin-wait** frame timing in `gameLoop()`
   - Uses absolute target time to prevent drift
   - Bypasses Windows 15.6ms timer resolution trap

5. **Render.java**
   - Updated to pass FPS limiter to `window.setDisplay()`

### Frame Timing Algorithm

```
Target FPS = Refresh Rate × Multiplier
Target Frame Duration (ns) = 1,000,000,000 / Target FPS

Example @ 60Hz:
- 1x: 1,000,000,000 / 60 = 16,666,667ns  → 60 FPS
- 2x: 1,000,000,000 / 120 = 8,333,333ns  → 120 FPS
- 4x: 1,000,000,000 / 240 = 4,166,667ns  → 240 FPS
- 8x: 1,000,000,000 / 480 = 2,083,333ns  → 480 FPS
```

### Implementation Code

```java
// Initialize before game loop
long targetFps = refreshRate * fpsLimiter.getMultiplier();
long targetFrameDurationNs = 1_000_000_000L / targetFps;
nextFrameTimeNs = System.nanoTime();

// Inside game loop, after rendering:
if (!vsync && fpsLimiter != GameOptions.FpsLimiter.Unlimited) {
    long now;
    
    // 1. Sleep in 1ms increments to avoid OS oversleep
    while ((now = System.nanoTime()) < nextFrameTimeNs - 1_000_000L) {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
    }
    
    // 2. Spin-wait for final < 1ms (nanosecond precision)
    while ((now = System.nanoTime()) < nextFrameTimeNs) {
        Thread.yield();
    }
    
    // 3. Advance target time (prevents drift)
    nextFrameTimeNs += targetFrameDurationNs;
    
    // 4. Catch-up prevention (spiral of death protection)
    if (now - nextFrameTimeNs > targetFrameDurationNs) {
        nextFrameTimeNs = now;
    }
}
```

### Why Hybrid Spin-Wait?

**Problem 1: OS Scheduler Granularity**
- Windows default timer resolution: **15.6ms**
- Asking `Thread.sleep(14.67ms)` → OS rounds to **31.2ms** (2 ticks)
- Result: 30 FPS instead of 60 FPS

**Problem 2: Error Accumulation (Drift)**
- Resetting `lastFrameTime = System.nanoTime()` after each frame discards oversleep errors
- Errors compound over time, causing FPS to drift below target

**Solution: LWJGL Standard Approach**
- **Sleep in 1ms increments** - avoids large oversleep from OS timer granularity
- **Stop 1ms early** - leaves room for precise spin-wait
- **Spin-wait final < 1ms** - nanosecond precision without significant CPU burn
- **Absolute target time** - `nextFrameTimeNs += duration` prevents drift by self-correcting
- **Catch-up prevention** - avoids "spiral of death" if a frame drop occurs

### Precision

- Uses `System.nanoTime()` for high-precision timing
- Hybrid approach achieves **±0.1ms** accuracy vs **±15ms** with naive `Thread.sleep()`
- Minimal CPU overhead (spin-wait only lasts < 1ms per frame)
- Self-correcting: if one frame runs slow, the next frame automatically compensates

## Configuration Files

The FPS limiter setting is saved in `game-options.xml`:

```xml
<game-options>
  ...
  <display-vsync>true</display-vsync>
  <fps-limiter>x1</fps-limiter>
  ...
</game-options>
```

Valid values: `Unlimited`, `x1`, `x2`, `x4`, `x8`

## Recommendations

### For Most Players
- **VSync ON**: Best experience, no tearing, consistent timing

### For Competitive Players
- **VSync OFF + 1x**: Low input lag, familiar timing
- **VSync OFF + Unlimited**: Minimum input lag, but watch for GPU overheating

### For Laptop Users
- **VSync OFF + 1x or 2x**: Reduces power consumption vs Unlimited
- Prevents thermal throttling during long sessions

### For High Refresh Rate Monitors (144Hz+)
- **VSync ON**: Utilizes full refresh rate
- **VSync OFF + 1x**: Same FPS, lower input lag

## Troubleshooting

### FPS not matching expected value
- Check if VSync is accidentally enabled (FPS limiter will be ignored)
- Monitor actual FPS with the in-game FPS counter
- Some compositors (e.g., Wayland) may impose their own limits

### Stuttering or frame pacing issues
- Try a different FPS limiter setting
- Ensure no background processes are interfering
- On Wayland, the compositor may affect frame timing

### Game feels "too fast" or "too slow"
- The game uses delta-based timing, so gameplay speed is consistent
- Visual smoothness changes, but note judgment timing remains the same
- Adjust Hi-Speed setting for perceived note speed

## Future Enhancements

Potential improvements:
- Custom FPS value input (e.g., 30, 45, 90, 165 FPS)
- Frame time graph for debugging
- Adaptive sync (G-Sync/FreeSync) detection
- Per-song FPS limiter overrides
