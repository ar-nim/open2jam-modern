package org.open2jam.render.lwjgl;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
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
 */
public class LWJGLGameWindow implements GameWindow {

    private long windowHandle = 0;
    private int width;
    private int height;
    private boolean vsync;
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

    // Available display modes
    private List<DisplayMode> displayModes = new ArrayList<>();

    public LWJGLGameWindow() {
        detectWayland();
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
        this.fullscreen = fullscreen;
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

        // Configure GLFW - use legacy OpenGL compatibility
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
        // Request OpenGL 3.0 without any specific profile - gets compatibility context
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 0);
        // Explicitly do NOT set CLIENT_API or OPENGL_PROFILE hints
        // This allows the driver to provide a compatibility context

        // Create window
        long monitor = fullscreen ? GLFW.glfwGetPrimaryMonitor() : 0;
        windowHandle = GLFW.glfwCreateWindow(width, height, title, monitor, 0);

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

        // Get framebuffer size (may differ from window size on HiDPI)
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            GLFW.glfwGetFramebufferSize(windowHandle, w, h);
            width = w.get(0);
            height = h.get(0);
        }

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

        // Enable alpha blending
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_BLEND);

        // Enable scissor test
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, 0, width, height);

        // Setup projection matrix
        DebugLogger.debug("Calling glMatrixMode(GL_PROJECTION)...");
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        DebugLogger.debug("Done glMatrixMode");
        GL11.glLoadIdentity();
        GL11.glOrtho(0, width, height, 0, -1, 1);
        DebugLogger.debug("Calling glMatrixMode(GL_MODELVIEW)...");
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        DebugLogger.debug("Done glMatrixMode");
        GL11.glLoadIdentity();

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

        GLFW.glfwSetFramebufferSizeCallback(windowHandle, (window, w, h) -> {
            width = w;
            height = h;
            // Only call glViewport if we have a current context
            if (capabilities != null) {
                GL11.glViewport(0, 0, w, h);
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
                        modes.add(new DisplayMode(
                                mode.width(),
                                mode.height(),
                                mode.redBits() + mode.greenBits() + mode.blueBits(),
                                mode.refreshRate()
                        ));
                    }
                } else {
                    // Fallback: try glfwGetVideoMode for single mode
                    GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
                    if (vidMode != null) {
                        modes.add(new DisplayMode(
                                vidMode.width(),
                                vidMode.height(),
                                vidMode.redBits() + vidMode.greenBits() + vidMode.blueBits(),
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
    }

    private void gameLoop() {
        gameRunning = true;
        shouldStop = false;
        exitViaESC = false;  // Reset for each game session

        DebugLogger.debug("Game loop started");
        int frameCount = 0;
        while (gameRunning && !shouldStop && !GLFW.glfwWindowShouldClose(windowHandle)) {
            frameCount++;

            // Clear screen
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glLoadIdentity();

            // Apply scale
            GL11.glScalef(scaleX, scaleY, 1);

            // Render frame
            callback.frameRendering();

            // Update window
            update();
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
}
