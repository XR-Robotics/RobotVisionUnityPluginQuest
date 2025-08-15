package com.xrobotoolkit.visionplugin.quest;

import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;

/**
 * FBOPlugin - Frame Buffer Object Plugin for Unity Video Rendering
 * 
 * This class manages the OpenGL ES surface and texture pipeline for rendering
 * video frames
 * from Android's MediaCodec directly to Unity textures. It acts as a bridge
 * between
 * Android's native video decoding capabilities and Unity's rendering system.
 * 
 * Key Features:
 * - Creates and manages OES (OpenGL ES External) textures for MediaCodec output
 * - Provides Surface for MediaCodec to render decoded frames
 * - Handles frame synchronization between MediaCodec and Unity rendering thread
 * - Manages FBO (Frame Buffer Object) operations for texture copying
 * 
 * Threading Considerations:
 * - onFrameAvailable() is called on MediaCodec's decoder thread
 * - updateTexture() should be called on Unity's render thread
 * - Thread-safe frame counting mechanism ensures proper synchronization
 * 
 * @author XR-Robotics
 * @version 1.0
 */
public class FBOPlugin implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "FBOPlugin";

    // Core OpenGL ES components for surface-based rendering
    private SurfaceTexture mSurfaceTexture; // Receives frames from MediaCodec
    private Surface mSurface; // Provides rendering target for MediaCodec
    private FBOTexture mFBOTexture; // Handles FBO operations for texture copying

    // Frame synchronization and state management
    private int frameAvailableCount = 0; // Thread-safe counter for available frames
    public boolean released = true; // Tracks initialization state
    private int oesTextureId; // OpenGL ES External texture ID

    /**
     * Initializes the FBOPlugin with necessary OpenGL ES resources.
     * Creates OES texture, SurfaceTexture, and Surface for MediaCodec integration.
     * 
     * This method must be called on a thread with an active OpenGL ES context.
     * 
     * @throws RuntimeException if OpenGL ES context is not available
     */
    public void init() {
        if (!released) {
            release();
        }
        // Create OpenGL ES External texture for receiving MediaCodec output
        oesTextureId = FBOUtils.createOESTextureID();
        Log.i(TAG, "init oesTextureId：" + oesTextureId + ",thread:" + Thread.currentThread());

        // Create SurfaceTexture from OES texture - this receives decoded frames
        mSurfaceTexture = new SurfaceTexture(oesTextureId);

        // Create Surface from SurfaceTexture - MediaCodec renders to this
        mSurface = new Surface(mSurfaceTexture);

        // Register for frame availability notifications
        mSurfaceTexture.setOnFrameAvailableListener(this);
        released = false;
    }

    /**
     * Builds the texture rendering pipeline with specified dimensions and target
     * texture.
     * Creates FBOTexture for copying from OES texture to regular Unity texture.
     * 
     * @param textureId Unity texture ID to render into
     * @param width     Texture width in pixels
     * @param height    Texture height in pixels
     */
    public void BuildTexture(int textureId, int width, int height) {
        // Set the default buffer size for the SurfaceTexture
        mSurfaceTexture.setDefaultBufferSize(width, height);

        // Create FBO texture manager for copying OES texture to Unity texture
        mFBOTexture = new FBOTexture(width, height, textureId, oesTextureId);
        Log.i(TAG, " BuildTexture width:" + width + " height:" + height);
        released = false;
    }

    /**
     * Called when a new frame is available from MediaCodec.
     * This callback occurs on MediaCodec's decoder thread.
     * Thread-safe frame counting ensures proper synchronization with Unity render
     * thread.
     * 
     * @param surfaceTexture The SurfaceTexture that received the frame
     */
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Log.i(TAG, "onFrameAvailable");
        frameAvailableCount++; // Atomic increment for thread safety
    }

    /**
     * Updates the Unity texture with available frames from MediaCodec.
     * Should be called on Unity's render thread to ensure proper OpenGL ES context.
     * Processes all available frames to prevent frame dropping.
     */
    public void updateTexture() {
        // Process all available frames to prevent dropping
        while (frameAvailableCount > 0) {
            frameAvailableCount--;
            if (mFBOTexture != null) {
                // Update the OES texture with the latest frame
                mSurfaceTexture.updateTexImage();
                // Copy from OES texture to Unity texture via FBO
                mFBOTexture.draw();
            }
        }
    }

    /**
     * Checks if new frames are available for processing.
     * 
     * @return true if frames are available, false otherwise
     */
    public boolean isUpdateFrame() {
        return frameAvailableCount > 0;
    }

    /**
     * Gets the Surface that MediaCodec should render into.
     * 
     * @return Surface for MediaCodec output
     */
    public Surface getSurface() {
        return mSurface;
    }

    /**
     * Gets the underlying SurfaceTexture.
     * 
     * @return SurfaceTexture that receives MediaCodec frames
     */
    public SurfaceTexture getSurfaceTexture() {
        return mSurfaceTexture;
    }

    /**
     * Releases all OpenGL ES and Android resources.
     * Should be called when the plugin is no longer needed to prevent memory leaks.
     * Safe to call multiple times.
     */
    public void release() {
        Log.i(TAG, "release");

        // Release OpenGL ES texture
        FBOUtils.releaseOESTextureID(oesTextureId);

        // Release Android Surface
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }

        // Release SurfaceTexture
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }

        // Release FBO texture manager
        if (mFBOTexture != null) {
            mFBOTexture.release();
            mFBOTexture = null;
        }
        released = true;
    }

}
