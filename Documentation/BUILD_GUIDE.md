# RunnerUp APK Build Guide

This guide provides step-by-step instructions for successfully building the RunnerUp Android APK from the command line without Android Studio.

## Prerequisites

### 1. Install Java JDK
```bash
# Install OpenJDK 17 (required by Android SDK command line tools)
sudo apt update
sudo apt install openjdk-17-jdk

# Verify installation
java -version
javac -version
```

### 2. Set JAVA_HOME Environment Variable
```bash
# Add to ~/.bashrc or ~/.profile
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# Reload shell
source ~/.bashrc
```

### 3. Install Git LFS
```bash
sudo apt-get install git-lfs
git lfs install
```

### 4. Checkout Gradle Wrapper (if using Git LFS)
```bash
cd /path/to/runnerup
git lfs checkout
```

### 5. Install Android SDK Command Line Tools
```bash
# Create Android SDK directory
mkdir -p ~/Android/Sdk

# Download command line tools from:
# https://developer.android.com/studio#command-tools
# Extract to ~/Android/Sdk/cmdline-tools/latest/

# Set ANDROID_HOME environment variable
export ANDROID_HOME=~/Android/Sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
export PATH=$ANDROID_HOME/platform-tools:$PATH

# Add to ~/.bashrc for persistence
echo 'export ANDROID_HOME=~/Android/Sdk' >> ~/.bashrc
echo 'export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$PATH' >> ~/.bashrc
echo 'export PATH=$ANDROID_HOME/platform-tools:$PATH' >> ~/.bashrc
```

### 6. Install Required SDK Components
```bash
# Accept licenses
yes | sdkmanager --licenses

# Install required SDK components
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

## Build Process

### 1. Navigate to Project Directory
```bash
cd /path/to/runnerup
```

### 2. Clean Previous Builds (Optional)
```bash
./gradlew clean
```

### 3. Build Debug APK
```bash
./gradlew assembleDebug
```

### 4. Build Release APK (Optional)
```bash
./gradlew assembleRelease
```

## Output Location

- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `app/build/outputs/apk/release/app-release.apk`

## Common Issues and Solutions

### Issue: JAVA_HOME not set
```
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```
**Solution**: Ensure JAVA_HOME is properly set and points to JDK 17:
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

### Issue: Permission denied on .gradle directory
```
java.io.FileNotFoundException: Permission denied
```
**Solution**: Fix ownership of gradle directories:
```bash
sudo chown -R $USER:$USER ~/.gradle
sudo chown -R $USER:$USER /path/to/runnerup
```

### Issue: UnsupportedClassVersionError
```
java.lang.UnsupportedClassVersionError: class file version 61.0
```
**Solution**: Android SDK command line tools require Java 17. Install and use JDK 17:
```bash
sudo apt install openjdk-17-jdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

### Issue: SDK components not found
```
SDK location not found
```
**Solution**: Ensure ANDROID_HOME is set and SDK components are installed:
```bash
export ANDROID_HOME=~/Android/Sdk
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

## Debugging APK Installation

### 1. Enable USB Debugging on Device
- Go to Settings > About Phone
- Tap "Build Number" 7 times to enable Developer Options
- Go to Settings > Developer Options
- Enable "USB Debugging"

### 2. Install ADB
```bash
sudo apt install android-tools-adb
```

### 3. Connect Device and Install APK
```bash
# Check device connection
adb devices

# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk

# View logs for debugging
adb logcat
```

## Environment Verification

Run this script to verify your environment is properly configured:

```bash
#!/bin/bash
echo "=== Environment Check ==="
echo "Java Version:"
java -version
echo ""
echo "JAVA_HOME: $JAVA_HOME"
echo "ANDROID_HOME: $ANDROID_HOME"
echo ""
echo "Android SDK Components:"
sdkmanager --list | grep -E "(platform-tools|platforms|build-tools)"
echo ""
echo "Git LFS:"
git lfs version
```

## Tips for Success

1. **Use Java 17**: Android SDK command line tools require Java 17 minimum
2. **Set environment variables**: Ensure JAVA_HOME and ANDROID_HOME are properly configured
3. **Install SDK components**: Use sdkmanager to install required platform tools
4. **Check permissions**: Ensure proper ownership of gradle and project directories
5. **Use Git LFS**: For large files like gradle wrapper
6. **Clean builds**: Use `./gradlew clean` when encountering build issues

## Troubleshooting Build Errors

### Gradle Build Failures
```bash
# Clean and rebuild
./gradlew clean
./gradlew assembleDebug

# Check gradle wrapper
./gradlew --version

# Run with debug info
./gradlew assembleDebug --debug
```

### Resource Compilation Errors
- Check for missing string resources in `app/res/values/strings.xml`
- Verify drawable resources exist and are properly referenced
- Ensure all required dependencies are in `build.gradle`

### Database Migration Issues
- Increment database version in `DBHelper.java`
- Add proper migration logic in `onUpgrade()` method
- Test database changes thoroughly

## Additional Resources

- [Android Developer Documentation](https://developer.android.com/studio/command-line)
- [Gradle Build Tool](https://gradle.org/)
- [Android SDK Manager](https://developer.android.com/studio/command-line/sdkmanager)

---

**Last Updated**: December 2024  
**Tested On**: Ubuntu 22.04 LTS, OpenJDK 17, Android SDK 34
