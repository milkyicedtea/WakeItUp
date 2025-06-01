# WakeItUp - Android Wake-on-LAN App

An Android application for sending Wake-on-LAN (WOL) magic packets to devices on your network.

## Features
- **Wake-on-LAN (WOL) Sending:** Quickly wake up network-enabled devices.
- **Device Management:** Add, edit, delete, and color-code devices (name, MAC, IP, port).
- **Group Organization:** Create custom groups and manage devices within them.
- **Wake All in Group:** Send WOL packets to all devices in a selected group.
- **Network Discovery:** Scan your local network to find and add devices.
- **Modern UI:** Built with Jetpack Compose and Material 3 for a clean experience.
- **Persistent Storage:** Uses Room database to save your devices and groups.
- **Input Validation:** Checks for valid MAC and IP address formats.

## Installation

### Option 1: Download Pre-built APK (Recommended)
[![Latest Release](https://img.shields.io/github/v/release/milkyicedtea/WakeItUp)](https://github.com/milkyicedtea/WakeItUp/releases/latest)

1. Go to the [Releases page](https://github.com/milkyicedtea/WakeItUp/releases).
2. Download the latest `.apk` file.
3. Transfer the APK to your Android device and install it. You may need to enable "Install from unknown sources" in your device settings.

### Option 2: Build from source

This project is built using Android Studio.

1. **Clone the repository:**
    ```bash
    git clone https://github.com/milkyicedtea/WakeItUp
    ```

2. **Open in Android Studio:**
   - Launch Android Studio (latest stable version recommended).
   - Select "Open" or "Open an Existing Project."
   - Navigate to the cloned `WakeItUp` directory and open it.

3. **Build the Application:**
   - Android Studio will automatically sync the Gradle project.
   - Once synced, build the project (usually `Build > Make Project` or by clicking the "Build" button).
   This will generate an APK file (usually in `app/build/outputs/apk/debug/` or `app/build/outputs/apk/release/`).

4. **Run the App:**
   - Select an Android emulator or connect a physical Android device.
   - Click the "Run" button in Android Studio.

## Future Enhancements (Ideas)
- **Import/Export:** Allow users to back up and restore their device and group configurations.
- **Device Status Check:** Implement a way to ping devices to see if they are currently online.
- **Scheduled Wake-ups:** Allow users to schedule WOL packets.
- **More Advanced Scanning Options:** Customize scan ranges or methods.
- **Themes:** Light/Dark mode support or customizable themes.
- **Help/FAQ Section:** Provide in-app guidance on WOL setup and troubleshooting.