using UnityEngine;
using UnityEngine.UI;

namespace XRobotoolkit.VisionPlugin
{
    /// <summary>
    /// Comprehensive test script for Unity Vision Plugin
    /// This script demonstrates all plugin features and provides debugging tools
    /// </summary>
    public class VisionPluginTester : MonoBehaviour
    {
        [Header("Plugin Reference")]
        public UnityVisionPluginWrapper visionPlugin;

        [Header("Test Textures")]
        public Texture2D[] testTextures;

        [Header("UI Elements")]
        public Button initializeButton;
        public Button loadTextureButton;
        public Button nextTextureButton;
        public Button refreshButton;
        public Slider alphaSlider;
        public Text statusText;
        public Text logText;
        public Toggle debugToggle;

        private int currentTextureIndex = 0;
        private string logMessage = "";

        void Start()
        {
            SetupUI();
            LogMessage("VisionPluginTester started");
        }

        void SetupUI()
        {
            if (initializeButton) initializeButton.onClick.AddListener(InitializePlugin);
            if (loadTextureButton) loadTextureButton.onClick.AddListener(LoadCurrentTexture);
            if (nextTextureButton) nextTextureButton.onClick.AddListener(NextTexture);
            if (refreshButton) refreshButton.onClick.AddListener(RefreshTexture);
            if (alphaSlider) alphaSlider.onValueChanged.AddListener(SetAlpha);
            if (debugToggle) debugToggle.onValueChanged.AddListener(SetDebugMode);

            UpdateUI();
        }

        public void InitializePlugin()
        {
            if (visionPlugin == null)
            {
                visionPlugin = FindObjectOfType<UnityVisionPluginWrapper>();
                if (visionPlugin == null)
                {
                    LogMessage("ERROR: UnityVisionPluginWrapper not found!");
                    return;
                }
            }

            visionPlugin.OnPluginInitialized += OnPluginInitialized;
            visionPlugin.OnPluginError += OnPluginError;
            visionPlugin.OnTextureLoaded += OnTextureLoaded;

            visionPlugin.InitializePlugin();
            LogMessage("Plugin initialization requested...");
        }

        void OnPluginInitialized()
        {
            LogMessage("✅ Plugin initialized successfully!");
            LogMessage($"Texture View ID: {visionPlugin.TextureViewId}");
            LogMessage($"OpenGL Info: {visionPlugin.GetOpenGLInfo()}");
            UpdateUI();
        }

        void OnPluginError(string error)
        {
            LogMessage($"❌ Plugin Error: {error}");
        }

        void OnTextureLoaded()
        {
            LogMessage("✅ Texture loaded successfully!");
            UpdateUI();
        }

        public void LoadCurrentTexture()
        {
            if (!ValidatePlugin()) return;

            if (testTextures == null || testTextures.Length == 0)
            {
                LogMessage("❌ No test textures available!");
                return;
            }

            if (currentTextureIndex >= testTextures.Length)
            {
                currentTextureIndex = 0;
            }

            Texture2D texture = testTextures[currentTextureIndex];
            if (texture == null)
            {
                LogMessage($"❌ Texture at index {currentTextureIndex} is null!");
                return;
            }

            LogMessage($"Loading texture: {texture.name} ({texture.width}x{texture.height}, {texture.format})");
            bool success = visionPlugin.LoadTexture(texture);

            if (!success)
            {
                LogMessage("❌ Failed to load texture!");
            }

            UpdateUI();
        }

        public void NextTexture()
        {
            if (testTextures == null || testTextures.Length == 0) return;

            currentTextureIndex = (currentTextureIndex + 1) % testTextures.Length;
            LogMessage($"Selected texture {currentTextureIndex + 1}/{testTextures.Length}");
            LoadCurrentTexture();
        }

        public void RefreshTexture()
        {
            if (!ValidatePlugin()) return;

            bool success = visionPlugin.RefreshTexture();
            LogMessage(success ? "✅ Texture refreshed" : "❌ Failed to refresh texture");
        }

        public void SetAlpha(float alpha)
        {
            if (!ValidatePlugin()) return;

            bool success = visionPlugin.SetTextureAlpha(alpha);
            LogMessage($"Alpha set to: {alpha:F2} {(success ? "✅" : "❌")}");
        }

        public void SetDebugMode(bool enabled)
        {
            if (visionPlugin != null)
            {
                // This would require a public property in the wrapper
                LogMessage($"Debug mode: {(enabled ? "ON" : "OFF")}");
            }
        }

        public void TestAllFeatures()
        {
            LogMessage("🧪 Starting comprehensive feature test...");
            StartCoroutine(TestAllFeaturesCoroutine());
        }

        System.Collections.IEnumerator TestAllFeaturesCoroutine()
        {
            // Test 1: Plugin initialization
            LogMessage("Test 1: Plugin initialization");
            if (!visionPlugin.IsInitialized)
            {
                InitializePlugin();
                yield return new WaitForSeconds(2f);
            }

            if (!visionPlugin.IsInitialized)
            {
                LogMessage("❌ Plugin initialization failed!");
                yield break;
            }
            LogMessage("✅ Plugin initialization passed");

            // Test 2: Texture loading
            LogMessage("Test 2: Texture loading");
            if (testTextures != null && testTextures.Length > 0)
            {
                LoadCurrentTexture();
                yield return new WaitForSeconds(1f);

                if (visionPlugin.IsTextureLoaded())
                {
                    LogMessage("✅ Texture loading passed");
                }
                else
                {
                    LogMessage("❌ Texture loading failed!");
                }
            }
            else
            {
                LogMessage("⚠️ No test textures available for testing");
            }

            // Test 3: Alpha blending
            LogMessage("Test 3: Alpha blending");
            float[] alphaValues = { 1.0f, 0.7f, 0.3f, 0.0f, 1.0f };
            foreach (float alpha in alphaValues)
            {
                visionPlugin.SetTextureAlpha(alpha);
                float actualAlpha = visionPlugin.GetTextureAlpha();
                LogMessage($"Set alpha {alpha:F1} → Got {actualAlpha:F1}");
                yield return new WaitForSeconds(0.5f);
            }
            LogMessage("✅ Alpha blending test completed");

            // Test 4: Texture refresh
            LogMessage("Test 4: Texture refresh");
            bool refreshSuccess = visionPlugin.RefreshTexture();
            LogMessage(refreshSuccess ? "✅ Texture refresh passed" : "❌ Texture refresh failed!");

            LogMessage("🎉 All tests completed!");
        }

        bool ValidatePlugin()
        {
            if (visionPlugin == null)
            {
                LogMessage("❌ Plugin not assigned!");
                return false;
            }

            if (!visionPlugin.IsInitialized)
            {
                LogMessage("❌ Plugin not initialized!");
                return false;
            }

            return true;
        }

        void LogMessage(string message)
        {
            Debug.Log($"[VisionPluginTester] {message}");
            logMessage += $"{System.DateTime.Now:HH:mm:ss} {message}\n";

            // Keep only last 20 lines
            string[] lines = logMessage.Split('\n');
            if (lines.Length > 20)
            {
                logMessage = string.Join("\n", lines, lines.Length - 20, 20);
            }

            UpdateUI();
        }

        void UpdateUI()
        {
            if (statusText)
            {
                string status = "Not initialized";
                if (visionPlugin != null)
                {
                    if (visionPlugin.IsInitialized)
                    {
                        status = $"Ready (ID: {visionPlugin.TextureViewId})";
                        if (visionPlugin.IsTextureLoaded())
                        {
                            status += " - Texture Loaded";
                        }
                    }
                    else
                    {
                        status = "Plugin found, not initialized";
                    }
                }
                statusText.text = status;
            }

            if (logText)
            {
                logText.text = logMessage;
            }

            if (alphaSlider && visionPlugin != null && visionPlugin.IsInitialized)
            {
                alphaSlider.value = visionPlugin.GetTextureAlpha();
            }

            // Enable/disable buttons based on state
            bool pluginReady = visionPlugin != null && visionPlugin.IsInitialized;
            if (loadTextureButton) loadTextureButton.interactable = pluginReady;
            if (nextTextureButton) nextTextureButton.interactable = pluginReady;
            if (refreshButton) refreshButton.interactable = pluginReady;
            if (alphaSlider) alphaSlider.interactable = pluginReady;
        }

        void OnDestroy()
        {
            if (visionPlugin != null)
            {
                visionPlugin.OnPluginInitialized -= OnPluginInitialized;
                visionPlugin.OnPluginError -= OnPluginError;
                visionPlugin.OnTextureLoaded -= OnTextureLoaded;
            }
        }

        // Context menu items for testing in editor
        [ContextMenu("Test All Features")]
        void TestAllFeaturesContext()
        {
            TestAllFeatures();
        }

        [ContextMenu("Print OpenGL Info")]
        void PrintOpenGLInfo()
        {
            if (ValidatePlugin())
            {
                LogMessage($"OpenGL Info: {visionPlugin.GetOpenGLInfo()}");
            }
        }

        [ContextMenu("Clear Log")]
        void ClearLog()
        {
            logMessage = "";
            UpdateUI();
        }
    }
}
