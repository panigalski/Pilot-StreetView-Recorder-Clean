# Build fix v1.1

Fixes the Java compilation failure in `pano/src/main/java/com/pi/pano/StitchingUtil.java` when compiling against modern Android SDKs.

`MediaMetadataRetriever.release()` declares `IOException` in API 33+, so the release call is now wrapped in a `try/catch (IOException)` block. Runtime behavior on Pilot OS is unchanged.
