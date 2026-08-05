# Lifecycle / camera release hotfix v1.3

## Symptom

After leaving Pilot Street View Recorder and opening another application, Pilot OS could show an "app stopped responding" message.

## Root causes found in v1.2

1. Camera and GPS cleanup happened only in `Activity.onDestroy()`. Android does not guarantee `onDestroy()` when the user merely switches applications.
2. The Labpano preview surface could remain attached until the window manager later destroyed it, delaying release of all four camera devices.
3. `PanoSDKListener.onPanoRelease()` is delivered from the SDK worker thread, but v1.2 directly changed Android Views from that callback.
4. After a surface release, `initialized` remained true, so returning to the recorder could not reliably recreate the preview.
5. Surface destruction and explicit cleanup could both request native release, creating a duplicate-release race.

## Changes

- Release the PilotSDK session from `onPause()` rather than waiting for `onDestroy()`.
- Explicitly detach the SDK preview view, causing immediate camera cleanup.
- Add a duplicate-release guard inside `PanoSurfaceView`.
- Move all UI work from `onPanoRelease()` onto the main thread.
- Stop GPS updates while the app is in the background.
- Stop an active recording on a worker thread before releasing the camera.
- Recreate the preview after returning to the app, but only after the previous native release callback completes.

## Test sequence

1. Install v1.3 over the current app.
2. Force-stop the original Labpano Camera app.
3. Launch Pilot Street View Recorder and leave the preview open for 60 seconds.
4. Press Home, open a non-camera app, and wait 30 seconds.
5. Return to the recorder. The preview should initialize again.
6. Repeat the app switch five times.
7. Then open the original Labpano Camera app after leaving the recorder and verify that it acquires the camera normally.
8. Only after the lifecycle test passes, make a 20-second internal recording and repeat the app-switch test.

## Diagnostics if the issue remains

Run before reproducing:

```bat
adb logcat -c
adb logcat -v threadtime > lifecycle-logcat.txt
```

After the ANR message appears, wait 10 seconds, stop logcat with Ctrl+C, then run:

```bat
adb shell dumpsys activity lastanr > lifecycle-last-anr.txt
adb shell dumpsys media.camera > lifecycle-camera.txt
```
