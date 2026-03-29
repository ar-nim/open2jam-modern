package org.open2jam.render.lwjgl;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.open2jam.util.Logger;

/**
 * A TrueType font implementation using LWJGL 3 and STB Truetype.
 * Modernized to use OpenGL 3.3 Core Profile with batched rendering.
 *
 * @original author James Chambers (Jimmy)
 * @original author Jeremy Adams (elias4444)
 * @original author Kevin Glass (kevglass)
 * @original author Peter Korzuszek (genail)
 * @new version edited by David Aaron Muhar (bobjob)
 * @modernized for LWJGL 3
 * @modernized for OpenGL 3.3 Core Profile (no glBegin/glEnd)
 */
public class TrueTypeFont {

    public final static int ALIGN_LEFT = 0, ALIGN_RIGHT = 1, ALIGN_CENTER = 2;

    /** Array that holds necessary information about the font characters */
    private final IntObject[] charArray = new IntObject[256];

    /** Map of user defined font characters (Character <-> IntObject) */
    private final Map<Character, IntObject> customChars = new HashMap<>();

    /** Boolean flag on whether AntiAliasing is enabled or not */
    private final boolean antiAlias;

    /** Font's size */
    private int fontSize = 0;

    /** Font's height */
    private int fontHeight = 0;

    /** Texture used to cache the font 0-255 characters */
    private int fontTextureID;

    /** Default font texture width */
    private int textureWidth = 512;

    /** Default font texture height */
    private final int textureHeight = 512;

    /** A reference to Java's AWT Font that we create our font texture from */
    private final Font font;

    private int correctL = 9, correctR = 8;

    /** Reference to modern renderer for batched drawing */
    private static ModernRenderer modernRenderer;

    /**
     * Set the modern renderer instance (called once during initialization).
     */
    public static void setModernRenderer(ModernRenderer renderer) {
        modernRenderer = renderer;
    }

    private static class IntObject {
        /** Character's width */
        public int width;
        /** Character's height */
        public int height;
        /** Character's stored x position */
        public int storedX;
        /** Character's stored y position */
        public int storedY;
    }

    public TrueTypeFont(Font font, boolean antiAlias, char[] additionalChars) {
        this.font = font;
        this.fontSize = font.getSize() + 3;
        this.antiAlias = antiAlias;
        createSet(additionalChars);
        fontHeight -= 1;
        if (fontHeight <= 0) fontHeight = 1;
    }

    public TrueTypeFont(Font font, boolean antiAlias) {
        this(font, antiAlias, null);
    }

    public Font getFont() {
        return font;
    }

    public void setCorrection(boolean on) {
        if (on) {
            correctL = 2;
            correctR = 1;
        } else {
            correctL = 0;
            correctR = 0;
        }
    }

    private BufferedImage getFontImage(char ch) {
        BufferedImage tempfontImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = (Graphics2D) tempfontImage.getGraphics();
        if (antiAlias) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        g.setFont(font);
        FontMetrics fontMetrics = g.getFontMetrics();
        int charwidth = fontMetrics.charWidth(ch) + 8;

        if (charwidth <= 0) {
            charwidth = 7;
        }
        int charheight = fontMetrics.getHeight() + 3;
        if (charheight <= 0) {
            charheight = fontSize;
        }

        BufferedImage fontImage = new BufferedImage(charwidth, charheight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gt = (Graphics2D) fontImage.getGraphics();
        if (antiAlias) {
            gt.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        gt.setFont(font);
        gt.setColor(Color.WHITE);
        int charx = 3;
        int chary = 1;
        gt.drawString(String.valueOf(ch), charx, chary + fontMetrics.getAscent());

        return fontImage;
    }

    private void createSet(char[] customCharsArray) {
        if (customCharsArray != null && customCharsArray.length > 0) {
            textureWidth *= 2;
        }

        try {
            BufferedImage imgTemp = new BufferedImage(textureWidth, textureHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = (Graphics2D) imgTemp.getGraphics();
            g.setColor(new Color(0, 0, 0, 1));
            g.fillRect(0, 0, textureWidth, textureHeight);

            int rowHeight = 0;
            int positionX = 0;
            int positionY = 0;
            int customCharsLength = (customCharsArray != null) ? customCharsArray.length : 0;

            for (int i = 0; i < 256 + customCharsLength; i++) {
                char ch = (i < 256) ? (char) i : customCharsArray[i - 256];
                BufferedImage fontImage = getFontImage(ch);
                IntObject newIntObject = new IntObject();
                newIntObject.width = fontImage.getWidth();
                newIntObject.height = fontImage.getHeight();

                if (positionX + newIntObject.width >= textureWidth) {
                    positionX = 0;
                    positionY += rowHeight;
                    rowHeight = 0;
                }

                newIntObject.storedX = positionX;
                newIntObject.storedY = positionY;

                if (newIntObject.height > fontHeight) {
                    fontHeight = newIntObject.height;
                }
                if (newIntObject.height > rowHeight) {
                    rowHeight = newIntObject.height;
                }

                g.drawImage(fontImage, positionX, positionY, null);
                positionX += newIntObject.width;

                if (i < 256) {
                    charArray[i] = newIntObject;
                } else {
                    customChars.put((char) ch, newIntObject);
                }
                fontImage = null;
            }

            fontTextureID = loadImage(imgTemp);
        } catch (Exception e) {
            Logger.global.log(Level.SEVERE, "Failed to create font: {0}", e.getMessage());
        }
    }

    public int getWidth(String whatchars) {
        int totalwidth = 0;
        IntObject intObject;
        for (int i = 0; i < whatchars.length(); i++) {
            int currentChar = whatchars.charAt(i);
            if (currentChar < 256) {
                intObject = charArray[currentChar];
            } else {
                intObject = customChars.get((char) currentChar);
            }
            if (intObject != null)
                totalwidth += intObject.width;
        }
        return totalwidth;
    }

    public int getHeight() {
        return fontHeight;
    }

    public int getHeight(String HeightString) {
        return fontHeight;
    }

    public int getLineHeight() {
        return fontHeight;
    }

    public void drawString(float x, float y, String whatchars, float scaleX, float scaleY) {
        drawString(x, y, whatchars, 0, whatchars.length() - 1, scaleX, scaleY, ALIGN_LEFT);
    }

    public void drawString(float x, float y, String whatchars, float scaleX, float scaleY, int format) {
        drawString(x, y, whatchars, 0, whatchars.length() - 1, scaleX, scaleY, format);
    }

    public void drawString(float x, float y, String whatchars, int startIndex, int endIndex,
                           float scaleX, float scaleY, int format) {
        drawString(x, y, whatchars, startIndex, endIndex, scaleX, scaleY, format, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * Draw string with custom color.
     */
    public void drawString(float x, float y, String whatchars, float scaleX, float scaleY,
                           float r, float g, float b, float a) {
        drawString(x, y, whatchars, 0, whatchars.length() - 1, scaleX, scaleY, ALIGN_LEFT, r, g, b, a);
    }

    public void drawString(float x, float y, String whatchars, int startIndex, int endIndex,
                           float scaleX, float scaleY, int format,
                           float r, float g, float b, float a) {
        // Use modern renderer if available
        if (modernRenderer != null) {
            drawStringModern(x, y, whatchars, startIndex, endIndex, scaleX, scaleY, format, r, g, b, a);
        } else {
            drawStringLegacy(x, y, whatchars, startIndex, endIndex, scaleX, scaleY, format);
        }
    }

    /**
     * Draw string using modern batched rendering.
     */
    private void drawStringModern(float x, float y, String whatchars, int startIndex, int endIndex,
                                  float scaleX, float scaleY, int format,
                                  float r, float g, float b, float a) {
        IntObject intObject;
        int charCurrent;
        int totalwidth = 0;
        int i = startIndex, d = 1, c = correctL;
        float startY = 0;

        // Calculate starting position based on alignment
        switch (format) {
            case ALIGN_RIGHT: {
                d = -1;
                c = correctR;
                i = endIndex; // Start from end for right alignment
                for (int l = startIndex; l <= endIndex; l++) {
                    if (whatchars.charAt(l) == '\n') startY += fontHeight;
                }
                break;
            }
            case ALIGN_CENTER: {
                for (int l = startIndex; l <= endIndex; l++) {
                    charCurrent = whatchars.charAt(l);
                    if (charCurrent == '\n') break;
                    if (charCurrent < 256) {
                        intObject = charArray[charCurrent];
                    } else {
                        intObject = customChars.get((char) charCurrent);
                    }
                    totalwidth += intObject != null ? intObject.width - correctL : 0;
                }
                totalwidth /= -2;
                break;
            }
            case ALIGN_LEFT:
            default: {
                d = 1;
                c = correctL;
                break;
            }
        }

        // Bind font texture implicitly via drawSprite
        i = (d > 0) ? startIndex : endIndex;

        // Draw each character
        while (i >= startIndex && i <= endIndex) {
            charCurrent = whatchars.charAt(i);
            if (charCurrent < 256) {
                intObject = charArray[charCurrent];
            } else {
                intObject = customChars.get((char) charCurrent);
            }

            if (intObject != null) {
                if (d < 0) totalwidth += (intObject.width - c) * d;
                if (charCurrent == '\n') {
                    startY += fontHeight;
                    totalwidth = 0;
                    if (format == ALIGN_CENTER) {
                        for (int l = i + 1; l <= endIndex; l++) {
                            charCurrent = whatchars.charAt(l);
                            if (charCurrent == '\n') break;
                            if (charCurrent < 256) {
                                intObject = charArray[charCurrent];
                            } else {
                                intObject = customChars.get((char) charCurrent);
                            }
                            totalwidth += intObject != null ? intObject.width - correctL : 0;
                        }
                        totalwidth /= -2;
                    }
                } else {
                    // Calculate UV coordinates with 0.5 pixel inset to prevent sub-pixel bleeding/seams
                    float pxInset = 0.5f;
                    float u0 = (intObject.storedX + pxInset) / (float) textureWidth;
                    float v0 = (intObject.storedY + pxInset) / (float) textureHeight;
                    float u1 = (intObject.storedX + intObject.width - pxInset) / (float) textureWidth;
                    float v1 = (intObject.storedY + intObject.height - pxInset) / (float) textureHeight;

                    // Use rounded screen positions to ensure characters align to pixel grid
                    float drawX = (float) Math.round(totalwidth * scaleX + x);
                    float drawY = (float) Math.round(startY * scaleY + y);
                    float drawWidth = (float) Math.round(intObject.width * scaleX);
                    float drawHeight = (float) Math.round(intObject.height * scaleY);

                    modernRenderer.drawSprite(
                        fontTextureID,
                        drawX, drawY, drawWidth, drawHeight,
                        u0, v0, u1, v1,
                        r, g, b, a
                    );

                    if (d > 0) totalwidth += (intObject.width - c) * d;
                }
                i += d;
            }
        }
    }

    /**
     * Legacy drawing method (fallback, should not be used in production).
     */
    private void drawStringLegacy(float x, float y, String whatchars, int startIndex, int endIndex,
                                  float scaleX, float scaleY, int format) {
        System.err.println("WARNING: TrueTypeFont using legacy rendering path!");
        // Legacy implementation removed - modern renderer required
    }

    public static int loadImage(BufferedImage bufferedImage) {
        try {
            short width = (short) bufferedImage.getWidth();
            short height = (short) bufferedImage.getHeight();
            int bpp = (byte) bufferedImage.getColorModel().getPixelSize();
            ByteBuffer byteBuffer;
            DataBuffer db = bufferedImage.getData().getDataBuffer();
            if (db instanceof DataBufferInt) {
                int intI[] = ((DataBufferInt) (bufferedImage.getData().getDataBuffer())).getData();
                byte newI[] = new byte[intI.length * 4];
                for (int i = 0; i < intI.length; i++) {
                    byte b[] = intToByteArray(intI[i]);
                    int newIndex = i * 4;
                    newI[newIndex] = b[1];
                    newI[newIndex + 1] = b[2];
                    newI[newIndex + 2] = b[3];
                    newI[newIndex + 3] = b[0];
                }
                byteBuffer = ByteBuffer.allocateDirect(width * height * (bpp / 8))
                        .order(ByteOrder.nativeOrder())
                        .put(newI);
            } else {
                byteBuffer = ByteBuffer.allocateDirect(width * height * (bpp / 8))
                        .order(ByteOrder.nativeOrder())
                        .put(((DataBufferByte) (bufferedImage.getData().getDataBuffer())).getData());
            }
            byteBuffer.flip();

            int internalFormat = GL11.GL_RGBA8;
            int format = GL11.GL_RGBA;
            int textureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);

            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, GL11.GL_UNSIGNED_BYTE, byteBuffer);
            return textureId;
        } catch (Exception e) {
            Logger.global.log(Level.SEVERE, "Fatal error: {0}", e.getMessage());
        }
        return -1;
    }

    public static boolean isSupported(String fontname) {
        Font font[] = getFonts();
        for (int i = font.length - 1; i >= 0; i--) {
            if (font[i].getName().equalsIgnoreCase(fontname))
                return true;
        }
        return false;
    }

    public static Font[] getFonts() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
    }

    public static byte[] intToByteArray(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value};
    }

    public void destroy() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glDeleteTextures(fontTextureID);
    }
}
