package com.xrobotoolkit.visionplugin.quest;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;

/**
 * GLSurfaceView for rendering Unity textures
 * This view can be integrated into Unity as a native Android plugin
 */
public class UnityTextureView extends GLSurfaceView {
    private static final String TAG = "UnityTextureView";
    
    private UnityTextureRenderer renderer;
    private boolean isInitialized = false;

    public UnityTextureView(Context context) {
        super(context);
        init(context);
    }

    public UnityTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        // Set OpenGL ES 3.0 context
        setEGLContextClientVersion(3);
        
        // Create renderer
        renderer = new UnityTextureRenderer(context);
        setRenderer(renderer);
        
        // Render on demand (not continuously)
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        
        isInitialized = true;
        Log.i(TAG, "UnityTextureView initialized");
    }

    /**
     * Load a Unity texture as Bitmap
     */
    public void loadUnityTexture(Bitmap bitmap) {
        if (!isInitialized) {
            Log.e(TAG, "View not initialized");
            return;
        }
        
        if (bitmap == null) {
            Log.e(TAG, "Cannot load null bitmap");
            return;
        }
        
        queueEvent(() -> {
            renderer.loadTexture(bitmap);
            requestRender();
        });
        
        Log.i(TAG, "Unity texture loaded: " + bitmap.getWidth() + "x" + bitmap.getHeight());
    }

    /**
     * Load Unity texture from raw byte data
     */
    public void loadUnityTextureFromBytes(byte[] data, int width, int height, int format) {
        if (!isInitialized) {
            Log.e(TAG, "View not initialized");
            return;
        }
        
        if (data == null) {
            Log.e(TAG, "Cannot load null texture data");
            return;
        }
        
        queueEvent(() -> {
            renderer.loadTextureFromBytes(data, width, height, format);
            requestRender();
        });
        
        Log.i(TAG, "Unity texture loaded from bytes: " + width + "x" + height);
    }

    /**
     * Set texture transparency
     */
    public void setTextureAlpha(float alpha) {
        if (!isInitialized) {
            Log.e(TAG, "View not initialized");
            return;
        }
        
        queueEvent(() -> {
            renderer.setAlpha(alpha);
            requestRender();
        });
    }

    /**
     * Get current texture transparency
     */
    public float getTextureAlpha() {
        if (!isInitialized) {
            return 0.0f;
        }
        return renderer.getAlpha();
    }

    /**
     * Check if texture is loaded
     */
    public boolean isTextureLoaded() {
        if (!isInitialized) {
            return false;
        }
        return renderer.isTextureLoaded();
    }

    /**
     * Set render callback listener
     */
    public void setOnTextureRenderedListener(UnityTextureRenderer.OnTextureRenderedListener listener) {
        if (renderer != null) {
            renderer.setOnTextureRenderedListener(listener);
        }
    }

    /**
     * Force refresh the texture rendering
     */
    public void refreshTexture() {
        if (isInitialized) {
            requestRender();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.i(TAG, "UnityTextureView paused");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "UnityTextureView resumed");
    }

    /**
     * Release resources
     */
    public void release() {
        if (renderer != null) {
            queueEvent(() -> renderer.release());
        }
        isInitialized = false;
        Log.i(TAG, "UnityTextureView released");
    }
}
