package com.xrobotoolkit.visionplugin.quest;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.view.Surface;
import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class UnityRenderTexture implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "UnityRenderTexture";

    public interface OnInitializedListener {
        void onInitialized(Surface surface);
    }

    private SurfaceTexture surfaceTexture;
    private Surface surface;
    private final int width;
    private final int height;
    private int oesTextureId = -1; // OES texture for MediaDecoder output
    private int unityTextureId = -1; // Regular RGB texture for Unity
    private int framebuffer = -1; // FBO for rendering
    private MediaDecoder mediaDecoder;

    public UnityRenderTexture(int width, int height, OnInitializedListener listener) {
        this.width = width;
        this.height = height;
        this.mediaDecoder = new MediaDecoder();

        RenderingCallbackManager.Instance().SubscribeOneShot(eventCode -> {
            // Create only one texture - go back to the original approach but fixed
            int[] texIds = new int[1];
            GLES20.glGenTextures(1, texIds, 0);
            oesTextureId = texIds[0];

            EGLContext unityContext = EGL14.eglGetCurrentContext();
            EGLDisplay unityDisplay = EGL14.eglGetCurrentDisplay();
            EGLSurface unityDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
            EGLSurface unityReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
            EGL14.eglMakeCurrent(unityDisplay, unityDrawSurface, unityReadSurface, unityContext);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);

            // Configure texture for sRGB color space handling
            // Note: For OES external textures, sRGB handling is typically managed by the
            // shader
            // The MediaDecoder output format should be configured for sRGB if supported
            Log.i(TAG, "OES texture configured with sRGB color space considerations");

            // Create SurfaceTexture from OES texture
            surfaceTexture = new SurfaceTexture(oesTextureId);
            surfaceTexture.setDefaultBufferSize(width, height);
            surfaceTexture.setOnFrameAvailableListener(this);
            surface = new Surface(surfaceTexture);

            try {
                mediaDecoder.initializeWithSurface(surface, width, height);
                Log.i(TAG, "MediaDecoder initialized with simplified approach");

                if (listener != null) {
                    listener.onInitialized(surface);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize MediaDecoder", e);
                if (listener != null) {
                    listener.onInitialized(null);
                }
            }
        });
    }

    public int getId() {
        return oesTextureId; // Return OES texture directly - no conversion
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * Start the media decoder server to receive and decode video data
     * 
     * @param port   The port to listen on for incoming video data
     * @param record Whether to record the received data to file
     */
    public void startDecoder(int port, boolean record) {
        if (mediaDecoder != null) {
            try {
                mediaDecoder.startServer(port, record);
                Log.i(TAG, "MediaDecoder server started on port " + port);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start MediaDecoder server", e);
            }
        } else {
            Log.e(TAG, "MediaDecoder not initialized");
        }
    }

    /**
     * Stop the media decoder server
     */
    public void stopDecoder() {
        if (mediaDecoder != null) {
            mediaDecoder.stopServer();
            Log.i(TAG, "MediaDecoder server stopped");
        }
    }

    /**
     * Update the texture with the latest decoded frame
     * This is now called automatically via onFrameAvailable callback
     */
    public void updateTexture() {
        // When using the integrated approach, texture updates are handled automatically
        // through the onFrameAvailable callback mechanism
        if (surfaceTexture != null) {
            surfaceTexture.updateTexImage();
        }
    }

    /**
     * Check if a new frame is available for rendering
     * Since we're using the automatic callback mechanism,
     * we don't need to actively check for frames
     * 
     * @return always false as frames are handled automatically
     */
    public boolean isFrameAvailable() {
        // With automatic updates via onFrameAvailable, we don't need to check manually
        return false;
    }

    /**
     * Save the current frame as a PNG image
     */
    public void saveCurrentFrame() {
        if (mediaDecoder != null) {
            mediaDecoder.SavePng();
        }
    }

    public void release() {
        Log.i(TAG, "Releasing UnityRenderTexture");

        if (mediaDecoder != null) {
            try {
                mediaDecoder.release();
                Log.i(TAG, "MediaDecoder released");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaDecoder", e);
            }
            mediaDecoder = null;
        }

        if (surface != null) {
            surface.release();
            surface = null;
        }
        if (surfaceTexture != null) {
            surfaceTexture.release();
            surfaceTexture = null;
        }

        if (oesTextureId != -1) {
            int[] texIds = { oesTextureId };
            GLES20.glDeleteTextures(1, texIds, 0);
            oesTextureId = -1;
        }

        Log.i(TAG, "UnityRenderTexture released");
    }

    public SurfaceTexture GetTexture() {
        // Note: MediaDecoder manages its own SurfaceTexture, so this may return null
        return surfaceTexture;
    }

    public Surface GetSurface() {
        // Note: MediaDecoder manages its own Surface, so this may return null
        return surface;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        // Simple update - back to original approach
        RenderingCallbackManager.Instance().SubscribeOneShot(eventCode -> {
            try {
                // Just update the OES texture
                surfaceTexture.updateTexImage();
                Log.v(TAG, "OES texture updated");
            } catch (Exception e) {
                Log.e(TAG, "Error updating OES texture", e);
            }
        });
    }
}
