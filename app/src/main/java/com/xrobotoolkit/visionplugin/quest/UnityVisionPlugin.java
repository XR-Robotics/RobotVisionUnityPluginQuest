package com.xrobotoolkit.visionplugin.quest;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.HashMap;
import java.util.Map;

/**
 * Unity Plugin Interface for Android OpenGL ES Texture Rendering
 * This class provides the main interface that Unity will call to interact with the native Android plugin
 */
public class UnityVisionPlugin {
    private static final String TAG = "UnityVisionPlugin";
    
    private static UnityVisionPlugin instance;
    private Context context;
    private Map<Integer, UnityTextureView> textureViews;
    private int nextViewId = 1;
    
    private UnityVisionPlugin() {
        textureViews = new HashMap<>();
    }
    
    /**
     * Get singleton instance
     */
    public static UnityVisionPlugin getInstance() {
        if (instance == null) {
            instance = new UnityVisionPlugin();
        }
        return instance;
    }
    
    /**
     * Initialize the plugin with Unity context
     * This should be called from Unity's main thread
     */
    public static void initialize(Context context) {
        Log.i(TAG, "Initializing Unity Vision Plugin");
        getInstance().context = context;
    }
    
    /**
     * Create a new texture view for rendering
     * Returns the view ID that can be used to reference this view
     */
    public static int createTextureView() {
        UnityVisionPlugin plugin = getInstance();
        if (plugin.context == null) {
            Log.e(TAG, "Plugin not initialized. Call initialize() first.");
            return -1;
        }
        
        try {
            int viewId = plugin.nextViewId++;
            UnityTextureView textureView = new UnityTextureView(plugin.context);
            plugin.textureViews.put(viewId, textureView);
            
            Log.i(TAG, "Created texture view with ID: " + viewId);
            return viewId;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create texture view: " + e.getMessage());
            return -1;
        }
    }
    
    /**
     * Add texture view to a parent view group
     * This allows Unity to embed the OpenGL view in its UI
     */
    public static boolean addTextureViewToParent(int viewId, ViewGroup parent, int width, int height) {
        UnityVisionPlugin plugin = getInstance();
        UnityTextureView textureView = plugin.textureViews.get(viewId);
        
        if (textureView == null) {
            Log.e(TAG, "Texture view not found for ID: " + viewId);
            return false;
        }
        
        try {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
            textureView.setLayoutParams(params);
            parent.addView(textureView);
            
            Log.i(TAG, "Added texture view " + viewId + " to parent with size: " + width + "x" + height);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to add texture view to parent: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Load texture from byte array (main method for Unity integration)
     * @param viewId The texture view ID
     * @param textureData Raw texture data from Unity
     * @param width Texture width
     * @param height Texture height
     * @param format OpenGL texture format (e.g., GLES30.GL_RGBA)
     */
    public static boolean loadTexture(int viewId, byte[] textureData, int width, int height, int format) {
        UnityVisionPlugin plugin = getInstance();
        UnityTextureView textureView = plugin.textureViews.get(viewId);
        
        if (textureView == null) {
            Log.e(TAG, "Texture view not found for ID: " + viewId);
            return false;
        }
        
        if (textureData == null || textureData.length == 0) {
            Log.e(TAG, "Invalid texture data");
            return false;
        }
        
        try {
            textureView.loadUnityTextureFromBytes(textureData, width, height, format);
            Log.i(TAG, "Loaded texture for view " + viewId + ": " + width + "x" + height);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load texture: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Load texture from bitmap (alternative method)
     */
    public static boolean loadTextureBitmap(int viewId, Bitmap bitmap) {
        UnityVisionPlugin plugin = getInstance();
        UnityTextureView textureView = plugin.textureViews.get(viewId);
        
        if (textureView == null) {
            Log.e(TAG, "Texture view not found for ID: " + viewId);
            return false;
        }
        
        if (bitmap == null) {
            Log.e(TAG, "Invalid bitmap");
            return false;
        }
        
        try {
            textureView.loadUnityTexture(bitmap);
            Log.i(TAG, "Loaded bitmap for view " + viewId + ": " + bitmap.getWidth() + "x" + bitmap.getHeight());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load bitmap: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Set texture transparency
     */
    public static boolean setTextureAlpha(int viewId, float alpha) {
        UnityVisionPlugin plugin = getInstance();
        UnityTextureView textureView = plugin.textureViews.get(viewId);
        
        if (textureView == null) {
            Log.e(TAG, "Texture view not found for ID: " + viewId);
            return false;
        }
        
        try {
            textureView.setTextureAlpha(alpha);
            Log.d(TAG, "Set alpha for view " + viewId + ": " + alpha);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set alpha: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get texture transparency
     */
    public static float getTextureAlpha(int viewId) {
        UnityVisionPlugin plugin = getInstance();
        UnityTextureView textureView = plugin.textureViews.get(viewId);
        
        if (textureView == null) {
            Log.e(TAG, "Texture view not found for ID: " + viewId);
            return 0.0f;
        }
        
        return textureView.getTextureAlpha();
    }
    
    /**
     * Check if texture is loaded
     */
    public static boolean isTextureLoaded(int viewId) {
        UnityVisionPlugin plugin = getInstance();
        UnityTextureView textureView = plugin.textureViews.get(viewId);
        
        if (textureView == null) {
            Log.e(TAG, "Texture view not found for ID: " + viewId);
            return false;
        }
        
        return textureView.isTextureLoaded();
    }
    
    /**
     * Refresh texture rendering
     */
    public static boolean refreshTexture(int viewId) {
        UnityVisionPlugin plugin = getInstance();
        UnityTextureView textureView = plugin.textureViews.get(viewId);
        
        if (textureView == null) {
            Log.e(TAG, "Texture view not found for ID: " + viewId);
            return false;
        }
        
        try {
            textureView.refreshTexture();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to refresh texture: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Set render callback for a texture view
     */
    public static boolean setRenderCallback(int viewId, UnityTextureRenderer.OnTextureRenderedListener listener) {
        UnityVisionPlugin plugin = getInstance();
        UnityTextureView textureView = plugin.textureViews.get(viewId);
        
        if (textureView == null) {
            Log.e(TAG, "Texture view not found for ID: " + viewId);
            return false;
        }
        
        try {
            textureView.setOnTextureRenderedListener(listener);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set render callback: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Remove texture view and release resources
     */
    public static boolean removeTextureView(int viewId) {
        UnityVisionPlugin plugin = getInstance();
        UnityTextureView textureView = plugin.textureViews.get(viewId);
        
        if (textureView == null) {
            Log.e(TAG, "Texture view not found for ID: " + viewId);
            return false;
        }
        
        try {
            textureView.release();
            
            // Remove from parent if attached
            ViewGroup parent = (ViewGroup) textureView.getParent();
            if (parent != null) {
                parent.removeView(textureView);
            }
            
            plugin.textureViews.remove(viewId);
            Log.i(TAG, "Removed texture view with ID: " + viewId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove texture view: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get texture view for direct manipulation (advanced usage)
     */
    public static UnityTextureView getTextureView(int viewId) {
        UnityVisionPlugin plugin = getInstance();
        return plugin.textureViews.get(viewId);
    }
    
    /**
     * Clean up all resources
     */
    public static void cleanup() {
        Log.i(TAG, "Cleaning up Unity Vision Plugin");
        UnityVisionPlugin plugin = getInstance();
        
        for (Map.Entry<Integer, UnityTextureView> entry : plugin.textureViews.entrySet()) {
            try {
                entry.getValue().release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing texture view " + entry.getKey() + ": " + e.getMessage());
            }
        }
        
        plugin.textureViews.clear();
        plugin.context = null;
    }
    
    /**
     * Get OpenGL ES version info
     */
    public static String getOpenGLInfo() {
        try {
            String vendor = GLES30.glGetString(GLES30.GL_VENDOR);
            String renderer = GLES30.glGetString(GLES30.GL_RENDERER);
            String version = GLES30.glGetString(GLES30.GL_VERSION);
            
            return "Vendor: " + vendor + "\nRenderer: " + renderer + "\nVersion: " + version;
        } catch (Exception e) {
            return "OpenGL info not available: " + e.getMessage();
        }
    }
}
