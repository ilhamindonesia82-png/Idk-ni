# Neon Voice FX — Android starter project

A native Kotlin Android app that captures microphone audio, applies lightweight real-time pitch/sound modulation, and plays the processed result to the phone's audio output.

## Important Roblox limitation

This app is a real-time **local monitoring prototype**. It does not inject its processed audio into Roblox Mobile's voice-chat microphone. Ordinary Android apps generally cannot replace another app's microphone source. Roblox would need to accept a compatible external audio route or the device/platform would need to provide a supported virtual-microphone feature. Do not assume Bluetooth headsets or speaker routing will make Roblox receive the transformed sound.

## Build APK

1. Install Android Studio on a computer.
2. Extract this project ZIP.
3. Open the `RobloxVoiceChanger` folder in Android Studio.
4. Let Gradle sync. Use JDK 17 if Android Studio asks.
5. Connect an Android device with USB debugging enabled, or use an emulator with microphone access.
6. Click **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
7. The debug APK is usually at `app/build/outputs/apk/debug/app-debug.apk`.
8. Transfer that APK to your Android phone and install it. You may need to allow installation from that source.

## Test

- Grant microphone permission.
- Connect headphones first to reduce acoustic feedback.
- Tap **START LIVE PREVIEW** and speak.
- Change presets and pitch while monitoring.

## Prototype notes

- Uses Android `AudioRecord` and `AudioTrack`; no third-party libraries.
- Presets include Normal, Deep, High, Robot, Alien, Crush, Glitch, and BROKEN 12X. BROKEN 12X uses strong digital distortion, bit-crushing, gated bursts, and ring-modulation texture; it does not raise physical loudness by 12x.
- The basic pitch effect changes pitch and playback speed together, so it is not a high-quality formant-preserving pitch shifter.
- Latency and audio routing vary by device. For production-grade low latency, consider Oboe/AAudio native DSP and a more robust time-stretch/pitch-shift algorithm.
- `compileSdk` 35 and Android Gradle Plugin 8.7.3 require a compatible Android Studio/Gradle/JDK setup.

## Build APK from an Android phone using GitHub Actions

This ZIP includes `.github/workflows/android.yml` for cloud building. It does not itself contain a compiled APK.

1. On GitHub, create a new repository (a private repository is fine).
2. Upload the **contents** of the `RobloxVoiceChanger` folder to the repository root. Make sure the `.github/workflows/android.yml` file is included. If GitHub's mobile upload page cannot include the hidden `.github` folder, create a new file on GitHub named `.github/workflows/android.yml` and paste in the workflow contents from this ZIP.
3. Open the repository's **Actions** tab and allow Actions if prompted.
4. Select **Build Android APK**. Tap **Run workflow** and run it on `main` (or `master`). A push to either branch also starts a build automatically.
5. When the run finishes with a green check, open that run, scroll to **Artifacts**, and download `RobloxVoiceChanger-debug-apk`.
6. Extract the downloaded artifact ZIP; it contains `app-debug.apk`. Open that APK on your Android phone to install it. Android may ask you to allow installs from the browser/file manager you used.

If the build fails, open the failed run and check the first red step in the log. This workflow compiles a debug APK; it does not publish the app to Google Play.
