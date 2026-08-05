# Recording cue and preview UI update — v1.6

## Changes

- Bundles the user-provided `Start Recording.wav` as `app/src/main/res/raw/start_recording.wav`.
- Plays the cue after the Pilot SDK confirms that the first video segment of a manually started recording session has begun.
- Does not replay the cue during automatic 4 GiB segment rotation.
- Hides the on-screen GPS quality chip while recording. GPS location updates continue to be sent to the recorder and written to the MP4 CAMM metadata track.
- Restores the GPS quality chip when recording stops or fails.
- Hides the top-left `StreetView Video` status-bar title.
- Hides the `Google StreetView Video` profile label and underline above the recording controls.
- Displays `Preview Ready` for five seconds, then hides that status chip. Other operational and error messages remain visible.

## Version

- Version code: `106`
- Version name: `1.6.0-recording-audio-clean-ui`

## Files changed

- `app/build.gradle`
- `app/src/main/java/com/panigalski/pilotstreetview/MainActivity.java`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/pilot_status_bar.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/raw/start_recording.wav` (new)

No GitHub Actions workflow files are changed.
