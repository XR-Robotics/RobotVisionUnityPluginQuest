# Robot Vision Unity Plugin Quest

An Android native plugin for Unity that provides real-time video streaming and texture rendering capabilities using OpenGL ES, specifically designed for Quest VR applications.

## Features

- **OpenGL ES 3.0 Support**: High-performance texture rendering using modern OpenGL ES
- **Real-time Video Streaming**: Network-based video streaming with media decoder integration
- **Unity Native Plugin**: Seamless integration with Unity's native plugin system
- **Multiple Texture Formats**: Support for RGBA, RGB, and custom Unity texture formats
- **Quest VR Optimized**: Designed specifically for Meta Quest platform
- **Low Latency**: Optimized for real-time applications with minimal latency
- **Automatic Texture Updates**: Frame-based texture updates via SurfaceTexture callbacks

## Project Structure

```
RobotVisionUnityPluginQuest/
├── app/                                # Android Library Module
│   ├── src/main/
│   │   ├── cpp/                       # Native C++ code
│   │   │   ├── RenderingPlugin.cpp    # Unity native plugin implementation
│   │   │   ├── RenderingPlugin.h      # Plugin headers
│   │   │   ├── CMakeLists.txt         # CMake build configuration
│   │   │   └── Unity/                 # Unity interface headers
│   │   └── java/com/xrobotoolkit/visionplugin/quest/
│   │       ├── RobotVisionUnityPluginQuest.java  # Main plugin interface
│   │       ├── UnityRenderTexture.java            # Texture rendering
│   │       ├── MediaDecoder.java                  # Video decoding
│   │       ├── FBOPlugin.java                     # Framebuffer operations
│   │       └── ...                                # Supporting classes
│   └── build.gradle                   # Module build configuration
├── unity-integration/                 # Unity integration files
│   └── RobotVisionUnityPluginQuest.aar# Generated AAR (after build)
├── unity-scripts/                     # Unity C# scripts
│   ├── OESYUVTexture.mat              # Material
│   ├── OESYUVTexture.shader           # Shader
│   └── RobotVisionUnityPluginQuest.cs # Unity integration script
└── build.gradle                       # Root project configuration
```

## Building the Plugin

### Prerequisites

- **Android Studio** 2022.3.1 (Giraffe) or newer
- **Android SDK** with API level 30 or higher
- **Android NDK** (latest LTS version recommended)
- **Gradle** 8.0 or newer
- **CMake** 3.18.1 or newer
- **Java** 11 or newer

### Build Steps

1. **Clone/Download the project**
   ```bash
   git clone https://github.com/XR-Robotics/RobotVisionUnityPluginQuest.git
   cd RobotVisionUnityPluginQuest
   ```

2. **Build using provided scripts**
   ```bash
   # Windows
   build.bat
   
   # Linux/macOS
   ./build.sh
   ```

3. **Generated Files**
   After successful build, you'll find:
   - **AAR File**: `unity-integration/RobotVisionUnityPluginQuest.aar`

### Build Targets

- **assembleRelease**: Build release AAR
- **assembleDebug**: Build debug AAR (with debug symbols)
- **clean**: Clean all build outputs
- **copyAARToUnity**: Copy AAR to unity-integration folder (runs automatically after assembleRelease)

### Build Configuration

The project uses the following configuration:
- **Target SDK**: 34
- **Minimum SDK**: 30
- **NDK ABIs**: arm64-v8a, armeabi-v7a
- **Java Version**: 11
- **C++ Standard**: C++14

## Unity Integration

### Installation

1. Copy `unity-integration/RobotVisionUnityPluginQuest.aar` to your Unity project's `Assets/Plugins/Android/` folder
2. Copy `unity-scripts/RobotVisionUnityPluginQuest.cs` to your Unity project's Scripts folder
3. Copy `unity-scripts/OESYUVTexture.mat` and `unity-scripts/OESYUVTexture.shader` to your Unity project
4. For Unity-side setup: Set the material of your RawImage component to `OESYUVTexture.mat` (this material uses the `OESYUVTexture.shader` for proper texture rendering)

### Usage

```csharp
// Add the RobotVisionUnityPluginQuest component to a GameObject
// Configure the parameters in the inspector:
// - Raw Image: UI element to display the video
// - Width/Height: Video dimensions (default: 2560x720)
// - Port: Network port for video streaming (default: 12345)
// - Record Video: Enable/disable video recording

// The plugin will automatically initialize and start receiving video data
```

### Plugin Parameters

- **Width/Height**: Video stream dimensions
- **Port**: Network port for incoming video data
- **Record Video**: Enable local video recording
- **Enable Debug Logs**: Show detailed logging information

## Development

### Building in Android Studio

1. Open the project in Android Studio
2. Wait for Gradle sync to complete
3. Build → Make Module 'app' or use Build → Build Bundle(s)/APK(s) → Build AAR

### Testing

The plugin includes test classes for standalone testing without Unity:
- Run the app module as an Android application for basic functionality testing
- Check LogCat for debug output and error messages

### Troubleshooting

**Build Issues:**
- Ensure NDK is properly installed and configured
- Check that CMake version meets minimum requirements
- Verify Android SDK and build tools are up to date

**Runtime Issues:**
- Check device supports OpenGL ES 3.0
- Verify network connectivity for video streaming
- Check Unity console for plugin initialization errors

## Citation

If you find this project useful, please consider citing it as follows.

```
@article{zhao2025xrobotoolkit,
      title={XRoboToolkit: A Cross-Platform Framework for Robot Teleoperation}, 
      author={Zhigen Zhao and Liuchuan Yu and Ke Jing and Ning Yang}, 
      journal={arXiv preprint arXiv:2508.00097},
      year={2025}
}
```
