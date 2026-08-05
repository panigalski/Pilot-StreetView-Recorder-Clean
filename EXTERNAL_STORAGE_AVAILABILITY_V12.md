# External-storage visibility update v1.2

The Recording Destination dialog now checks mounted volumes before displaying
its choices.

- With no USB drive or SD card mounted, only **Internal Storage** is shown.
- With a writable removable volume mounted, **Internal Storage** and
  **External Storage (USB/SD)** are shown.
- If External Storage was previously selected and the drive is removed, the
  saved preference is automatically returned to Internal Storage.
- Detection runs on a worker thread and does not create directories, test
  writes, query free space, or destroy the panoramic preview surface.

The actual output directory is still fully validated on a worker thread when
recording starts.
