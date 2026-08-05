# Third-party notices

This project includes the Labpano Pilot Open API `pano` module and its native
libraries from the public `labpano/pilot-open-api` repository. The upstream
license is included as `LABPANO_OPEN_API_LICENSE.txt`.

One targeted stability change is applied to
`pano/src/main/java/com/pi/pano/CameraSurfaceView.java`: the SDK's automatic
four-camera reopen after repeated low-FPS samples is suppressed because it was
observed to risk a full Pilot One/Pilot One EE device reboot on Pilot OS 5.18.x.
