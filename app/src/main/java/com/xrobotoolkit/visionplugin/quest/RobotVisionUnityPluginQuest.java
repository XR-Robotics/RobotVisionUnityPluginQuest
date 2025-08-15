package com.xrobotoolkit.visionplugin.quest;

import android.util.Log;

/**
 * RobotVisionUnityPluginQuest - Main Unity Plugin Interface
 * 
 * This class provides the primary interface for Unity applications to integrate
 * real-time video streaming and decoding capabilities. It acts as a facade that
 * simplifies the complex underlying video processing pipeline.
 * 
 * Key Features:
 * - Real-time H.264 video stream reception via TCP
 * - Hardware-accelerated video decoding using MediaCodec
 * - Direct integration with Unity textures for efficient rendering
 * - Automatic texture updates via callback mechanisms
 * - Optional video recording functionality
 * - PNG frame export capabilities
 * 
 * Architecture:
 * - Uses UnityRenderTexture for OpenGL ES integration
 * - Leverages MediaDecoder for video processing
 * - Implements automatic callback-based texture updates
 * - Provides simplified API for Unity C# integration
 * 
 * Usage Pattern:
 * 1. Call CreateMediaDecoderTexture() to initialize video pipeline
 * 2. Unity receives texture ID via OnInitializedListener callback
 * 3. Video frames are automatically decoded and rendered to Unity texture
 * 4. Call StopDecoder() and Release() when done
 * 
 * Threading Model:
 * - TCP server runs on background thread for network I/O
 * - MediaCodec decoding occurs on decoder thread
 * - Texture updates synchronized with Unity render thread
 * - All public methods are thread-safe
 * 
 * @author XR-Robotics
 * @version 1.0
 */
public class RobotVisionUnityPluginQuest {
    private static final String TAG = "RobotVisionUnityPluginQuest";

    /**
     * Callback interface for texture initialization notifications.
     * Implemented by Unity to receive the texture ID when ready.
     */
    public interface OnInitializedListener {
        /**
         * Called when the video texture has been created and is ready for use.
         * 
         * @param textureId The OpenGL ES texture ID that Unity can use for rendering
         */
        void onInitialized(int textureId);
    }

    // Static reference to the active render texture instance
    private static UnityRenderTexture renderTexture;

    /**
     * Creates and initializes a Unity render texture for media decoding.
     * This method sets up the complete video processing pipeline including:
     * - OpenGL ES texture creation on Unity render thread
     * - MediaCodec decoder initialization with hardware acceleration
     * - TCP server for receiving H.264 video streams
     * - Automatic texture update callbacks
     * 
     * The method is asynchronous - the listener callback provides the texture ID
     * when initialization is complete and the texture is ready for Unity rendering.
     * 
     * @param width    Width of the video texture in pixels
     * @param height   Height of the video texture in pixels
     * @param port     TCP port number to listen on for incoming video data
     * @param record   Whether to record received video data to file (for debugging)
     * @param listener Callback interface to receive texture ID when ready
     */
    public static void CreateMediaDecoderTexture(int width, int height, int port, boolean record,
            OnInitializedListener listener) {
        Log.i(TAG, "Creating MediaDecoder texture: " + width + "x" + height + " on port " + port);

        renderTexture = new UnityRenderTexture(width, height, surface -> {
            Log.i(TAG, "UnityRenderTexture initialized, starting decoder");

            // Start the media decoder server to listen for video data
            renderTexture.startDecoder(port, record);

            // Notify Unity that the texture is ready for use
            listener.onInitialized(renderTexture.getId());
        });
    }

    /**
     * Updates the texture with the latest decoded frame.
     * 
     * Note: With the integrated callback-based approach, texture updates happen
     * automatically via the SurfaceTexture.OnFrameAvailableListener mechanism.
     * This method is kept for compatibility but calling it regularly is not
     * required.
     * 
     * Legacy Usage: In polling-based implementations, this would be called every
     * frame
     * from Unity's Update() method to check for new video frames.
     */
    public static void UpdateTexture() {
        if (renderTexture != null) {
            renderTexture.updateTexture();
        }
    }

    /**
     * Checks if a new frame is available for rendering.
     * 
     * Note: With the integrated callback-based approach, frames are processed
     * automatically and this method always returns false. Frame availability
     * is handled transparently by the SurfaceTexture callback system.
     * 
     * @return Always false as frames are processed automatically via callbacks
     */
    public static boolean IsFrameAvailable() {
        return renderTexture != null && renderTexture.isFrameAvailable();
    }

    /**
     * Stops the media decoder and closes the TCP server.
     * This method should be called when video streaming is no longer needed
     * to free up network resources and stop background processing threads.
     */
    public static void StopDecoder() {
        if (renderTexture != null) {
            renderTexture.stopDecoder();
            Log.i(TAG, "Media decoder stopped");
        }
    }

    /**
     * Saves the current decoded frame as a PNG image.
     * The image is saved to the device's Downloads folder with a timestamp
     * filename.
     * This method is useful for debugging, quality analysis, or creating
     * screenshots.
     * 
     * Note: PNG saving is performed asynchronously on a background thread
     * to avoid blocking the video pipeline.
     */
    public static void SaveCurrentFrame() {
        if (renderTexture != null) {
            renderTexture.saveCurrentFrame();
            Log.i(TAG, "Saving current frame");
        }
    }

    /**
     * Releases all plugin resources including textures, decoders, and network
     * connections.
     * This method should be called when the plugin is no longer needed, typically
     * when the Unity application is closing or the video component is destroyed.
     * 
     * Failure to call this method may result in resource leaks including:
     * - OpenGL ES textures and framebuffers
     * - MediaCodec decoder instances
     * - TCP server sockets and threads
     * - Native memory allocations
     */
    public static void Release() {
        Log.i(TAG, "Releasing plugin resources");

        if (renderTexture != null) {
            renderTexture.release();
            renderTexture = null;
        }
    }

    /**
     * Gets the current texture ID for the active video stream.
     * 
     * @return The OpenGL ES texture ID if initialized, or -1 if not available
     */
    public static int GetTextureId() {
        return renderTexture != null ? renderTexture.getId() : -1;
    }
}
