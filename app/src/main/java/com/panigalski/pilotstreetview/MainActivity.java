package com.panigalski.pilotstreetview;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
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
        implements LocationListener, GpsStatus.Listener, SegmentedStreetViewRecorder.Listener {

    private static final int PERMISSION_REQUEST = 1001;
    private static final long GPS_MAX_FIX_AGE_MS = 10_000L;
    private static final float GPS_MAX_ACCURACY_METERS = 25F;
    private static final int GPS_MIN_SATELLITES_USED = 4;
    private static final long PREVIEW_READY_DISPLAY_MS = 5_000L;

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
    private final Runnable gpsUiRefresh = new Runnable() {
        @Override
        public void run() {
            if (!activityResumed) {
                return;
            }
            updateGpsUi();
            uiHandler.postDelayed(this, 2000L);
        }
    };
    private final Runnable hidePreviewReadyStatus = new Runnable() {
        @Override
        public void run() {
            if (statusText == null || !previewReady
                    || (recorder != null && recorder.isRecording())) {
                return;
            }
            if (getString(R.string.preview_ready).contentEquals(statusText.getText())) {
                statusText.setVisibility(View.GONE);
            }
        }
    };

    private FrameLayout previewContainer;
    private TextView statusText;
    private TextView gpsText;
    private TextView pathText;
    private TextView destinationButton;
    private TextView pictureValue;
    private TextView recordCaption;
    private TextView recordTime;
    private ImageButton recordButton;
    private View pictureButton;
    private View storageButton;

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
    private boolean activityResumed;
    private boolean cameraReleaseInProgress;
    private boolean locationUpdatesActive;
    private boolean gpsStatusListenerActive;
    private Location latestGpsLocation;
    private long latestGpsFixReceivedAt;
    private int gpsSatellitesVisible;
    private int gpsSatellitesUsed;
    private MediaPlayer startRecordingPlayer;
    private MediaPlayer stopRecordingPlayer;
    private boolean recordingSessionActive;

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
        pictureValue = findViewById(R.id.picture_value);
        recordCaption = findViewById(R.id.record_caption);
        recordTime = findViewById(R.id.record_time);
        recordButton = findViewById(R.id.record_button);
        pictureButton = findViewById(R.id.picture_button);
        storageButton = findViewById(R.id.storage_button);

        statusBar = new PilotStatusBarController(this, "");
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        recorder = new SegmentedStreetViewRecorder(this);

        storageButton.setOnClickListener(new View.OnClickListener() {
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
        pictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (recorder.isRecording()) {
                    Toast.makeText(MainActivity.this,
                            "Stop recording before changing picture settings.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                PictureAdjustmentsDialog.show(MainActivity.this,
                        new PictureAdjustmentsDialog.Listener() {
                            @Override
                            public void onPictureAdjustmentsChanged() {
                                updatePictureSummary();
                                showStatus(R.string.picture_adjustments_applied);
                            }
                        });
            }
        });

        updatePictureSummary();
        showSelectedModeWithoutDiskProbe();
        requestPermissionsAndInitialize();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        UiChrome.apply(this);
        statusBar.start();
        showSelectedModeWithoutDiskProbe();
        updatePictureSummary();
        startLocationUpdates();
        uiHandler.removeCallbacks(gpsUiRefresh);
        uiHandler.post(gpsUiRefresh);

        if (!initialized && !cameraReleaseInProgress
                && checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            initializeCameraAndLocation();
        } else if (cameraReleaseInProgress) {
            showStatus("Releasing camera...");
            recordButton.setEnabled(false);
        }
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        destinationScanGeneration++;
        uiHandler.removeCallbacks(recordingClock);
        uiHandler.removeCallbacks(gpsUiRefresh);
        uiHandler.removeCallbacks(hidePreviewReadyStatus);
        releaseStartRecordingSound();
        releaseStopRecordingSound();
        recordingSessionActive = false;
        statusBar.stop();
        stopLocationUpdates();
        beginCameraReleaseForBackground();
        super.onPause();
    }

    /**
     * Releases all camera and GPS resources as soon as this Activity leaves the
     * foreground. Android does not guarantee onDestroy() when switching apps,
     * so cleanup must not be deferred until destruction.
     */
    private void beginCameraReleaseForBackground() {
        if (cameraReleaseInProgress) {
            return;
        }
        previewReady = false;
        recordButton.setEnabled(false);

        if (pilotSDK == null) {
            initialized = false;
            return;
        }

        cameraReleaseInProgress = true;
        showStatus("Releasing camera...");

        if (recorder != null && recorder.isRecording()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    recorder.stopForLifecycle();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            releasePilotSdkOnUiThread();
                        }
                    });
                }
            }, "pilot-recording-lifecycle-stop").start();
        } else {
            releasePilotSdkOnUiThread();
        }
    }

    private void releasePilotSdkOnUiThread() {
        PilotSDK sdk = pilotSDK;
        pilotSDK = null;
        initialized = false;
        if (sdk == null) {
            cameraReleaseInProgress = false;
            return;
        }
        try {
            sdk.release();
        } catch (RuntimeException error) {
            cameraReleaseInProgress = false;
        }
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
            showStatus(R.string.permissions_required);
            return;
        }
        initializeCameraAndLocation();
    }

    private void initializeCameraAndLocation() {
        if (initialized || cameraReleaseInProgress || isFinishing() || isDestroyed()) {
            return;
        }
        initialized = true;
        startLocationUpdates();
        showStatus(R.string.preview_initializing);

        pilotSDK = PreviewHelper.initPanoView(previewContainer, new PanoSDKListener() {
            @Override
            public void onPanoCreate() {
                if (!cameraReleaseInProgress) {
                    configureStreetViewPreview();
                }
            }

            @Override
            public void onPanoRelease() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        previewReady = false;
                        initialized = false;
                        pilotSDK = null;
                        cameraReleaseInProgress = false;
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        recordButton.setEnabled(false);
                        showStatus("Preview released");
                        if (activityResumed) {
                            uiHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (activityResumed && !cameraReleaseInProgress) {
                                        initializeCameraAndLocation();
                                    }
                                }
                            }, 300L);
                        }
                    }
                });
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
                        if (cameraReleaseInProgress) {
                            return;
                        }
                        PilotSDK.setPreviewMode(PiPreviewMode.planet, 0F, false);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (cameraReleaseInProgress
                                        || isFinishing() || isDestroyed()) {
                                    return;
                                }
                                try {
                                    PictureAdjustments.applySaved(MainActivity.this);
                                } catch (RuntimeException error) {
                                    Toast.makeText(MainActivity.this,
                                            getString(R.string.picture_apply_failed) + ": "
                                                    + String.valueOf(error.getMessage()),
                                            Toast.LENGTH_LONG).show();
                                }
                                previewReady = true;
                                showPreviewReadyTemporarily();
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
    private void updatePictureSummary() {
        if (pictureValue == null) {
            return;
        }
        String iso = PictureAdjustments.isoLabel(RecorderPreferences.getIso(this));
        String ev = PictureAdjustments.evLabel(
                RecorderPreferences.getExposureCompensation(this));
        pictureValue.setText(ev + " • " + iso);
    }

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
                ? getString(R.string.destination_external_short)
                : getString(R.string.destination_internal_short));
        pathText.setText("");
        if (previewReady) {
            showPreviewReadyTemporarily();
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
        destinationButton.setText(getString(R.string.storage_checking_short));
        pathText.setText("");
        if (previewReady) {
            showStatus(R.string.storage_checking_short);
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
            destinationButton.setText(StorageResolver.MODE_EXTERNAL.equals(mode)
                    ? getString(R.string.destination_external_short)
                    : getString(R.string.destination_internal_short));
            pathText.setText(destination.directory.getAbsolutePath());
            if (!recorder.isRecording()) {
                if (previewReady) {
                    showPreviewReadyTemporarily();
                } else {
                    showStatus(R.string.preview_initializing);
                }
            }
        } else {
            destinationButton.setText(StorageResolver.MODE_EXTERNAL.equals(mode)
                    ? getString(R.string.destination_external_short) + " • unavailable"
                    : getString(R.string.destination_internal_short) + " • unavailable");
            pathText.setText(errorMessage == null
                    ? getString(R.string.storage_unavailable) : errorMessage);
            showStatus("Destination unavailable");
        }
        recordButton.setEnabled(previewReady && !destinationCheckInProgress
                && !recorder.isRecording());
    }

    private void showStatus(int stringResId) {
        showStatus(getString(stringResId));
    }

    private void showStatus(CharSequence message) {
        uiHandler.removeCallbacks(hidePreviewReadyStatus);
        statusText.setVisibility(View.VISIBLE);
        statusText.setText(message);
    }

    private void showPreviewReadyTemporarily() {
        showStatus(R.string.preview_ready);
        uiHandler.postDelayed(hidePreviewReadyStatus, PREVIEW_READY_DISPLAY_MS);
    }

    private void playStartRecordingSound() {
        releaseStopRecordingSound();
        releaseStartRecordingSound();
        MediaPlayer player = MediaPlayer.create(this, R.raw.start_recording);
        if (player == null) {
            return;
        }
        startRecordingPlayer = player;
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                if (startRecordingPlayer == mediaPlayer) {
                    startRecordingPlayer = null;
                }
                mediaPlayer.release();
            }
        });
        player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                if (startRecordingPlayer == mediaPlayer) {
                    startRecordingPlayer = null;
                }
                mediaPlayer.release();
                return true;
            }
        });
        player.start();
    }

    private void releaseStartRecordingSound() {
        MediaPlayer player = startRecordingPlayer;
        startRecordingPlayer = null;
        if (player == null) {
            return;
        }
        try {
            player.stop();
        } catch (IllegalStateException ignored) {
            // Playback had already completed or had not started.
        }
        player.release();
    }

    private void playStopRecordingSound() {
        releaseStartRecordingSound();
        releaseStopRecordingSound();
        MediaPlayer player = MediaPlayer.create(this, R.raw.recording_stop_cue);
        if (player == null) {
            return;
        }
        stopRecordingPlayer = player;
        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                if (stopRecordingPlayer == mediaPlayer) {
                    stopRecordingPlayer = null;
                }
                mediaPlayer.release();
            }
        });
        player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                if (stopRecordingPlayer == mediaPlayer) {
                    stopRecordingPlayer = null;
                }
                mediaPlayer.release();
                return true;
            }
        });
        player.start();
    }

    private void releaseStopRecordingSound() {
        MediaPlayer player = stopRecordingPlayer;
        stopRecordingPlayer = null;
        if (player == null) {
            return;
        }
        try {
            player.stop();
        } catch (IllegalStateException ignored) {
            // Playback had already completed or had not started.
        }
        player.release();
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
        String gpsProblem = getGpsReadinessProblem();
        if (gpsProblem != null) {
            showStatus(R.string.gps_required_short);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.gps_required_title)
                    .setMessage(gpsProblem)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
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
        storageButton.setEnabled(false);
        pictureButton.setEnabled(false);
        showStatus(R.string.storage_checking_short);

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
                        storageButton.setEnabled(true);
                        pictureButton.setEnabled(true);

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
                        destinationButton.setText(StorageResolver.MODE_EXTERNAL.equals(mode)
                                ? getString(R.string.destination_external_short)
                                : getString(R.string.destination_internal_short));
                        pathText.setText(finalResult.directory.getAbsolutePath());
                        continueStartAfterStorageValidation();
                    }
                });
            }
        }, "recording-storage-validate").start();
    }

    private void continueStartAfterStorageValidation() {
        String gpsProblem = getGpsReadinessProblem();
        if (gpsProblem != null) {
            storageButton.setEnabled(true);
            pictureButton.setEnabled(true);
            recordButton.setEnabled(previewReady && !destinationCheckInProgress);
            showStatus(R.string.gps_required_short);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.gps_required_title)
                    .setMessage(gpsProblem)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        startRecordingNow();
    }

    private void startRecordingNow() {
        storageButton.setEnabled(false);
        pictureButton.setEnabled(false);
        recordButton.setEnabled(true);
        recordButton.setBackgroundResource(R.drawable.record_button_active);
        recordCaption.setText(R.string.stop_recording);
        recordingStartedAt = System.currentTimeMillis();
        recordTime.setText("00:00:00");
        recordTime.setVisibility(View.VISIBLE);
        uiHandler.removeCallbacks(recordingClock);
        uiHandler.post(recordingClock);
        recorder.start(
                selectedDestination.directory,
                latestGpsLocation == null ? null : new Location(latestGpsLocation));
    }

    private void resetRecordingUi() {
        uiHandler.removeCallbacks(recordingClock);
        recordTime.setVisibility(View.GONE);
        recordButton.setBackgroundResource(R.drawable.record_button_idle);
        recordCaption.setText(R.string.start_recording);
        recordButton.setEnabled(previewReady && !destinationCheckInProgress);
        storageButton.setEnabled(true);
        pictureButton.setEnabled(true);
        gpsText.setVisibility(View.VISIBLE);
        updateGpsUi();
    }

    private void startLocationUpdates() {
        if (locationUpdatesActive || locationManager == null) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            gpsText.setText(R.string.gps_permission_missing);
            return;
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0F, this);
            locationUpdatesActive = true;
            if (!gpsStatusListenerActive) {
                gpsStatusListenerActive = locationManager.addGpsStatusListener(this);
            }
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gpsText.setText(R.string.gps_disabled);
            } else {
                updateGpsUi();
            }
        } catch (RuntimeException error) {
            locationUpdatesActive = false;
            gpsStatusListenerActive = false;
            gpsText.setText(R.string.gps_unavailable);
        }
    }

    private void stopLocationUpdates() {
        if (locationManager == null) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            if (locationUpdatesActive) {
                try {
                    locationManager.removeUpdates(this);
                } catch (RuntimeException ignored) {
                    // Provider may already have been stopped by Pilot OS.
                }
            }
            if (gpsStatusListenerActive) {
                try {
                    locationManager.removeGpsStatusListener(this);
                } catch (RuntimeException ignored) {
                    // Listener may already have been removed by Pilot OS.
                }
            }
        }
        locationUpdatesActive = false;
        gpsStatusListenerActive = false;
        latestGpsLocation = null;
        latestGpsFixReceivedAt = 0L;
        gpsSatellitesVisible = 0;
        gpsSatellitesUsed = 0;
    }

    private String getGpsReadinessProblem() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return getString(R.string.gps_problem_permission);
        }
        if (locationManager == null) {
            return getString(R.string.gps_problem_unavailable);
        }
        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                return getString(R.string.gps_problem_disabled);
            }
        } catch (RuntimeException error) {
            return getString(R.string.gps_problem_unavailable);
        }
        if (latestGpsLocation == null || latestGpsFixReceivedAt == 0L) {
            return getString(R.string.gps_problem_no_fix);
        }
        long ageMs = SystemClock.elapsedRealtime() - latestGpsFixReceivedAt;
        if (ageMs < 0L || ageMs > GPS_MAX_FIX_AGE_MS) {
            return getString(R.string.gps_problem_stale);
        }
        if (!latestGpsLocation.hasAccuracy()) {
            return getString(R.string.gps_problem_no_accuracy);
        }
        if (latestGpsLocation.getAccuracy() > GPS_MAX_ACCURACY_METERS) {
            return getString(R.string.gps_problem_weak_accuracy,
                    latestGpsLocation.getAccuracy(), GPS_MAX_ACCURACY_METERS);
        }
        if (gpsSatellitesVisible > 0 && gpsSatellitesUsed < GPS_MIN_SATELLITES_USED) {
            return getString(R.string.gps_problem_satellites,
                    gpsSatellitesUsed, GPS_MIN_SATELLITES_USED);
        }
        return null;
    }

    private void updateGpsUi() {
        if (recorder != null && recorder.isRecording()) {
            return;
        }
        String problem = getGpsReadinessProblem();
        if (problem == null && latestGpsLocation != null) {
            gpsText.setText(getString(R.string.gps_ready_quality,
                    latestGpsLocation.getAccuracy(), gpsSatellitesUsed));
            return;
        }
        if (latestGpsLocation != null && latestGpsLocation.hasAccuracy()) {
            gpsText.setText(getString(R.string.gps_weak_quality,
                    latestGpsLocation.getAccuracy(), gpsSatellitesUsed));
        } else if (gpsSatellitesVisible > 0) {
            gpsText.setText(getString(R.string.gps_acquiring_satellites,
                    gpsSatellitesUsed, gpsSatellitesVisible));
        } else {
            gpsText.setText(R.string.gps_waiting);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null || !LocationManager.GPS_PROVIDER.equals(location.getProvider())) {
            return;
        }
        latestGpsLocation = new Location(location);
        latestGpsFixReceivedAt = SystemClock.elapsedRealtime();

        // Forward every fresh fix to the recorder. While recording, the
        // recorder feeds it to PilotSDK so it is muxed into the MP4 CAMM track.
        if (recorder != null) {
            recorder.updateLocation(location);
        }
        updateGpsUi();
    }

    @Override
    public void onGpsStatusChanged(int event) {
        if (locationManager == null
                || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (event == GpsStatus.GPS_EVENT_STOPPED) {
            gpsSatellitesVisible = 0;
            gpsSatellitesUsed = 0;
            updateGpsUi();
            return;
        }
        if (event != GpsStatus.GPS_EVENT_SATELLITE_STATUS
                && event != GpsStatus.GPS_EVENT_FIRST_FIX
                && event != GpsStatus.GPS_EVENT_STARTED) {
            return;
        }
        try {
            GpsStatus status = locationManager.getGpsStatus(null);
            int visible = 0;
            int used = 0;
            if (status != null) {
                for (GpsSatellite satellite : status.getSatellites()) {
                    visible++;
                    if (satellite.usedInFix()) {
                        used++;
                    }
                }
            }
            gpsSatellitesVisible = visible;
            gpsSatellitesUsed = used;
            updateGpsUi();
        } catch (RuntimeException ignored) {
            // Satellite metadata is optional on some Pilot OS builds. Accuracy
            // and fix freshness still provide a strict recording gate.
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        latestGpsLocation = null;
        latestGpsFixReceivedAt = 0L;
        gpsText.setText(R.string.gps_disabled);
    }

    @Override
    public void onProviderEnabled(String provider) {
        latestGpsLocation = null;
        latestGpsFixReceivedAt = 0L;
        gpsText.setText(R.string.gps_waiting);
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override
    public void onRecordingStarted(String path, int partNumber) {
        recordButton.setBackgroundResource(R.drawable.record_button_active);
        recordCaption.setText(R.string.stop_recording);
        recordButton.setEnabled(true);
        storageButton.setEnabled(false);
        pictureButton.setEnabled(false);
        pathText.setText(path);
        gpsText.setVisibility(View.GONE);
        if (partNumber == 1) {
            recordingSessionActive = true;
            playStartRecordingSound();
        }
        showStatus("Recording part " + partNumber);
    }

    @Override
    public void onRecordingStopped(List<String> completedFiles) {
        boolean shouldPlayStopCue = recordingSessionActive;
        recordingSessionActive = false;
        resetRecordingUi();
        if (shouldPlayStopCue) {
            playStopRecordingSound();
        }
        showStatus("Stopped • " + completedFiles.size() + " file(s) saved");
        showSelectedModeWithoutDiskProbe();
    }

    @Override
    public void onStatus(String message) {
        showStatus(message);
    }

    @Override
    public void onError(String message) {
        boolean shouldPlayStopCue = recordingSessionActive;
        recordingSessionActive = false;
        resetRecordingUi();
        if (shouldPlayStopCue) {
            playStopRecordingSound();
        }
        showStatus(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        activityResumed = false;
        uiHandler.removeCallbacks(recordingClock);
        uiHandler.removeCallbacks(gpsUiRefresh);
        uiHandler.removeCallbacks(hidePreviewReadyStatus);
        releaseStartRecordingSound();
        releaseStopRecordingSound();
        recordingSessionActive = false;
        stopLocationUpdates();
        beginCameraReleaseForBackground();
        super.onDestroy();
    }
}
