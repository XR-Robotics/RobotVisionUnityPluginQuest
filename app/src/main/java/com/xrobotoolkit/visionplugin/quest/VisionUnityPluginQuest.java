package com.xrobotoolkit.visionplugin.quest;

import android.util.Log;

public class VisionUnityPluginQuest {
    private static final String TAG = "VisionUnityPluginQuest";

    public interface OnInitializedListener {
        void onInitialized(int textureId);
    }

    private static UnityRenderTexture renderTexture;

    /**
     * Create a UnityRenderTexture configured for media decoding
     * 
     * @param width    Width of the texture
     * @param height   Height of the texture
     * @param port     Port to listen on for incoming video data
     * @param record   Whether to record received data to file
     * @param listener Callback when texture is initialized
     */
    public static void CreateMediaDecoderTexture(int width, int height, int port, boolean record,
            OnInitializedListener listener) {
        Log.i(TAG, "Creating MediaDecoder texture: " + width + "x" + height + " on port " + port);

        renderTexture = new UnityRenderTexture(width, height, surface -> {
            Log.i(TAG, "UnityRenderTexture initialized, starting decoder");

            // Start the media decoder server
            renderTexture.startDecoder(port, record);

            // Notify Unity that the texture is ready
            listener.onInitialized(renderTexture.getId());
        });
    }

    /**
     * Update the texture with the latest decoded frame
     * Note: With the integrated approach, texture updates happen automatically
     * via the SurfaceTexture.OnFrameAvailableListener callback mechanism.
     * This method is kept for compatibility but is not required to be called
     * regularly.
     */
    public static void UpdateTexture() {
        if (renderTexture != null) {
            renderTexture.updateTexture();
        }
    }

    /**
     * Check if a new frame is available for rendering
     * Note: With the integrated approach, frames are handled automatically
     * 
     * @return Always false as frames are processed via callbacks
     */
    public static boolean IsFrameAvailable() {
        return renderTexture != null && renderTexture.isFrameAvailable();
    }

    /**
     * Stop the media decoder
     */
    public static void StopDecoder() {
        if (renderTexture != null) {
            renderTexture.stopDecoder();
            Log.i(TAG, "Media decoder stopped");
        }
    }

    /**
     * Save the current decoded frame as a PNG
     */
    public static void SaveCurrentFrame() {
        if (renderTexture != null) {
            renderTexture.saveCurrentFrame();
            Log.i(TAG, "Saving current frame");
        }
    }

    /**
     * Release all resources
     */
    public static void Release() {
        Log.i(TAG, "Releasing TestPlugin resources");

        if (renderTexture != null) {
            renderTexture.release();
            renderTexture = null;
        }
    }

    /**
     * Get the current texture ID
     * 
     * @return The OpenGL texture ID, or -1 if not initialized
     */
    public static int GetTextureId() {
        return renderTexture != null ? renderTexture.getId() : -1;
    }
}
