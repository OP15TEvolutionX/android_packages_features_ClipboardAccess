/* Copyright (C) 2026 Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */
package org.evolution.settings.clipboardaccess

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import android.os.UserHandle
import java.text.Collator

/** One installed app and whether it may read the clipboard. */
internal data class ClipboardApp(
    val info: ApplicationInfo,
    val label: String,
    val blocked: Boolean,
) {
    val packageName: String get() = info.packageName
    val isSystem: Boolean get() = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
}

/**
 * Reads and writes the READ_CLIPBOARD app op. AppOps stores the mode per user and
 * ClipboardService checks it, returning no clip to a blocked app; writing stays allowed.
 */
internal class ClipboardAccessRepository(context: Context) {
    private val packageManager = context.packageManager
    private val appOps = context.getSystemService(AppOpsManager::class.java)!!

    /** Apps a block can apply to, sorted by label. Call off the main thread. */
    fun loadApps(): List<ClipboardApp> {
        val collator = Collator.getInstance()
        return packageManager.getInstalledApplications(0)
            // AppOps always allows root and system, so a block could never apply.
            .filter {
                val appId = UserHandle.getAppId(it.uid)
                appId != Process.ROOT_UID && appId != Process.SYSTEM_UID
            }
            // Overlays and other resource-only packages run no code, so they can't read it.
            .filter { it.flags and ApplicationInfo.FLAG_HAS_CODE != 0 && !it.isResourceOverlay }
            .map { info ->
                ClipboardApp(info, info.loadLabel(packageManager).toString(), isBlocked(info))
            }
            .sortedWith { first, second ->
                val result = collator.compare(first.label, second.label)
                if (result != 0) result else first.packageName.compareTo(second.packageName)
            }
    }

    /** Sets the op and reads it back; throws if the new mode didn't take effect. */
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

    /** The package's own mode, ignoring the default; IGNORED and ERRORED both deny reading. */
    private fun isBlocked(info: ApplicationInfo): Boolean {
        val mode = appOps.unsafeCheckOpRawNoThrow(
            AppOpsManager.OP_READ_CLIPBOARD, info.uid, info.packageName,
        )
        return mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED
    }
}
