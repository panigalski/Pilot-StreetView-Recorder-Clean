# Pilot Street View Recorder — clean restart v1.5

Standalone Android application for Labpano Pilot One and Pilot One EE. It is
installed beside the original system Camera application and uses a separate
package name:

`com.panigalski.pilotstreetview`

## Implemented profile

- 8K stitched preview and recording
- 7 FPS Street View recording mode
- Google Street View recording flag
- Ultra High bitrate multiplier
- Approximate 4 GiB file rotation
- Internal storage or dynamically detected USB/SD storage
- External Storage is shown in the chooser only while a removable volume is mounted
- GPS metadata embedded in the MP4 CAMM track and verified at runtime
- Timestamp filenames such as `260805_174655883.mp4`
- In-place settings and storage dialogs so the vendor preview SurfaceView is
  not destroyed when choosing storage
- Camera low-FPS auto-reopen suppression for Pilot OS stability

## Start a completely new GitHub repository

1. Create an empty repository named `Pilot-StreetView-Recorder-Clean`.
2. Do **not** add a README, `.gitignore`, license, or workflow during creation.
3. Extract this ZIP on your computer.
4. Upload **the contents of the extracted folder**, not the outer folder.
5. Confirm the repository root contains:

```
.github/
app/
pano/
gradle/
scripts/
build.gradle
gradle.properties
gradlew
gradlew.bat
settings.gradle
```

6. Confirm this exact workflow file exists:

```
.github/workflows/build-debug-apk.yml
```

A duplicate copy is provided at `BUILD_WORKFLOW_COPY.yml` only for inspection.
It is not used by GitHub Actions.

## Build on GitHub

The workflow runs automatically after a push to `main`. It can also be started
manually:

1. Open **Actions**.
2. Select **Build debug APK**.
3. Select **Run workflow**.
4. After the green check, download the artifact
   `PilotStreetViewRecorder-debug`.
5. Extract `app-debug.apk`.

The project uses Android Gradle Plugin 8.7.3, Gradle 8.9, JDK 17, and Android
API 35 for compilation. The application still targets Android 10/API 29 and has
minimum Android 7/API 24 for Pilot OS compatibility.

## Install on one test camera

Stop the original Camera app so it releases the four camera devices:

```bat
adb shell am force-stop com.pi.pilot.camera
```

Install or update the standalone recorder:

```bat
adb install -r app-debug.apk
```

Launch it:

```bat
adb shell monkey -p com.panigalski.pilotstreetview 1
```

Grant Camera, Microphone, Storage, and Location permissions.

## Safe test sequence

1. Open the app and leave the preview running for 60 seconds.
2. Open and cancel the storage selector five times.
3. Select Internal and External repeatedly without pressing Record.
4. Make a 20-second Internal recording.
5. Connect the USB drive and make a 20-second External recording.
6. Only after those pass, test a recording long enough to create a second
   approximately 4 GiB segment.

## Storage behavior

Internal recordings use:

```
/storage/emulated/0/DCIM/Videos/Stitched/
```

External recordings resolve the current removable-volume UUID dynamically and
prefer:

```
/storage/<volume-uuid>/DCIM/Videos/Stitched/
```

If public external storage is not writable, the app falls back to its
app-specific directory on that removable volume.

No disk enumeration or write testing occurs while the storage dialog is open.
The selected volume is validated on a worker thread only when recording starts.

## Important

This is an independently signed standalone application, not a replacement for
`com.pi.pilot.camera`. Test on one camera first. Do not run the original Camera
app and this recorder at the same time.

## v1.5 update

Version 1.5 installs the newly supplied Pilot One launcher icon, changes every
video filename to `yyMMdd_HHmmssSSS.mp4`, forwards every fresh GPS fix into the
Street View CAMM track, and verifies that GPS samples are actually written to
each MP4 segment. See `GPS_CAMM_FILENAME_ICON_UPDATE_V15.md`.
