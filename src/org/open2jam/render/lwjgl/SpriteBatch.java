package org.open2jam.render.lwjgl;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import org.open2jam.util.DebugLogger;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Batched sprite renderer using VBO + VAO with shader-based rendering.
 * Reduces hundreds of draw calls per frame to 1-5 batched draws.
 * 
 * Vertex format (8 floats per vertex = 32 bytes):
 * - Position: 2 floats (x, y)
 * - UV: 2 floats (u, v)
 * - Color: 4 floats (r, g, b, a)
 * 
 * Usage:
 * 1. begin() - Start batching
 * 2. draw() - Add quads to batch
 * 3. render() - Flush all quads in a single draw call
 */
public class SpriteBatch {
    
    // Vertex format: 8 floats per vertex (x, y, u, v, r, g, b, a)
    private static final int VERTEX_SIZE = 8;
    private static final int VERTICES_PER_QUAD = 4;
    private static final int INDICES_PER_QUAD = 6;
    
    // Buffer sizes (can hold up to 10,000 quads = 40,000 vertices)
    private static final int MAX_QUADS = 10000;
    private static final int MAX_VERTICES = MAX_QUADS * VERTICES_PER_QUAD;
    private static final int MAX_INDICES = MAX_QUADS * INDICES_PER_QUAD;
    
    // OpenGL objects
    private final int vaoId;
    private final int vboId;
    private final int iboId;
    
    // Buffers
    private final FloatBuffer vertexBuffer;
    private final ByteBuffer indexBuffer;
    
    // State
    private boolean batching;
    private int quadCount;
    private int vertexIndex;
    
    // Shader uniforms
    private final int projMatrixLocation;
    private final int textureLocation;
    
    // Current texture
    private Texture currentTexture;
    
    /**
     * Creates a new SpriteBatch.
     */
    public SpriteBatch(ShaderProgram shader) {
        // Generate VAO
        vaoId = GL30.glGenVertexArrays();
        if (vaoId == 0) {
            throw new RuntimeException("Failed to generate VAO");
        }
        
        // Generate VBO
        vboId = GL15.glGenBuffers();
        if (vboId == 0) {
            GL30.glDeleteVertexArrays(vaoId);
            throw new RuntimeException("Failed to generate VBO");
        }
        
        // Generate IBO
        iboId = GL15.glGenBuffers();
        if (iboId == 0) {
            GL15.glDeleteBuffers(vboId);
            GL30.glDeleteVertexArrays(vaoId);
            throw new RuntimeException("Failed to generate IBO");
        }
        
        // Create vertex buffer (direct, for streaming)
        vertexBuffer = BufferUtils.createFloatBuffer(MAX_VERTICES * VERTEX_SIZE);
        
        // Create index buffer
        indexBuffer = BufferUtils.createByteBuffer(MAX_INDICES * 4);
        for (int i = 0; i < MAX_QUADS; i++) {
            int baseVertex = i * VERTICES_PER_QUAD;
            indexBuffer.putInt(baseVertex);
            indexBuffer.putInt(baseVertex + 1);
            indexBuffer.putInt(baseVertex + 2);
            indexBuffer.putInt(baseVertex);
            indexBuffer.putInt(baseVertex + 2);
            indexBuffer.putInt(baseVertex + 3);
        }
        indexBuffer.flip();
        
        // Setup VAO
        setupVAO(shader);
        
        // Get uniform locations
        projMatrixLocation = shader.getUniformLocation("uProjection");
        textureLocation = shader.getUniformLocation("uTexture");
        
        DebugLogger.debug("SpriteBatch created: VAO=" + vaoId + ", VBO=" + vboId + ", IBO=" + iboId);
    }
    
    /**
     * Sets up VAO vertex attribute pointers.
     */
    private void setupVAO(ShaderProgram shader) {
        // Bind VAO
        GL30.glBindVertexArray(vaoId);

        // Bind and UPLOAD index data to IBO (this was missing - must call glBufferData!)
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, iboId);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15.GL_STATIC_DRAW);

        // Bind VBO (empty for now; data uploaded each frame in render())
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) MAX_VERTICES * VERTEX_SIZE * 4, GL15.GL_STREAM_DRAW);

        // Position attribute (location = 0): 2 floats at offset 0
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, VERTEX_SIZE * 4, 0);

        // UV attribute (location = 1): 2 floats at offset 8 bytes
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, VERTEX_SIZE * 4, 8);

        // Color attribute (location = 2): 4 floats at offset 16 bytes
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false, VERTEX_SIZE * 4, 16);

        // Unbind VAO
        GL30.glBindVertexArray(0);
    }
    
    /**
     * Begins batching. Call before drawing sprites.
     */
    public void begin() {
        if (batching) {
            throw new IllegalStateException("SpriteBatch.begin() called twice without render()");
        }
        batching = true;
        quadCount = 0;
        vertexIndex = 0;
        vertexBuffer.clear();
        currentTexture = null;
    }
    
    /**
     * Adds a quad to the batch.
     * 
     * @param x X position
     * @param y Y position
     * @param width Width
     * @param height Height
     * @param u0 Left UV coordinate
     * @param v0 Top UV coordinate
     * @param u1 Right UV coordinate
     * @param v1 Bottom UV coordinate
     * @param r Red color (0-1)
     * @param g Green color (0-1)
     * @param b Blue color (0-1)
     * @param a Alpha (0-1)
     */
    public void draw(float x, float y, float width, float height,
                     float u0, float v0, float u1, float v1,
                     float r, float g, float b, float a) {
        if (!batching) {
            throw new IllegalStateException("SpriteBatch.draw() called without begin()");
        }
        
        if (quadCount >= MAX_QUADS) {
            DebugLogger.debug("SpriteBatch overflow - rendering early");
            render();
            begin();
        }
        
        // Vertex 0: top-left
        vertexBuffer.put(x);
        vertexBuffer.put(y);
        vertexBuffer.put(u0);
        vertexBuffer.put(v0);
        vertexBuffer.put(r);
        vertexBuffer.put(g);
        vertexBuffer.put(b);
        vertexBuffer.put(a);
        
        // Vertex 1: bottom-left
        vertexBuffer.put(x);
        vertexBuffer.put(y + height);
        vertexBuffer.put(u0);
        vertexBuffer.put(v1);
        vertexBuffer.put(r);
        vertexBuffer.put(g);
        vertexBuffer.put(b);
        vertexBuffer.put(a);
        
        // Vertex 2: bottom-right
        vertexBuffer.put(x + width);
        vertexBuffer.put(y + height);
        vertexBuffer.put(u1);
        vertexBuffer.put(v1);
        vertexBuffer.put(r);
        vertexBuffer.put(g);
        vertexBuffer.put(b);
        vertexBuffer.put(a);
        
        // Vertex 3: top-right
        vertexBuffer.put(x + width);
        vertexBuffer.put(y);
        vertexBuffer.put(u1);
        vertexBuffer.put(v0);
        vertexBuffer.put(r);
        vertexBuffer.put(g);
        vertexBuffer.put(b);
        vertexBuffer.put(a);
        
        quadCount++;
        vertexIndex += VERTICES_PER_QUAD;
    }
    
    /**
     * Renders all batched quads in a single draw call.
     */
    public void render() {
        if (!batching) {
            throw new IllegalStateException("SpriteBatch.render() called without begin()");
        }
        
        if (quadCount > 0) {
            // Flip buffer for reading
            vertexBuffer.flip();
            
            // Bind VAO FIRST (critical for core profile state)
            GL30.glBindVertexArray(vaoId);

            // Upload vertex data to VBO (orphaning for streaming)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexBuffer, GL15.GL_STREAM_DRAW);
            
            // Draw indexed quads
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, iboId);
            GL11.glDrawElements(GL11.GL_TRIANGLES, quadCount * INDICES_PER_QUAD, GL11.GL_UNSIGNED_INT, 0);
            
            // Unbind VAO
            GL30.glBindVertexArray(0);
        }
        
        batching = false;
    }
    
    /**
     * Deletes the SpriteBatch and frees OpenGL resources.
     */
    public void delete() {
        GL30.glDeleteVertexArrays(vaoId);
        GL15.glDeleteBuffers(vboId);
        GL15.glDeleteBuffers(iboId);
    }
}
