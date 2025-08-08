using UnityEngine;
using System;

namespace XRobotoolkit.VisionPlugin
{
    /// <summary>
    /// Unity wrapper for the Android Vision Plugin
    /// Provides easy integration with Unity projects
    /// </summary>
    public class UnityVisionPluginWrapper : MonoBehaviour
    {
        [Header("Plugin Settings")]
        [SerializeField] private bool initializeOnStart = true;
        [SerializeField] private bool debugLogging = true;

        [Header("Texture Settings")]
        [SerializeField] private Texture2D defaultTexture;
        [SerializeField] private float defaultAlpha = 1.0f;

        private AndroidJavaClass pluginClass;
        private int textureViewId = -1;
        private bool isInitialized = false;

        public event Action OnPluginInitialized;
        public event Action<string> OnPluginError;
        public event Action OnTextureLoaded;

        public bool IsInitialized => isInitialized;
        public int TextureViewId => textureViewId;

        void Start()
        {
            if (initializeOnStart)
            {
                InitializePlugin();
            }
        }

        /// <summary>
        /// Initialize the Android plugin
        /// </summary>
        public void InitializePlugin()
        {
            if (isInitialized)
            {
                LogDebug("Plugin already initialized");
                return;
            }

#if UNITY_ANDROID && !UNITY_EDITOR
            try 
            {
                // Get the Unity activity
                AndroidJavaClass unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
                AndroidJavaObject activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");
                
                // Initialize the plugin
                pluginClass = new AndroidJavaClass("com.xrobotoolkit.visionplugin.quest.UnityVisionPlugin");
                pluginClass.CallStatic("initialize", activity);
                
                // Create texture view
                textureViewId = pluginClass.CallStatic<int>("createTextureView");
                
                if (textureViewId != -1)
                {
                    isInitialized = true;
                    LogDebug($"Unity Vision Plugin initialized. Texture View ID: {textureViewId}");
                    
                    // Load default texture if provided
                    if (defaultTexture != null)
                    {
                        LoadTexture(defaultTexture);
                    }
                    
                    // Set default alpha
                    SetTextureAlpha(defaultAlpha);
                    
                    OnPluginInitialized?.Invoke();
                }
                else
                {
                    LogError("Failed to create texture view");
                    OnPluginError?.Invoke("Failed to create texture view");
                }
            }
            catch (Exception e) 
            {
                LogError($"Failed to initialize Unity Vision Plugin: {e.Message}");
                OnPluginError?.Invoke(e.Message);
            }
#else
            LogDebug("Plugin initialization skipped - not running on Android device");
#endif
        }

        /// <summary>
        /// Load a Unity texture into the renderer
        /// </summary>
        /// <param name="texture">The texture to load</param>
        /// <returns>True if successful</returns>
        public bool LoadTexture(Texture2D texture)
        {
            if (!ValidateInitialization()) return false;
            if (texture == null)
            {
                LogError("Cannot load null texture");
                return false;
            }

#if UNITY_ANDROID && !UNITY_EDITOR
            try 
            {
                // Convert Unity texture to byte array
                byte[] textureData = texture.GetRawTextureData();
                int glFormat = GetGLFormat(texture.format);
                
                // Load texture in the plugin
                bool success = pluginClass.CallStatic<bool>("loadTexture", 
                    textureViewId, textureData, texture.width, texture.height, glFormat);
                
                if (success) 
                {
                    LogDebug($"Texture loaded successfully: {texture.width}x{texture.height}, format: {texture.format}");
                    OnTextureLoaded?.Invoke();
                    return true;
                }
                else 
                {
                    LogError("Failed to load texture in native plugin");
                    OnPluginError?.Invoke("Failed to load texture");
                    return false;
                }
            }
            catch (Exception e) 
            {
                LogError($"Error loading texture: {e.Message}");
                OnPluginError?.Invoke(e.Message);
                return false;
            }
#else
            LogDebug($"LoadTexture called with {texture.name} (Editor mode)");
            return true;
#endif
        }

        /// <summary>
        /// Set the transparency of the rendered texture
        /// </summary>
        /// <param name="alpha">Alpha value (0.0 = transparent, 1.0 = opaque)</param>
        /// <returns>True if successful</returns>
        public bool SetTextureAlpha(float alpha)
        {
            if (!ValidateInitialization()) return false;

            alpha = Mathf.Clamp01(alpha);

#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                bool success = pluginClass.CallStatic<bool>("setTextureAlpha", textureViewId, alpha);
                if (success)
                {
                    LogDebug($"Alpha set to: {alpha}");
                }
                else
                {
                    LogError("Failed to set alpha in native plugin");
                }
                return success;
            }
            catch (Exception e)
            {
                LogError($"Error setting alpha: {e.Message}");
                OnPluginError?.Invoke(e.Message);
                return false;
            }
#else
            LogDebug($"SetTextureAlpha called with {alpha} (Editor mode)");
            return true;
#endif
        }

        /// <summary>
        /// Get the current texture alpha value
        /// </summary>
        /// <returns>Current alpha value</returns>
        public float GetTextureAlpha()
        {
            if (!ValidateInitialization()) return 0.0f;

#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                return pluginClass.CallStatic<float>("getTextureAlpha", textureViewId);
            }
            catch (Exception e)
            {
                LogError($"Error getting alpha: {e.Message}");
                return 0.0f;
            }
#else
            return defaultAlpha;
#endif
        }

        /// <summary>
        /// Force refresh the texture rendering
        /// </summary>
        /// <returns>True if successful</returns>
        public bool RefreshTexture()
        {
            if (!ValidateInitialization()) return false;

#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                bool success = pluginClass.CallStatic<bool>("refreshTexture", textureViewId);
                if (success)
                {
                    LogDebug("Texture refreshed");
                }
                else
                {
                    LogError("Failed to refresh texture");
                }
                return success;
            }
            catch (Exception e)
            {
                LogError($"Error refreshing texture: {e.Message}");
                OnPluginError?.Invoke(e.Message);
                return false;
            }
#else
            LogDebug("RefreshTexture called (Editor mode)");
            return true;
#endif
        }

        /// <summary>
        /// Check if texture is currently loaded
        /// </summary>
        /// <returns>True if texture is loaded</returns>
        public bool IsTextureLoaded()
        {
            if (!ValidateInitialization()) return false;

#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                return pluginClass.CallStatic<bool>("isTextureLoaded", textureViewId);
            }
            catch (Exception e)
            {
                LogError($"Error checking texture status: {e.Message}");
                return false;
            }
#else
            return defaultTexture != null;
#endif
        }

        /// <summary>
        /// Get OpenGL information from the device
        /// </summary>
        /// <returns>OpenGL info string</returns>
        public string GetOpenGLInfo()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                if (pluginClass != null)
                {
                    return pluginClass.CallStatic<string>("getOpenGLInfo");
                }
            }
            catch (Exception e)
            {
                LogError($"Error getting OpenGL info: {e.Message}");
            }
#endif
            return "OpenGL info not available";
        }

        private bool ValidateInitialization()
        {
            if (!isInitialized)
            {
                LogError("Plugin not initialized. Call InitializePlugin() first.");
                return false;
            }
            return true;
        }

        private int GetGLFormat(TextureFormat format)
        {
            // Map Unity texture formats to OpenGL formats
            switch (format)
            {
                case TextureFormat.RGBA32:
                    return 0x1908; // GL_RGBA
                case TextureFormat.RGB24:
                    return 0x1907; // GL_RGB
                case TextureFormat.Alpha8:
                    return 0x1906; // GL_ALPHA
                case TextureFormat.ARGB32:
                    return 1000; // Custom ARGB format
                default:
                    LogDebug($"Unsupported texture format {format}, defaulting to RGBA");
                    return 0x1908; // Default to RGBA
            }
        }

        private void LogDebug(string message)
        {
            if (debugLogging)
            {
                Debug.Log($"[UnityVisionPlugin] {message}");
            }
        }

        private void LogError(string message)
        {
            Debug.LogError($"[UnityVisionPlugin] {message}");
        }

        void OnDestroy()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                if (isInitialized && textureViewId != -1) 
                {
                    pluginClass.CallStatic<bool>("removeTextureView", textureViewId);
                    LogDebug("Texture view removed");
                }
                
                if (pluginClass != null)
                {
                    pluginClass.CallStatic("cleanup");
                    LogDebug("Plugin cleanup completed");
                }
            }
            catch (Exception e)
            {
                LogError($"Error during cleanup: {e.Message}");
            }
            finally
            {
                isInitialized = false;
                textureViewId = -1;
                pluginClass = null;
            }
#endif
        }

        void OnApplicationPause(bool pauseStatus)
        {
            if (pauseStatus)
            {
                LogDebug("Application paused");
            }
            else
            {
                LogDebug("Application resumed");
                if (isInitialized)
                {
                    RefreshTexture();
                }
            }
        }
    }
}
