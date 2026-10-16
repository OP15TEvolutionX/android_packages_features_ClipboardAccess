/* Copyright (C) 2026 Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */
package org.evolution.settings.clipboardaccess

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.MenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.android.settings.R
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.widget.SettingsThemeHelper

class ClipboardAccessActivity : CollapsingToolbarBaseActivity() {
    private var refreshVersion by mutableIntStateOf(0)
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshVersion++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(
            if (SettingsThemeHelper.isExpressiveTheme(this)) {
                R.style.Theme_SubSettings_Expressive
            } else {
                R.style.Theme_SubSettings
            }
        )
        super.onCreate(savedInstanceState)
        title = getString(R.string.clipboard_access_title)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        setContentView(ComposeView(this).apply {
            setContent { ClipboardAccessScreen(refreshVersion) }
        })
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onResume() {
        super.onResume()
        refreshVersion++
    }

    override fun onStop() {
        unregisterReceiver(packageReceiver)
        super.onStop()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNavigateUp(): Boolean {
        finish()
        return true
    }
}
