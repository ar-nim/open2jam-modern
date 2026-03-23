package org.open2jam.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Debug logging utility with runtime toggle support.
 * 
 * Usage:
 * - Debug logs: Only shown when -debug launch option is provided
 * - Info logs: Always shown (for important user-facing messages)
 * 
 * Launch with debug logging enabled:
 *   java -jar open2jam-modern-all.jar -debug
 */
public class DebugLogger {
    
    private static boolean debugEnabled = false;
    private static final Logger logger = Logger.getLogger(DebugLogger.class.getName());
    
    /**
     * Enable or disable debug logging.
     * Must be called at application startup before any debug logs.
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }
    
    /**
     * Check if debug logging is enabled.
     */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }
    
    /**
     * Log a debug message.
     * Only outputs if debug mode is enabled via -debug launch option.
     *
     * @param message the message to log
     */
    public static void debug(String message) {
        if (debugEnabled) {
            System.out.println("DEBUG: " + message);
        }
    }

    /**
     * Log a debug message with format arguments.
     * Only outputs if debug mode is enabled via -debug launch option.
     *
     * @param message the message to log
     * @param args format arguments
     */
    public static void debug(String message, Object... args) {
        if (debugEnabled) {
            System.out.println("DEBUG: " + String.format(message, args));
        }
    }
    
    /**
     * Log an info message.
     * Always outputs regardless of debug mode.
     * 
     * @param message the message to log
     */
    public static void info(String message) {
        logger.log(Level.INFO, message);
    }
    
    /**
     * Log an info message with format arguments.
     * Always outputs regardless of debug mode.
     * 
     * @param message the message to log
     * @param args format arguments
     */
    public static void info(String message, Object... args) {
        logger.log(Level.INFO, String.format(message, args));
    }
}
