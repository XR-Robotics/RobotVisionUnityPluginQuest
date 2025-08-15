package com.xrobotoolkit.visionplugin.quest;

import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.util.Log;

/**
 * FBOUtils - OpenGL ES Frame Buffer Object Utility Class
 * 
 * This utility class provides static methods for managing OpenGL ES resources
 * commonly used in video rendering applications. It encapsulates shader
 * compilation,
 * program linking, FBO management, and OES texture creation/destruction.
 * 
 * Key Features:
 * - Shader compilation and program linking with error handling
 * - Frame Buffer Object (FBO) creation and cleanup
 * - OpenGL ES External (OES) texture management for MediaCodec integration
 * - Comprehensive error checking and logging
 * 
 * Thread Safety:
 * - All methods assume they are called on a thread with an active OpenGL ES
 * context
 * - No internal synchronization - caller responsible for thread safety
 * 
 * @author XR-Robotics
 * @version 1.0
 */
public class FBOUtils {
    private static final String TAG = "FBOUtils";

    /**
     * Compiles an OpenGL ES shader from source code.
     * 
     * @param type       Shader type (GLES30.GL_VERTEX_SHADER or
     *                   GLES30.GL_FRAGMENT_SHADER)
     * @param shaderCode GLSL shader source code as string
     * @return Compiled shader object ID, or 0 if compilation failed
     */
    public static int compileShader(int type, String shaderCode) {
        // Create shader IDs based on different types
        final int shaderObjectId = GLES30.glCreateShader(type);
        if (shaderObjectId == 0) {
            return 0;
        }

        // Connect shader ID and shader program content
        GLES30.glShaderSource(shaderObjectId, shaderCode);

        // Compile shader
        GLES30.glCompileShader(shaderObjectId);

        // Verify if the compilation result has failed
        final int[] compileStatus = new int[1];
        // glGetShaderiv function is versatile and used to validate results in shader
        // compilation
        GLES30.glGetShaderiv(shaderObjectId, GLES30.GL_COMPILE_STATUS, compileStatus, 0);

        if (compileStatus[0] == 0) {
            Log.i(TAG, "compileShader failed: " + shaderCode);
            // Delete if failed to prevent resource leak
            GLES30.glDeleteShader(shaderObjectId);
            return 0;
        }
        return shaderObjectId;
    }

    /**
     * Creates and links an OpenGL ES program from vertex and fragment shaders.
     * 
     * @param vertexShaderId   Compiled vertex shader ID
     * @param fragmentShaderId Compiled fragment shader ID
     * @return Linked program object ID, or 0 if linking failed
     */
    public static int linkProgram(int vertexShaderId, int fragmentShaderId) {
        // Create OpenGL program ID
        final int programObjectId = GLES30.glCreateProgram();
        if (programObjectId == 0) {
            return 0;
        }

        // Attach vertex shader to program
        GLES30.glAttachShader(programObjectId, vertexShaderId);

        // Attach fragment shader to program
        GLES30.glAttachShader(programObjectId, fragmentShaderId);

        // Link the OpenGL program after attaching shaders
        GLES30.glLinkProgram(programObjectId);

        // Verify if the linking result has failed
        final int[] linkStatus = new int[1];
        GLES30.glGetProgramiv(programObjectId, GLES30.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            Log.e(TAG, "linkProgram error");
            // Delete OpenGL program if failed to prevent resource leak
            GLES30.glDeleteProgram(programObjectId);
            return 0;
        }
        return programObjectId;
    }

    /**
     * Validates an OpenGL ES program to ensure it can execute in the current state.
     * 
     * @param programObjectId The program ID to validate
     * @return true if validation successful, false otherwise
     */
    public static boolean validateProgram(int programObjectId) {
        GLES30.glValidateProgram(programObjectId);
        final int[] validateStatus = new int[1];
        GLES30.glGetProgramiv(programObjectId, GLES30.GL_VALIDATE_STATUS, validateStatus, 0);
        return validateStatus[0] != 0;
    }

    /**
     * Builds a complete OpenGL ES program from vertex and fragment shader source
     * code.
     * This is a convenience method that combines shader compilation, program
     * linking, and validation.
     * 
     * @param vertexShaderSource   GLSL vertex shader source code
     * @param fragmentShaderSource GLSL fragment shader source code
     * @return Complete program object ID, or 0 if any step failed
     */
    public static int buildProgram(String vertexShaderSource, String fragmentShaderSource) {
        // Compile vertex shader
        int vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexShaderSource);

        // Compile fragment shader
        int fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderSource);

        // Link shaders into program
        int program = linkProgram(vertexShader, fragmentShader);

        // Validate the program
        boolean valid = validateProgram(program);
        Log.i(TAG, "buildProgram validation result: " + valid);
        return program;
    }

    /**
     * Creates a new Frame Buffer Object (FBO) for off-screen rendering.
     * 
     * @return FBO object ID, or 0 if creation failed
     */
    public static int createFBO() {
        int[] fbo = new int[1];
        GLES30.glGenFramebuffers(fbo.length, fbo, 0);
        return fbo[0];
    }

    /**
     * Releases a Frame Buffer Object and associated resources.
     * 
     * @param id FBO object ID to release
     */
    public static void releaseFBO(int id) {
        int[] texture = new int[1];
        texture[0] = id;
        GLES30.glDeleteFramebuffers(1, texture, 0);
    }

    /**
     * Creates an OpenGL ES External (OES) texture for use with MediaCodec.
     * OES textures are specifically designed for camera and video input sources.
     * They support YUV color formats and automatic color space conversion.
     * 
     * @return OES texture ID, or 0 if creation failed
     */
    public static int createOESTextureID() {
        int[] texture = new int[1];

        // Log current EGL context for debugging
        android.opengl.EGLContext currentContext = EGL14.eglGetCurrentContext();
        Log.i("FBOUtils", "currentContext:" + currentContext);

        // Generate OpenGL ES texture object
        GLES30.glGenTextures(texture.length, texture, 0);
        Log.i(TAG, "glGenTextures - texture created");

        // Bind the texture as an external OES texture
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);

        // Configure texture parameters for video content
        // Linear filtering with mipmapping for smooth scaling
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER,
                GLES30.GL_LINEAR_MIPMAP_LINEAR);
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);

        // Clamp to edge to prevent texture coordinate wrapping artifacts
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);

        // Generate mipmaps for the OES texture
        GLES30.glGenerateMipmap(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        Log.i(TAG, "glGenTextures - texture configured");
        return texture[0];
    }

    /**
     * Releases an OpenGL ES External (OES) texture.
     * 
     * @param id OES texture ID to release
     */
    public static void releaseOESTextureID(int id) {
        int[] texture = new int[1];
        texture[0] = id;
        GLES30.glDeleteTextures(1, texture, 0);
    }

}
