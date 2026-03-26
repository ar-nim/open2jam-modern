package org.open2jam.render.lwjgl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.open2jam.util.DebugLogger;

import java.util.logging.Level;

/**
 * Shader program utility for loading and managing GLSL shaders.
 * Supports OpenGL 3.3 core profile shaders.
 */
public class ShaderProgram {
    
    private final int programId;
    private boolean validated = false;
    
    /**
     * Creates a new shader program from vertex and fragment shader sources.
     * 
     * @param vertexSource GLSL vertex shader source code
     * @param fragmentSource GLSL fragment shader source code
     */
    public ShaderProgram(String vertexSource, String fragmentSource) {
        // Create program
        programId = GL20.glCreateProgram();
        if (programId == 0) {
            throw new RuntimeException("Failed to create shader program");
        }
        
        // Compile and attach shaders
        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
        
        GL20.glAttachShader(programId, vertexShader);
        GL20.glAttachShader(programId, fragmentShader);
        
        // Link program
        GL20.glLinkProgram(programId);
        
        // Check link status
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(programId, 1024);
            GL20.glDeleteProgram(programId);
            throw new RuntimeException("Failed to link shader program: " + log);
        }
        
        // Validate program
        GL20.glValidateProgram(programId);
        if (GL20.glGetProgrami(programId, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
            DebugLogger.debug("Shader program validation warning: " + 
                GL20.glGetProgramInfoLog(programId, 1024));
            // Don't throw - validation warnings are often informational
        }
        
        // Delete individual shaders (they're now part of the program)
        GL20.glDetachShader(programId, vertexShader);
        GL20.glDetachShader(programId, fragmentShader);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
        
        validated = true;
        DebugLogger.debug("Shader program created successfully (ID: " + programId + ")");
    }
    
    /**
     * Compiles a shader from source code.
     */
    private int compileShader(int type, String source) {
        int shaderId = GL20.glCreateShader(type);
        if (shaderId == 0) {
            throw new RuntimeException("Failed to create shader type: " + type);
        }
        
        GL20.glShaderSource(shaderId, source);
        GL20.glCompileShader(shaderId);
        
        // Check compile status
        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shaderId, 1024);
            GL20.glDeleteShader(shaderId);
            throw new RuntimeException("Failed to compile shader: " + log);
        }
        
        return shaderId;
    }
    
    /**
     * Binds the shader program for use.
     */
    public void bind() {
        GL20.glUseProgram(programId);
    }
    
    /**
     * Unbinds the shader program.
     */
    public void unbind() {
        GL20.glUseProgram(0);
    }
    
    /**
     * Gets the location of a uniform variable.
     */
    public int getUniformLocation(String name) {
        int location = GL20.glGetUniformLocation(programId, name);
        if (location == -1) {
            DebugLogger.debug("Warning: Uniform '" + name + "' not found in shader program");
        }
        return location;
    }
    
    /**
     * Sets a float uniform.
     */
    public void setUniform(String name, float value) {
        int location = getUniformLocation(name);
        GL20.glUniform1f(location, value);
    }
    
    /**
     * Sets a vec2 uniform.
     */
    public void setUniform(String name, float x, float y) {
        int location = getUniformLocation(name);
        GL20.glUniform2f(location, x, y);
    }
    
    /**
     * Sets a vec4 uniform.
     */
    public void setUniform(String name, float x, float y, float z, float w) {
        int location = getUniformLocation(name);
        GL20.glUniform4f(location, x, y, z, w);
    }
    
    /**
     * Sets a 4x4 matrix uniform.
     */
    public void setUniformMatrix4(String name, float[] matrix) {
        int location = getUniformLocation(name);
        GL20.glUniformMatrix4fv(location, false, matrix);
    }
    
    /**
     * Sets an integer uniform.
     */
    public void setUniform(String name, int value) {
        int location = getUniformLocation(name);
        GL20.glUniform1i(location, value);
    }
    
    /**
     * Gets the program ID.
     */
    public int getProgramId() {
        return programId;
    }
    
    /**
     * Deletes the shader program and frees resources.
     */
    public void delete() {
        if (validated) {
            GL20.glDeleteProgram(programId);
            validated = false;
        }
    }
}
