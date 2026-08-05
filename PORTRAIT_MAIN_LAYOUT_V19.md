# Portrait Main Layout v1.9

## Control hierarchy

```text
┌──────────────────────────────────┐
│ System title / time / battery    │
├──────────────────────────────────┤
│ GPS status              00:00:00 │
│                                  │
│           8K preview             │
│                                  │
│          transient status        │
├──────────────────────────────────┤
│  [ Picture ]   [  REC  ] [Save to]
│  EV/ISO summary          Internal │
└──────────────────────────────────┘
```

The two side controls are direct actions rather than nested settings:

- **Picture** opens `PictureAdjustmentsDialog`.
- **Save to** opens `StorageSelectionDialog`.
- The centre control starts or stops recording.

The unfinished Gallery control and general Settings indirection were removed from the main screen. Picture and storage controls are disabled while storage is being validated and while recording. The GPS chip remains in the preview area and is hidden while recording, as in the previous version.

## Files changed

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/drawable/pilot_action_card_background.xml`
- `app/src/main/res/drawable/ic_picture_adjustments.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/com/panigalski/pilotstreetview/MainActivity.java`
- `app/build.gradle`
- `scripts/validate_project.py`
