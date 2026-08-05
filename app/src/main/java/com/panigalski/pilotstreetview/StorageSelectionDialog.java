package com.panigalski.pilotstreetview;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

/**
 * In-place recording destination chooser.
 *
 * The Pilot panorama preview is backed by a vendor SurfaceView whose
 * surfaceDestroyed() callback synchronously releases native panorama and
 * four-camera resources. Opening a separate opaque Activity for a two-option
 * selector can therefore block or destabilize Pilot OS. This dialog keeps the
 * current Activity and preview Surface alive and performs no filesystem work.
 */
public final class StorageSelectionDialog {
    public interface Listener {
        void onSelected(String mode);
    }

    private StorageSelectionDialog() {
    }

    public static void show(final Activity activity, final Listener listener) {
        final String current = RecorderPreferences.getDestinationMode(activity);
        final String[] modes = new String[]{
                StorageResolver.MODE_INTERNAL,
                StorageResolver.MODE_EXTERNAL
        };
        final String[] labels = new String[]{
                activity.getString(R.string.destination_internal),
                activity.getString(R.string.destination_external)
        };
        int checked = StorageResolver.MODE_EXTERNAL.equals(current) ? 1 : 0;

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.destination_title)
                .setSingleChoiceItems(labels, checked,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface ignored, int which) {
                                String mode = modes[which];
                                RecorderPreferences.setDestinationMode(activity, mode);
                                if (listener != null) {
                                    listener.onSelected(mode);
                                }
                                ignored.dismiss();
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface ignored) {
                // Dialog windows can reveal system bars on Android 7.
                UiChrome.apply(activity);
            }
        });
        dialog.show();
    }
}
