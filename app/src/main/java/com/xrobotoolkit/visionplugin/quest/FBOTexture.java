package com.xrobotoolkit.visionplugin.quest;

import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * FBOTexture - Frame Buffer Object Texture Renderer
 * 
 * This class handles the OpenGL ES rendering pipeline for copying content from
 * an OES (OpenGL ES External) texture to a regular 2D texture that can be used
 * by Unity.
 * 
 * The class implements a complete OpenGL ES 3.0 rendering pipeline including:
 * - Vertex and fragment shaders for OES texture sampling
 * - Vertex Buffer Objects (VBOs) for geometry data
 * - Frame Buffer Object (FBO) for off-screen rendering
 * - Texture coordinate mapping and transformation
 * 
 * Technical Details:
 * - Uses OpenGL ES 3.0 for improved performance and features
 * - Fragment shader samples from samplerExternalOES for MediaCodec
 * compatibility
 * - Renders a full-screen quad using triangle strip primitive
 * - Supports direct rendering to Unity texture via FBO attachment
 * 
 * @author XR-Robotics
 * @version 1.0
 */
public class FBOTexture {
        /**
         * Vertex shader source code for OpenGL ES 3.0.
         * Transforms vertex positions and passes texture coordinates to fragment
         * shader.
         * Uses normalized device coordinates (-1 to 1) for full-screen rendering.
         */
        private static final String vertexShaderCode = "#version 300 es                       \n" +
                        "in vec4 a_Position;           \n" +
                        "in vec2 a_TexCoord;           \n" +
                        "out vec2 v_TexCoord;          \n" +
                        "void main() {                 \n" +
                        "   gl_Position = a_Position;  \n" +
                        "   v_TexCoord = a_TexCoord;   \n" +
                        "}                             \n";

        /**
         * Fragment shader source code for OpenGL ES 3.0.
         * Samples from external OES texture (required for MediaCodec output).
         * The samplerExternalOES is specifically designed for camera/video textures.
         */
        private static final String fragmentShaderCode = "#version 300 es                                                 \n"
                        +
                        "#extension GL_OES_EGL_image_external_essl3 : require    \n" +
                        "precision mediump float;                                \n" +
                        "in vec2 v_TexCoord;                                     \n" +
                        "out vec4 fragColor;                                     \n" +
                        "uniform samplerExternalOES s_Texture;                   \n" +
                        "void main() {                                           \n" +
                        "   fragColor = texture(s_Texture, v_TexCoord);          \n" +
                        "}                                                       \n";

        /**
         * Vertex data for full-screen quad rendering.
         * Two triangles forming a quad in normalized device coordinates:
         * Triangle 1: (-1,1), (1,1), (-1,-1)
         * Triangle 2: (1,1), (1,-1), (-1,-1)
         * Using triangle strip for efficient rendering.
         */
        private final float[] vertexData = {
                        -1f, 1f, // Top-left
                        1f, 1f, // Top-right
                        -1f, -1f, // Bottom-left
                        1f, -1f, // Bottom-right
        };
        private FloatBuffer vertexBuffer;
        private final int vertexVBO; // Vertex Buffer Object for vertex data

        /**
         * Texture coordinate data for mapping the full texture.
         * Maps texture coordinates (0,0) to (1,1) across the quad:
         * (0,0) - Bottom-left of texture -> Top-left of quad
         * (1,0) - Bottom-right of texture -> Top-right of quad
         * (0,1) - Top-left of texture -> Bottom-left of quad
         * (1,1) - Top-right of texture -> Bottom-right of quad
         */
        private final float[] textureData = {
                        0f, 0f, // Bottom-left of texture
                        1f, 0f, // Bottom-right of texture
                        0f, 1f, // Top-left of texture
                        1f, 1f, // Top-right of texture
        };
        private FloatBuffer textureBuffer;
        private final int textureVBO; // Vertex Buffer Object for texture coordinates

        // Shader program and attribute/uniform locations
        private final int shaderProgram; // Compiled and linked shader program
        private final int a_Position; // Vertex position attribute location
        private final int a_TexCoord; // Texture coordinate attribute location
        private final int s_Texture; // OES texture sampler uniform location

        // Rendering target properties
        private final int width; // Target texture width
        private final int height; // Target texture height
        private final int unityTextureId; // Unity texture to render into
        private final int oesTextureId; // OES texture to read from
        private final int FBO; // Frame Buffer Object for off-screen rendering

        /**
         * Constructs a new FBOTexture renderer.
         * Initializes all OpenGL ES resources including shaders, buffers, and FBO.
         * 
         * @param width          Target texture width in pixels
         * @param height         Target texture height in pixels
         * @param unityTextureId Unity texture ID to render into
         * @param oesTextureId   OES texture ID to sample from
         */
        FBOTexture(int width, int height, int unityTextureId, int oesTextureId) {
                this.width = width;
                this.height = height;
                this.unityTextureId = unityTextureId;
                this.oesTextureId = oesTextureId;
                FBO = FBOUtils.createFBO();

                Log.i("FBOTexture", "unityTextureId:" + unityTextureId + " oesTextureId:" + oesTextureId + " width:"
                                + width + " height:" + height);

                // Generate Vertex Buffer Objects for vertex and texture coordinate data
                int[] vbo = new int[2];
                GLES30.glGenBuffers(2, vbo, 0);

                // Initialize vertex data buffer (native byte order for optimal performance)
                vertexBuffer = ByteBuffer.allocateDirect(vertexData.length * 4)
                                .order(ByteOrder.nativeOrder())
                                .asFloatBuffer()
                                .put(vertexData);
                vertexBuffer.position(0);
                vertexVBO = vbo[0];

                // Initialize texture coordinate buffer
                textureBuffer = ByteBuffer.allocateDirect(textureData.length * 4)
                                .order(ByteOrder.nativeOrder())
                                .asFloatBuffer()
                                .put(textureData);
                textureBuffer.position(0);
                textureVBO = vbo[1];

                // Build and link shader program
                shaderProgram = FBOUtils.buildProgram(vertexShaderCode, fragmentShaderCode);
                GLES30.glUseProgram(shaderProgram);

                // Get attribute and uniform locations
                a_Position = GLES30.glGetAttribLocation(shaderProgram, "a_Position");
                a_TexCoord = GLES30.glGetAttribLocation(shaderProgram, "a_TexCoord");
                s_Texture = GLES30.glGetUniformLocation(shaderProgram, "s_Texture");
        }

        /**
         * Renders the OES texture content to the Unity texture using FBO.
         * This method performs the complete rendering pipeline:
         * 1. Sets up FBO with Unity texture as color attachment
         * 2. Configures viewport and clears background
         * 3. Binds shader program and sets up vertex attributes
         * 4. Binds OES texture and renders full-screen quad
         * 5. Restores default framebuffer
         */
        void draw() {
                // Set up viewport to match target texture dimensions
                GLES30.glViewport(0, 0, width, height);
                GLES30.glClearColor(1.0f, 1.0f, 1.0f, 0.0f);
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

                // Bind FBO for off-screen rendering
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, FBO);

                // Attach Unity texture as color attachment to FBO
                GLES30.glFramebufferTexture2D(
                                GLES30.GL_FRAMEBUFFER,
                                GLES30.GL_COLOR_ATTACHMENT0,
                                GLES30.GL_TEXTURE_2D,
                                unityTextureId,
                                0);

                // Verify framebuffer completeness
                int status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
                if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                        Log.e("OpenGL", "Framebuffer not complete, status: " + status);
                        return;
                }

                // Use our shader program
                GLES30.glUseProgram(shaderProgram);

                // Set up vertex position attribute
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexVBO);
                GLES30.glBufferData(
                                GLES30.GL_ARRAY_BUFFER,
                                vertexData.length * 4,
                                vertexBuffer,
                                GLES30.GL_STATIC_DRAW);

                GLES30.glEnableVertexAttribArray(a_Position);
                GLES30.glVertexAttribPointer(
                                a_Position, 2, GLES30.GL_FLOAT, false, 2 * 4, 0);

                // Set up texture coordinate attribute
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, textureVBO);
                GLES30.glBufferData(
                                GLES30.GL_ARRAY_BUFFER,
                                textureData.length * 4,
                                textureBuffer,
                                GLES30.GL_STATIC_DRAW);

                GLES30.glEnableVertexAttribArray(a_TexCoord);
                GLES30.glVertexAttribPointer(
                                a_TexCoord, 2, GLES30.GL_FLOAT, false, 2 * 4, 0);

                // Bind OES texture for sampling
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
                GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId);
                GLES30.glUniform1i(s_Texture, 0);

                // Render the full-screen quad using triangle strip
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);

                // Clean up OpenGL ES state
                GLES30.glDisableVertexAttribArray(a_Position);
                GLES30.glDisableVertexAttribArray(a_TexCoord);
                GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        }

        /**
         * Releases all OpenGL ES resources.
         * Should be called when the FBOTexture is no longer needed.
         */
        public void release() {
                vertexBuffer = null;
                textureBuffer = null;
                FBOUtils.releaseFBO(FBO);
        }
}
