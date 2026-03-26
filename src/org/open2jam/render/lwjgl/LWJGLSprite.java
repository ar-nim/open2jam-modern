package org.open2jam.render.lwjgl;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.open2jam.GameOptions;
import org.open2jam.render.Sprite;

/**
 * Modern OpenGL sprite implementation using shader-based batched rendering.
 * Compatible with OpenGL 3.3 Core Profile (no display lists or immediate mode).
 */
public class LWJGLSprite implements Sprite {

    /** The texture that stores the image for this sprite */
    private final Texture texture;

    /** the position inside the texture of the sprite */
    private final int x, y;
    private final int originalX, originalY;

    /** The width and height in pixels of this sprite */
    private final int width, height;

    /** Current drawn dimensions (may be sliced) */
    private float drawWidth, drawHeight;

    /** UV coordinates (pre-calculated) */
    private float u0, v0, u1, v1;

    /** the scale of the image */
    private float scale_x = 1f, scale_y = 1f;

    /** the alpha */
    private float alpha = 1f;

    /** do blend alpha */
    private boolean blend_alpha = false;

    /** Visibility mod for special rectangles */
    private final GameOptions.VisibilityMod visibilityMod;

    /** Reference to modern renderer for batched drawing */
    private static ModernRenderer modernRenderer;

    /**
     * Set the modern renderer instance (called once during initialization).
     */
    public static void setModernRenderer(ModernRenderer renderer) {
        modernRenderer = renderer;
    }

    /**
     * Create a new sprite from a specified image.
     *
     * @param window The window in which the sprite will be displayed
     * @param ref A reference to the image on which this sprite should be based
     */
    public LWJGLSprite(LWJGLGameWindow window, URL ref, Rectangle slice) throws IOException {
        texture = window.getTextureLoader().getTexture(ref);
        visibilityMod = null;
        if (slice == null) {
            x = 0;
            y = 0;
            width = texture.getWidth();
            height = texture.getHeight();
        } else {
            x = slice.x;
            y = slice.y;
            width = slice.width;
            height = slice.height;
        }
        this.originalX = x;
        this.originalY = y;
        this.drawWidth = 1.0f;
        this.drawHeight = 1.0f;
        initUVs(x, y, width, height);
    }

    public LWJGLSprite(LWJGLGameWindow window, BufferedImage image) {
        texture = window.getTextureLoader().createTexture(image);
        visibilityMod = null;
        x = 0;
        y = 0;
        this.originalX = 0;
        this.originalY = 0;
        width = texture.getWidth();
        height = texture.getHeight();
        this.drawWidth = 1.0f;
        this.drawHeight = 1.0f;
        initUVs(x, y, width, height);
    }

    public LWJGLSprite(int w, int h, GameOptions.VisibilityMod type) {
        texture = null;
        visibilityMod = type;
        x = 0;
        y = 0;
        this.originalX = 0;
        this.originalY = 0;
        this.width = w;
        this.height = h;
        this.drawWidth = 1.0f;
        this.drawHeight = 1.0f;
        // Default UVs for solid color (will be overridden in draw for visibility mods)
        u0 = 0; v0 = 0; u1 = 0; v1 = 0;
    }

    private void initUVs(int x, int y, int width, int height) {
        float texW = texture.getWidth();
        float texH = texture.getHeight();
        this.u0 = x / texW;
        this.v0 = y / texH;
        this.u1 = (x + width) / texW;
        this.v1 = (y + height) / texH;
    }

    /**
     * Get the width of this sprite in pixels
     *
     * @return The width of this sprite in pixels
     */
    public double getWidth() {
        return width * scale_x;
    }

    /**
     * Get the height of this sprite in pixels
     *
     * @return The height of this sprite in pixels
     */
    public double getHeight() {
        return height * scale_y;
    }

    public Texture getTexture() {
        return texture;
    }

    public void setSlice(float sx, float sy) {
        // sx/sy here are percentages (e.g. 0.5 for half bar) 
        // This exactly mimics the legacy logic for setSlice.
        int px = originalX;
        int py = originalY;
        
        if(sx < 0) px += width;
        if(sy < 0) py += height;
        
        this.drawWidth = sx; // Must preserve sign for negative drawing direction
        this.drawHeight = sy; // Must preserve sign for negative drawing direction
        
        initUVs(px, py, (int)Math.round(sx * width), (int)Math.round(sy * height));
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    /**
     * Draw the sprite at the specified location using modern batched rendering.
     */
    void draw(float px, float py, float sx, float sy, int w, int h, ByteBuffer buffer) {
        // Handle dynamic texture updates (rare case)
        if (buffer != null && texture != null) {
            // Flush modern renderer batch before modifying active texture
            if (modernRenderer != null) {
                modernRenderer.getSpriteBatch().render();
                modernRenderer.getSpriteBatch().begin();
            }

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getTextureID());
            GL11.glTexSubImage2D(
                texture.target, 0, 0, 0,
                w, h,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                buffer);
        }

        // Use modern renderer if available
        if (modernRenderer != null) {
            drawModern(px, py, sx, sy);
        } else {
            // Fallback: legacy rendering (should not happen in normal operation)
            drawLegacy(px, py, sx, sy, w, h, buffer);
        }
    }

    private void drawModern(float px, float py, float sx, float sy) {
        float fw = this.width * sx * this.drawWidth;
        float fh = this.height * sy * this.drawHeight;

        // Pass exact logical floats. The new ModernRenderer shader uses round() to snap these correctly to physical pixels.
        float finalX = px;
        float finalY = py;
        float finalWidth = fw;
        float finalHeight = fh;

        float send_u0 = this.u0;
        float send_v0 = this.v0;
        float send_u1 = this.u1;
        float send_v1 = this.v1;

        // ModernRenderer expects to un-mirror quads with negative dimensions.
        // But for sprites, negative dimensions mean the entity or slice explicitly
        // wants it mirrored (or drawn backwards like LifeBar).
        // Therefore, we pre-swap the UVs here to cancel out ModernRenderer's swap.
        if (finalWidth < 0) {
            float temp = send_u0; send_u0 = send_u1; send_u1 = temp;
        }
        if (finalHeight < 0) {
            float temp = send_v0; send_v0 = send_v1; send_v1 = temp;
        }
 
        if (visibilityMod != null) {
            // Draw visibility mod rectangles (Hidden, Sudden, Dark)
            drawVisibilityModModern(finalX, finalY, finalWidth, finalHeight);
        } else if (texture != null) {
            // Normal textured sprite
            modernRenderer.drawSprite(
                texture.getTextureID(),
                finalX, finalY, finalWidth, finalHeight,
                send_u0, send_v0, send_u1, send_v1,
                1.0f, 1.0f, 1.0f, alpha
            );
        }
    }

    /**
     * Draw visibility mod effects using modern rendering.
     * These are gradient rectangles that hide/show parts of the note field.
     */
    private void drawVisibilityModModern(float px, float py, float width, float height) {
        float split = height / 4f;

        switch (visibilityMod) {
            case Hidden:
                // Gradient from transparent (middle) to black (bottom)
                // Top part: transparent (don't draw)
                // Middle gradient: transparent to black
                modernRenderer.drawSprite(
                    0, px, py + split * 1.9f, width, split * 0.1f,
                    0, 0, 0, 0,
                    0, 0, 0, 0.5f
                );
                // Bottom black
                modernRenderer.drawSprite(
                    0, px, py + split * 2f, width, split * 2f,
                    0, 0, 0, 0,
                    0, 0, 0, 1.0f
                );
                break;

            case Sudden:
                // Black at bottom, gradient to transparent (middle)
                // Bottom black
                modernRenderer.drawSprite(
                    0, px, py, width, split * 1.9f,
                    0, 0, 0, 0,
                    0, 0, 0, 1.0f
                );
                // Middle gradient: black to transparent
                modernRenderer.drawSprite(
                    0, px, py + split * 1.9f, width, split * 0.1f,
                    0, 0, 0, 0,
                    0, 0, 0, 0.5f
                );
                break;

            case Dark:
                // Black at bottom and top, two transparent gaps in middle
                // Bottom black
                modernRenderer.drawSprite(
                    0, px, py, width, split * 1.3f,
                    0, 0, 0, 0,
                    0, 0, 0, 1.0f
                );
                // First gradient
                modernRenderer.drawSprite(
                    0, px, py + split * 1.3f, width, split * 0.2f,
                    0, 0, 0, 0,
                    0, 0, 0, 0.5f
                );
                // Second gradient
                modernRenderer.drawSprite(
                    0, px, py + split * 2.5f, width, split * 0.2f,
                    0, 0, 0, 0,
                    0, 0, 0, 0.5f
                );
                // Top black
                modernRenderer.drawSprite(
                    0, px, py + split * 2.7f, width, split * 1.3f,
                    0, 0, 0, 0,
                    0, 0, 0, 1.0f
                );
                break;

            default:
                break;
        }
    }

    /**
     * Legacy rendering fallback (for debugging only).
     */
    private void drawLegacy(float px, float py, float sx, float sy, int w, int h, ByteBuffer buffer) {
        // This should never be called in production with core profile
        // Kept for debugging/comparison only
        System.err.println("WARNING: LWJGLSprite using legacy rendering path!");
    }

    public void draw(float x, float y, float scale_x, float scale_y) {
        this.draw(x, y, scale_x, scale_y, 0, 0, null);
    }

    public void draw(double x, double y, float scale_x, float scale_y) {
        this.draw((float) x, (float) y, scale_x, scale_y);
    }

    public void draw(double x, double y) {
        this.draw((float) x, (float) y, scale_x, scale_y);
    }

    public void setScale(float x, float y) {
        this.scale_x = x;
        this.scale_y = y;
    }

    public float getScaleX() {
        return scale_x;
    }

    public float getScaleY() {
        return scale_y;
    }

    @Override
    public void draw(double x, double y, int w, int h, ByteBuffer buffer) {
        this.draw((float) x, (float) y, scale_x, scale_y, w, h, buffer);
    }

    @Override
    public void draw(double x, double y, float scale_x, float scale_y, int w, int h, ByteBuffer buffer) {
        this.draw((float) x, (float) y, scale_x, scale_y, w, h, buffer);
    }

    @Override
    public void setBlendAlpha(boolean b) {
        blend_alpha = b;
    }
}
