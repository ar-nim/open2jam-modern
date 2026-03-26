package org.open2jam.render.lwjgl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.open2jam.util.DebugLogger;

/**
 * Modern OpenGL 2D renderer using batched rendering with shaders.
 * Replaces legacy immediate mode rendering with VBO/VAO + GLSL shaders.
 */
public class ModernRenderer {
    
    // Shader sources
    private static final String VERTEX_SHADER = 
        "#version 330 core\n" +
        "\n" +
        "layout(location = 0) in vec2 aPos;\n" +
        "layout(location = 1) in vec2 aUV;\n" +
        "layout(location = 2) in vec4 aColor;\n" +
        "\n" +
        "uniform mat4 uProjection;\n" +
        "uniform vec2 uGlobalScale;\n" +
        "\n" +
        "out vec2 vUV;\n" +
        "out vec4 vColor;\n" +
        "\n" +
        "void main() {\n" +
        "    vec2 scaledPos = vec2(aPos.x * uGlobalScale.x, aPos.y * uGlobalScale.y);\n" +
        "    // Legacy rendering didn't snap vertices before projection. \n" +
        "    // Snapping here causes major seams at high resolutions (e.g. 1600x1200)\n" +
        "    // where the scale is non-integer or logical coordinates don't map 1:1.\n" +
        "    gl_Position = uProjection * vec4(scaledPos.x, scaledPos.y, 0.0, 1.0);\n" +
        "    vUV = aUV;\n" +
        "    vColor = aColor;\n" +
        "}\n";
    
    private static final String FRAGMENT_SHADER = 
        "#version 330 core\n" +
        "\n" +
        "in vec2 vUV;\n" +
        "in vec4 vColor;\n" +
        "\n" +
        "uniform sampler2D uTexture;\n" +
        "\n" +
        "out vec4 fragColor;\n" +
        "\n" +
        "void main() {\n" +
        "    vec4 texColor = texture(uTexture, vUV);\n" +
        "    fragColor = texColor * vColor;\n" +
        "}\n";
    
    // Components
    private final ShaderProgram shader;
    private final SpriteBatch spriteBatch;
    
    // Projection matrix
    private float[] projectionMatrix;
    private float globalScaleX = 1.0f;
    private float globalScaleY = 1.0f;
    
    // Current texture and blending state
    private int currentTextureId = 0;
    private int currentSrcFactor = GL11.GL_SRC_ALPHA;
    private int currentDstFactor = GL11.GL_ONE_MINUS_SRC_ALPHA;
    
    // State
    private boolean initialized = false;
    private boolean batching = false;
    
    /**
     * Creates a new ModernRenderer.
     */
    public ModernRenderer() {
        // Create shader program
        shader = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        
        // Create sprite batch
        spriteBatch = new SpriteBatch(shader);
        
        initialized = true;
        DebugLogger.debug("ModernRenderer initialized");
    }
    
    /**
     * Sets up the projection matrix for 2D rendering.
     * 
     * @param width Logical width of the screen
     * @param height Logical height of the screen
     */
    public void setProjection(int width, int height) {
        setProjection(width, height, 1f, 1f);
    }

    /**
     * Sets up the projection matrix for 2D rendering, incorporating skin/window scale.
     * scaleX/scaleY match the legacy glScalef(scaleX, scaleY, 1) call in the game loop.
     *
     * @param width  Logical width of the screen
     * @param height Logical height of the screen
     * @param scaleX Horizontal skin scale factor
     * @param scaleY Vertical skin scale factor
     */
    public void setProjection(int width, int height, float scaleX, float scaleY) {
        // Use standard orthographic projection (0..width, 0..height)
        projectionMatrix = new float[] {
            2.0f / width,  0.0f,           0.0f,  0.0f,  // col 0
            0.0f,         -2.0f / height,  0.0f,  0.0f,  // col 1
            0.0f,          0.0f,          -1.0f,  0.0f,  // col 2
           -1.0f,          1.0f,           0.0f,  1.0f   // col 3
        };

        DebugLogger.debug("Projection matrix set: " + width + "x" + height);
    }

    /**
     * Sets a global scale factor applied to all subsequently drawn sprites.
     * Mimics legacy glScalef() behavior.
     */
    public void setGlobalScale(float sx, float sy) {
        this.globalScaleX = sx;
        this.globalScaleY = sy;
    }
    
    /**
     * Begins batching sprites. Call before drawing.
     */
    public void begin() {
        if (!initialized) return;
        
        if (batching) {
            throw new IllegalStateException("ModernRenderer.begin() called twice without end()");
        }
        
        // Reset current texture ID for new frame
        currentTextureId = 0;

        // Bind shader
        shader.bind();

        // Set projection matrix and global scale
        shader.setUniformMatrix4("uProjection", projectionMatrix);
        shader.setUniform("uGlobalScale", globalScaleX, globalScaleY);

        // Bind texture sampler to unit 0 (must be set explicitly)
        GL20.glUniform1i(shader.getUniformLocation("uTexture"), 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        // Reset blend state cache to default
        currentSrcFactor = GL11.GL_SRC_ALPHA;
        currentDstFactor = GL11.GL_ONE_MINUS_SRC_ALPHA;
        GL11.glBlendFunc(currentSrcFactor, currentDstFactor);

        // Begin sprite batch
        spriteBatch.begin();
        batching = true;
    }
    
    /**
     * Ends batching and renders all sprites.
     */
    public void end() {
        if (!initialized || !batching) return;
        
        // Render sprite batch
        spriteBatch.render();
        
        // Unbind shader
        shader.unbind();
        
        batching = false;
    }
    
    /**
     * Draws a sprite using the batched renderer.
     * 
     * @param textureId OpenGL texture ID
     * @param x X position
     * @param y Y position
     * @param width Width
     * @param height Height
     * @param u0 Left UV coordinate (0-1)
     * @param v0 Top UV coordinate (0-1)
     * @param u1 Right UV coordinate (0-1)
     * @param v1 Bottom UV coordinate (0-1)
     * @param r Red color (0-1)
     * @param g Green color (0-1)
     * @param b Blue color (0-1)
     * @param a Alpha (0-1)
     */
    public void drawSprite(int textureId, float x, float y, float width, float height,
                          float u0, float v0, float u1, float v1,
                          float r, float g, float b, float a) {
        drawSprite(textureId, x, y, width, height, u0, v0, u1, v1, r, g, b, a, 
                   GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
    
    /**
     * Advanced drawSprite with support for custom blending modes.
     * Restores vibrant flare effects of legacy O2Jam.
     */
    public void drawSprite(int textureId, float x, float y, float width, float height,
                          float u0, float v0, float u1, float v1,
                          float r, float g, float b, float a,
                          int srcFactor, int dstFactor) {
        if (!initialized || !batching) return;
        
        // If texture or blending state changes, flush current batch and start a new one
        if (textureId != currentTextureId || srcFactor != currentSrcFactor || dstFactor != currentDstFactor) {
            spriteBatch.render(); // Flush existing quads
            spriteBatch.begin();  // Start new batch
            
            if (textureId != currentTextureId) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
                currentTextureId = textureId;
            }
            
            if (srcFactor != currentSrcFactor || dstFactor != currentDstFactor) {
                GL11.glBlendFunc(srcFactor, dstFactor);
                currentSrcFactor = srcFactor;
                currentDstFactor = dstFactor;
            }
        }
        
        // Add to batch
        // If dimensions are negative, swap UVs to handle mirroring/flipping correctly
        float finalU0 = u0, finalV0 = v0, finalU1 = u1, finalV1 = v1;
        if (width < 0) {
            finalU0 = u1;
            finalU1 = u0;
        }
        if (height < 0) {
            finalV0 = v1;
            finalV1 = v0;
        }

        spriteBatch.draw(x, y, width, height, finalU0, finalV0, finalU1, finalV1, r, g, b, a);
    }
    
    /**
     * Gets the shader program.
     */
    public ShaderProgram getShader() {
        return shader;
    }
    
    /**
     * Gets the sprite batch.
     */
    public SpriteBatch getSpriteBatch() {
        return spriteBatch;
    }
    
    /**
     * Deletes the renderer and frees OpenGL resources.
     */
    public void delete() {
        if (initialized) {
            spriteBatch.delete();
            shader.delete();
            initialized = false;
            DebugLogger.debug("ModernRenderer deleted");
        }
    }
}
