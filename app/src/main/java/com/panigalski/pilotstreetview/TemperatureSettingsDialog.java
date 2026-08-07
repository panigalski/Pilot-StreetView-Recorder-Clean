package com.panigalski.pilotstreetview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** In-place temperature warning threshold editor for the Pilot One screen. */
public final class TemperatureSettingsDialog {
    public interface Listener {
        void onTemperatureThresholdChanged(int thresholdCelsius);
    }

    private TemperatureSettingsDialog() { }

    public static void show(final Activity activity, final Listener listener) {
        final int min = RecorderPreferences.MIN_TEMPERATURE_SOUND_THRESHOLD_C;
        final int max = RecorderPreferences.MAX_TEMPERATURE_SOUND_THRESHOLD_C;
        final int initial = RecorderPreferences.getTemperatureSoundThreshold(activity);
        final int[] selected = new int[]{initial};

        int padding = dp(activity, 20);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, dp(activity, 10), padding, 0);

        final TextView value = new TextView(activity);
        value.setText(activity.getString(R.string.temperature_sound_threshold_value, initial));
        value.setTextSize(24F);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setGravity(android.view.Gravity.CENTER);
        content.addView(value, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(activity);
        hint.setText(R.string.temperature_sound_threshold_hint);
        hint.setTextSize(14F);
        hint.setPadding(0, dp(activity, 8), 0, dp(activity, 8));
        content.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final SeekBar seekBar = new SeekBar(activity);
        seekBar.setMax(max - min);
        seekBar.setProgress(initial - min);
        content.addView(seekBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView range = new TextView(activity);
        range.setText(activity.getString(R.string.temperature_sound_threshold_range, min, max));
        range.setGravity(android.view.Gravity.CENTER);
        range.setTextSize(12F);
        content.addView(range, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                selected[0] = min + progress;
                value.setText(activity.getString(
                        R.string.temperature_sound_threshold_value, selected[0]));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.temperature_sound_settings_title)
                .setView(content)
                .setPositiveButton(R.string.temperature_sound_save,
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface ignored, int which) {
                                RecorderPreferences.setTemperatureSoundThreshold(activity, selected[0]);
                                if (listener != null) {
                                    listener.onTemperatureThresholdChanged(selected[0]);
                                }
                            }
                        })
                .setNeutralButton(R.string.temperature_sound_test, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(
                        new View.OnClickListener() {
                            @Override public void onClick(View view) {
                                TemperatureAlarmSound.play();
                            }
                        });
            }
        });
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface ignored) {
                UiChrome.apply(activity);
            }
        });
        dialog.show();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
