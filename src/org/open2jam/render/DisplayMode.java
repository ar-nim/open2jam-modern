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
        return width + "x" + height + " @" + frequency + "Hz (" + bitsPerPixel + "bpp)";
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
