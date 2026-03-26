package org.open2jam;

import java.awt.EventQueue;
import java.io.File;
import java.util.logging.Handler;
import java.util.logging.Level;
import javax.swing.UIManager;
import com.formdev.flatlaf.FlatDarkLaf;
import org.lwjgl.glfw.GLFW;
import org.open2jam.parsers.ChartCacheSQLite;
import org.open2jam.gui.Interface;
import org.open2jam.util.DebugLogger;
import org.open2jam.util.Logger;

public class Main implements Runnable
{
    private static final String LIB_PATH =
        System.getProperty("user.dir") + File.separator +
        "lib" + File.separator +
        "native" + File.separator +
        getOS();

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
        //Config c = Config.get();
        //if(c.log_handle != null)Logger.global.addHandler(c.log_handle);
        for(Handler h : Logger.global.getHandlers())h.setLevel(Level.INFO);
        Logger.global.setLevel(Level.INFO);
    }

    private static void initFlatLaf()
    {
        try {
            // Enable HiDPI scaling specifically for FlatLaf
            // This ensures Linux environments like KDE Plasma respect scaling factors
            System.setProperty("flatlaf.uiScale.enabled", "true");
            
            // Apply custom UI scale from config if set (1.0 = automatic)
            double uiScale = Config.getInstance().getGameOptions().uiScale;
            if (uiScale > 0.5 && uiScale != 1.0) {
                System.setProperty("flatlaf.uiScale", String.valueOf(uiScale));
            }
            
            // Set up the dark theme as default for modern Open2Jam
            FlatDarkLaf.setup();
            
            // Refine some UI components for a more premium feel
            UIManager.put("Component.focusWidth", 2);
            UIManager.put("Button.arc", 6);
            UIManager.put("ProgressBar.arc", 6);
            UIManager.put("ScrollBar.showButtons", true);
            UIManager.put("TabbedPane.showTabSeparators", true);
            UIManager.put("TabbedPane.selectedBackground", 0x3d3d3d);
            
        } catch (Exception e) {
            Logger.global.log(Level.WARNING, "Failed to initialize FlatLaf, falling back to system LAF", e);
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                // Last resort
            }
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


