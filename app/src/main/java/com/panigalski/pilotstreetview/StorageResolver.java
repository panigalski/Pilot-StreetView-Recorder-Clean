package com.panigalski.pilotstreetview;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Returns true when Pilot OS currently exposes a non-primary mounted volume.
     *
     * Pilot One USB disks are commonly mounted as /storage/<UUID> through FUSE,
     * but some firmware revisions do not publish that disk through
     * getExternalFilesDirs() or mark the corresponding StorageVolume as
     * removable. /proc/mounts is therefore checked first because it reports the
     * real mount without touching or spinning up the disk.
     */
    public static boolean isExternalStorageConnected(Context context) {
        if (!externalRootsFromProcMounts().isEmpty()) {
            return true;
        }

        try {
            StorageManager manager =
                    (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            if (manager != null) {
                List<StorageVolume> volumes = manager.getStorageVolumes();
                if (volumes != null) {
                    for (StorageVolume volume : volumes) {
                        if (volume == null || volume.isPrimary()) {
                            continue;
                        }
                        String state = volume.getState();
                        if (Environment.MEDIA_MOUNTED.equals(state)
                                || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "StorageManager presence check failed", error);
        }

        try {
            File[] appDirs = context.getExternalFilesDirs(null);
            if (appDirs != null) {
                for (File appDir : appDirs) {
                    if (appDir == null || !isRemovable(appDir)) {
                        continue;
                    }
                    String state = Environment.getExternalStorageState(appDir);
                    if (Environment.MEDIA_MOUNTED.equals(state)
                            || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                        return true;
                    }
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "External-files presence check failed", error);
        }

        // Last-resort OEM fallback. This runs on a worker thread in the chooser.
        try {
            File[] roots = new File("/storage").listFiles();
            if (roots != null) {
                for (File root : roots) {
                    if (isPossibleExternalRoot(root)) {
                        return true;
                    }
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "Direct /storage presence check failed", error);
        }
        return false;
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

    /** Returns the writable removable destination with the most free space. */
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

        for (File root : discoverExternalRoots(context)) {
            try {
                File target = new File(root, RELATIVE_RECORDING_PATH);
                ensureWritable(target);
                addIfUnique(candidates, new Destination(
                        MODE_EXTERNAL,
                        "External Storage (" + root.getName() + ")",
                        target,
                        false,
                        availableBytes(target)));
            } catch (IOException error) {
                Log.i(TAG, "Public removable path is not writable: " + root);
            } catch (RuntimeException error) {
                Log.w(TAG, "Ignoring removable root: " + root, error);
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

    private static List<File> discoverExternalRoots(Context context) {
        Map<String, File> roots = new LinkedHashMap<>();

        for (File root : externalRootsFromProcMounts()) {
            addRoot(roots, root);
        }

        try {
            StorageManager manager =
                    (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            if (manager != null) {
                List<StorageVolume> volumes = manager.getStorageVolumes();
                if (volumes != null) {
                    for (StorageVolume volume : volumes) {
                        if (volume == null || volume.isPrimary()) {
                            continue;
                        }
                        String state = volume.getState();
                        if (!Environment.MEDIA_MOUNTED.equals(state)
                                && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                            continue;
                        }
                        File path = storageVolumePath(volume);
                        if (path != null) {
                            addRoot(roots, path);
                        }
                    }
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "Cannot enumerate StorageVolume paths", error);
        }

        try {
            File[] appDirs = context.getExternalFilesDirs(null);
            if (appDirs != null) {
                for (File appDir : appDirs) {
                    if (appDir != null && isRemovable(appDir)) {
                        addRoot(roots, volumeRootFromAppDir(appDir));
                    }
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "Cannot derive roots from external app dirs", error);
        }

        try {
            File[] storageRoots = new File("/storage").listFiles();
            if (storageRoots != null) {
                for (File root : storageRoots) {
                    if (isPossibleExternalRoot(root)) {
                        addRoot(roots, root);
                    }
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "Cannot enumerate /storage roots", error);
        }

        return new ArrayList<>(roots.values());
    }

    private static List<File> externalRootsFromProcMounts() {
        List<File> roots = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/mounts"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split("\\s+");
                if (columns.length < 2) {
                    continue;
                }
                String mountPoint = decodeMountPath(columns[1]);
                if (mountPoint.startsWith("/storage/")) {
                    File root = new File(mountPoint);
                    if (isPossibleExternalRoot(root)) {
                        roots.add(root);
                    }
                } else if (mountPoint.startsWith("/mnt/media_rw/")) {
                    String volumeName = new File(mountPoint).getName();
                    File publicRoot = new File("/storage", volumeName);
                    if (isPossibleExternalRoot(publicRoot)) {
                        roots.add(publicRoot);
                    }
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "Cannot read /proc/mounts", error);
        }
        return roots;
    }

    private static String decodeMountPath(String value) {
        return value.replace("\\040", " ")
                .replace("\\011", "\t")
                .replace("\\134", "\\");
    }

    private static File storageVolumePath(StorageVolume volume) {
        String[] methodNames = new String[]{"getDirectory", "getPathFile", "getPath"};
        for (String methodName : methodNames) {
            try {
                Method method = StorageVolume.class.getMethod(methodName);
                Object value = method.invoke(volume);
                if (value instanceof File) {
                    return (File) value;
                }
                if (value instanceof String && !((String) value).isEmpty()) {
                    return new File((String) value);
                }
            } catch (Throwable ignored) {
                // Android 7 vendor builds expose different hidden method names.
            }
        }
        String uuid = volume.getUuid();
        return uuid == null || uuid.isEmpty() ? null : new File("/storage", uuid);
    }

    private static boolean isPossibleExternalRoot(File root) {
        if (root == null) {
            return false;
        }
        String name = root.getName();
        if ("emulated".equals(name) || "self".equals(name) || "enc_emulated".equals(name)) {
            return false;
        }
        String path = root.getAbsolutePath();
        try {
            File primary = Environment.getExternalStorageDirectory();
            if (primary != null && path.equals(primary.getAbsolutePath())) {
                return false;
            }
        } catch (RuntimeException ignored) {
            // Keep using the known-name filters.
        }
        return path.startsWith("/storage/") && path.length() > "/storage/".length();
    }

    private static void addRoot(Map<String, File> roots, File root) {
        if (!isPossibleExternalRoot(root)) {
            return;
        }
        String key;
        try {
            key = root.getCanonicalPath();
        } catch (IOException error) {
            key = root.getAbsolutePath();
        }
        roots.put(key, root);
    }

    private static Destination buildExternalCandidate(File appDir) {
        String path = appDir.getAbsolutePath();
        int androidIndex = path.indexOf(File.separator + "Android" + File.separator
                + "data" + File.separator);

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
