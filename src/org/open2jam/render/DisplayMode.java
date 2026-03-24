package org.open2jam.render;

/**
 * Modern display mode data class.
 * Replaces LWJGL 2 DisplayMode with a simple data holder.
 */
public class DisplayMode {
    private int width;
    private int height;
    private int bitsPerPixel;
    private int frequency;

    /** No-arg constructor for XML serialization */
    public DisplayMode() {
        this(800, 600, 32, 60);
    }

    public DisplayMode(int width, int height, int bitsPerPixel, int frequency) {
        this.width = width;
        this.height = height;
        this.bitsPerPixel = bitsPerPixel;
        this.frequency = frequency;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getBitsPerPixel() {
        return bitsPerPixel;
    }

    public int getFrequency() {
        return frequency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DisplayMode that = (DisplayMode) o;
        return width == that.width &&
               height == that.height &&
               bitsPerPixel == that.bitsPerPixel &&
               frequency == that.frequency;
    }

    @Override
    public int hashCode() {
        int result = width;
        result = 31 * result + height;
        result = 31 * result + bitsPerPixel;
        result = 31 * result + frequency;
        return result;
    }

    @Override
    public String toString() {
        String aspectRatio = calculateAspectRatio(width, height);
        return width + "x" + height + " (" + aspectRatio + ")";
    }

    /**
     * Calculate aspect ratio using iterative tolerance algorithm.
     * Uses epsilon-based comparison to handle floating-point imprecision.
     * Prioritizes common industry-standard aspect ratios (16:9, 16:10, 21:9, etc.)
     * over mathematically equivalent but uncommon ratios (e.g., prefers 16:10 over 8:5).
     * 
     * @param width screen width
     * @param height screen height
     * @return aspect ratio string in format "W:H" (e.g., "16:9", "4:3", "21:9")
     */
    public static String calculateAspectRatio(int width, int height) {
        if (height == 0) return "0:0";
        
        double ratio = (double) width / height;
        
        // Check for ultrawide (21:9) first - industry standard term for 2.3-2.45 ratio
        // This covers 2560x1080 (2.37), 3440x1440 (2.39), 3840x1600 (2.4), etc.
        if (ratio >= 2.30 && ratio <= 2.45) {
            return "21:9";
        }
        
        final double EPSILON = 0.005;  // Tolerance for other ratios (0.5%)
        
        // Common industry-standard aspect ratios (ordered by priority/commonality)
        // These are the ratios people actually use and recognize
        int[][] commonRatios = {
            {16, 9},   // Most common widescreen (1920x1080, 2560x1440, 3840x2160)
            {16, 10},  // Common productivity (1920x1200, 2560x1600, 1680x1050, 1440x900)
            {4, 3},    // Classic/retro (1024x768, 1280x960, 1600x1200, 800x600)
            {3, 2},    // Some laptops/tablets (1440x960, 3000x2000)
            {32, 9},   // Super ultrawide (3840x1080, 5120x1440)
            {5, 4},    // Old LCDs (1280x1024)
            {5, 3},    // Some widescreens (1600x960)
            {2, 1},    // Some ultrawides (2048x1024)
        };
        
        // First, check if the ratio matches any common industry standard
        for (int[] commonRatio : commonRatios) {
            double commonRatioValue = (double) commonRatio[0] / commonRatio[1];
            if (Math.abs(ratio - commonRatioValue) < EPSILON) {
                return commonRatio[0] + ":" + commonRatio[1];
            }
        }
        
        // If no common ratio matched, fall back to iterative method
        // but prefer simpler ratios (smaller numbers)
        final int MAX_DENOMINATOR = 100;
        
        for (int h = 1; h <= MAX_DENOMINATOR; h++) {
            double potentialWidth = ratio * h;
            int roundedWidth = (int) Math.round(potentialWidth);
            double diff = Math.abs(potentialWidth - roundedWidth);
            
            if (diff < EPSILON) {
                // Found a ratio, but check if we can simplify it
                int gcd = gcd(roundedWidth, h);
                return (roundedWidth / gcd) + ":" + (h / gcd);
            }
        }
        
        // Fallback: return the raw ratio if no simple fraction found
        return String.format("%.3f:1", ratio);
    }
    
    /**
     * Calculate greatest common divisor for simplifying ratios.
     */
    private static int gcd(int a, int b) {
        while (b != 0) {
            int temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    /**
     * Check if this display mode is fullscreen capable.
     * For simplicity, all modes are considered fullscreen capable.
     */
    public boolean isFullscreenCapable() {
        return true;
    }
    
    // XML serialization compatibility setters (must be public for JavaBeans)
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
    public void setBitsPerPixel(int bitsPerPixel) { this.bitsPerPixel = bitsPerPixel; }
    public void setRefreshRate(int frequency) { this.frequency = frequency; }
}
