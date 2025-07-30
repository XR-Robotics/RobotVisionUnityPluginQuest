using UnityEngine;
using UnityEngine.UI;
using System.Collections;

namespace XRobotoolkit.VisionPlugin
{
    /// <summary>
    /// Example usage script for Unity Vision Plugin
    /// Demonstrates how to use the plugin with various textures and effects
    /// </summary>
    public class VisionPluginExample : MonoBehaviour
    {
        [Header("Plugin Reference")]
        [SerializeField] private UnityVisionPluginWrapper visionPlugin;

        [Header("Test Textures")]
        [SerializeField] private Texture2D[] testTextures;
        [SerializeField] private int currentTextureIndex = 0;

        [Header("Animation Settings")]
        [SerializeField] private bool animateAlpha = false;
        [SerializeField] private float alphaSpeed = 1.0f;
        [SerializeField] private AnimationCurve alphaCurve = AnimationCurve.EaseInOut(0, 0, 1, 1);

        [Header("Auto Texture Switching")]
        [SerializeField] private bool autoSwitchTextures = false;
        [SerializeField] private float switchInterval = 3.0f;

        [Header("UI Elements (Optional)")]
        [SerializeField] private Button nextTextureButton;
        [SerializeField] private Button prevTextureButton;
        [SerializeField] private Slider alphaSlider;
        [SerializeField] private Text statusText;
        [SerializeField] private Text infoText;

        private float alphaAnimationTime = 0f;
        private Coroutine autoSwitchCoroutine;

        void Start()
        {
            SetupPlugin();
            SetupUI();
        }

        void SetupPlugin()
        {
            // Find plugin if not assigned
            if (visionPlugin == null)
            {
                visionPlugin = FindObjectOfType<UnityVisionPluginWrapper>();
            }

            if (visionPlugin == null)
            {
                Debug.LogError("UnityVisionPluginWrapper not found! Please add it to the scene.");
                UpdateStatusText("Plugin not found!");
                return;
            }

            // Subscribe to events
            visionPlugin.OnPluginInitialized += OnPluginInitialized;
            visionPlugin.OnPluginError += OnPluginError;
            visionPlugin.OnTextureLoaded += OnTextureLoaded;

            // Initialize if not already done
            if (!visionPlugin.IsInitialized)
            {
                visionPlugin.InitializePlugin();
            }
            else
            {
                OnPluginInitialized();
            }
        }

        void SetupUI()
        {
            // Setup buttons
            if (nextTextureButton != null)
                nextTextureButton.onClick.AddListener(NextTexture);

            if (prevTextureButton != null)
                prevTextureButton.onClick.AddListener(PreviousTexture);

            // Setup alpha slider
            if (alphaSlider != null)
            {
                alphaSlider.value = 1.0f;
                alphaSlider.onValueChanged.AddListener(OnAlphaSliderChanged);
            }

            // Update info text
            UpdateInfoText();
        }

        void OnPluginInitialized()
        {
            Debug.Log("Vision Plugin initialized successfully!");
            UpdateStatusText("Plugin Ready");

            // Load first texture if available
            if (testTextures != null && testTextures.Length > 0)
            {
                LoadCurrentTexture();
            }

            // Start auto switching if enabled
            if (autoSwitchTextures && testTextures != null && testTextures.Length > 1)
            {
                StartAutoSwitching();
            }

            // Update info
            UpdateInfoText();
        }

        void OnPluginError(string error)
        {
            Debug.LogError($"Vision Plugin Error: {error}");
            UpdateStatusText($"Error: {error}");
        }

        void OnTextureLoaded()
        {
            Debug.Log("Texture loaded successfully!");
            UpdateStatusText("Texture Loaded");
        }

        void Update()
        {
            if (visionPlugin == null || !visionPlugin.IsInitialized) return;

            // Animate alpha if enabled
            if (animateAlpha)
            {
                AnimateAlpha();
            }
        }

        void AnimateAlpha()
        {
            alphaAnimationTime += Time.deltaTime * alphaSpeed;
            float normalizedTime = (alphaAnimationTime % 2.0f) / 2.0f;

            // Ping-pong animation
            if (normalizedTime > 1.0f)
                normalizedTime = 2.0f - normalizedTime;

            float alpha = alphaCurve.Evaluate(normalizedTime);
            visionPlugin.SetTextureAlpha(alpha);

            // Update UI slider
            if (alphaSlider != null && !alphaSlider.interactable)
            {
                alphaSlider.value = alpha;
            }
        }

        public void NextTexture()
        {
            if (testTextures == null || testTextures.Length == 0) return;

            currentTextureIndex = (currentTextureIndex + 1) % testTextures.Length;
            LoadCurrentTexture();
        }

        public void PreviousTexture()
        {
            if (testTextures == null || testTextures.Length == 0) return;

            currentTextureIndex = (currentTextureIndex - 1 + testTextures.Length) % testTextures.Length;
            LoadCurrentTexture();
        }

        public void LoadCurrentTexture()
        {
            if (visionPlugin == null || !visionPlugin.IsInitialized) return;
            if (testTextures == null || currentTextureIndex >= testTextures.Length) return;

            Texture2D currentTexture = testTextures[currentTextureIndex];
            if (currentTexture == null)
            {
                Debug.LogWarning($"Texture at index {currentTextureIndex} is null");
                return;
            }

            bool success = visionPlugin.LoadTexture(currentTexture);
            if (success)
            {
                UpdateStatusText($"Loaded: {currentTexture.name}");
                Debug.Log($"Loaded texture: {currentTexture.name} ({currentTexture.width}x{currentTexture.height})");
            }
        }

        public void ToggleAlphaAnimation()
        {
            animateAlpha = !animateAlpha;

            if (!animateAlpha && alphaSlider != null)
            {
                // Reset to slider value when stopping animation
                visionPlugin.SetTextureAlpha(alphaSlider.value);
            }

            // Enable/disable slider interaction
            if (alphaSlider != null)
            {
                alphaSlider.interactable = !animateAlpha;
            }
        }

        public void ToggleAutoSwitching()
        {
            autoSwitchTextures = !autoSwitchTextures;

            if (autoSwitchTextures)
            {
                StartAutoSwitching();
            }
            else
            {
                StopAutoSwitching();
            }
        }

        void StartAutoSwitching()
        {
            if (autoSwitchCoroutine != null)
            {
                StopCoroutine(autoSwitchCoroutine);
            }

            if (testTextures != null && testTextures.Length > 1)
            {
                autoSwitchCoroutine = StartCoroutine(AutoSwitchTextures());
            }
        }

        void StopAutoSwitching()
        {
            if (autoSwitchCoroutine != null)
            {
                StopCoroutine(autoSwitchCoroutine);
                autoSwitchCoroutine = null;
            }
        }

        IEnumerator AutoSwitchTextures()
        {
            while (autoSwitchTextures)
            {
                yield return new WaitForSeconds(switchInterval);
                NextTexture();
            }
        }

        void OnAlphaSliderChanged(float value)
        {
            if (!animateAlpha && visionPlugin != null && visionPlugin.IsInitialized)
            {
                visionPlugin.SetTextureAlpha(value);
            }
        }

        void UpdateStatusText(string status)
        {
            if (statusText != null)
            {
                statusText.text = status;
            }
        }

        void UpdateInfoText()
        {
            if (infoText == null) return;

            string info = "";

            if (visionPlugin != null && visionPlugin.IsInitialized)
            {
                info += $"Plugin Status: Ready\\n";
                info += $"Texture View ID: {visionPlugin.TextureViewId}\\n";
                info += $"Texture Loaded: {visionPlugin.IsTextureLoaded()}\\n";
                info += $"Current Alpha: {visionPlugin.GetTextureAlpha():F2}\\n";

#if UNITY_ANDROID && !UNITY_EDITOR
                info += $"OpenGL Info: {visionPlugin.GetOpenGLInfo()}\\n";
#endif
            }
            else
            {
                info = "Plugin not initialized";
            }

            if (testTextures != null && testTextures.Length > 0)
            {
                info += $"\\nTextures: {testTextures.Length} available\\n";
                info += $"Current: {currentTextureIndex + 1}/{testTextures.Length}";

                if (currentTextureIndex < testTextures.Length && testTextures[currentTextureIndex] != null)
                {
                    Texture2D current = testTextures[currentTextureIndex];
                    info += $"\\n{current.name} ({current.width}x{current.height})";
                }
            }

            infoText.text = info;
        }

        void OnDestroy()
        {
            // Unsubscribe from events
            if (visionPlugin != null)
            {
                visionPlugin.OnPluginInitialized -= OnPluginInitialized;
                visionPlugin.OnPluginError -= OnPluginError;
                visionPlugin.OnTextureLoaded -= OnTextureLoaded;
            }

            // Stop auto switching
            StopAutoSwitching();
        }

        // Public methods for UI buttons
        [ContextMenu("Refresh Texture")]
        public void RefreshTexture()
        {
            if (visionPlugin != null && visionPlugin.IsInitialized)
            {
                visionPlugin.RefreshTexture();
            }
        }

        [ContextMenu("Update Info")]
        public void UpdateInfo()
        {
            UpdateInfoText();
        }

        // Debug methods
        [ContextMenu("Print OpenGL Info")]
        public void PrintOpenGLInfo()
        {
            if (visionPlugin != null && visionPlugin.IsInitialized)
            {
                Debug.Log($"OpenGL Info: {visionPlugin.GetOpenGLInfo()}");
            }
        }
    }
}
