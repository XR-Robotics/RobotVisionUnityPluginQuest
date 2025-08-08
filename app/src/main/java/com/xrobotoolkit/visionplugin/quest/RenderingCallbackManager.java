package com.xrobotoolkit.visionplugin.quest;

import android.util.Log;
import java.util.ArrayList;

public class RenderingCallbackManager {
    private static final String TAG = "RenderingCallbackManager";

    // Load the native library
    static {
        try {
            System.loadLibrary("NativeUnityRenderingCallback");
            Log.d(TAG, "Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native library: " + e.getMessage());
            throw e;
        }
    }

    @SuppressWarnings("JniMissingFunction")
    public native void nativeInit();

    private RenderingCallbackManager() {
    }

    private static RenderingCallbackManager _instance;

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

    private final ArrayList<OnUnityRenderListener> listeners = new ArrayList<>();
    private final ArrayList<OnUnityRenderListener> oneShotListeners = new ArrayList<>();

    public interface OnUnityRenderListener {
        void onUnityRender(int eventCode);
    }

    public void SubscribeRenderEvent(OnUnityRenderListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void UnsubscribeRenderEvent(OnUnityRenderListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public void SubscribeOneShot(OnUnityRenderListener listener) {
        synchronized (oneShotListeners) {
            oneShotListeners.add(listener);
        }
    }

    private void JavaOnRenderEvent(int eventCode) {
        synchronized (listeners) {
            for (OnUnityRenderListener listener : listeners) {
                listener.onUnityRender(eventCode);
            }
        }
        synchronized (oneShotListeners) {
            for (OnUnityRenderListener listener : oneShotListeners) {
                listener.onUnityRender(eventCode);
            }
            oneShotListeners.clear();
        }
    }
}
