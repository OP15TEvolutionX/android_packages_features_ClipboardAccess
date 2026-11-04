/* Copyright (C) 2026 Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */
package org.evolution.clipreader;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.util.Log;

/**
 * Writes or reads the clipboard once the window has focus, logs the result and finishes.
 *
 * <p>am start -n org.evolution.clipreader/.ClipActivity --es write TEXT logs "WROTE TEXT";
 * without extras it logs "READ text=... has=... desc=..." with "null" for anything ignored, or
 * "READ denied" when the op is in deny mode and the service throws.
 */
public class ClipActivity extends Activity {
    private static final String TAG = "ClipReader";
    private boolean mDone;

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // ClipboardService only lets the focused app read, so wait for focus.
        if (!hasFocus || mDone) return;
        mDone = true;
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        String text = getIntent().getStringExtra("write");
        if (text != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("test", text));
            Log.i(TAG, "WROTE " + text);
        } else {
            try {
                logRead(clipboard);
            } catch (SecurityException e) {
                Log.i(TAG, "READ denied");
            }
        }
        finish();
    }

    private static void logRead(ClipboardManager clipboard) {
        ClipData clip = clipboard.getPrimaryClip();
        CharSequence read = clip == null ? null : clip.getItemAt(0).getText();
        ClipDescription description = clipboard.getPrimaryClipDescription();
        Log.i(TAG, "READ text=" + read + " has=" + clipboard.hasPrimaryClip()
                + " desc=" + (description == null ? null : description.getLabel()));
    }
}
