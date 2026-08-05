# v1.8.1 build fix

The v1.8 Java source referenced `R.raw.stop_recording`. In the repository that failed, the corresponding raw resource was not generated, producing a `cannot find symbol` error at `MainActivity.java:578`.

This repair:

- includes the stop sound resource explicitly;
- renames it to `recording_stop_cue.wav`;
- updates the Java reference to `R.raw.recording_stop_cue`;
- increments the application version to 1.8.1.

No workflow files are changed.
