# v1.5 — GPS/CAMM verification, timestamp filenames, and launcher icon

## Launcher icon

The launcher artwork supplied for v1.5 replaces all legacy launcher-density
resources (`mdpi` through `xxxhdpi`) and the round-icon resources. The original
1024 x 1024 PNG is retained in `drawable-nodpi/pilot_one_app_icon.png`.

## Video filename format

Every new MP4 uses the local camera date/time in this format:

```
yyMMdd_HHmmssSSS
```

Example:

```
260805_174655883.mp4
```

`PilotSDK.startRecord()` expects a filename without an extension and appends
`.mp4` itself. Each approximately 4 GiB segment receives a new timestamp-based
filename when that segment starts.

## GPS metadata in the MP4

Street View recording uses `useForGoogleMap=true`. The Labpano recorder creates
an `application/gyro` CAMM metadata track and writes:

- GPS epoch time
- latitude and longitude
- altitude
- horizontal and vertical accuracy
- east/north/up velocity
- accelerometer samples
- gyroscope samples

The app now forwards every fresh Android GPS fix through the recorder to
`PilotSDK.setLocationInfo()` while recording. The current fix is also seeded
before every new 4 GiB segment so the segment can receive GPS metadata as soon
as its muxer starts.

The SDK counts successfully muxed GPS CAMM samples. If a segment has written no
GPS sample after a seven-second grace period, recording is stopped with an
explicit error instead of silently producing an MP4 without GPS metadata.
Recording is also stopped if no fresh GPS fix is received for more than 15
seconds.

## Files changed

- `app/build.gradle`
- `app/src/main/java/com/panigalski/pilotstreetview/MainActivity.java`
- `app/src/main/java/com/panigalski/pilotstreetview/SegmentedStreetViewRecorder.java`
- `pano/src/main/java/com/pi/pano/MediaRecorderUtil.java`
- `pano/src/main/java/com/pi/pano/PilotSDK.java`
- launcher PNGs under `app/src/main/res/mipmap-*`
- `app/src/main/res/drawable-nodpi/pilot_one_app_icon.png`

## Validation performed

- Project validator passed.
- All Android XML resources parsed successfully.
- The modified recorder class passed a Java 8 syntax compilation against API
  stubs.
- Launcher PNG dimensions and transparency were checked.

The complete Android Gradle build must still be run by the repository's working
GitHub Actions workflow.
