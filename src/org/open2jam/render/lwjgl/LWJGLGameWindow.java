package org.open2jam.render.lwjgl;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.nio.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

import org.open2jam.render.GameWindow;
import org.open2jam.render.GameWindowCallback;
import org.open2jam.render.DisplayMode;
import org.open2jam.util.Logger;

public class LWJGLGameWindow implements GameWindow {
    private static boolean glfwInitialized = false;

    private GameWindowCallback callback;
    private long window;
    private int width;
    private int height;
    private String title;
    private boolean gameRunning = true;
    private float scale_x = 1f, scale_y = 1f;

    private TextureLoader textureLoader;

    public LWJGLGameWindow() {
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
        if (window != 0) {
            glfwSetWindowTitle(window, title);
        }
    }

    @Override
    public void setDisplay(DisplayMode dm, boolean vsync, boolean fs) {
        this.width = dm.getWidth();
        this.height = dm.getHeight();
    }

    @Override
    public int getResolutionHeight() { return height; }
    @Override
    public int getResolutionWidth() { return width; }

    @Override
    public void startRendering() {
        if (callback == null) throw new RuntimeException("Need callback to start rendering!");

        initGLFW();
        initGL();

        textureLoader = new TextureLoader();
        callback.initialise();

        gameLoop();

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        window = 0;
        glfwPollEvents();
    }

    private void initGLFW() {
        synchronized (LWJGLGameWindow.class) {
            if (!glfwInitialized) {
                GLFWErrorCallback.createPrint(System.err).set();

                if (!glfwInit()) {
                    throw new IllegalStateException("Unable to initialize GLFW");
                }
                glfwInitialized = true;
            }
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(width, height, title, NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(win, true);
            }
        });

        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);

            // Wayland does not support setting window position.
            // Calling this on Wayland triggers a GLFW_FEATURE_UNAVAILABLE error.
            // Check multiple environment variables to detect Wayland.
            if (!isWaylandSession()) {
                GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
                if (vidmode != null) {
                    glfwSetWindowPos(window, (vidmode.width() - pWidth.get(0)) / 2, (vidmode.height() - pHeight.get(0)) / 2);
                }
            }
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // Enable v-sync
        glfwShowWindow(window);
    }

    private void initGL() {
        GL.createCapabilities();

        glEnable(GL_TEXTURE_2D);
        glDisable(GL_DEPTH_TEST);
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, width, height, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private void gameLoop() {
        gameRunning = true;
        while (!glfwWindowShouldClose(window) && gameRunning) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glLoadIdentity();
            glScalef(scale_x, scale_y, 1);

            callback.frameRendering();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
        callback.windowClosed();
    }

    @Override
    public void update() {
        glfwSwapBuffers(window);
        glfwPollEvents();
    }

    @Override
    public void setGameWindowCallback(GameWindowCallback callback) {
        this.callback = callback;
    }

    @Override
    public boolean isKeyDown(int keyCode) {
        int glfwKey = Keyboard.toGLFW(keyCode);
        if (glfwKey == GLFW_KEY_UNKNOWN) return false;
        return glfwGetKey(window, glfwKey) == GLFW_PRESS;
    }

    @Override
    public void initScales(double w, double h) {
        scale_x = (float) (width / w);
        scale_y = (float) (height / h);
    }

    @Override
    public void destroy() {
        gameRunning = false;
        glfwSetWindowShouldClose(window, true);
    }

    @Override
    public List<DisplayMode> getAvailableDisplayModes() {
        List<DisplayMode> list = new ArrayList<>();
        java.util.Set<String> uniqueModes = new java.util.HashSet<>();
        int maxW = 0, maxH = 0;
        int currentRefresh = 60;

        try {
            // Attempt hardware enumeration
            if (glfwInit()) {
                long monitor = glfwGetPrimaryMonitor();
                if (monitor != 0) {
                    GLFWVidMode.Buffer modes = glfwGetVideoModes(monitor);
                    if (modes != null) {
                        for (int i = 0; i < modes.limit(); i++) {
                            modes.position(i);
                            int bpp = 32;
                            String key = modes.width() + "x" + modes.height() + "@" + modes.refreshRate();
                            if (uniqueModes.add(key)) {
                                list.add(new DisplayMode(modes.width(), modes.height(), modes.refreshRate(), bpp));
                            }
                            maxW = Math.max(maxW, modes.width());
                            maxH = Math.max(maxH, modes.height());
                            currentRefresh = Math.max(currentRefresh, modes.refreshRate());
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // If anything fails (Missing natives, Wayland/X11 issues), we skip to fallback
        }
        
        // Wayland support & Fallback: Supplement with standard resolutions
        // If list is empty or very small, inject standard monitor resolutions up to maxW/maxH
        if (list.size() <= 1) {
            if (maxW == 0 || maxH == 0) {
                // If enumeration failed entirely, allow up to 4K for selection
                maxW = 3840; maxH = 2160;
            }
            int[][] std = {
                {640, 480}, {800, 600}, {1024, 768}, {1152, 864}, {1280, 720}, {1280, 800}, 
                {1280, 960}, {1280, 1024}, {1366, 768}, {1400, 1050}, {1440, 900}, 
                {1600, 900}, {1600, 1200}, {1680, 1050}, {1920, 1080}, {1920, 1200},
                {2560, 1080}, {2560, 1440}, {3440, 1440}, {3840, 2160}
            };
            for (int[] res : std) {
                if (res[0] <= maxW && res[1] <= maxH) {
                    String key = res[0] + "x" + res[1] + "@" + currentRefresh;
                    if (uniqueModes.add(key)) {
                        list.add(new DisplayMode(res[0], res[1], currentRefresh, 32));
                    }
                }
            }
        }
        
        // Final Sort by width then height
        list.sort((a, b) -> {
            if (a.width() == b.width()) return Integer.compare(a.height(), b.height());
            return Integer.compare(a.width(), b.width());
        });

        return list;
    }

    /**
     * Detects if the current session is running on Wayland.
     * Checks multiple environment variables for robust detection.
     * @return true if running on Wayland, false otherwise
     */
    private static boolean isWaylandSession() {
        // Check XDG_SESSION_TYPE (most common)
        String xdgSessionType = System.getenv("XDG_SESSION_TYPE");
        if (xdgSessionType != null && xdgSessionType.equalsIgnoreCase("wayland")) {
            return true;
        }

        // Check WAYLAND_DISPLAY (Wayland compositor identifier)
        String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        if (waylandDisplay != null && !waylandDisplay.isEmpty()) {
            return true;
        }

        // Check GDK_BACKEND (GTK toolkit backend)
        String gdkBackend = System.getenv("GDK_BACKEND");
        if (gdkBackend != null && gdkBackend.equalsIgnoreCase("wayland")) {
            return true;
        }

        // Check QT_QPA_PLATFORM (Qt toolkit platform)
        String qtQpaPlatform = System.getenv("QT_QPA_PLATFORM");
        if (qtQpaPlatform != null && qtQpaPlatform.toLowerCase().contains("wayland")) {
            return true;
        }

        return false;
    }

    TextureLoader getTextureLoader() {
        return textureLoader;
    }
}
