package com.panigalski.pilotstreetview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

/**
 * In-place Street View settings panel.
 *
 * Keeping settings in the current Activity avoids destroying the vendor
 * panorama SurfaceView. No camera, mount, directory, or free-space operation
 * is performed while this dialog is open.
 */
public final class SettingsDialog {
    public interface Listener {
        void onDestinationChanged(String mode);
        void onPictureAdjustmentsChanged();
    }

    private SettingsDialog() {
    }

    public static void show(final Activity activity, final Listener listener) {
        final String destination = StorageResolver.MODE_EXTERNAL.equals(
                RecorderPreferences.getDestinationMode(activity))
                ? activity.getString(R.string.destination_external)
                : activity.getString(R.string.destination_internal);

        final String[] rows = new String[]{
                activity.getString(R.string.settings_frame_rate) + "\n"
                        + activity.getString(R.string.settings_frame_rate_value),
                activity.getString(R.string.settings_bitrate) + "\n"
                        + activity.getString(R.string.settings_bitrate_value),
                activity.getString(R.string.settings_countdown) + "\n"
                        + activity.getString(R.string.settings_countdown_value),
                activity.getString(R.string.settings_fragment) + "\n"
                        + activity.getString(R.string.settings_fragment_value),
                activity.getString(R.string.settings_picture_adjustments) + "\n"
                        + PictureAdjustments.compactSummary(activity),
                activity.getString(R.string.settings_destination) + "\n" + destination
        };

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.settings_title)
                .setItems(rows, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface ignored, int which) {
                        if (which == 4) {
                            PictureAdjustmentsDialog.show(activity,
                                    new PictureAdjustmentsDialog.Listener() {
                                        @Override
                                        public void onPictureAdjustmentsChanged() {
                                            if (listener != null) {
                                                listener.onPictureAdjustmentsChanged();
                                            }
                                        }
                                    });
                            return;
                        }
                        if (which == 5) {
                            StorageSelectionDialog.show(activity,
                                    new StorageSelectionDialog.Listener() {
                                        @Override
                                        public void onSelected(String mode) {
                                            if (listener != null) {
                                                listener.onDestinationChanged(mode);
                                            }
                                        }
                                    });
                            return;
                        }
                        new AlertDialog.Builder(activity)
                                .setTitle(rows[which])
                                .setMessage(R.string.settings_fixed_message)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
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
}
