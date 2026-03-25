package org.open2jam;

/**
 * Stores the version of the game
 *
 * @author Thai Pangsakulyanont
 */
public class Open2jam {

    public static final String PRODUCT_NAME = "open2jam-modern";
    
    // Version is injected from build.gradle manifest
    // Fallback to development version if not available
    public static final String OPEN2JAM_VERSION = System.getProperty("open2jam.version", "0.1.0-SNAPSHOT");

    public static String getProductTitle() {
        return PRODUCT_NAME + " [" + OPEN2JAM_VERSION + "]";
    }

}
