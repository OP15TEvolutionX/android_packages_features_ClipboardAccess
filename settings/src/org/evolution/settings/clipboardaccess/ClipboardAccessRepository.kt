/* Copyright (C) 2026 Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */
package org.evolution.settings.clipboardaccess

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import java.text.Collator

internal data class ClipboardApp(
    val info: ApplicationInfo,
    val label: String,
    val blocked: Boolean,
) {
    val packageName: String get() = info.packageName
}

/** AppOps owns persistence, user scoping and enforcement in ClipboardService. */
internal class ClipboardAccessRepository(context: Context) {
    private val packageManager = context.packageManager
    private val appOps = context.getSystemService(AppOpsManager::class.java)!!

    fun loadApps(): List<ClipboardApp> {
        val collator = Collator.getInstance()
        return packageManager.getInstalledApplications(0)
            .map { info ->
                ClipboardApp(info, info.loadLabel(packageManager).toString(), isBlocked(info))
            }
            .sortedWith { first, second ->
                val result = collator.compare(first.label, second.label)
                if (result != 0) result else first.packageName.compareTo(second.packageName)
            }
    }

    fun setBlocked(app: ClipboardApp, blocked: Boolean): ClipboardApp {
        // Reject stale rows after uninstall/reinstall rather than changing a reused UID.
        val current = packageManager.getApplicationInfo(app.packageName, 0)
        check(current.uid == app.info.uid) { "Application UID changed" }
        appOps.setMode(
            AppOpsManager.OP_READ_CLIPBOARD,
            current.uid,
            current.packageName,
            if (blocked) AppOpsManager.MODE_IGNORED else AppOpsManager.MODE_ALLOWED,
        )
        val actual = isBlocked(current)
        // UID-wide/admin restrictions can override a package mode. Never show a false success.
        check(actual == blocked) { "Clipboard mode is overridden" }
        return app.copy(info = current, blocked = actual)
    }

    private fun isBlocked(info: ApplicationInfo): Boolean {
        val mode = appOps.unsafeCheckOpRawNoThrow(
            AppOpsManager.OP_READ_CLIPBOARD, info.uid, info.packageName,
        )
        return mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED
    }
}
