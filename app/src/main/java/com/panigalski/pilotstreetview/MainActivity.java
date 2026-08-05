package com.panigalski.pilotstreetview;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.pi.pano.ChangeResolutionListener;
import com.pi.pano.PanoSDKListener;
import com.pi.pano.PilotSDK;
import com.pi.pano.annotation.PiPreviewMode;
import com.pi.pano.annotation.PiVideoResolution;
import com.pi.pano.helper.PreviewHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity
        implements LocationListener, SegmentedStreetViewRecorder.Listener {

    private static final int PERMISSION_REQUEST = 1001;

    private final String[] requestedPermissions = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable recordingClock = new Runnable() {
        @Override
        public void run() {
            if (!recorder.isRecording()) {
                return;
            }
            long elapsed = (System.currentTimeMillis() - recordingStartedAt) / 1000L;
            long hours = elapsed / 3600L;
            long minutes = (elapsed % 3600L) / 60L;
            long seconds = elapsed % 60L;
            recordTime.setText(String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds));
            uiHandler.postDelayed(this, 1000L);
        }
    };

    private FrameLayout previewContainer;
    private TextView statusText;
    private TextView gpsText;
    private TextView pathText;
    private TextView destinationButton;
    private TextView recordCaption;
    private TextView recordTime;
    private ImageButton recordButton;
    private ImageButton settingsButton;
    private ImageButton galleryButton;

    private PilotStatusBarController statusBar;
    private LocationManager locationManager;
    private PilotSDK pilotSDK;
    private SegmentedStreetViewRecorder recorder;
    private StorageResolver.Destination selectedDestination;
    private boolean previewReady;
    private boolean initialized;
    private long recordingStartedAt;
    private int destinationScanGeneration;
    private boolean destinationCheckInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiChrome.apply(this);
        setContentView(R.layout.activity_main);

        previewContainer = findViewById(R.id.preview_container);
        statusText = findViewById(R.id.status_text);
        gpsText = findViewById(R.id.gps_text);
        pathText = findViewById(R.id.path_text);
        destinationButton = findViewById(R.id.destination_button);
        recordCaption = findViewById(R.id.record_caption);
        recordTime = findViewById(R.id.record_time);
        recordButton = findViewById(R.id.record_button);
        settingsButton = findViewById(R.id.settings_button);
        galleryButton = findViewById(R.id.gallery_button);

        statusBar = new PilotStatusBarController(this, getString(R.string.screen_title));
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        recorder = new SegmentedStreetViewRecorder(this);

        destinationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showStorageSelection();
            }
        });
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleRecording();
            }
        });
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (recorder.isRecording()) {
                    Toast.makeText(MainActivity.this,
                            "Stop recording before opening settings.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                SettingsDialog.show(MainActivity.this, new SettingsDialog.Listener() {
                    @Override
                    public void onDestinationChanged(String mode) {
                        showSelectedModeWithoutDiskProbe();
                    }
                });
            }
        });
        galleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(MainActivity.this, R.string.gallery_not_ready, Toast.LENGTH_SHORT).show();
            }
        });

        showSelectedModeWithoutDiskProbe();
        requestPermissionsAndInitialize();
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiChrome.apply(this);
        statusBar.start();
        showSelectedModeWithoutDiskProbe();
    }

    @Override
    protected void onPause() {
        destinationScanGeneration++;
        statusBar.stop();
        super.onPause();
    }

    private void requestPermissionsAndInitialize() {
        List<String> missing = new ArrayList<>();
        for (String permission : requestedPermissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        if (missing.isEmpty()) {
            initializeCameraAndLocation();
        } else {
            requestPermissions(missing.toArray(new String[0]), PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_REQUEST) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            statusText.setText(R.string.permissions_required);
            return;
        }
        initializeCameraAndLocation();
    }

    private void initializeCameraAndLocation() {
        if (initialized) {
            return;
        }
        initialized = true;
        startLocationUpdates();
        statusText.setText(R.string.preview_initializing);

        pilotSDK = PreviewHelper.initPanoView(previewContainer, new PanoSDKListener() {
            @Override
            public void onPanoCreate() {
                configureStreetViewPreview();
            }

            @Override
            public void onPanoRelease() {
                previewReady = false;
                recordButton.setEnabled(false);
                statusText.setText("Preview released");
            }

            @Override public void onChangePreviewMode(int mode) { }
            @Override public void onSingleTap() { }
            @Override public void onEncodeFrame(int count) { }
        });
    }

    private void configureStreetViewPreview() {
        PreviewHelper.changeCameraResolutionForVideo(
                PiVideoResolution._8K,
                false,
                new ChangeResolutionListener() {
                    @Override
                    protected void onChangeResolution(int width, int height) {
                        PilotSDK.setPreviewMode(PiPreviewMode.planet, 0F, false);
                        previewReady = true;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusText.setText(R.string.preview_ready);
                                recordButton.setEnabled(!destinationCheckInProgress);
                            }
                        });
                    }
                });
    }

    /**
     * Shows storage choices in the current Activity window. Starting a second,
     * opaque Activity destroys the SDK SurfaceView. The public Pilot SDK then
     * performs a synchronous native release from surfaceDestroyed(), which can
     * block Pilot OS. A dialog keeps the preview Surface alive.
     */
    private void showStorageSelection() {
        if (recorder != null && recorder.isRecording()) {
            Toast.makeText(this, "Stop recording before changing storage.", Toast.LENGTH_SHORT).show();
            return;
        }
        StorageSelectionDialog.show(this, new StorageSelectionDialog.Listener() {
            @Override
            public void onSelected(String mode) {
                showSelectedModeWithoutDiskProbe();
            }
        });
    }

    /**
     * Updates labels only. No mount enumeration, mkdir, write test, or StatFs
     * call is allowed while the user is choosing a destination.
     */
    private void showSelectedModeWithoutDiskProbe() {
        if (recorder != null && recorder.isRecording()) {
            return;
        }
        destinationScanGeneration++;
        destinationCheckInProgress = false;
        selectedDestination = null;
        String mode = RecorderPreferences.getDestinationMode(this);
        destinationButton.setText(StorageResolver.MODE_EXTERNAL.equals(mode)
                ? getString(R.string.destination_external) + " • "
                    + getString(R.string.storage_selected)
                : getString(R.string.destination_internal) + " • "
                    + getString(R.string.storage_selected));
        pathText.setText("");
        if (previewReady) {
            statusText.setText(R.string.preview_ready);
        }
        recordButton.setEnabled(previewReady);
    }

    /**
     * Resolves storage on a worker thread. Pilot OS can block for several
     * seconds while a USB HDD spins up; doing this from onResume previously
     * triggered an Application Not Responding state immediately after the
     * user selected External Storage.
     */
    private void refreshDestinationAsync() {
        if (recorder != null && recorder.isRecording()) {
            return;
        }

        final int generation = ++destinationScanGeneration;
        final String mode = RecorderPreferences.getDestinationMode(this);
        destinationCheckInProgress = true;
        selectedDestination = null;
        recordButton.setEnabled(false);
        destinationButton.setText(StorageResolver.MODE_EXTERNAL.equals(mode)
                ? getString(R.string.storage_checking_external)
                : getString(R.string.storage_checking));
        pathText.setText("");
        if (previewReady) {
            statusText.setText(R.string.storage_checking_short);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                StorageResolver.Destination result = null;
                String errorMessage = null;
                try {
                    result = StorageResolver.resolve(MainActivity.this, mode);
                } catch (Exception error) {
                    errorMessage = error.getMessage();
                }

                final StorageResolver.Destination finalResult = result;
                final String finalErrorMessage = errorMessage;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (generation != destinationScanGeneration
                                || isFinishing() || isDestroyed()) {
                            return;
                        }
                        destinationCheckInProgress = false;
                        applyDestinationResult(mode, finalResult, finalErrorMessage);
                    }
                });
            }
        }, "main-storage-resolve").start();
    }

    private void applyDestinationResult(String mode,
                                        StorageResolver.Destination destination,
                                        String errorMessage) {
        selectedDestination = destination;
        if (destination != null) {
            destinationButton.setText(destination.label + " • "
                    + StorageResolver.formatBytes(destination.freeBytes));
            pathText.setText(destination.directory.getAbsolutePath());
            if (!recorder.isRecording()) {
                statusText.setText(previewReady
                        ? getString(R.string.preview_ready)
                        : getString(R.string.preview_initializing));
            }
        } else {
            destinationButton.setText(StorageResolver.MODE_EXTERNAL.equals(mode)
                    ? getString(R.string.destination_external) + " • unavailable"
                    : getString(R.string.destination_internal) + " • unavailable");
            pathText.setText(errorMessage == null
                    ? getString(R.string.storage_unavailable) : errorMessage);
            statusText.setText("Destination unavailable");
        }
        recordButton.setEnabled(previewReady && !destinationCheckInProgress
                && !recorder.isRecording());
    }

    private void toggleRecording() {
        if (recorder.isRecording()) {
            recorder.stop();
            return;
        }
        if (!previewReady) {
            Toast.makeText(this, "Wait for the 8K preview to become ready.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (destinationCheckInProgress) {
            Toast.makeText(this, R.string.storage_checking_short, Toast.LENGTH_SHORT).show();
            return;
        }
        validateDestinationAndStartAsync();
    }

    /** Revalidates the selected disk without blocking the UI before recording. */
    private void validateDestinationAndStartAsync() {
        final int generation = ++destinationScanGeneration;
        final String mode = RecorderPreferences.getDestinationMode(this);
        destinationCheckInProgress = true;
        recordButton.setEnabled(false);
        destinationButton.setEnabled(false);
        settingsButton.setEnabled(false);
        statusText.setText(R.string.storage_checking_short);

        new Thread(new Runnable() {
            @Override
            public void run() {
                StorageResolver.Destination result = null;
                String errorMessage = null;
                try {
                    result = StorageResolver.resolve(MainActivity.this, mode);
                } catch (Exception error) {
                    errorMessage = error.getMessage();
                }

                final StorageResolver.Destination finalResult = result;
                final String finalErrorMessage = errorMessage;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (generation != destinationScanGeneration
                                || isFinishing() || isDestroyed()) {
                            return;
                        }
                        destinationCheckInProgress = false;
                        destinationButton.setEnabled(true);
                        settingsButton.setEnabled(true);

                        if (finalResult == null) {
                            applyDestinationResult(mode, null, finalErrorMessage);
                            Toast.makeText(MainActivity.this,
                                    finalErrorMessage == null
                                            ? "Selected storage is unavailable."
                                            : finalErrorMessage,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        selectedDestination = finalResult;
                        destinationButton.setText(finalResult.label + " • "
                                + StorageResolver.formatBytes(finalResult.freeBytes));
                        pathText.setText(finalResult.directory.getAbsolutePath());
                        continueStartAfterStorageValidation();
                    }
                });
            }
        }, "recording-storage-validate").start();
    }

    private void continueStartAfterStorageValidation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setTitle("GPS permission missing")
                    .setMessage("Street View video should include GPS. Record without GPS anyway?")
                    .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                        destinationButton.setEnabled(true);
                        settingsButton.setEnabled(true);
                        recordButton.setEnabled(previewReady && !destinationCheckInProgress);
                        statusText.setText(R.string.preview_ready);
                    })
                    .setPositiveButton("Record", (dialog, which) -> startRecordingNow())
                    .show();
            return;
        }
        startRecordingNow();
    }

    private void startRecordingNow() {
        destinationButton.setEnabled(false);
        settingsButton.setEnabled(false);
        recordButton.setEnabled(true);
        recordButton.setBackgroundResource(R.drawable.record_button_active);
        recordCaption.setText(R.string.stop_recording);
        recordingStartedAt = System.currentTimeMillis();
        recordTime.setText("00:00:00");
        recordTime.setVisibility(View.VISIBLE);
        uiHandler.removeCallbacks(recordingClock);
        uiHandler.post(recordingClock);
        recorder.start(selectedDestination.directory);
    }

    private void resetRecordingUi() {
        uiHandler.removeCallbacks(recordingClock);
        recordTime.setVisibility(View.GONE);
        recordButton.setBackgroundResource(R.drawable.record_button_idle);
        recordCaption.setText(R.string.start_recording);
        recordButton.setEnabled(previewReady && !destinationCheckInProgress);
        destinationButton.setEnabled(true);
        settingsButton.setEnabled(true);
    }

    private void startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            gpsText.setText("GPS permission not granted");
            return;
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0F, this);
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gpsText.setText(R.string.gps_disabled);
            }
        } catch (RuntimeException error) {
            gpsText.setText("GPS unavailable");
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        // The location metadata is only consumed by the recorder. Avoid touching
        // the recorder's static metadata state during preview-only operation.
        if (recorder != null && recorder.isRecording()) {
            PilotSDK.setLocationInfo(location);
        }
        gpsText.setText(String.format(Locale.US, getString(R.string.gps_ready),
                location.getLatitude(), location.getLongitude()));
    }

    @Override public void onProviderDisabled(String provider) { gpsText.setText(R.string.gps_disabled); }
    @Override public void onProviderEnabled(String provider) { gpsText.setText(R.string.gps_waiting); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override
    public void onRecordingStarted(String path, int partNumber) {
        recordButton.setBackgroundResource(R.drawable.record_button_active);
        recordCaption.setText(R.string.stop_recording);
        recordButton.setEnabled(true);
        destinationButton.setEnabled(false);
        pathText.setText(path);
        statusText.setText("Recording part " + partNumber);
    }

    @Override
    public void onRecordingStopped(List<String> completedFiles) {
        resetRecordingUi();
        statusText.setText("Stopped • " + completedFiles.size() + " file(s) saved");
        showSelectedModeWithoutDiskProbe();
    }

    @Override
    public void onStatus(String message) {
        statusText.setText(message);
    }

    @Override
    public void onError(String message) {
        resetRecordingUi();
        statusText.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacks(recordingClock);
        if (recorder != null && recorder.isRecording()) {
            recorder.stop();
        }
        if (locationManager != null
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.removeUpdates(this);
            } catch (RuntimeException ignored) {
                // No-op.
            }
        }
        super.onDestroy();
    }
}
