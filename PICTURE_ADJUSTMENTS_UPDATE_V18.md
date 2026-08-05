# Picture Adjustments Update v1.8

Version: `1.8.0-picture-adjustments` (`versionCode 108`)

## New pre-recording controls

Open the gear icon and choose **Picture Adjustments**. The app stores and
immediately applies the selected controls to the live camera preview. The same
values are reapplied whenever the four-camera preview is recreated.

Available controls:

- Exposure time: Auto, 1/15, 1/50, 1/100, 1/500, 1/1000 and 1/3200 second.
- ISO:
  - Auto-exposure mode: Auto, 50, 100, 200, 400, 800 and 1600.
  - Manual-exposure mode: 100, 200, 400, 600, 800, 1600 and 3200.
- Exposure compensation: EV -4 through EV +4.
- White balance: Auto, Incandescent, Fluorescent, Daylight and Cloudy daylight.
- Auto-white-balance lock.
- Stitching distance: automatic measurement or the complete manual range from
  -100 to 100.
- Reset Picture Defaults.

All controls can be changed only before recording because the existing settings
button remains disabled while a recording is active.

## Manual exposure availability

The supplied Pilot SDK implements manual exposure by writing `/efs/.ex_en` and
`/efs/.ex_val`. The standalone app exposes manual shutter selection only when
both files exist and are writable. On firmware that restricts those files, the
row is shown as **Unavailable on this firmware** while the other controls remain
fully available.

## Controls intentionally not exposed

The SDK source contains a `StitchingConfig` object with fields named gamma,
saturation, brightness, highlights, shadows and colour temperature. The source
itself states that this configuration is unused, and no verified path connects
those fields to the live 8K encoder. Contrast is not exposed by the camera or
encoder API. These controls are therefore omitted rather than presenting
settings that may not affect the recorded MP4.

## Application and CI impact

- No GitHub Actions workflow file is changed.
- Existing GPS gating, CAMM metadata, storage selection, recording sounds,
  filename formatting and lifecycle fixes remain unchanged.
