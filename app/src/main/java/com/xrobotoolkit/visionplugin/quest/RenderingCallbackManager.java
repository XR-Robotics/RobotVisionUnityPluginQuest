package com.xrobotoolkit.visionplugin.quest;

import android.util.Log;
import java.util.ArrayList;

/**
 * RenderingCallbackManager - Unity Rendering Thread Synchronization Manager
 * 
 * This singleton class provides a bridge between Unity's native rendering
 * thread and
 * Java/Android OpenGL ES operations. It manages callbacks that need to be
 * executed
 * on Unity's render thread to ensure proper OpenGL ES context and thread
 * safety.
 * 
 * Key Features:
 * - Singleton pattern ensures single instance across the application
 * - Thread-safe callback registration and execution
 * - Support for persistent and one-shot callback subscriptions
 * - Integration with native Unity rendering pipeline via JNI
 * - Proper resource management and error handling
 * 
 * Usage Pattern:
 * 1. Subscribe callback listeners using SubscribeRenderEvent() or
 * SubscribeOneShot()
 * 2. Native Unity plugin calls JavaOnRenderEvent() from render thread
 * 3. All registered callbacks are executed on Unity's render thread with proper
 * OpenGL ES context
 * 
 * Thread Safety:
 * - All callback lists are synchronized to prevent race conditions
 * - Callbacks are executed atomically on Unity's render thread
 * - One-shot callbacks are automatically cleared after execution
 * 
 * @author XR-Robotics
 * @version 1.0
 */
public class RenderingCallbackManager {
    private static final String TAG = "RenderingCallbackManager";

    // Load the native library that provides Unity rendering callback integration
    static {
        try {
            System.loadLibrary("NativeUnityRenderingCallback");
            Log.d(TAG, "Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Native method to initialize the rendering callback system.
     * This method is implemented in the native Unity plugin.
     */
    @SuppressWarnings("JniMissingFunction")
    public native void nativeInit();

    // Private constructor to enforce singleton pattern
    private RenderingCallbackManager() {
    }

    // Singleton instance
    private static RenderingCallbackManager _instance;

    /**
     * Gets the singleton instance of RenderingCallbackManager.
     * Initializes the instance and native callback system if not already done.
     * 
     * @return The singleton RenderingCallbackManager instance
     * @throws UnsatisfiedLinkError if native library initialization fails
     */
    public static RenderingCallbackManager Instance() {
        if (_instance == null) {
            _instance = new RenderingCallbackManager();
            try {
                _instance.nativeInit();
                Log.d(TAG, "RenderingCallbackManager initialized successfully");
            } catch (UnsatisfiedLinkError e) {
                Log.e(TAG, "Failed to initialize native callback manager: " + e.getMessage());
                throw e;
            }
        }
        return _instance;
    }

    // Thread-safe callback storage
    private final ArrayList<OnUnityRenderListener> listeners = new ArrayList<>();
    private final ArrayList<OnUnityRenderListener> oneShotListeners = new ArrayList<>();

    /**
     * Interface for Unity render event listeners.
     * Callbacks implementing this interface will be executed on Unity's render
     * thread.
     */
    public interface OnUnityRenderListener {
        /**
         * Called when a Unity render event occurs.
         * This method is executed on Unity's render thread with active OpenGL ES
         * context.
         * 
         * @param eventCode The event code passed from Unity's render pipeline
         */
        void onUnityRender(int eventCode);
    }

    /**
     * Subscribes a persistent listener for Unity render events.
     * The listener will be called for every render event until explicitly
     * unsubscribed.
     * 
     * @param listener The listener to subscribe for render events
     */
    public void SubscribeRenderEvent(OnUnityRenderListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Unsubscribes a listener from Unity render events.
     * 
     * @param listener The listener to unsubscribe
     */
    public void UnsubscribeRenderEvent(OnUnityRenderListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Subscribes a one-shot listener for Unity render events.
     * The listener will be called once on the next render event and then
     * automatically removed.
     * Useful for OpenGL ES operations that need to be executed exactly once on the
     * render thread.
     * 
     * @param listener The listener to subscribe for a single render event
     */
    public void SubscribeOneShot(OnUnityRenderListener listener) {
        synchronized (oneShotListeners) {
            oneShotListeners.add(listener);
        }
    }

    /**
     * Called from native Unity plugin when a render event occurs.
     * This method is invoked on Unity's render thread and executes all registered
     * callbacks.
     * 
     * Thread Safety: This method is called from Unity's render thread and must be
     * thread-safe.
     * The synchronized blocks ensure atomic execution of callback lists.
     * 
     * @param eventCode The event code from Unity's render pipeline
     */
    private void JavaOnRenderEvent(int eventCode) {
        // Execute all persistent listeners
        synchronized (listeners) {
            for (OnUnityRenderListener listener : listeners) {
                listener.onUnityRender(eventCode);
            }
        }

        // Execute and clear all one-shot listeners
        synchronized (oneShotListeners) {
            for (OnUnityRenderListener listener : oneShotListeners) {
                listener.onUnityRender(eventCode);
            }
            oneShotListeners.clear(); // Automatically remove after execution
        }
    }
}
