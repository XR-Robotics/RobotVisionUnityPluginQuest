package com.xrobotoolkit.visionplugin.quest;

import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.util.Log;

public class FBOUtils {
    private static final String TAG = "FBOUtils";
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
        // To verify if the compilation result has failed
        final int[] compileStatus = new int[1];
        // The glDVB-Shaderiv function is quite versatile and is used to validate results in both shader and OpenGL program stages.
        GLES30.glGetShaderiv(shaderObjectId, GLES30.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            Log.i(TAG, "compileShader: "+ shaderCode);
            // Delete if failed
            GLES30.glDeleteShader(shaderObjectId);
            return 0;
        }
        return shaderObjectId;
    }

    // Create OpenGL program and shader links
    public static int linkProgram(int vertexShaderId, int fragmentShaderId) {
        // Create OpenGL program ID
        final int programObjectId = GLES30.glCreateProgram();
        if (programObjectId == 0) {
            return 0;
        }
        // Link vertex shaders
        GLES30.glAttachShader(programObjectId, vertexShaderId);
        // Link to fragment shader
        GLES30.glAttachShader(programObjectId, fragmentShaderId);
        // After linking the shader, link the OpenGL program
        GLES30.glLinkProgram(programObjectId);
        // Verify if the link result has failed
        final int[] linkStatus = new int[1];
        GLES30.glGetProgramiv(programObjectId, GLES30.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            Log.e(TAG,"linkProgram error");
            // Delete OpenGL program if failed
            GLES30.glDeleteProgram(programObjectId);
            return 0;
        }
        return programObjectId;
    }

    public static boolean validateProgram(int programObjectId) {
        GLES30.glValidateProgram(programObjectId);
        final int[] validateStatus = new int[1];
        GLES30.glGetProgramiv(programObjectId, GLES30.GL_VALIDATE_STATUS, validateStatus, 0);
        return validateStatus[0] != 0;
    }

    public static int buildProgram(String vertexShaderSource, String fragmentShaderSource) {
        // Compile vertex shader
        int vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexShaderSource);
        // Compile fragment shader
        int fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderSource);
        int program = linkProgram(vertexShader, fragmentShader);
        boolean valid = validateProgram(program);
        Log.i(TAG, "buildProgram: "+ valid);
        return program;
    }

    public static int createFBO() {
        int[] fbo = new int[1];
        GLES30.glGenFramebuffers(fbo.length, fbo, 0);
        return fbo[0];
    }

    public static void releaseFBO(int id) {
        int[] texture = new int[1];
        texture[0] = id;
        GLES30.glDeleteFramebuffers(1, texture, 0);
    }

    public static int createOESTextureID() {
        int[] texture = new int[1];
        android.opengl.EGLContext currentContext = EGL14.eglGetCurrentContext();
        Log.i("FBOUtils","currentContext:"+currentContext);
        GLES30.glGenTextures(texture.length, texture, 0);
        Log.i(TAG,"glGenTextures1");
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR);
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glGenerateMipmap(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        Log.i(TAG,"glGenTextures2");
        return texture[0];
    }

    public static void releaseOESTextureID(int id) {
        int[] texture = new int[1];
        texture[0] = id;
        GLES30.glDeleteTextures(1, texture, 0);
    }


}
