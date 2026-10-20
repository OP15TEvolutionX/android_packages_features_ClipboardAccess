/* Copyright (C) 2026 Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */
package org.evolution.settings.clipboardaccess

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.android.settings.R
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity
import com.android.settingslib.widget.SettingsThemeHelper

class ClipboardAccessActivity : CollapsingToolbarBaseActivity() {
    companion object {
        private const val KEY_SHOW_ONLY_BLOCKED = "show_only_blocked"
        private const val MENU_FILTER = 1
    }

    private var refreshVersion by mutableIntStateOf(0)
    private var showOnlyBlocked by mutableStateOf(false)
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
        showOnlyBlocked = savedInstanceState?.getBoolean(KEY_SHOW_ONLY_BLOCKED) ?: false
        title = getString(R.string.clipboard_access_title)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        setContentView(ComposeView(this).apply {
            setContent { ClipboardAccessScreen(refreshVersion, showOnlyBlocked) }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SHOW_ONLY_BLOCKED, showOnlyBlocked)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_FILTER, Menu.NONE, R.string.clipboard_access_show_blocked)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(MENU_FILTER)?.setTitle(
            if (showOnlyBlocked) R.string.clipboard_access_show_all else R.string.clipboard_access_show_blocked
        )
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            MENU_FILTER -> {
                showOnlyBlocked = !showOnlyBlocked
                invalidateOptionsMenu()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNavigateUp(): Boolean {
        finish()
        return true
    }
}
