package org.open2jam;

import java.awt.EventQueue;
import java.util.logging.Handler;
import java.util.logging.Level;

import javax.swing.UIManager;

import org.open2jam.gui.Interface;
import org.open2jam.persistence.ChartDatabase;
import org.open2jam.util.DebugLogger;
import org.open2jam.util.Logger;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;

public class Main implements Runnable
{
    private final AppContext context;  // NEW: Store AppContext
    
    public static void main(String []args)
    {
        // LWJGL 3 handles native libraries automatically via Gradle dependencies
        // No need to set java.library.path manually

        // Check if running with proper JVM tuning flags
        checkJvmTuning();

        // Parse command-line arguments
        parseArguments(args);

        // Add shutdown hook to terminate GLFW at application exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                org.lwjgl.glfw.GLFW.glfwTerminate();
            } catch (Exception e) {
                // Ignore
            }
            // Clean up SQLite database
            try {
                ChartDatabase.close();
            } catch (Exception e) {
                // Ignore
            }
        }));

        // NEW: Create Config and AppContext explicitly (composition root)
        Config config = Config.load();
        AppContext context = new AppContext(config);

        // Initialize SQLite chart database
        ChartDatabase.initialize();

        setupLogging();

        // Pass context to initFlatLaf
        initFlatLaf(context);

        // Pass context to Main instance
        EventQueue.invokeLater(new Main(context));
    }

    // NEW: Constructor accepts AppContext
    public Main(AppContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        // Pass context to Interface
        new Interface(context).setVisible(true);
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

    public static void initFlatLaf(AppContext context)
    {
        try {
            // Enable HiDPI scaling specifically for FlatLaf
            System.setProperty("flatlaf.uiScale.enabled", "true");

            // Handle UI Scale: "automatic" (system default) or a custom number
            String uiScaleStr = context.config.getGameOptions().getUiScale();
            if (uiScaleStr != null && !"automatic".equalsIgnoreCase(uiScaleStr)) {
                System.setProperty("flatlaf.uiScale", uiScaleStr);
            }

            // Handle UI Theme selection
            boolean isDark = getIsDarkMode(context);

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
    private static boolean getIsDarkMode(AppContext context) {
        GameOptions.UiTheme themeSetting = context.config.getGameOptions().getUiTheme();
        if (themeSetting == GameOptions.UiTheme.AUTOMATIC) {
            // Auto-detection for system dark mode (heuristic)
            // For now, default to Light until native detection is added via JNA or platform calls
            return false;
        }
        return themeSetting == GameOptions.UiTheme.DARK;
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

    /**
     * Check if JVM tuning flags are present, warn if running without them.
     * This ensures users get optimal performance for rhythm gameplay.
     */
    private static void checkJvmTuning() {
        boolean hasMaxRAMPercentage = System.getProperty("XX:MaxRAMPercentage") != null ||
            java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .anyMatch(arg -> arg.contains("MaxRAMPercentage"));
        
        boolean hasZGC = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
            .anyMatch(arg -> arg.contains("UseZGC"));
        
        // If neither flag is present, user likely ran JAR directly without launch script
        if (!hasMaxRAMPercentage && !hasZGC) {
            System.out.println("╔══════════════════════════════════════════════════════════════╗");
            System.out.println("║  WARNING: Running without optimized JVM tuning flags!        ║");
            System.out.println("║                                                              ║");
            System.out.println("║  For best performance, please use the launch script:         ║");
            System.out.println("║    ./open2jam-modern          (Linux)                        ║");
            System.out.println("║    open2jam-modern.command    (macOS - double-click)         ║");
            System.out.println("║    open2jam-modern.bat        (Windows)                      ║");
            System.out.println("║                                                              ║");
            System.out.println("║  The launch script configures:                               ║");
            System.out.println("║    - Dynamic heap sizing (30% of RAM, max 16GB)              ║");
            System.out.println("║    - ZGC for sub-millisecond GC pauses                       ║");
            System.out.println("║    - Optimized settings for rhythm gameplay                  ║");
            System.out.println("║                                                              ║");
            System.out.println("║  Continuing in 3 seconds... (press Ctrl+C to cancel)         ║");
            System.out.println("╚══════════════════════════════════════════════════════════════╝");
            
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                // Continue anyway
            }
        }
    }
}


