-------------------------------------------------------------------------------
|                Royale example project for AndroidStudio                     |
-------------------------------------------------------------------------------

# Introduction
 This example project has been created with Android Studio 3.1.2 
 and Android NDK r10e.
 Please remember that this is just an example, and not a full application.
 It lacks proper error handling and Activity changes, such as rotation.
 
# Prerequisits
 Android Studio 3.1.2+ installed
 Android SDK API level 23+ (can be downloaded within Android Studio SDK Manager)
 Android NDK 17.0 installed and Directory added to PATH
 Android device (Supporting Android SDK API level 23+)
 Royale compatible camera device

# How to run
 * Open the project "RoyaleAndroidExampleV3" in Android Studio
 * Wait for the gradle sync to finish
 * make your changes inside the royaleSample.cpp file located in
   <ProjectRoot>/app/src/main/jni/
 * Connect your target Android device
 * Press "Run"
 * Wait for the build process to finish
 * Select your target device within the appearing popup window
 * The application should now start on your target device
 * Connect your Royale camera device to the target device via USB
 * Press the start button on your device's screen

# Output
 When pressing "Run" you should see a log window located on the lower left 
 area of Android studio. When pressing "Start" inside the application a pop up
 window appears on the device's screen prompting for permission to access the
 USB device. Accept the prompt.
 You should see information about the connected camera device within the log
 window of Android Studio.
 If your device is not capable of connecting both the debug USB cable to your
 host machine as well as the camera device at the same time you can view the
 log aftwerwards by executing "adb logcat" within a command prompt on your host
 machine while your Android target device is connected.
 