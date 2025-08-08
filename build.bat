@echo off
echo Building Robot Vision Unity Plugin Quest...
echo.

REM Clean previous build
echo Cleaning previous build...
call gradlew.bat clean
if %ERRORLEVEL% neq 0 (
    echo Build failed during clean step.
    pause
    exit /b 1
)

REM Build release AAR
echo Building release AAR...
call gradlew.bat assembleRelease
if %ERRORLEVEL% neq 0 (
    echo Build failed during assembly.
    pause
    exit /b 1
)

REM Copy AAR to unity integration folder
echo Copying AAR to Unity integration folder...
call gradlew.bat copyAARToUnity
if %ERRORLEVEL% neq 0 (
    echo Failed to copy AAR to Unity integration folder.
    pause
    exit /b 1
)

echo.
echo ========================================
echo BUILD SUCCESSFUL!
echo ========================================
echo.
echo AAR file location: unity-integration\RobotVisionUnityPluginQuest.aar
echo Copy this file to your Unity project's Assets/Plugins/Android/ folder
echo.
pause
