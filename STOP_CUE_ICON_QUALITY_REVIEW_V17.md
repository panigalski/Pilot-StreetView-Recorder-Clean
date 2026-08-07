# v1.7 — stop cue, launcher icon, and image-control review

## Implemented

- Replaced all launcher-density icons with the supplied underwater Pilot One artwork.
- Added `app/src/main/res/raw/stop_recording.wav`.
- Plays the stop cue once when a recording session ends normally.
- Also plays the stop cue when an active recording is stopped by a recording error.
- Does not play the cue during automatic approximately 4 GiB segment rotation.
- Does not play the cue during background lifecycle cleanup because that path is intended to release camera hardware quietly when switching apps.
- Prevents start and stop cues from overlapping.

## Image-quality controls found in Labpano sources

### Supported live camera/preview controls

The public Pilot SDK and the decompiled original Camera application expose:

- exposure compensation (`PilotSDK.setExposureCompensation`) from -4 to +4;
- ISO (`PilotSDK.setISO`), including auto and fixed values;
- manual exposure time (`PilotSDK.setExposeTime` / `ExposeTimeAdjustHelper`);
- white balance (`PilotSDK.setWhiteBalance`): auto, incandescent, fluorescent, daylight, and cloudy daylight;
- auto-white-balance lock (`PilotSDK.setAutoWhiteBalanceLock`);
- stitching distance (`PilotSDK.setStitchingDistance`).

The original Camera application routes these through
`com.pi.pilot.core.PreviewHelper` and its professional-settings UI.

### Gamma, saturation, brightness, highlights, shadows, temperature

`com.pi.pano.StitchingConfig` defines properties and conversion functions for:

- highlights;
- shadows;
- brightness;
- gamma;
- saturation;
- colour temperature.

However, the source comment says this configuration is unused or may not take
effect. In the decompiled Camera app, these values are associated with the
offline stitching path for unstitched media, not the live 8K stitched Street
View recording call. The currently supplied official SDK source does not apply
that configuration in its current `StitchingThread` implementation.

### Contrast

No recording-pipeline contrast setter was found. `contrast` references in the
decompiled resources are Android UI `ImageFilterView` attributes and are not
evidence that encoded camera video contrast can be changed.

## Recommended next quality-control feature

For live Street View recording, implement a conservative Professional panel in
this order:

1. EV;
2. ISO;
3. white balance / AWB lock;
4. stitching distance;
5. manual exposure time only after device testing confirms it remains stable at
   the 8K/7 FPS recording profile.

Do not expose gamma, saturation, brightness, highlights, shadows, temperature,
or contrast as live recording controls until a test build proves that the
native encoder path actually honors them.
