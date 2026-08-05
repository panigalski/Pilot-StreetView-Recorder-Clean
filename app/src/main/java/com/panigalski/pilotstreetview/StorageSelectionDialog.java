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
 * current Activity and preview Surface alive.
 *
 * External-storage availability is checked on a worker thread before the
 * choices are displayed. The check uses Android's mounted-volume metadata and
 * performs no directory creation, write test, free-space query, or media scan.
 */
public final class StorageSelectionDialog {
    public interface Listener {
        void onSelected(String mode);
    }

    private StorageSelectionDialog() {
    }

    public static void show(final Activity activity, final Listener listener) {
        final AlertDialog checkingDialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.destination_title)
                .setMessage(R.string.storage_detecting_external)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        checkingDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface ignored) {
                UiChrome.apply(activity);
            }
        });
        checkingDialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean externalConnected =
                        StorageResolver.isExternalStorageConnected(activity);

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.isFinishing() || activity.isDestroyed()) {
                            return;
                        }
                        if (!checkingDialog.isShowing()) {
                            return;
                        }
                        checkingDialog.dismiss();
                        showChoices(activity, listener, externalConnected);
                    }
                });
            }
        }, "storage-presence-check").start();
    }

    private static void showChoices(final Activity activity,
                                    final Listener listener,
                                    boolean externalConnected) {
        String current = RecorderPreferences.getDestinationMode(activity);

        // Never keep a stale External selection after the drive is removed.
        if (!externalConnected && StorageResolver.MODE_EXTERNAL.equals(current)) {
            current = StorageResolver.MODE_INTERNAL;
            RecorderPreferences.setDestinationMode(activity, current);
            if (listener != null) {
                listener.onSelected(current);
            }
        }

        final String[] modes;
        final String[] labels;
        final int checked;

        if (externalConnected) {
            modes = new String[]{
                    StorageResolver.MODE_INTERNAL,
                    StorageResolver.MODE_EXTERNAL
            };
            labels = new String[]{
                    activity.getString(R.string.destination_internal),
                    activity.getString(R.string.destination_external)
            };
            checked = StorageResolver.MODE_EXTERNAL.equals(current) ? 1 : 0;
        } else {
            modes = new String[]{StorageResolver.MODE_INTERNAL};
            labels = new String[]{activity.getString(R.string.destination_internal)};
            checked = 0;
        }

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
