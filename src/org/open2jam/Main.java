package org.open2jam;

import java.awt.EventQueue;
import java.io.File;
import java.util.logging.Handler;
import java.util.logging.Level;
import javax.swing.UIManager;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import com.formdev.flatlaf.util.SystemInfo;
import org.lwjgl.glfw.GLFW;
import org.open2jam.parsers.ChartCacheSQLite;
import org.open2jam.gui.Interface;
import org.open2jam.util.DebugLogger;
import org.open2jam.util.Logger;

public class Main implements Runnable
{
    public static void main(String []args)
    {
        // LWJGL 3 handles native libraries automatically via Gradle dependencies
        // No need to set java.library.path manually

        // Parse command-line arguments
        parseArguments(args);

        // Add shutdown hook to terminate GLFW at application exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                org.lwjgl.glfw.GLFW.glfwTerminate();
            } catch (Exception e) {
                // Ignore
            }
            // Clean up SQLite cache
            try {
                ChartCacheSQLite.close();
            } catch (Exception e) {
                // Ignore
            }
        }));

        // Initialize Config (loads from save/config.json)
        Config.getInstance();

        // Initialize SQLite chart cache
        ChartCacheSQLite.initialize();

        setupLogging();

        initFlatLaf();

        EventQueue.invokeLater(new Main());
    }

    @Override
    public void run() {
        new Interface().setVisible(true);
    }

    /**
     * Parse command-line arguments.
     * Supported options:
     *   -debug    Enable debug logging
     */
    private static void parseArguments(String[] args) {
        for (String arg : args) {
            if ("-debug".equals(arg)) {
                DebugLogger.setDebugEnabled(true);
                DebugLogger.info("Debug logging enabled");
            }
        }
    }

    private static void setupLogging()
    {
        for(Handler h : Logger.global.getHandlers())h.setLevel(Level.INFO);
        Logger.global.setLevel(Level.INFO);
    }

    public static void initFlatLaf()
    {
        try {
            // Enable HiDPI scaling specifically for FlatLaf
            System.setProperty("flatlaf.uiScale.enabled", "true");

            // Handle UI Scale: "automatic" (system default) or a custom number
            String uiScaleStr = Config.getInstance().getGameOptions().uiScale;
            if (uiScaleStr != null && !"automatic".equalsIgnoreCase(uiScaleStr)) {
                System.setProperty("flatlaf.uiScale", uiScaleStr);
            }

            // Handle UI Theme selection
            boolean isDark = getIsDarkMode();

            // Choose theme based on OS and dark mode preference
            setupLookAndFeel(isDark, getOS());

            // Refine UI for a more premium feel
            UIManager.put("Component.focusWidth", 2);
            UIManager.put("Button.arc", 6);
            UIManager.put("ProgressBar.arc", 6);
            UIManager.put("ScrollBar.showButtons", true);
            UIManager.put("TabbedPane.showTabSeparators", true);

            if (isDark) {
                UIManager.put("TabbedPane.selectedBackground", 0x3d3d3d);
            }

        } catch (Exception e) {
            Logger.global.log(Level.WARNING, "Failed to initialize FlatLaf, falling back to system LAF", e);
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                // Last resort
            }
        }
    }

    /**
     * Determine if dark mode should be used based on theme setting.
     */
    private static boolean getIsDarkMode() {
        GameOptions.UiTheme themeSetting = Config.getInstance().getGameOptions().uiTheme;
        if (themeSetting == GameOptions.UiTheme.Automatic) {
            // Auto-detection for system dark mode (heuristic)
            // For now, default to Light until native detection is added via JNA or platform calls
            return false;
        }
        return themeSetting == GameOptions.UiTheme.Dark;
    }

    /**
     * Set up the appropriate LookAndFeel based on OS and dark mode preference.
     */
    private static void setupLookAndFeel(boolean isDark, String os) {
        if ("macosx".equals(os)) {
            if (isDark) FlatMacDarkLaf.setup();
            else FlatMacLightLaf.setup();
        } else if ("windows".equals(os)) {
            // For Windows, use IntelliJLaf for light and DarkLaf for dark
            if (isDark) FlatDarkLaf.setup();
            else FlatIntelliJLaf.setup();
        } else {
            // Linux/Other: use default Flat themes
            if (isDark) FlatDarkLaf.setup();
            else FlatLightLaf.setup();
        }
    }

    private static String getOS()
    {
        String os = System.getProperty("os.name").toLowerCase();
        if(os.contains("win")){
            return "windows";
        }else if(os.contains("mac")){
            return "macosx";
        }else if(os.contains("nix") || os.contains("nux")){
            return "linux";
        }else if(os.contains("solaris")){
            return "solaris";
        }else{
            return os;
        }
    }
}


