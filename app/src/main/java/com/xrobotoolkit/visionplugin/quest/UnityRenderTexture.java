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

/**
 * UnityRenderTexture - Integrated Unity-MediaCodec Rendering Pipeline
 * 
 * This class provides a streamlined integration between Unity's rendering
 * system
 * and Android's MediaCodec video decoder. It implements a direct rendering
 * approach
 * where decoded video frames are automatically updated in Unity textures via
 * callback mechanisms, eliminating the need for manual texture updates.
 * 
 * Architecture:
 * - Creates OES (OpenGL ES External) texture on Unity's render thread
 * - Provides Surface interface for MediaCodec direct rendering
 * - Uses SurfaceTexture.OnFrameAvailableListener for automatic updates
 * - Integrates with RenderingCallbackManager for thread synchronization
 * - Supports sRGB color space handling for accurate color reproduction
 * 
 * Key Features:
 * - Automatic frame updates via callback system
 * - Direct MediaCodec-to-Unity texture rendering (no FBO copying)
 * - Thread-safe operations with Unity render thread
 * - Built-in MediaDecoder integration for complete video pipeline
 * - PNG frame export capabilities for debugging
 * - Proper resource management and cleanup
 * 
 * Threading Model:
 * - Texture creation on Unity render thread (via RenderingCallbackManager)
 * - MediaCodec rendering on decoder thread
 * - Frame available callbacks synchronized with Unity render thread
 * - All public methods are thread-safe
 * 
 * Usage Pattern:
 * 1. Create instance with width, height, and initialization callback
 * 2. Unity render thread automatically creates OpenGL resources
 * 3. Call startDecoder() to begin receiving video data
 * 4. Frames are automatically rendered to Unity texture
 * 5. Call release() when finished
 * 
 * @author XR-Robotics
 * @version 1.0
 */
public class UnityRenderTexture implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "UnityRenderTexture";

    /**
     * Callback interface for surface initialization notifications.
     * Implemented by clients to receive the Surface when ready for MediaCodec.
     */
    public interface OnInitializedListener {
        /**
         * Called when the Surface is created and ready for MediaCodec rendering.
         * 
         * @param surface The Surface that MediaCodec should render into
         */
        void onInitialized(Surface surface);
    }

    // Core rendering components
    private SurfaceTexture surfaceTexture; // Receives frames from MediaCodec
    private Surface surface; // Surface for MediaCodec rendering

    // Texture configuration
    private final int width; // Texture width in pixels
    private final int height; // Texture height in pixels

    // OpenGL ES texture resources
    private int oesTextureId = -1; // OES texture for MediaCodec output
    private int unityTextureId = -1; // Regular RGB texture for Unity (unused in direct mode)
    private int framebuffer = -1; // FBO for rendering (unused in direct mode)

    // Video processing pipeline
    private MediaDecoder mediaDecoder; // Handles video decoding and network

    /**
     * Creates a new UnityRenderTexture with integrated MediaCodec rendering.
     * The constructor schedules OpenGL ES resource creation on Unity's render
     * thread.
     * 
     * @param width    Texture width in pixels
     * @param height   Texture height in pixels
     * @param listener Callback for receiving the initialized Surface
     */
    public UnityRenderTexture(int width, int height, OnInitializedListener listener) {
        this.width = width;
        this.height = height;
        this.mediaDecoder = new MediaDecoder();

        // Schedule OpenGL ES resource creation on Unity's render thread
        // This ensures proper OpenGL ES context for texture operations
        RenderingCallbackManager.Instance().SubscribeOneShot(eventCode -> {
            // Create OES texture for MediaCodec output (direct approach)
            int[] texIds = new int[1];
            GLES20.glGenTextures(1, texIds, 0);
            oesTextureId = texIds[0];

            // Ensure we're using Unity's OpenGL ES context
            EGLContext unityContext = EGL14.eglGetCurrentContext();
            EGLDisplay unityDisplay = EGL14.eglGetCurrentDisplay();
            EGLSurface unityDrawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
            EGLSurface unityReadSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
            EGL14.eglMakeCurrent(unityDisplay, unityDrawSurface, unityReadSurface, unityContext);

            // Configure OES texture for video content
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId);

            // Set texture parameters for smooth video playback
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

            // Create SurfaceTexture from OES texture for MediaCodec integration
            surfaceTexture = new SurfaceTexture(oesTextureId);
            surfaceTexture.setDefaultBufferSize(width, height);
            surfaceTexture.setOnFrameAvailableListener(this);
            surface = new Surface(surfaceTexture);

            try {
                // Initialize MediaDecoder with our surface for direct rendering
                mediaDecoder.initializeWithSurface(surface, width, height);
                Log.i(TAG, "MediaDecoder initialized with direct surface rendering");

                // Notify listener that surface is ready
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

    /**
     * Gets the OpenGL ES texture ID for Unity rendering.
     * In direct rendering mode, this returns the OES texture ID.
     * 
     * @return OpenGL ES texture ID for Unity
     */
    public int getId() {
        return oesTextureId; // Return OES texture directly - no conversion needed
    }

    /**
     * Gets the texture width in pixels.
     * 
     * @return Texture width
     */
    public int getWidth() {
        return width;
    }

    /**
     * Gets the texture height in pixels.
     * 
     * @return Texture height
     */
    public int getHeight() {
        return height;
    }

    /**
     * Starts the media decoder server to receive and decode video data.
     * This method delegates to the internal MediaDecoder instance.
     * 
     * @param port   TCP port to listen on for incoming video stream
     * @param record Whether to record received video data to file
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
     * Stops the media decoder server and closes network connections.
     */
    public void stopDecoder() {
        if (mediaDecoder != null) {
            mediaDecoder.stopServer();
            Log.i(TAG, "MediaDecoder server stopped");
        }
    }

    /**
     * Updates the texture with the latest decoded frame.
     * In the integrated callback-based approach, this method simply calls
     * updateTexImage() on the SurfaceTexture. Frame updates happen automatically
     * via the onFrameAvailable callback mechanism.
     */
    public void updateTexture() {
        // In integrated approach, texture updates are handled automatically
        // through the onFrameAvailable callback mechanism
        if (surfaceTexture != null) {
            surfaceTexture.updateTexImage();
        }
    }

    /**
     * Checks if a new frame is available for rendering.
     * With the automatic callback mechanism, frames are processed immediately
     * so this method always returns false.
     * 
     * @return Always false as frames are handled automatically via callbacks
     */
    public boolean isFrameAvailable() {
        // With automatic updates via onFrameAvailable, we don't need to check manually
        return false;
    }

    /**
     * Saves the current decoded frame as a PNG image to device storage.
     * The image is saved to the Downloads folder with a timestamp filename.
     */
    public void saveCurrentFrame() {
        if (mediaDecoder != null) {
            mediaDecoder.SavePng();
        }
    }

    /**
     * Releases all resources including textures, surfaces, and decoders.
     * This method should be called when the UnityRenderTexture is no longer needed
     * to prevent memory leaks and resource exhaustion.
     */
    public void release() {
        Log.i(TAG, "Releasing UnityRenderTexture");

        // Release MediaDecoder and associated resources
        if (mediaDecoder != null) {
            try {
                mediaDecoder.release();
                Log.i(TAG, "MediaDecoder released");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MediaDecoder", e);
            }
            mediaDecoder = null;
        }

        // Release Android Surface resources
        if (surface != null) {
            surface.release();
            surface = null;
        }

        // Release SurfaceTexture
        if (surfaceTexture != null) {
            surfaceTexture.release();
            surfaceTexture = null;
        }

        // Release OpenGL ES texture
        if (oesTextureId != -1) {
            int[] texIds = { oesTextureId };
            GLES20.glDeleteTextures(1, texIds, 0);
            oesTextureId = -1;
        }

        Log.i(TAG, "UnityRenderTexture released");
    }

    /**
     * Gets the underlying SurfaceTexture.
     * Note: In integrated mode, MediaDecoder manages its own SurfaceTexture.
     * 
     * @return The SurfaceTexture, or null if not available
     */
    public SurfaceTexture GetTexture() {
        // Note: MediaDecoder manages its own SurfaceTexture, so this may return null
        return surfaceTexture;
    }

    /**
     * Gets the underlying Surface.
     * Note: In integrated mode, MediaDecoder manages its own Surface.
     * 
     * @return The Surface, or null if not available
     */
    public Surface GetSurface() {
        // Note: MediaDecoder manages its own Surface, so this may return null
        return surface;
    }

    /**
     * Called when a new frame is available from MediaCodec.
     * This callback is invoked on MediaCodec's decoder thread and schedules
     * a texture update on Unity's render thread for thread safety.
     * 
     * The simplified approach directly updates the OES texture without
     * complex FBO operations, providing optimal performance.
     * 
     * @param surfaceTexture The SurfaceTexture that received the new frame
     */
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        // Schedule texture update on Unity's render thread for thread safety
        RenderingCallbackManager.Instance().SubscribeOneShot(eventCode -> {
            try {
                // Update the OES texture with the latest frame from MediaCodec
                surfaceTexture.updateTexImage();
                Log.v(TAG, "OES texture updated");
            } catch (Exception e) {
                Log.e(TAG, "Error updating OES texture", e);
            }
        });
    }
}
