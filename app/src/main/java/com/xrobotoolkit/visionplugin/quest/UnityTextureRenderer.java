package com.xrobotoolkit.visionplugin.quest;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;
import android.graphics.Bitmap;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Unity Texture Renderer using OpenGL ES 3.0
 * This class handles rendering Unity textures using OpenGL ES shaders
 */
public class UnityTextureRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "UnityTextureRenderer";
    
    // Shader sources
    private static final String VERTEX_SHADER_CODE =
            "#version 300 es\n" +
            "layout (location = 0) in vec4 vPosition;\n" +
            "layout (location = 1) in vec2 vTexCoord;\n" +
            "uniform mat4 uMVPMatrix;\n" +
            "out vec2 texCoord;\n" +
            "void main() {\n" +
            "    gl_Position = uMVPMatrix * vPosition;\n" +
            "    texCoord = vTexCoord;\n" +
            "}";

    private static final String FRAGMENT_SHADER_CODE =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "in vec2 texCoord;\n" +
            "uniform sampler2D uTexture;\n" +
            "uniform float uAlpha;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    vec4 texColor = texture(uTexture, texCoord);\n" +
            "    fragColor = vec4(texColor.rgb, texColor.a * uAlpha);\n" +
            "}";

    // Quad vertices and texture coordinates
    private static final float[] QUAD_VERTICES = {
            -1.0f, -1.0f, 0.0f,  // Bottom left
             1.0f, -1.0f, 0.0f,  // Bottom right
            -1.0f,  1.0f, 0.0f,  // Top left
             1.0f,  1.0f, 0.0f   // Top right
    };

    private static final float[] TEXTURE_COORDS = {
            0.0f, 1.0f,  // Bottom left
            1.0f, 1.0f,  // Bottom right
            0.0f, 0.0f,  // Top left
            1.0f, 0.0f   // Top right
    };

    private static final short[] INDICES = {
            0, 1, 2,  // First triangle
            1, 3, 2   // Second triangle
    };

    private FloatBuffer vertexBuffer;
    private FloatBuffer textureBuffer;
    private ByteBuffer indexBuffer;
    
    private int program;
    private int textureId;
    private int mvpMatrixHandle;
    private int alphaHandle;
    private int textureHandle;
    
    private float[] mvpMatrix = new float[16];
    private float[] projectionMatrix = new float[16];
    private float[] viewMatrix = new float[16];
    
    private float alpha = 1.0f;
    private boolean textureLoaded = false;
    
    private Context context;
    private OnTextureRenderedListener listener;

    public interface OnTextureRenderedListener {
        void onTextureRendered();
        void onRenderError(String error);
    }

    public UnityTextureRenderer(Context context) {
        this.context = context;
        initializeBuffers();
    }

    public void setOnTextureRenderedListener(OnTextureRenderedListener listener) {
        this.listener = listener;
    }

    private void initializeBuffers() {
        // Initialize vertex buffer
        ByteBuffer bb = ByteBuffer.allocateDirect(QUAD_VERTICES.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(QUAD_VERTICES);
        vertexBuffer.position(0);

        // Initialize texture coordinate buffer
        bb = ByteBuffer.allocateDirect(TEXTURE_COORDS.length * 4);
        bb.order(ByteOrder.nativeOrder());
        textureBuffer = bb.asFloatBuffer();
        textureBuffer.put(TEXTURE_COORDS);
        textureBuffer.position(0);

        // Initialize index buffer
        indexBuffer = ByteBuffer.allocateDirect(INDICES.length * 2);
        indexBuffer.order(ByteOrder.nativeOrder());
        for (short index : INDICES) {
            indexBuffer.putShort(index);
        }
        indexBuffer.position(0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        
        // Create shader program
        int vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER_CODE);
        int fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE);
        
        program = GLES30.glCreateProgram();
        GLES30.glAttachShader(program, vertexShader);
        GLES30.glAttachShader(program, fragmentShader);
        GLES30.glLinkProgram(program);
        
        // Get handles to shader variables
        mvpMatrixHandle = GLES30.glGetUniformLocation(program, "uMVPMatrix");
        alphaHandle = GLES30.glGetUniformLocation(program, "uAlpha");
        textureHandle = GLES30.glGetUniformLocation(program, "uTexture");
        
        // Generate texture
        int[] textures = new int[1];
        GLES30.glGenTextures(1, textures, 0);
        textureId = textures[0];
        
        checkGLError("onSurfaceCreated");
        Log.i(TAG, "OpenGL ES surface created successfully");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES30.glViewport(0, 0, width, height);
        
        float ratio = (float) width / height;
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1, 1, 3, 7);
        Matrix.setLookAtM(viewMatrix, 0, 0, 0, 3, 0, 0, 0, 0, 1, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
        
        Log.i(TAG, "Surface changed: " + width + "x" + height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        
        if (!textureLoaded) {
            return;
        }
        
        GLES30.glUseProgram(program);
        
        // Set vertex attributes
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, vertexBuffer);
        GLES30.glEnableVertexAttribArray(0);
        
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, textureBuffer);
        GLES30.glEnableVertexAttribArray(1);
        
        // Set uniforms
        GLES30.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);
        GLES30.glUniform1f(alphaHandle, alpha);
        
        // Bind texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId);
        GLES30.glUniform1i(textureHandle, 0);
        
        // Draw
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, INDICES.length, 
                             GLES30.GL_UNSIGNED_SHORT, indexBuffer);
        
        // Disable vertex arrays
        GLES30.glDisableVertexAttribArray(0);
        GLES30.glDisableVertexAttribArray(1);
        
        checkGLError("onDrawFrame");
        
        if (listener != null) {
            listener.onTextureRendered();
        }
    }

    /**
     * Load a texture from a Bitmap (Unity texture)
     */
    public void loadTexture(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "Cannot load null bitmap");
            return;
        }
        
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId);
        
        // Set texture parameters
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        
        // Load bitmap into texture
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0);
        
        textureLoaded = true;
        
        checkGLError("loadTexture");
        Log.i(TAG, "Texture loaded: " + bitmap.getWidth() + "x" + bitmap.getHeight());
    }

    /**
     * Load texture from byte array (raw Unity texture data)
     */
    public void loadTextureFromBytes(byte[] data, int width, int height, int format) {
        if (data == null) {
            Log.e(TAG, "Cannot load null texture data");
            return;
        }
        
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId);
        
        // Set texture parameters
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        
        ByteBuffer buffer = ByteBuffer.allocateDirect(data.length);
        buffer.put(data);
        buffer.position(0);
        
        // Load texture data
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, format, width, height, 0, format, GLES30.GL_UNSIGNED_BYTE, buffer);
        
        textureLoaded = true;
        
        checkGLError("loadTextureFromBytes");
        Log.i(TAG, "Texture loaded from bytes: " + width + "x" + height);
    }

    public void setAlpha(float alpha) {
        this.alpha = Math.max(0.0f, Math.min(1.0f, alpha));
    }

    public float getAlpha() {
        return alpha;
    }

    public boolean isTextureLoaded() {
        return textureLoaded;
    }

    public void release() {
        if (textureId != 0) {
            GLES30.glDeleteTextures(1, new int[]{textureId}, 0);
            textureId = 0;
        }
        if (program != 0) {
            GLES30.glDeleteProgram(program);
            program = 0;
        }
        textureLoaded = false;
        Log.i(TAG, "Renderer resources released");
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES30.glCreateShader(type);
        GLES30.glShaderSource(shader, shaderCode);
        GLES30.glCompileShader(shader);
        
        int[] compiled = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            String error = GLES30.glGetShaderInfoLog(shader);
            Log.e(TAG, "Shader compilation error: " + error);
            GLES30.glDeleteShader(shader);
            if (listener != null) {
                listener.onRenderError("Shader compilation failed: " + error);
            }
            return 0;
        }
        
        return shader;
    }

    private void checkGLError(String operation) {
        int error = GLES30.glGetError();
        if (error != GLES30.GL_NO_ERROR) {
            String errorMsg = "OpenGL error in " + operation + ": " + error;
            Log.e(TAG, errorMsg);
            if (listener != null) {
                listener.onRenderError(errorMsg);
            }
        }
    }
}
