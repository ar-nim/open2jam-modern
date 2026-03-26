package org.open2jam.render.lwjgl;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.open2jam.GameOptions;
import org.open2jam.render.DisplayMode;
import org.open2jam.render.GameWindow;
import org.open2jam.render.GameWindowCallback;
import org.open2jam.util.DebugLogger;
import org.open2jam.util.Logger;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * LWJGL 3 implementation of GameWindow using GLFW.
 * Features proper Wayland detection and window lifecycle management.
 * 
 * Pure Letterboxing Approach:
 * - Fullscreen windows are created at native desktop resolution
 * - User's selected resolution is rendered via glViewport with black borders
 * - This ensures consistent behavior across Windows, Wayland, and macOS
 * 
 * Letterboxing Details:
 * - viewportX/viewportY store the offset for centering the user's resolution
 * - The viewport is scaled by HiDPI factor for correct physical rendering
 * - Black borders appear when aspect ratios differ (e.g., 4:3 on 16:9)
 * 
 * Mouse Input (Future):
 * - If GLFW mouse input is added, coordinates must be offset by viewportX/viewportY
 * - glfwGetCursorPos returns window-relative coordinates, not viewport-relative
 * - Formula: gameMouseX = glfwMouseX - viewportX (use LOGICAL offset, not physical)
 * - Currently not needed as game uses keyboard-only input
 */
public class LWJGLGameWindow implements GameWindow {

    private long windowHandle = 0;
    private int width;   // LOGICAL size (user's selected resolution, for projection/game logic)
    private int height;  // LOGICAL size (user's selected resolution, for projection/game logic)
    private int physicalWidth;  // PHYSICAL framebuffer size (for viewport/scissor)
    private int physicalHeight; // PHYSICAL framebuffer size (for viewport/scissor)
    private int windowWidth;    // Actual window size (native for fullscreen, user's for windowed)
    private int windowHeight;   // Actual window size (native for fullscreen, user's for windowed)
    private int viewportX;      // Viewport offset X for letterboxing (fullscreen only)
    private int viewportY;      // Viewport offset Y for letterboxing (fullscreen only)
    private boolean vsync;
    private GameOptions.FpsLimiter fpsLimiter;
    private boolean fullscreen;
    private String title = "open2jam";
    private GameWindowCallback callback;
    private boolean gameRunning = true;
    private volatile boolean shouldStop = false;
    private boolean exitViaESC = false;  // Track if exit was via ESC key (instant close)
    private float scaleX = 1f, scaleY = 1f;
    private GLCapabilities capabilities;
    private boolean isWayland = false;
    private TextureLoader textureLoader;
    private int refreshRate = 60;  // Default refresh rate (will be updated from monitor)

    // FPS limiter: absolute target time for next frame (nanoseconds)
    private long nextFrameTimeNs = 0;

    // Available display modes
    private List<DisplayMode> displayModes = new ArrayList<>();
    
    // Modern OpenGL renderer (shaders + batched rendering)
    private ModernRenderer modernRenderer;

    public LWJGLGameWindow() {
        detectWayland();
        // Detect primary monitor refresh rate at initialization
        this.refreshRate = getPrimaryMonitorRefreshRate();
        DebugLogger.debug("Primary monitor refresh rate detected: " + refreshRate + "Hz");
    }

    /**
     * Get the primary monitor's current refresh rate dynamically.
     * This ensures the FPS limiter uses the actual monitor refresh rate,
     * not a hardcoded value or potentially stale DisplayMode frequency.
     *
     * For multi-monitor setups, returns the primary monitor's rate.
     * Future enhancement: detect which monitor the window is on and use that.
     *
     * @return refresh rate in Hz, or 60 if detection fails
     */
    private int getPrimaryMonitorRefreshRate() {
        try {
            long monitor = GLFW.glfwGetPrimaryMonitor();
            if (monitor != 0) {
                GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
                if (mode != null) {
                    return mode.refreshRate();
                }
            }
        } catch (Exception e) {
            Logger.global.log(Level.WARNING, "Failed to get monitor refresh rate: {0}", e.getMessage());
        }
        return 60;  // Fallback to 60Hz if detection fails
    }

    /**
     * Update the refresh rate from the current monitor.
     * Called when entering fullscreen or when display settings may have changed.
     */
    private void updateRefreshRate() {
        int detectedRate = getPrimaryMonitorRefreshRate();
        if (detectedRate > 0) {
            this.refreshRate = detectedRate;
            DebugLogger.debug("Refresh rate updated: " + refreshRate + "Hz");
        }
    }

    /**
     * Detect if running under Wayland session.
     * Multiple methods are checked for robustness.
     */
    private void detectWayland() {
        // Check XDG_SESSION_TYPE
        String sessionType = System.getenv("XDG_SESSION_TYPE");
        if ("wayland".equalsIgnoreCase(sessionType)) {
            isWayland = true;
            return;
        }

        // Check WAYLAND_DISPLAY
        String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        if (waylandDisplay != null && !waylandDisplay.isEmpty()) {
            isWayland = true;
            return;
        }

        // Check GDK_BACKEND
        String gdkBackend = System.getenv("GDK_BACKEND");
        if ("wayland".equalsIgnoreCase(gdkBackend)) {
            isWayland = true;
            return;
        }

        // Check QT_QPA_PLATFORM
        String qtPlatform = System.getenv("QT_QPA_PLATFORM");
        if (qtPlatform != null && qtPlatform.contains("wayland")) {
            isWayland = true;
            return;
        }

        isWayland = false;
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
        if (windowHandle != 0) {
            GLFW.glfwSetWindowTitle(windowHandle, title);
        }
    }

    @Override
    public void setDisplay(DisplayMode dm, boolean vsync, boolean fullscreen) {
        this.width = dm.getWidth();
        this.height = dm.getHeight();
        this.vsync = vsync;
        this.fpsLimiter = GameOptions.FpsLimiter.x1;  // Default to x1 if not specified
        this.fullscreen = fullscreen;
        // Use dynamically detected monitor refresh rate, fallback to DisplayMode frequency
        this.refreshRate = getPrimaryMonitorRefreshRate();
    }

    /**
     * Set display mode with FPS limiter.
     * @param dm display mode
     * @param vsync enable VSync
     * @param fullscreen enable fullscreen
     * @param fpsLimiter FPS limiter multiplier (ignored when VSync is enabled)
     */
    public void setDisplay(DisplayMode dm, boolean vsync, boolean fullscreen, GameOptions.FpsLimiter fpsLimiter) {
        this.width = dm.getWidth();
        this.height = dm.getHeight();
        this.vsync = vsync;
        this.fpsLimiter = fpsLimiter != null ? fpsLimiter : GameOptions.FpsLimiter.x1;
        this.fullscreen = fullscreen;
        // Use dynamically detected monitor refresh rate, fallback to DisplayMode frequency
        this.refreshRate = getPrimaryMonitorRefreshRate();
    }

    @Override
    public int getResolutionHeight() {
        return height;
    }

    @Override
    public int getResolutionWidth() {
        return width;
    }

    @Override
    public void startRendering() {
        if (callback == null) {
            throw new RuntimeException("Need callback to start rendering!");
        }

        // Reset exit flag for new game session
        exitViaESC = false;
        DebugLogger.debug("startRendering() called, exitViaESC reset to false");

        // Initialize GLFW
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW - use OpenGL 3.3 Core Profile (modern rendering only)
        // All legacy code (LWJGLSprite, TrueTypeFont) has been ported to ModernRenderer
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
        // Request OpenGL 3.3 Core Profile (no legacy features)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE); // Required for macOS

        // Create window
        // PURE LETTERBOXING APPROACH:
        // - Fullscreen: create at native desktop resolution, letterbox to user's resolution
        // - Windowed: create at user's selected resolution
        long monitor = fullscreen ? GLFW.glfwGetPrimaryMonitor() : 0;
        
        if (fullscreen) {
            // Get native desktop resolution for fullscreen
            GLFWVidMode nativeMode = GLFW.glfwGetVideoMode(monitor);
            if (nativeMode == null) {
                throw new RuntimeException("Failed to get native video mode");
            }
            windowWidth = nativeMode.width();
            windowHeight = nativeMode.height();
            
            // Update refresh rate from monitor (important for fullscreen mode)
            updateRefreshRate();

            // Create fullscreen window at native resolution
            windowHandle = GLFW.glfwCreateWindow(windowWidth, windowHeight, title, monitor, 0);

            // Calculate letterbox viewport offset to center user's selected resolution
            viewportX = (windowWidth - width) / 2;
            viewportY = (windowHeight - height) / 2;

            DebugLogger.debug("Fullscreen: Native=" + windowWidth + "x" + windowHeight +
                             ", User=" + width + "x" + height +
                             ", Viewport offset=(" + viewportX + ", " + viewportY + ")");
        } else {
            // Windowed mode: use user's selected resolution
            windowWidth = width;
            windowHeight = height;
            viewportX = 0;
            viewportY = 0;
            
            windowHandle = GLFW.glfwCreateWindow(windowWidth, windowHeight, title, 0, 0);
        }

        if (windowHandle == 0) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        // Make context current FIRST before any OpenGL operations
        GLFW.glfwMakeContextCurrent(windowHandle);
        GLFW.glfwSwapInterval(vsync ? 1 : 0);

        // Verify context is current
        long currentContext = GLFW.glfwGetCurrentContext();
        DebugLogger.debug("GLFW Context check: windowHandle=" + windowHandle + " currentContext=" + currentContext);
        if (currentContext != windowHandle) {
            throw new RuntimeException("Failed to make OpenGL context current. Current: " + currentContext + " Expected: " + windowHandle);
        }

        // Initialize OpenGL capabilities
        DebugLogger.debug("Creating GL capabilities...");
        capabilities = GL.createCapabilities();
        DebugLogger.debug("GL capabilities created: " + (capabilities != null));
        if (capabilities == null) {
            throw new RuntimeException("Failed to create OpenGL capabilities");
        }

        // Log OpenGL info
        DebugLogger.debug("OpenGL Version: " + GL11.glGetString(GL11.GL_VERSION));
        DebugLogger.debug("GLSL Version: " + GL11.glGetString(GL32.GL_SHADING_LANGUAGE_VERSION));
        DebugLogger.debug("Renderer: " + GL11.glGetString(GL11.GL_RENDERER));
        DebugLogger.debug("Vendor: " + GL11.glGetString(GL11.GL_VENDOR));

        // Create modern renderer (shaders + batched rendering)
        modernRenderer = new ModernRenderer();
        
        // Set modern renderer in sprite and font classes
        LWJGLSprite.setModernRenderer(modernRenderer);
        TrueTypeFont.setModernRenderer(modernRenderer);

        // Get physical framebuffer size (for viewport/scissor)
        // Note: width/height are already set to user's selected resolution (logical)
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            
            GLFW.glfwGetFramebufferSize(windowHandle, w, h);
            physicalWidth = w.get(0);
            physicalHeight = h.get(0);
        }
        
        DebugLogger.debug("Window: " + windowWidth + "x" + windowHeight + 
                         ", Framebuffer: " + physicalWidth + "x" + physicalHeight +
                         ", User Resolution: " + width + "x" + height);

        // Center window (skip on Wayland - not supported)
        if (!isWayland && !fullscreen) {
            centerWindow();
        }

        // Setup callbacks AFTER context is current
        setupCallbacks();

        // Show window
        GLFW.glfwShowWindow(windowHandle);

        // Enable textures
        DebugLogger.debug("Calling glEnable(GL_TEXTURE_2D)...");
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        DebugLogger.debug("Done glEnable");

        // Disable depth test for 2D rendering
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        // Set clear color
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        // Enable alpha blending (standard non-premultiplied)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_BLEND);

        // Calculate HiDPI scale factor (physical / logical)
        float hidpiScaleX = (float) physicalWidth / windowWidth;
        float hidpiScaleY = (float) physicalHeight / windowHeight;
        
        // Apply letterboxing: viewport is centered with black borders
        // Scale viewport offset and size by HiDPI factor
        int viewportPhysicalX = (int) (viewportX * hidpiScaleX);
        int viewportPhysicalY = (int) (viewportY * hidpiScaleY);
        int viewportPhysicalW = (int) (width * hidpiScaleX);
        int viewportPhysicalH = (int) (height * hidpiScaleY);
        
        DebugLogger.debug("Viewport: pos=(" + viewportPhysicalX + ", " + viewportPhysicalY + 
                         "), size=" + viewportPhysicalW + "x" + viewportPhysicalH);

        // Set viewport with letterboxing (centered user resolution with black borders)
        GL11.glViewport(viewportPhysicalX, viewportPhysicalY, viewportPhysicalW, viewportPhysicalH);

        // Enable scissor test matching viewport (for proper clipping)
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(viewportPhysicalX, viewportPhysicalY, viewportPhysicalW, viewportPhysicalH);

        // Setup ModernRenderer projection matrix (shader-based, includes scale)
        // Initialize ModernRenderer with identity projection (1:1 mapping)
        modernRenderer.setProjection(width, height, 1.0f, 1.0f);
        modernRenderer.setGlobalScale(1.0f, 1.0f); // Default to unscaled for loading
        DebugLogger.debug("ModernRenderer initialized for logical size: " + width + "x" + height);

        // Initialize texture loader
        textureLoader = new TextureLoader();

        // Initialize callback
        callback.initialise();

        // Start game loop
        gameLoop();
    }

    private void setupCallbacks() {
        GLFW.glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            int keyCode = Keyboard.translateKeyCode(key);
            boolean pressed = action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT;
            Keyboard.setKeyState(keyCode, pressed);

            // Handle ESC key - signal to stop (instant exit, no delay)
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
                exitViaESC = true;  // Mark as ESC exit (instant close)
                DebugLogger.debug("ESC pressed - exitViaESC set to true, calling stopRendering()");
                stopRendering();  // ← Signal game loop to exit
            }
        });

        GLFW.glfwSetFramebufferSizeCallback(windowHandle, (window, newPhysicalW, newPhysicalH) -> {
            // Update physical dimensions (for viewport/scissor)
            physicalWidth = newPhysicalW;
            physicalHeight = newPhysicalH;

            DebugLogger.debug("Framebuffer resize: " + newPhysicalW + "x" + newPhysicalH);

            // Only update OpenGL state if we have a current context
            if (capabilities != null) {
                // Recalculate HiDPI scale
                float hidpiScaleX = (float) newPhysicalW / windowWidth;
                float hidpiScaleY = (float) newPhysicalH / windowHeight;

                // Recalculate letterboxed viewport
                int viewportPhysicalX = (int) (viewportX * hidpiScaleX);
                int viewportPhysicalY = (int) (viewportY * hidpiScaleY);
                int viewportPhysicalW = (int) (width * hidpiScaleX);
                int viewportPhysicalH = (int) (height * hidpiScaleY);

                // Set viewport with letterboxing
                GL11.glViewport(viewportPhysicalX, viewportPhysicalY, viewportPhysicalW, viewportPhysicalH);
                GL11.glScissor(viewportPhysicalX, viewportPhysicalY, viewportPhysicalW, viewportPhysicalH);
                
                // Update projection matrix (modern renderer only)
                if (modernRenderer != null) {
                    modernRenderer.setProjection(width, height, scaleX, scaleY);
                }
            }
        });

        // Handle logical window size changes (for projection/game logic)
        GLFW.glfwSetWindowSizeCallback(windowHandle, (window, newLogicalW, newLogicalH) -> {
            // Update window dimensions
            windowWidth = newLogicalW;
            windowHeight = newLogicalH;

            // For fullscreen, recalculate letterbox offset (user resolution unchanged)
            if (fullscreen) {
                viewportX = (windowWidth - width) / 2;
                viewportY = (windowHeight - height) / 2;
                DebugLogger.debug("Window resize: " + newLogicalW + "x" + newLogicalH +
                                 ", Viewport offset=(" + viewportX + ", " + viewportY + ")");
            } else {
                // Windowed mode: window size = user resolution
                width = newLogicalW;
                height = newLogicalH;
                viewportX = 0;
                viewportY = 0;
                DebugLogger.debug("Window resize: " + newLogicalW + "x" + newLogicalH);
            }

            if (capabilities != null) {
                // Update ModernRenderer projection matrix
                if (modernRenderer != null) {
                    modernRenderer.setProjection(width, height, scaleX, scaleY);
                }
            }
        });

        GLFW.glfwSetWindowCloseCallback(windowHandle, (window) -> {
            stopRendering();  // ← Signal game loop to exit
        });
    }

    private void centerWindow() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer winW = stack.mallocInt(1);
            IntBuffer winH = stack.mallocInt(1);

            GLFWVidMode vidmode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
            if (vidmode != null) {
                GLFW.glfwGetWindowSize(windowHandle, winW, winH);
                GLFW.glfwSetWindowPos(
                        windowHandle,
                        (vidmode.width() - winW.get(0)) / 2,
                        (vidmode.height() - winH.get(0)) / 2
                );
            }
        }
    }

    /**
     * Enumerate available display modes.
     * Returns a list sorted by resolution and refresh rate.
     * Note: On Wayland, only the native resolution is typically available.
     */
    public List<DisplayMode> getAvailableDisplayModes() {
        List<DisplayMode> modes = new ArrayList<>();

        // Check if GLFW is already initialized by trying to get primary monitor
        long monitor = GLFW.glfwGetPrimaryMonitor();
        boolean needsCleanup = false;

        if (monitor == 0) {
            // GLFW not initialized, initialize it temporarily
            if (!GLFW.glfwInit()) {
                Logger.global.log(Level.WARNING, "Failed to initialize GLFW for display mode enumeration");
                return getFallbackDisplayModes();
            }
            needsCleanup = true;
            monitor = GLFW.glfwGetPrimaryMonitor();
        }

        try {
            if (monitor != 0) {
                // Get all available video modes for the primary monitor
                GLFWVidMode.Buffer vidModes = GLFW.glfwGetVideoModes(monitor);
                if (vidModes != null) {
                    // Add all modes from the monitor
                    for (int i = 0; i < vidModes.capacity(); i++) {
                        GLFWVidMode mode = vidModes.get(i);
                        // Modern desktop systems use 32-bit framebuffers (8 bits per channel + 8-bit alpha)
                        // GLFW only reports RGB bits; alpha is implicit in desktop compositors
                        int bitsPerPixel = mode.redBits() + mode.greenBits() + mode.blueBits() + 8;
                        modes.add(new DisplayMode(
                                mode.width(),
                                mode.height(),
                                bitsPerPixel,
                                mode.refreshRate()
                        ));
                    }
                } else {
                    // Fallback: try glfwGetVideoMode for single mode
                    GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
                    if (vidMode != null) {
                        // Modern desktop systems use 32-bit framebuffers (8 bits per channel + 8-bit alpha)
                        // GLFW only reports RGB bits; alpha is implicit in desktop compositors
                        int bitsPerPixel = vidMode.redBits() + vidMode.greenBits() + vidMode.blueBits() + 8;
                        modes.add(new DisplayMode(
                                vidMode.width(),
                                vidMode.height(),
                                bitsPerPixel,
                                vidMode.refreshRate()
                        ));
                    }
                }
            }
        } catch (Exception e) {
            Logger.global.log(Level.WARNING, "Failed to get display modes: {0}", e.getMessage());
        } finally {
            // Only terminate if we initialized it
            if (needsCleanup) {
                GLFW.glfwTerminate();
            }
        }

        // Remove duplicates (same width/height/frequency)
        List<DisplayMode> uniqueModes = new ArrayList<>();
        for (DisplayMode mode : modes) {
            boolean isDuplicate = false;
            for (DisplayMode existing : uniqueModes) {
                if (existing.getWidth() == mode.getWidth() &&
                    existing.getHeight() == mode.getHeight() &&
                    existing.getFrequency() == mode.getFrequency()) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                uniqueModes.add(mode);
            }
        }

        // If only 1 resolution is available (Wayland/macOS), add common windowed resolutions
        if (uniqueModes.size() == 1) {
            DisplayMode nativeMode = uniqueModes.get(0);
            List<DisplayMode> commonModes = getCommonWindowedModes(nativeMode);
            // Add common modes that are smaller than native resolution
            for (DisplayMode common : commonModes) {
                if (common.getWidth() < nativeMode.getWidth() || 
                    common.getHeight() < nativeMode.getHeight()) {
                    uniqueModes.add(common);
                }
            }
        }

        // Sort by width (descending), then height (descending), then refresh rate (descending)
        uniqueModes.sort((a, b) -> {
            int cmp = Integer.compare(b.getWidth(), a.getWidth());
            if (cmp != 0) return cmp;
            cmp = Integer.compare(b.getHeight(), a.getHeight());
            if (cmp != 0) return cmp;
            return Integer.compare(b.getFrequency(), a.getFrequency());
        });

        return uniqueModes;
    }

    /**
     * Generate common 16:9 and 4:3 windowed resolutions for systems with only 1 native mode.
     * Useful for Wayland and macOS where only native resolution is enumerated.
     */
    private List<DisplayMode> getCommonWindowedModes(DisplayMode nativeMode) {
        List<DisplayMode> modes = new ArrayList<>();
        int nativeWidth = nativeMode.getWidth();
        int nativeHeight = nativeMode.getHeight();
        int refreshRate = nativeMode.getFrequency();
        int bpp = nativeMode.getBitsPerPixel();

        // Common 16:9 resolutions (widescreen)
        int[][] resolutions16x9 = {
            {1920, 1080}, {1600, 900}, {1366, 768}, {1280, 720}
        };

        // Common 4:3 resolutions (classic)
        int[][] resolutions4x3 = {
            {1600, 1200}, {1400, 1050}, {1280, 1024}, {1280, 960},
            {1152, 864}, {1024, 768}, {800, 600}, {640, 480}
        };

        // Add 16:9 modes
        for (int[] res : resolutions16x9) {
            if (res[0] <= nativeWidth && res[1] <= nativeHeight) {
                modes.add(new DisplayMode(res[0], res[1], bpp, refreshRate));
            }
        }

        // Add 4:3 modes
        for (int[] res : resolutions4x3) {
            if (res[0] <= nativeWidth && res[1] <= nativeHeight) {
                modes.add(new DisplayMode(res[0], res[1], bpp, refreshRate));
            }
        }

        return modes;
    }
    
    /**
     * Fallback display modes when GLFW fails.
     */
    private List<DisplayMode> getFallbackDisplayModes() {
        List<DisplayMode> modes = new ArrayList<>();
        int[][] standardResolutions = {
                {800, 600}, {1024, 768}, {1280, 720}, {1366, 768},
                {1600, 900}, {1920, 1080}, {1920, 1200}
        };
        for (int[] res : standardResolutions) {
            modes.add(new DisplayMode(res[0], res[1], 32, 60));
        }
        return modes;
    }

    @Override
    public void update() {
        if (windowHandle != 0 && capabilities != null) {
            GLFW.glfwSwapBuffers(windowHandle);
            GLFW.glfwPollEvents();
        }
    }

    @Override
    public void setGameWindowCallback(GameWindowCallback callback) {
        this.callback = callback;
    }

    /**
     * Signal the render thread to stop gracefully.
     * The gameLoop will exit and call destroy() automatically.
     */
    public void stopRendering() {
        shouldStop = true;
        // Also mark window for closing in case loop is blocked
        if (windowHandle != 0) {
            GLFW.glfwSetWindowShouldClose(windowHandle, true);
        }
    }

    @Override
    public boolean isKeyDown(int keyCode) {
        return Keyboard.isKeyDown(keyCode);
    }

    @Override
    public void initScales(double w, double h) {
        scaleX = (float) (width / w);
        scaleY = (float) (height / h);
        
        DebugLogger.debug("initScales: Scales updated to (" + scaleX + ", " + scaleY + ") for skin size " + w + "x" + h);
    }

    private void gameLoop() {
        gameRunning = true;
        shouldStop = false;
        exitViaESC = false;  // Reset for each game session

        DebugLogger.debug("Game loop started");
        int frameCount = 0;

        // Initialize FPS limiter timing
        long targetFps = vsync ? 0 : (refreshRate * (fpsLimiter != null ? fpsLimiter.getMultiplier() : 1));
        long targetFrameDurationNs = targetFps > 0 ? 1_000_000_000L / targetFps : 0;
        nextFrameTimeNs = System.nanoTime();
        
        DebugLogger.debug("FPS Limiter: " + fpsLimiter + ", VSync: " + vsync + 
                         ", Refresh Rate: " + refreshRate + "Hz, Target FPS: " + targetFps + 
                         ", Target Frame Duration: " + (targetFrameDurationNs / 1_000_000.0) + "ms");

        while (gameRunning && !shouldStop && !GLFW.glfwWindowShouldClose(windowHandle)) {
            frameCount++;

            // Clear entire screen to black (including letterbox borders)
            // Must disable scissor test to clear the full framebuffer
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);

            // Begin batched rendering (mirror glLoadIdentity() + glScalef() from legacy)
            if (modernRenderer != null) {
                modernRenderer.setGlobalScale(scaleX, scaleY);
                modernRenderer.begin();
            }

            // Render frame using modern batched rendering
            callback.frameRendering();

            // End batched rendering (flushes all quads in single draw call)
            if (modernRenderer != null) {
                modernRenderer.end();
            }

            // Update window (swap buffers and poll events)
            update();

            // FPS limiter - Hybrid spin-wait approach (only when VSync is OFF)
            if (!vsync && fpsLimiter != null && fpsLimiter != GameOptions.FpsLimiter.Unlimited) {
                long now;
                
                // 1. Sleep while we have more than 1ms remaining (saves CPU)
                // Sleep in 1ms increments to avoid OS oversleep issues
                while ((now = System.nanoTime()) < nextFrameTimeNs - 1_000_000L) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                // 2. Spin-wait for the remaining < 1ms for nanosecond precision
                // This burns a tiny fraction of CPU but guarantees exact timing
                while ((now = System.nanoTime()) < nextFrameTimeNs) {
                    Thread.yield(); // Yield to prevent locking up the core entirely
                }
                
                // 3. Advance target time strictly (prevents drift!)
                nextFrameTimeNs += targetFrameDurationNs;
                
                // 4. Catch-up prevention (spiral of death protection)
                // If the OS froze us for too long, don't try to run at 10,000 FPS to catch up
                if (now - nextFrameTimeNs > targetFrameDurationNs) {
                    nextFrameTimeNs = now;
                }
            } else {
                // No FPS limit - just reset timing marker
                nextFrameTimeNs = System.nanoTime();
            }
        }

        // Loop exited - unbind context FIRST before cleanup
        // This is critical to prevent EGL/Mesa crashes
        DebugLogger.debug("=== Game loop exiting === shouldStop=" + shouldStop + " exitViaESC=" + exitViaESC + " glfwWindowShouldClose=" + GLFW.glfwWindowShouldClose(windowHandle) + " frameCount=" + frameCount);
        GLFW.glfwMakeContextCurrent(0);  // ← Unbind context from this thread

        DebugLogger.debug("Game loop exited, context unbound, calling destroy()...");
        destroy();
        DebugLogger.debug("Window destroyed, gameLoop() returning");
    }

    @Override
    public void hideWindow() {
        if (windowHandle != 0) {
            GLFW.glfwHideWindow(windowHandle);
        }
    }

    @Override
    public void showWindow() {
        if (windowHandle != 0) {
            GLFW.glfwShowWindow(windowHandle);
        }
    }

    @Override
    public void destroy() {
        DebugLogger.debug("destroy() called, gameRunning=" + gameRunning);

        // Prevent recursive destroy calls
        if (!gameRunning) {
            DebugLogger.debug("destroy() already called, skipping");
            return;
        }
        gameRunning = false;
        shouldStop = true;

        // Cleanup OpenGL resources before destroying window
        if (modernRenderer != null) {
            DebugLogger.debug("Deleting ModernRenderer...");
            modernRenderer.delete();
            modernRenderer = null;
        }
        
        if (callback != null) {
            DebugLogger.debug("Calling callback.windowClosed()");
            callback.windowClosed();
            callback = null;
        }

        // Free callbacks and hide window BEFORE destroying
        if (windowHandle != 0) {
            DebugLogger.debug("Freeing GLFW callbacks for window " + windowHandle);
            GLFW.glfwSetKeyCallback(windowHandle, null);
            GLFW.glfwSetFramebufferSizeCallback(windowHandle, null);
            GLFW.glfwSetWindowCloseCallback(windowHandle, null);

            // Pause for 5 seconds before hiding/closing window (only if song ended naturally)
            // ESC exit is instant - no delay
            if (!exitViaESC) {
                DebugLogger.debug("Pausing 5 seconds before closing window (song ended)...");
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
            } else {
                DebugLogger.debug("ESC exit - closing window instantly (no delay)");
            }

            // Hide window first (helps with Wayland compositors)
            DebugLogger.debug("Hiding GLFW window " + windowHandle);
            GLFW.glfwHideWindow(windowHandle);
            GLFW.glfwPollEvents();  // Flush compositor messages

            // Small delay to let compositor process hide
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // Ignore
            }

            // Destroy window (context already unbound by gameLoop)
            DebugLogger.debug("Destroying GLFW window " + windowHandle);
            GLFW.glfwDestroyWindow(windowHandle);
            GLFW.glfwPollEvents();  // Ensure compositor processes destruction
            windowHandle = 0;
            DebugLogger.debug("GLFW window destroyed");
        } else {
            DebugLogger.debug("Window already destroyed (windowHandle=0)");
        }

        // DO NOT call glfwTerminate() here - only at application shutdown
        DebugLogger.debug("destroy() complete");
    }

    public boolean isWayland() {
        return isWayland;
    }

    public long getWindowHandle() {
        return windowHandle;
    }

    public GLCapabilities getCapabilities() {
        return capabilities;
    }

    /**
     * Get the texture loader for this window.
     * Package-private, only accessible within the render package.
     */
    TextureLoader getTextureLoader() {
        return textureLoader;
    }

    @Override
    public org.open2jam.render.lwjgl.ModernRenderer getModernRenderer() {
        return modernRenderer;
    }
}
