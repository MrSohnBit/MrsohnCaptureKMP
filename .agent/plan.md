# Project Plan

Create a Kotlin Multiplatform (KMP) Desktop application named 'MrSohn Capture'. The app's purpose is to capture the screen of a connected Android device, display the captured image in the desktop app, and save the file to the PC. This project is strictly for the Desktop target. It should be based on existing Android source code logic for screen capture found at /Users/okpos/eclipse-workspace/mrsohnbit-androidcapture-dce8fd797f17/src/mrsohn.

## Project Brief

# Project Brief: MrSohn Capture

MrSohn Capture is a Kotlin Multiplatform (KMP) Desktop application designed to streamline the process of capturing and saving screenshots from a connected Android device directly to a PC.

### Features
* **Real-time Device Preview:** View the live screen of a connected Android device within the desktop interface.
* **One-Click Capture:** Instantly trigger a high-quality screen capture of the connected device.
* **Automatic PC Saving:** Automatically save captured images to a designated folder on the local computer.
* **Connection Management:** Detect and manage connections to Android devices via ADB (Android Debug Bridge).

### High-Level Technical Stack
* **Kotlin:** Primary programming language for cross-platform logic.
* **Compose Multiplatform:** For building the desktop UI using Jetpack Compose APIs.
* **Kotlin Coroutines:** For asynchronous handling of image data and device communication.
* **KSP (Kotlin Symbol Processing):** For efficient code generation.
* **ADB (Android Debug Bridge):** Integrated via command-line or library for device interaction and screen streaming logic.

### UI Design Image
![UI Design](/Users/okpos/AndroidStudioProjects/MrSohnCapture/input_images/mrsohn_capture_ui.jpg)
Image path = /Users/okpos/AndroidStudioProjects/MrSohnCapture/input_images/mrsohn_capture_ui.jpg

## Implementation Steps
**Total Duration:** 28m

### Task_1_SetupDesktopProject: Reconfigure the project to support Compose Multiplatform for Desktop. Update the build configuration to target Desktop and remove Android-specific dependencies not needed for the desktop app.
- **Status:** COMPLETED
- **Updates:** Successfully reconfigured the project for Compose Multiplatform Desktop. Updated build scripts (libs.versions.toml, root build.gradle.kts, app/build.gradle.kts) to target JVM. Removed Android dependencies. Created desktop entry point in Main.kt. Verified successful Gradle sync and app launch via ./gradlew :app:run.
- **Acceptance Criteria:**
  - Project build.gradle.kts updated for Compose Desktop
  - Successful Gradle sync
  - A basic desktop window can be launched via ./gradlew run
  - No Android-specific build errors
- **Duration:** 11m 43s

### Task_2_ImplementADBIntegration: Implement the ADB (Android Debug Bridge) interface layer. This includes logic to detect connected Android devices and execute commands to capture the device screen. Refer to existing logic at /Users/okpos/eclipse-workspace/mrsohnbit-androidcapture-dce8fd797f17/src/mrsohn if available.
- **Status:** COMPLETED
- **Updates:** Implemented AdbRunner for device detection and screen capture. Added DeviceInfo data class. Updated Main.kt to list devices on startup using Compose. Verified ADB communication and image fetching logic. Background execution handled via Coroutines.
- **Acceptance Criteria:**
  - ADB commands executable via Kotlin (e.g., ProcessBuilder)
  - Connected devices can be listed programmatically
  - Capability to fetch raw screen data or screenshot from device
  - Integration tests or logs confirm device communication
- **Duration:** 1m 3s

### Task_3_BuildDesktopUI: Develop the Desktop UI using Compose Multiplatform and Material 3, following the provided design image. Include a device selector, a preview area for the device screen, and capture controls.
- **Status:** COMPLETED
- **Updates:** Developed the Desktop UI using Compose Multiplatform and Material 3. Implemented a dark theme with vibrant colors, a device selector sidebar, a preview area with focus overlays, and a capture control section. Integrated AdbRunner to populate the device list and handle screen captures. Added a recent captures gallery and automatic file saving to the PC. Verified UI matches design and functionality works.
- **Acceptance Criteria:**
  - UI matches the design in /Users/okpos/AndroidStudioProjects/MrSohnCapture/input_images/mrsohn_capture_ui.jpg
  - Material 3 theme with vibrant color scheme implemented
  - Device list displayed and selectable
  - Preview area updates with device screen data
- **Duration:** 7m 1s

### Task_4_ImplementCaptureAndSave: Implement the core functionality to capture the screen upon user interaction and save the resulting image to the local PC filesystem.
- **Status:** COMPLETED
- **Updates:** Implemented high-quality screen capture using ADB, saving to ~/Pictures/MrSohnCapture/ with timestamped filenames. Added a visual flash effect and status updates for user feedback. Integrated saving logic with the recent captures gallery and ensured all operations are non-blocking using Coroutines. Verified saved images are valid PNGs.
- **Acceptance Criteria:**
  - Capture button triggers high-quality screen capture
  - Captured images are saved to a predefined or selectable folder on the PC
  - Visual feedback provided upon successful capture
  - Saved images are valid and viewable
- **Duration:** 2m 10s

### Task_5_FinalVerification: Perform a full end-to-end test of the application. Verify stability, UI fidelity, and ensure all features work as expected on the Desktop target.
- **Status:** COMPLETED
- **Updates:** Refactored the application to remove the explicit capture button and implement click-to-capture functionality directly on the preview area. Extracted the capture logic into a reusable lambda and integrated it with both the empty preview and the active capture image view. Enhanced the recent captures gallery to allow users to click on any thumbnail to display that image in the main preview area. Added a loading indicator for visual feedback during the capture process. Verified all changes via local build and test.
- **Acceptance Criteria:**
  - App does not crash during usage
  - Build passes successfully
  - UI matches design requirements
  - End-to-end flow (Connect -> Preview -> Capture -> Save) is functional
- **Duration:** 6m 3s

