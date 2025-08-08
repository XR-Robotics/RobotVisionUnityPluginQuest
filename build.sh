#!/bin/bash

echo "Building Robot Vision Unity Plugin Quest..."
echo

# Clean previous build
echo "Cleaning previous build..."
./gradlew clean
if [ $? -ne 0 ]; then
    echo "Build failed during clean step."
    exit 1
fi

# Build release AAR
echo "Building release AAR..."
./gradlew assembleRelease
if [ $? -ne 0 ]; then
    echo "Build failed during assembly."
    exit 1
fi

# Copy AAR to unity integration folder
echo "Copying AAR to Unity integration folder..."
./gradlew copyAARToUnity
if [ $? -ne 0 ]; then
    echo "Failed to copy AAR to Unity integration folder."
    exit 1
fi

echo
echo "========================================"
echo "BUILD SUCCESSFUL!"
echo "========================================"
echo
echo "AAR file location: unity-integration/RobotVisionUnityPluginQuest.aar"
echo "Copy this file to your Unity project's Assets/Plugins/Android/ folder"
echo
