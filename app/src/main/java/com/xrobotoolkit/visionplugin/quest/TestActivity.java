package com.xrobotoolkit.visionplugin.quest;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * Test Activity to demonstrate Unity texture rendering functionality
 * This can be used for testing the AAR before integrating with Unity
 */
public class TestActivity extends Activity {
    private static final String TAG = "TestActivity";
    
    private FrameLayout textureContainer;
    private Button loadTextureButton;
    private Button changeAlphaButton;
    private Button refreshButton;
    
    private int textureViewId = -1;
    private float currentAlpha = 1.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize the Unity plugin
        UnityVisionPlugin.initialize(this);
        
        setupUI();
        setupTextureView();
        
        Log.i(TAG, "Test Activity created");
    }

    private void setupUI() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(20, 20, 20, 20);
        
        // Texture container
        textureContainer = new FrameLayout(this);
        textureContainer.setBackgroundColor(Color.GRAY);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                800, 600);
        containerParams.setMargins(0, 0, 0, 20);
        textureContainer.setLayoutParams(containerParams);
        
        // Buttons
        loadTextureButton = new Button(this);
        loadTextureButton.setText("Load Test Texture");
        loadTextureButton.setOnClickListener(v -> loadTestTexture());
        
        changeAlphaButton = new Button(this);
        changeAlphaButton.setText("Change Alpha (1.0)");
        changeAlphaButton.setOnClickListener(v -> changeAlpha());
        
        refreshButton = new Button(this);
        refreshButton.setText("Refresh Texture");
        refreshButton.setOnClickListener(v -> refreshTexture());
        
        // Add views to layout
        mainLayout.addView(textureContainer);
        mainLayout.addView(loadTextureButton);
        mainLayout.addView(changeAlphaButton);
        mainLayout.addView(refreshButton);
        
        setContentView(mainLayout);
    }

    private void setupTextureView() {
        // Create texture view
        textureViewId = UnityVisionPlugin.createTextureView();
        
        if (textureViewId == -1) {
            showError("Failed to create texture view");
            return;
        }
        
        // Add to container
        boolean success = UnityVisionPlugin.addTextureViewToParent(
                textureViewId, textureContainer, 800, 600);
        
        if (!success) {
            showError("Failed to add texture view to container");
            return;
        }
        
        // Set render callback
        UnityVisionPlugin.setRenderCallback(textureViewId, new UnityTextureRenderer.OnTextureRenderedListener() {
            @Override
            public void onTextureRendered() {
                runOnUiThread(() -> {
                    // Texture rendered successfully
                    Log.d(TAG, "Texture rendered");
                });
            }
            
            @Override
            public void onRenderError(String error) {
                runOnUiThread(() -> {
                    showError("Render error: " + error);
                });
            }
        });
        
        Log.i(TAG, "Texture view setup complete with ID: " + textureViewId);
    }

    private void loadTestTexture() {
        if (textureViewId == -1) {
            showError("No texture view available");
            return;
        }
        
        // Create a test bitmap with a gradient pattern
        Bitmap testBitmap = createTestBitmap(512, 512);
        
        boolean success = UnityVisionPlugin.loadTextureBitmap(textureViewId, testBitmap);
        
        if (success) {
            showMessage("Test texture loaded successfully");
            Log.i(TAG, "Test texture loaded");
        } else {
            showError("Failed to load test texture");
        }
    }

    private Bitmap createTestBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        
        // Create a colorful gradient pattern
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float hue = (float) x / width * 360f;
                float saturation = (float) y / height;
                float value = 1.0f;
                
                int color = Color.HSVToColor(new float[]{hue, saturation, value});
                bitmap.setPixel(x, y, color);
            }
        }
        
        return bitmap;
    }

    private void changeAlpha() {
        if (textureViewId == -1) {
            showError("No texture view available");
            return;
        }
        
        // Cycle through alpha values: 1.0 -> 0.7 -> 0.4 -> 0.1 -> 1.0
        if (currentAlpha > 0.9f) {
            currentAlpha = 0.7f;
        } else if (currentAlpha > 0.6f) {
            currentAlpha = 0.4f;
        } else if (currentAlpha > 0.3f) {
            currentAlpha = 0.1f;
        } else {
            currentAlpha = 1.0f;
        }
        
        boolean success = UnityVisionPlugin.setTextureAlpha(textureViewId, currentAlpha);
        
        if (success) {
            changeAlphaButton.setText("Change Alpha (" + String.format("%.1f", currentAlpha) + ")");
            showMessage("Alpha changed to " + String.format("%.1f", currentAlpha));
            Log.i(TAG, "Alpha changed to: " + currentAlpha);
        } else {
            showError("Failed to change alpha");
        }
    }

    private void refreshTexture() {
        if (textureViewId == -1) {
            showError("No texture view available");
            return;
        }
        
        boolean success = UnityVisionPlugin.refreshTexture(textureViewId);
        
        if (success) {
            showMessage("Texture refreshed");
            Log.i(TAG, "Texture refreshed");
        } else {
            showError("Failed to refresh texture");
        }
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showError(String error) {
        Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show();
        Log.e(TAG, error);
    }

    @Override
    protected void onResume() {
        super.onResume();
        UnityTextureView textureView = UnityVisionPlugin.getTextureView(textureViewId);
        if (textureView != null) {
            textureView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        UnityTextureView textureView = UnityVisionPlugin.getTextureView(textureViewId);
        if (textureView != null) {
            textureView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up texture view
        if (textureViewId != -1) {
            UnityVisionPlugin.removeTextureView(textureViewId);
        }
        
        // Clean up plugin
        UnityVisionPlugin.cleanup();
        
        Log.i(TAG, "Test Activity destroyed");
    }
}
