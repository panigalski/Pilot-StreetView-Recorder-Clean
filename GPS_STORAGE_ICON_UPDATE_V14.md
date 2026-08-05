# GPS, external storage, and launcher icon update — v1.4

## Recording GPS gate

Recording now starts only when all of these conditions are true:

- precise-location permission is granted;
- the GPS provider is enabled;
- a GPS coordinate fix has been received in the last 10 seconds;
- the fix reports accuracy of 25 metres or better;
- when satellite-status data is available, at least four satellites are used in the fix.

The preview overlay reports fix quality and satellite counts. The check runs once when REC is pressed and again after storage validation, so a stale fix cannot pass while a USB disk is spinning up.

## External storage detection

Pilot One firmware can expose a USB disk at `/storage/<UUID>` while omitting it from `getExternalFilesDirs()` and/or reporting incomplete `StorageVolume` metadata. Detection now combines:

1. `/proc/mounts` (fast path for `/storage/<UUID>` and `/mnt/media_rw/<UUID>`);
2. `StorageManager.getStorageVolumes()`;
3. reflected Android 7 vendor path methods (`getPath`, `getPathFile`);
4. application external-file directories;
5. a direct `/storage` directory fallback.

The external option remains hidden when no removable mount is reported. The actual path is still write-tested on a worker thread immediately before recording.

## Launcher icon

The supplied Pilot One artwork is included as the application launcher icon in all legacy Android density buckets. Pilot OS Android 7 uses these PNG resources.

## Version

- versionCode: 104
- versionName: 1.4.0-gps-storage-icon
