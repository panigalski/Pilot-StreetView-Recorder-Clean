package com.panigalski.pilotstreetview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.pi.pano.annotation.PiProEt;
import com.pi.pano.annotation.PiProIso;
import com.pi.pano.annotation.PiProWb;

/** In-place controls for every live image adjustment exposed by PilotSDK. */
public final class PictureAdjustmentsDialog {
    public interface Listener {
        void onPictureAdjustmentsChanged();
    }

    private static final int ROW_EXPOSURE_TIME = 0;
    private static final int ROW_ISO = 1;
    private static final int ROW_EV = 2;
    private static final int ROW_WHITE_BALANCE = 3;
    private static final int ROW_AWB_LOCK = 4;
    private static final int ROW_STITCHING_DISTANCE = 5;
    private static final int ROW_RESET = 6;

    private PictureAdjustmentsDialog() {
    }

    public static void show(final Activity activity, final Listener listener) {
        boolean manualExposureAvailable = PictureAdjustments.isManualExposureAvailable();
        String exposureValue = manualExposureAvailable
                ? PictureAdjustments.exposureTimeLabel(
                        RecorderPreferences.getExposureTime(activity))
                : activity.getString(R.string.picture_manual_exposure_unavailable);

        String[] rows = new String[]{
                activity.getString(R.string.picture_exposure_time) + "\n" + exposureValue,
                activity.getString(R.string.picture_iso) + "\n"
                        + PictureAdjustments.isoLabel(RecorderPreferences.getIso(activity)),
                activity.getString(R.string.picture_ev) + "\n"
                        + PictureAdjustments.evLabel(
                                RecorderPreferences.getExposureCompensation(activity)),
                activity.getString(R.string.picture_white_balance) + "\n"
                        + PictureAdjustments.whiteBalanceLabel(
                                RecorderPreferences.getWhiteBalance(activity)),
                activity.getString(R.string.picture_awb_lock) + "\n"
                        + (RecorderPreferences.getAutoWhiteBalanceLock(activity)
                        ? activity.getString(R.string.picture_on)
                        : activity.getString(R.string.picture_off)),
                activity.getString(R.string.picture_stitching_distance) + "\n"
                        + PictureAdjustments.stitchingDistanceLabel(
                                RecorderPreferences.getStitchingDistance(activity)),
                activity.getString(R.string.picture_reset_defaults)
        };

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.picture_adjustments_title)
                .setItems(rows, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface ignored, int which) {
                        switch (which) {
                            case ROW_EXPOSURE_TIME:
                                showExposureTime(activity, listener);
                                break;
                            case ROW_ISO:
                                showIso(activity, listener);
                                break;
                            case ROW_EV:
                                showEv(activity, listener);
                                break;
                            case ROW_WHITE_BALANCE:
                                showWhiteBalance(activity, listener);
                                break;
                            case ROW_AWB_LOCK:
                                showAwbLock(activity, listener);
                                break;
                            case ROW_STITCHING_DISTANCE:
                                showStitchingDistanceChoice(activity, listener);
                                break;
                            case ROW_RESET:
                                RecorderPreferences.resetPictureAdjustments(activity);
                                applyAndNotify(activity, listener, true);
                                break;
                            default:
                                break;
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface ignored) {
                UiChrome.apply(activity);
            }
        });
        dialog.show();
    }

    private static void showExposureTime(final Activity activity, final Listener listener) {
        if (!PictureAdjustments.isManualExposureAvailable()) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.picture_exposure_time)
                    .setMessage(R.string.picture_manual_exposure_details)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        final int[] values = new int[]{
                PiProEt.normal, PiProEt._15, PiProEt._50, PiProEt._100,
                PiProEt._500, PiProEt._1000, PiProEt._3200
        };
        String[] labels = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            labels[index] = PictureAdjustments.exposureTimeLabel(values[index]);
        }
        int checked = indexOf(values, RecorderPreferences.getExposureTime(activity));
        new AlertDialog.Builder(activity)
                .setTitle(R.string.picture_exposure_time)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int value = values[which];
                        RecorderPreferences.setExposureTime(activity, value);
                        if (value != PiProEt.normal
                                && !PictureAdjustments.isManualExposureIso(
                                RecorderPreferences.getIso(activity))) {
                            RecorderPreferences.setIso(activity, PiProIso._100);
                        }
                        dialog.dismiss();
                        applyAndNotify(activity, listener, false);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showIso(final Activity activity, final Listener listener) {
        boolean manual = RecorderPreferences.getExposureTime(activity) != PiProEt.normal
                && PictureAdjustments.isManualExposureAvailable();
        final int[] values = manual
                ? new int[]{PiProIso._100, PiProIso._200, PiProIso._400,
                PiProIso._600, PiProIso._800, PiProIso._1600, PiProIso._3200}
                : new int[]{PiProIso.auto, PiProIso._50, PiProIso._100,
                PiProIso._200, PiProIso._400, PiProIso._800, PiProIso._1600};
        String[] labels = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            labels[index] = PictureAdjustments.isoLabel(values[index]);
        }
        int checked = indexOf(values, RecorderPreferences.getIso(activity));
        new AlertDialog.Builder(activity)
                .setTitle(manual ? R.string.picture_iso_manual : R.string.picture_iso)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        RecorderPreferences.setIso(activity, values[which]);
                        dialog.dismiss();
                        applyAndNotify(activity, listener, false);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showEv(final Activity activity, final Listener listener) {
        final int[] values = new int[]{-4, -3, -2, -1, 0, 1, 2, 3, 4};
        String[] labels = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            labels[index] = PictureAdjustments.evLabel(values[index]);
        }
        int checked = indexOf(values, RecorderPreferences.getExposureCompensation(activity));
        new AlertDialog.Builder(activity)
                .setTitle(R.string.picture_ev)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        RecorderPreferences.setExposureCompensation(activity, values[which]);
                        dialog.dismiss();
                        applyAndNotify(activity, listener, false);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showWhiteBalance(final Activity activity, final Listener listener) {
        final String[] values = new String[]{
                PiProWb.auto, PiProWb.incandescent, PiProWb.fluorescent,
                PiProWb.daylight, PiProWb.cloudy_daylight
        };
        String[] labels = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            labels[index] = PictureAdjustments.whiteBalanceLabel(values[index]);
        }
        int checked = indexOf(values, RecorderPreferences.getWhiteBalance(activity));
        new AlertDialog.Builder(activity)
                .setTitle(R.string.picture_white_balance)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        RecorderPreferences.setWhiteBalance(activity, values[which]);
                        if (!PiProWb.auto.equals(values[which])) {
                            RecorderPreferences.setAutoWhiteBalanceLock(activity, false);
                        }
                        dialog.dismiss();
                        applyAndNotify(activity, listener, false);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showAwbLock(final Activity activity, final Listener listener) {
        final String[] labels = new String[]{
                activity.getString(R.string.picture_off),
                activity.getString(R.string.picture_on)
        };
        int checked = RecorderPreferences.getAutoWhiteBalanceLock(activity) ? 1 : 0;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.picture_awb_lock)
                .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean enabled = which == 1;
                        RecorderPreferences.setAutoWhiteBalanceLock(activity, enabled);
                        if (enabled) {
                            RecorderPreferences.setWhiteBalance(activity, PiProWb.auto);
                        }
                        dialog.dismiss();
                        applyAndNotify(activity, listener, false);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showStitchingDistanceChoice(
            final Activity activity, final Listener listener) {
        String[] choices = new String[]{
                activity.getString(R.string.picture_stitch_auto),
                activity.getString(R.string.picture_stitch_manual)
        };
        int checked = RecorderPreferences.getStitchingDistance(activity) < 0 ? 0 : 1;
        new AlertDialog.Builder(activity)
                .setTitle(R.string.picture_stitching_distance)
                .setSingleChoiceItems(choices, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        if (which == 0) {
                            RecorderPreferences.setStitchingDistance(activity, -1);
                            applyAndNotify(activity, listener, false);
                        } else {
                            showStitchingDistanceSlider(activity, listener);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showStitchingDistanceSlider(
            final Activity activity, final Listener listener) {
        int current = RecorderPreferences.getStitchingDistance(activity);
        if (current < -100 || current > 100) {
            current = 0;
        }

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(activity, 20);
        content.setPadding(padding, dp(activity, 8), padding, 0);

        final TextView valueText = new TextView(activity);
        valueText.setTextSize(18F);
        valueText.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(valueText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(activity);
        hint.setText(R.string.picture_stitch_slider_hint);
        hint.setPadding(0, dp(activity, 6), 0, dp(activity, 8));
        content.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final SeekBar seekBar = new SeekBar(activity);
        seekBar.setMax(200);
        seekBar.setProgress(current + 100);
        content.addView(seekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        updateStitchingValueText(valueText, current);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateStitchingValueText(valueText, progress - 100);
            }

            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.picture_stitch_manual)
                .setView(content)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface ignored, int which) {
                        RecorderPreferences.setStitchingDistance(
                                activity, seekBar.getProgress() - 100);
                        applyAndNotify(activity, listener, false);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
    }

    private static void updateStitchingValueText(TextView view, int value) {
        if (value <= -100) {
            view.setText("-100 • approximately 0.5 m");
        } else if (value == 0) {
            view.setText("0 • approximately 2 m");
        } else if (value >= 100) {
            view.setText("100 • infinity");
        } else {
            view.setText("Value " + value);
        }
    }

    private static void applyAndNotify(
            Activity activity, Listener listener, boolean reset) {
        try {
            PictureAdjustments.applySaved(activity);
            Toast.makeText(activity,
                    reset ? R.string.picture_defaults_restored
                            : R.string.picture_adjustments_applied,
                    Toast.LENGTH_SHORT).show();
        } catch (RuntimeException error) {
            Toast.makeText(activity,
                    activity.getString(R.string.picture_apply_failed) + ": "
                            + String.valueOf(error.getMessage()),
                    Toast.LENGTH_LONG).show();
        }
        if (listener != null) {
            listener.onPictureAdjustmentsChanged();
        }
    }

    private static int indexOf(int[] values, int value) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == value) {
                return index;
            }
        }
        return -1;
    }

    private static int indexOf(String[] values, String value) {
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(value)) {
                return index;
            }
        }
        return -1;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
