# Temperature Monitoring v2.1.0

This update adds Pilot One CPU-temperature monitoring modelled directly on the original Labpano Camera app.

Behavior:
- Reads `/sys/class/thermal/thermal_zone0/temp`.
- Polls every 1 second on a dedicated thread named `CpuTemperatureWatch`.
- Monitoring starts in `MainActivity.onResume()` and stops in `onPause()`.
- Warnings are generated only while temperature is rising.
- First warning threshold: 80 C.
- Critical warning threshold: 85 C.
- Each threshold warns at most once per foreground preview session.
- Recording is not automatically stopped at either threshold, matching the original Camera app.
- Unlike the original app, monitoring is not limited to serial numbers beginning with `S`; it runs whenever the thermal node is readable.

No fan sysfs values are written by this update. Fan control remains Pilot OS/kernel-managed.
