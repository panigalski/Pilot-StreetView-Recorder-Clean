# Apply v1.5 to the existing repository

Upload the contents of this folder to the **root** of the existing clean
repository and allow files to be replaced. Do not change the working GitHub
Actions workflow.

After committing, run the normal debug APK workflow and install with:

```bat
adb install -r app-debug.apk
```

Test outdoors with a good GPS fix. Record for at least 10 seconds, then confirm
the output filename resembles `260805_174655883.mp4`. Upload a short MP4 for
CAMM/GPS metadata inspection if independent verification is desired.
