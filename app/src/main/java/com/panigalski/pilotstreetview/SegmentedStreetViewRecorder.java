package com.panigalski.pilotstreetview;

import android.os.Handler;
import android.os.Looper;

import com.pi.pano.MediaRecorderListener;
import com.pi.pano.MediaRecorderUtil;
import com.pi.pano.PilotSDK;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Records 8K/7 FPS Google Street View MP4 files and rotates them at the
 * approximate 4 GiB boundary, matching the original Camera app's timing model.
 */
public final class SegmentedStreetViewRecorder {
    private static final int VIDEO_WIDTH = 7680;
    private static final int CODEC_H264 = 0;
    private static final int CHANNEL_COUNT = 2;
    private static final float ULTRA_HIGH_BITRATE_MULTIPLE = 2.0f;
    private static final int FRAME_RATE_RATIO_7_FPS = MediaRecorderUtil.VIDEO_FRAME_RATE_7FPS;
    private static final long FOUR_GIB_BITS = 4L * 1024L * 1024L * 1024L * 8L;
    private static final long STREET_VIEW_BITRATE =
            (7L * (long) (VIDEO_WIDTH * VIDEO_WIDTH * 3) * 2L * 2L) / 30L;
    private static final long SEGMENT_DURATION_MS =
            (FOUR_GIB_BITS / STREET_VIEW_BITRATE) * 1000L;
    private static final long MIN_FREE_BYTES = 5L * 1024L * 1024L * 1024L;
    private static final String FIRMWARE_VERSION = "5.18.11";

    public interface Listener {
        void onRecordingStarted(String path, int partNumber);
        void onRecordingStopped(List<String> completedFiles);
        void onStatus(String message);
        void onError(String message);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final List<String> completedFiles = new ArrayList<>();

    private File directory;
    private boolean recording;
    private boolean rotating;
    private int partNumber;

    private final Runnable rotateRunnable = new Runnable() {
        @Override
        public void run() {
            rotateSegment();
        }
    };

    private final Runnable storageMonitor = new Runnable() {
        @Override
        public void run() {
            if (!recording) {
                return;
            }
            if (directory == null || !directory.exists() || !directory.canWrite()) {
                failAndStop("The recording drive was removed or became unwritable.");
                return;
            }
            if (StorageResolver.availableBytes(directory) < 512L * 1024L * 1024L) {
                failAndStop("Recording stopped: less than 512 MB remains on the destination.");
                return;
            }
            handler.postDelayed(this, 1000L);
        }
    };

    public SegmentedStreetViewRecorder(Listener listener) {
        this.listener = listener;
    }

    public boolean isRecording() {
        return recording;
    }

    public long getSegmentDurationMs() {
        return SEGMENT_DURATION_MS;
    }

    public void start(File destinationDirectory) {
        if (recording) {
            return;
        }
        directory = destinationDirectory;
        long free = StorageResolver.availableBytes(directory);
        if (free > 0 && free < MIN_FREE_BYTES) {
            listener.onError("At least 5 GB of free space is required.");
            return;
        }
        completedFiles.clear();
        partNumber = 0;
        recording = true;
        rotating = false;
        startNextSegment();
        handler.post(storageMonitor);
    }

    public void stop() {
        stopInternal(true);
    }

    /**
     * Stops recording during Activity backgrounding without invoking UI
     * callbacks from the lifecycle worker thread.
     */
    public void stopForLifecycle() {
        stopInternal(false);
    }

    private synchronized void stopInternal(boolean notifyListener) {
        if (!recording) {
            return;
        }
        recording = false;
        rotating = false;
        handler.removeCallbacks(rotateRunnable);
        handler.removeCallbacks(storageMonitor);
        String current = PilotSDK.getCurrentVideoFilePath();
        try {
            PilotSDK.stopRecord(FIRMWARE_VERSION);
            addCompleted(current);
            if (notifyListener) {
                listener.onRecordingStopped(new ArrayList<>(completedFiles));
            }
        } catch (RuntimeException error) {
            if (notifyListener) {
                listener.onError("Unable to stop recording: " + error.getMessage());
            }
        }
    }

    private void startNextSegment() {
        if (!recording) {
            return;
        }
        partNumber++;
        String fileName = generateFileName(partNumber);
        String dirPath = directory.getAbsolutePath() + File.separator;
        int result = PilotSDK.startRecord(
                dirPath,
                fileName,
                CODEC_H264,
                VIDEO_WIDTH,
                CHANNEL_COUNT,
                true,
                ULTRA_HIGH_BITRATE_MULTIPLE,
                FRAME_RATE_RATIO_7_FPS,
                new MediaRecorderListener() {
                    @Override
                    public void onError(final int what) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                failAndStop("PilotSDK recording error: " + what);
                            }
                        });
                    }
                });

        if (result != 0) {
            recording = false;
            handler.removeCallbacks(storageMonitor);
            listener.onError("PilotSDK.startRecord failed with code " + result + ".");
            return;
        }

        String current = PilotSDK.getCurrentVideoFilePath();
        listener.onRecordingStarted(current, partNumber);
        listener.onStatus("Recording part " + partNumber + " • next 4 GB split in about "
                + (SEGMENT_DURATION_MS / 1000L) + " seconds");
        handler.postDelayed(rotateRunnable, SEGMENT_DURATION_MS);
    }

    private void rotateSegment() {
        if (!recording || rotating) {
            return;
        }
        rotating = true;
        String current = PilotSDK.getCurrentVideoFilePath();
        try {
            PilotSDK.stopRecord(FIRMWARE_VERSION);
            addCompleted(current);
        } catch (RuntimeException error) {
            failAndStop("Unable to close a 4 GB segment: " + error.getMessage());
            return;
        }
        rotating = false;
        if (recording) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startNextSegment();
                }
            }, 350L);
        }
    }

    private void failAndStop(String message) {
        boolean wasRecording = recording;
        recording = false;
        rotating = false;
        handler.removeCallbacks(rotateRunnable);
        handler.removeCallbacks(storageMonitor);
        if (wasRecording) {
            String current = PilotSDK.getCurrentVideoFilePath();
            try {
                PilotSDK.stopRecord(FIRMWARE_VERSION);
                addCompleted(current);
            } catch (RuntimeException ignored) {
                // Preserve the original error message.
            }
        }
        listener.onError(message);
    }

    private void addCompleted(String path) {
        if (path == null || path.length() == 0 || completedFiles.contains(path)) {
            return;
        }
        completedFiles.add(path);
    }

    private static String generateFileName(int part) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return String.format(Locale.US, "SV_%s_P%03d", timestamp, part);
    }
}
