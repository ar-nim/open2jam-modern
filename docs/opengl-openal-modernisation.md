# OpenGL & OpenAL Implementation Analysis

## Current State Summary

The rendering and audio subsystems use LWJGL 3 bindings but rely entirely on **legacy OpenGL 1.x/2.x fixed-function pipeline** and a **basic OpenAL setup** with no latency-oriented optimizations.

---

## Part 1: OpenGL — Legacy → Modern Migration

### What's Legacy Right Now

| Legacy API | Where Used | Count |
|---|---|---|
| `glBegin` / `glEnd` (immediate mode) | [LWJGLSprite](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/lwjgl/LWJGLSprite.java#13-285), [TrueTypeFont](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/lwjgl/TrueTypeFont.java#30-402) | Every sprite draw, every glyph |
| `glTexCoord2f` / `glVertex2f` | [LWJGLSprite](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/lwjgl/LWJGLSprite.java#13-285), [TrueTypeFont](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/lwjgl/TrueTypeFont.java#30-402) | Every quad vertex |
| `glNewList` / `glCallList` (display lists) | [LWJGLSprite](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/lwjgl/LWJGLSprite.java#13-285) | Every sprite construction |
| `glMatrixMode` / `glLoadIdentity` / `glOrtho` | [LWJGLGameWindow](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/lwjgl/LWJGLGameWindow.java#42-853) | Projection/modelview setup |
| `glPushMatrix` / `glPopMatrix` / `glTranslatef` / `glScalef` | [LWJGLSprite](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/lwjgl/LWJGLSprite.java#13-285) | Every draw call |
| `glColor4f` / `glColor3f` | [LWJGLSprite](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/lwjgl/LWJGLSprite.java#13-285), [TrueTypeFont](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/lwjgl/TrueTypeFont.java#30-402) | Alpha/color tinting |
| `glTexEnvf(GL_TEXTURE_ENV)` | [TrueTypeFont](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/lwjgl/TrueTypeFont.java#30-402) | Font texture setup |
| `glEnable(GL_TEXTURE_2D)` | [LWJGLGameWindow](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/lwjgl/LWJGLGameWindow.java#42-853) | Texture state toggle |
| `GL_CLAMP` wrap mode | [TrueTypeFont](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/lwjgl/TrueTypeFont.java#30-402) | Deprecated constant |

> [!CAUTION]
> The code requests OpenGL 3.0 **without** a core profile (`GLFW_OPENGL_PROFILE` is not set). This gets a compatibility context — which works today, but many drivers (especially Mesa/Wayland) may eventually drop compatibility support. macOS already only supports core profiles for GL 3.2+.

### Modernization Roadmap

#### Phase 1: Batched Sprite Renderer (Highest Impact)

Replace per-sprite `glBegin`/`glEnd`/display lists with a **single VBO + VAO batched sprite renderer**.

```
Modern Approach:
┌──────────────────────────────────────┐
│     SpriteBatch                       │
│  ┌─ VAO (1 per batch)               │
│  ├─ VBO (dynamic, mapped)           │
│  ├─ Vertex Shader (position + UV)   │
│  └─ Fragment Shader (texture + tint)│
│                                      │
│  begin() → accumulate quads          │
│  end()   → single glDrawArrays      │
└──────────────────────────────────────┘
```

**Key design:**
- One `SpriteBatch` class with a float buffer for `[x, y, u, v, r, g, b, a]` per vertex
- **Texture atlas sorting** — batch consecutive draws with the same texture
- Single `glDrawArrays(GL_TRIANGLES)` or `glDrawElements` call per flush
- Use `GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT` for orphaning

**Impact:** Reduces hundreds of draw calls per frame to 1-5 batched draws.

#### Phase 2: Shader Pipeline

Replace fixed-function with two GLSL shaders:

**Vertex shader:**
```glsl
#version 330 core
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aUV;
layout(location = 2) in vec4 aColor;

uniform mat4 uProjection;

out vec2 vUV;
out vec4 vColor;

void main() {
    gl_Position = uProjection * vec4(aPos, 0.0, 1.0);
    vUV = aUV;
    vColor = aColor;
}
```

**Fragment shader:**
```glsl
#version 330 core
in vec2 vUV;
in vec4 vColor;

uniform sampler2D uTexture;

out vec4 fragColor;

void main() {
    fragColor = texture(uTexture, vUV) * vColor;
}
```

**Projection matrix** replaces `glOrtho`:
```java
// Orthographic projection (matches current glOrtho(0, width, height, 0, -1, 1))
float[] ortho = {
    2f/width, 0,        0,  -1,
    0,       -2f/height, 0,  1,  // flipped Y
    0,        0,        -1,  0,
    0,        0,         0,  1
};
```

#### Phase 3: Request Core Profile

```java
GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE); // macOS
```

> [!IMPORTANT]
> Once you switch to core profile, **all** legacy calls (`glBegin`, `glMatrixMode`, display lists, etc.) will cause `GL_INVALID_OPERATION`. Phase 1 and 2 must be completed first.

#### Phase 4: Texture Atlas

Current: Each sprite image = 1 OpenGL texture, padded to power-of-two.

Modern: Pack all skin sprites into a **single texture atlas** at load time.
- Eliminates texture bind switching during rendering
- Removes POT padding waste (modern GL supports NPOT textures with `GL_CLAMP_TO_EDGE`)
- `TextureLoader.getNextPOT()` becomes unnecessary

#### Phase 5: TrueTypeFont Modernization

Current [TrueTypeFont](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/lwjgl/TrueTypeFont.java#30-402) uses `glBegin(GL_QUADS)` per character.

Modern:
- Pre-bake glyph atlas (already done via [createSet](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/lwjgl/TrueTypeFont.java#133-188))
- Render all characters via the same `SpriteBatch` as sprites
- Or use STB TrueType's `stbtt_PackBegin` / `stbtt_GetPackedQuad` for tighter packing

### Migration Compatibility Strategy

Since this is a 2D game with fixed-resolution rendering, the migration can be **incremental**:

1. Build `SpriteBatch` alongside existing rendering
2. Create shader program utility
3. Port `LWJGLSprite.draw()` to use batch (keep old code behind a flag)
4. Port `TrueTypeFont.drawString()` to use batch
5. Remove all `glMatrixMode`, `glPushMatrix`, etc.
6. Switch GLFW to core profile
7. Delete legacy code paths

---

## Part 2: OpenAL — Latency Optimization for Keysounds

### Current Architecture Analysis

```
Current flow (key press → sound):
┌─────────┐    ┌──────────────┐    ┌─────────────┐    ┌──────────────────┐
│ Keyboard │───→│ check_keyboard│───→│ ALSound.play │───→│ ALSoundInstance   │
│  event   │    │  (Render.java)│    │              │    │ alSourcei(BUFFER) │
│          │    │               │    │              │    │ alSourcef(GAIN)   │
│          │    │               │    │              │    │ alSourcef(PITCH)  │
│          │    │               │    │              │    │ alSource3f(POS)   │
│          │    │               │    │              │    │ alSourcePlay()    │
└─────────┘    └──────────────┘    └─────────────┘    └──────────────────┘
                                                        ~5 AL calls per play
```

### Identified Latency Sources

#### 1. **No OpenAL Soft Low-Latency Configuration** (⚠️ HIGH IMPACT)

The context is created with **no attributes** (empty [IntBuffer](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/lwjgl/TextureLoader.java#250-263)):
```java
// Current (ALSoundSystem.java:83-84)
IntBuffer contextAttribs = stack.mallocInt(1);
contextAttribs.put(0).flip(); // No special attributes
```

OpenAL Soft (the default Linux/PipeWire backend) supports critical low-latency attributes:

```java
// Recommended for rhythm game keysounds
IntBuffer contextAttribs = stack.ints(
    ALC10.ALC_FREQUENCY, 44100,         // Match sample rate to avoid resampling
    SOFTOutputMode.ALC_OUTPUT_MODE_SOFT, SOFTOutputMode.ALC_STEREO_SOFT,
    SOFTHRTF.ALC_HRTF_SOFT, ALC10.ALC_FALSE,  // Disable HRTF (latency killer)
    0  // Terminator
);
```

Additionally, set environment variable or OpenAL Soft config:
```
# ~/.alsoftrc or ALSOFT_CONF env
[general]
period_size = 256   # Lower = lower latency (default is 1024)
periods = 2         # Minimum double-buffering
```

#### 2. **Linear Source Scanning** (⚠️ MEDIUM IMPACT)

[acquireSource()](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/sound/ALSoundSystem.java#211-236) does a **linear O(n) scan** of 200 sources:
```java
for (int i = 0; i < MAX_SOURCES; i++) {
    if (!sourceInUse[i]) { ... }
}
```

For a keysound-heavy chart (O2Jam can have 7+ simultaneous key hits), this adds microseconds per trigger. 

**Fix:** Use a **free-list stack** (O(1) acquire/release):
```java
private int[] freeStack = new int[MAX_SOURCES];
private int freeCount = MAX_SOURCES;

int acquireSource() {
    if (freeCount > 0) return freeStack[--freeCount];
    return stealOldestSource();
}
void releaseSource(int idx) {
    freeStack[freeCount++] = idx;
}
```

#### 3. **Per-Play AL State Setup** (⚠️ MEDIUM IMPACT)

Each [ALSoundInstance](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/sound/ALSoundInstance.java#9-76) constructor makes **5 OpenAL calls**:
```java
AL10.alSourcei(sourceId, AL10.AL_BUFFER, ...);  // buffer bind
AL10.alSourcef(sourceId, AL10.AL_GAIN, ...);     // volume
AL10.alSource3f(sourceId, AL10.AL_POSITION, ...); // pan
AL10.alSourcef(sourceId, AL10.AL_PITCH, ...);     // pitch
AL10.alSourcePlay(sourceId);                       // play
```

**Optimizations:**
- **Pre-bind buffers at load time** — For keysounds that don't change, bind the buffer once and only call `alSourcePlay` on trigger
- **Batch AL calls** — Group parameter changes before the play call
- **Skip unchanged parameters** — Cache last-set gain/pitch per source, skip if unchanged

#### 4. **update() Polls All 200 Sources** (⚠️ LOW-MEDIUM IMPACT)

```java
// Current: called every frame
public void update() {
    for (int i = 0; i < MAX_SOURCES; i++) {
        if (sourceInUse[i]) {
            int state = AL10.alGetSourcei(sourcePool[i], AL10.AL_SOURCE_STATE);
            ...
        }
    }
}
```

This queries `alGetSourcei` for every in-use source every frame. 

**Fix:** Only check sources that are past their expected duration, or use a timeout-based approach. Alternatively, maintain an **active source list** (compact array) instead of scanning the full pool.

#### 5. **Full Decode at Load + ByteArrayOutputStream Copy** (⚠️ LOW IMPACT)

```java
// ALSoundSystem.load()
ByteArrayOutputStream out = new ByteArrayOutputStream();
sampleData.copyTo(out);
byte[] audioData = out.toByteArray();  // ← Heap copy
ByteBuffer buffer = MemoryUtil.memAlloc(audioData.length);
buffer.put(audioData);  // ← Copy to native
```

This does a **double-copy** (stream → byte[] → ByteBuffer). For keysounds this only happens at load time so it's not a runtime latency issue, but it increases load time.

**Fix:** Stream directly to a pre-sized `ByteBuffer`:
```java
int size = sampleData.getSize(); // if available
ByteBuffer buffer = MemoryUtil.memAlloc(size);
sampleData.copyTo(buffer); // Direct native write
```

#### 6. **No Source Pre-warming** (⚠️ LOW IMPACT)

OpenAL Soft may have a "cold start" delay for the first source played. Some implementations lazily initialize mixing threads.

**Fix:** Play a silent buffer on each source during initialization to warm the pipeline.

### Recommended OpenAL Architecture for Lowest Latency

```
Optimized Keysound Flow:
┌─────────┐    ┌──────────────┐    ┌───────────────────────┐
│ Keyboard │───→│ check_keyboard│───→│ KeysoundPlayer         │
│  event   │    │              │    │  ┌─ pre-bound sources  │
│          │    │              │    │  ├─ free-list stack     │
│          │    │              │    │  ├─ cached gain/pitch   │
│          │    │              │    │  └─ alSourcePlay() ONLY │ ← 1 AL call
└─────────┘    └──────────────┘    └───────────────────────┘
```

### Priority Order for OpenAL Changes

| Priority | Change | Expected Impact |
|---|---|---|
| 🔴 P0 | Configure OpenAL Soft context attributes (period_size, HRTF off) | -5-15ms system latency |
| 🟠 P1 | Free-list source pool (O(1) acquire) | Eliminates scanning jitter |
| 🟠 P1 | Pre-bind buffers to sources for keysounds | -1-3 AL calls per trigger |
| 🟡 P2 | Active source list instead of full-pool polling in [update()](file:///home/arnim/projects/o2jam/open2jam-modern/src/org/open2jam/render/lwjgl/LWJGLGameWindow.java#629-636) | Reduces per-frame overhead |
| 🟡 P2 | Cache per-source state to skip unchanged `alSourcef` calls | ~2 fewer AL calls per play |
| 🟢 P3 | Source pre-warming at init | Eliminates cold-start spike |
| 🟢 P3 | Direct ByteBuffer loading (skip double-copy) | Faster load time |

---

## Summary

| Area | Current | Target |
|---|---|---|
| **OpenGL** | Legacy 1.x fixed-function, display lists, immediate mode | Modern 3.3 core, batched VBO, shaders |
| **Draw calls/frame** | Hundreds (1 per sprite/glyph) | ~1-5 batched |
| **OpenAL latency** | Default context, per-play setup, linear scan | Tuned context, pre-bound, O(1) pool |
| **Key-to-sound calls** | 5 AL calls per trigger | 1 (`alSourcePlay` only) |
