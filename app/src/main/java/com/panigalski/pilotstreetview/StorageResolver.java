package com.panigalski.pilotstreetview;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Resolves recording folders without hard-coding a removable-volume UUID. */
public final class StorageResolver {
    public static final String MODE_INTERNAL = "internal";
    public static final String MODE_EXTERNAL = "external";

    private static final String TAG = "PilotStorage";
    private static final String RELATIVE_RECORDING_PATH = "DCIM/Videos/Stitched";

    private StorageResolver() {
    }

    public static final class Destination {
        public final String mode;
        public final String label;
        public final File directory;
        public final boolean appSpecificFallback;
        public final long freeBytes;

        Destination(String mode, String label, File directory,
                    boolean appSpecificFallback, long freeBytes) {
            this.mode = mode;
            this.label = label;
            this.directory = directory;
            this.appSpecificFallback = appSpecificFallback;
            this.freeBytes = freeBytes;
        }
    }

    public static Destination resolve(Context context, String mode) throws IOException {
        if (MODE_EXTERNAL.equals(mode)) {
            Destination external = resolveBestExternal(context);
            if (external == null) {
                throw new IOException("No writable USB or SD storage is mounted.");
            }
            return external;
        }
        return resolveInternal();
    }

    public static Destination resolveInternal() throws IOException {
        try {
            File root = Environment.getExternalStorageDirectory();
            if (root == null) {
                throw new IOException("Internal storage path is unavailable.");
            }
            File target = new File(root, RELATIVE_RECORDING_PATH);
            ensureWritable(target);
            return new Destination(
                    MODE_INTERNAL,
                    "Internal Storage",
                    target,
                    false,
                    availableBytes(target));
        } catch (SecurityException error) {
            throw new IOException("Internal storage permission was denied.", error);
        } catch (RuntimeException error) {
            throw new IOException("Internal storage could not be inspected.", error);
        }
    }

    /**
     * Returns the writable removable destination with the most free space.
     *
     * Pilot OS is based on Android 7 and its removable-volume implementation can
     * throw vendor-specific RuntimeExceptions/SecurityExceptions while a disk is
     * mounting or spinning up. This method intentionally isolates every volume
     * probe so a bad/unready volume cannot crash the destination screen.
     */
    public static Destination resolveBestExternal(Context context) {
        List<Destination> candidates = new ArrayList<>();

        File[] appDirs = null;
        try {
            appDirs = context.getExternalFilesDirs(null);
        } catch (RuntimeException error) {
            Log.w(TAG, "getExternalFilesDirs failed", error);
        }

        if (appDirs != null) {
            for (File appDir : appDirs) {
                try {
                    if (appDir == null || !isRemovable(appDir)) {
                        continue;
                    }
                    Destination candidate = buildExternalCandidate(appDir);
                    if (candidate != null) {
                        addIfUnique(candidates, candidate);
                    }
                } catch (RuntimeException error) {
                    Log.w(TAG, "Ignoring unusable external app directory: " + appDir, error);
                }
            }
        }

        // Pilot OS exposes removable media as /storage/<volume-id>. This scan is
        // a fallback for OEMs that do not return a removable app-specific dir.
        File[] roots = null;
        try {
            roots = new File("/storage").listFiles();
        } catch (SecurityException error) {
            Log.w(TAG, "Cannot enumerate /storage", error);
        } catch (RuntimeException error) {
            Log.w(TAG, "Storage enumeration failed", error);
        }

        if (roots != null) {
            for (File root : roots) {
                try {
                    if (root == null) {
                        continue;
                    }
                    String name = root.getName();
                    if (!root.isDirectory() || "emulated".equals(name) || "self".equals(name)) {
                        continue;
                    }
                    File target = new File(root, RELATIVE_RECORDING_PATH);
                    ensureWritable(target);
                    addIfUnique(candidates, new Destination(
                            MODE_EXTERNAL,
                            "External Storage (" + name + ")",
                            target,
                            false,
                            availableBytes(target)));
                } catch (IOException error) {
                    Log.i(TAG, "Public removable path is not writable: " + root);
                } catch (RuntimeException error) {
                    Log.w(TAG, "Ignoring removable root: " + root, error);
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }
        Collections.sort(candidates, new Comparator<Destination>() {
            @Override
            public int compare(Destination left, Destination right) {
                if (left.freeBytes == right.freeBytes) {
                    return 0;
                }
                return left.freeBytes < right.freeBytes ? 1 : -1;
            }
        });
        return candidates.get(0);
    }

    private static Destination buildExternalCandidate(File appDir) {
        String path = appDir.getAbsolutePath();
        int androidIndex = path.indexOf(File.separator + "Android" + File.separator
                + "data" + File.separator);

        // Try the public DCIM path first. Some Pilot OS builds allow this for a
        // normal app when WRITE_EXTERNAL_STORAGE is granted.
        if (androidIndex > 0) {
            File volumeRoot = new File(path.substring(0, androidIndex));
            File publicTarget = new File(volumeRoot, RELATIVE_RECORDING_PATH);
            try {
                ensureWritable(publicTarget);
                return new Destination(
                        MODE_EXTERNAL,
                        "External Storage (" + volumeRoot.getName() + ")",
                        publicTarget,
                        false,
                        availableBytes(publicTarget));
            } catch (IOException error) {
                Log.i(TAG, "Public removable path denied; trying app folder: " + publicTarget);
            } catch (RuntimeException error) {
                Log.w(TAG, "Public removable probe failed: " + publicTarget, error);
            }
        }

        // App-specific removable storage is the safe fallback for an
        // independently signed app and does not require manufacturer keys.
        File fallback = new File(appDir, "StreetView/Stitched");
        try {
            ensureWritable(fallback);
            File volume = volumeRootFromAppDir(appDir);
            String volumeName = volume != null ? volume.getName() : "removable";
            return new Destination(
                    MODE_EXTERNAL,
                    "External Storage (app folder on " + volumeName + ")",
                    fallback,
                    true,
                    availableBytes(fallback));
        } catch (IOException error) {
            Log.w(TAG, "App-specific removable path is not writable: " + fallback, error);
            return null;
        } catch (RuntimeException error) {
            Log.w(TAG, "App-specific removable probe failed: " + fallback, error);
            return null;
        }
    }

    private static File volumeRootFromAppDir(File appDir) {
        String path = appDir.getAbsolutePath();
        int androidIndex = path.indexOf(File.separator + "Android" + File.separator
                + "data" + File.separator);
        return androidIndex > 0 ? new File(path.substring(0, androidIndex)) : null;
    }

    private static boolean isRemovable(File file) {
        try {
            return Environment.isExternalStorageRemovable(file);
        } catch (Throwable ignored) {
            // Some Android 7 vendor builds have incomplete Environment APIs.
            String primaryPath = "";
            try {
                File primary = Environment.getExternalStorageDirectory();
                primaryPath = primary != null ? primary.getAbsolutePath() : "";
            } catch (RuntimeException ignoredAgain) {
                // Keep an empty primary path and use path heuristics below.
            }
            String path = file.getAbsolutePath();
            return !path.startsWith(primaryPath)
                    && !path.contains(File.separator + "emulated" + File.separator);
        }
    }

    private static void addIfUnique(List<Destination> list, Destination candidate) {
        String path = candidate.directory.getAbsolutePath();
        for (Destination item : list) {
            if (item.directory.getAbsolutePath().equals(path)) {
                return;
            }
        }
        list.add(candidate);
    }

    public static void ensureWritable(File directory) throws IOException {
        try {
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("Cannot create directory: " + directory);
            }
            if (!directory.isDirectory()) {
                throw new IOException("Not a directory: " + directory);
            }

            File test = new File(directory, ".pilot_write_test");
            try (FileOutputStream output = new FileOutputStream(test)) {
                output.write(1);
                output.flush();
            } finally {
                //noinspection ResultOfMethodCallIgnored
                test.delete();
            }
        } catch (SecurityException error) {
            throw new IOException("Write access denied: " + directory, error);
        } catch (RuntimeException error) {
            throw new IOException("Cannot test destination: " + directory, error);
        }
    }

    public static long availableBytes(File directory) {
        try {
            StatFs statFs = new StatFs(directory.getAbsolutePath());
            return statFs.getAvailableBytes();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    public static String formatBytes(long bytes) {
        if (bytes <= 0L) {
            return "unknown free space";
        }
        double gib = bytes / (1024.0 * 1024.0 * 1024.0);
        return String.format(java.util.Locale.US, "%.1f GB free", gib);
    }
}
