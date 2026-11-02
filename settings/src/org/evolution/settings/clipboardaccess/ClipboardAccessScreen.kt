/* Copyright (C) 2026 Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */
package org.evolution.settings.clipboardaccess

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.android.settings.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.evolution.settings.extrafeatures.ui.AppExplanation
import org.evolution.settings.extrafeatures.ui.AppFilterBar
import org.evolution.settings.extrafeatures.ui.AppRowContent
import org.evolution.settings.extrafeatures.ui.ExtraFeaturesCard
import org.evolution.settings.extrafeatures.ui.ExtraFeaturesTheme
import org.evolution.settings.extrafeatures.ui.rememberAppIcon
import org.evolution.settings.extrafeatures.ui.listItemShape

@Composable
internal fun ClipboardAccessScreen(refreshVersion: Int) = ExtraFeaturesTheme {
    val context = LocalContext.current
    val repository = remember(context) { ClipboardAccessRepository(context) }
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<ClipboardApp>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var onlySelected by rememberSaveable { mutableStateOf(false) }
    // null shows both kinds, otherwise only system (true) or third-party (false) apps.
    var systemFilter by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var retryVersion by remember { mutableStateOf(0) }
    // Serializes writes and keeps reloads from racing saves.
    val saveMutex = remember { Mutex() }
    val icons = remember { HashMap<String, ImageBitmap?>() }

    LaunchedEffect(repository, refreshVersion, retryVersion) {
        loading = true
        loadFailed = false
        try {
            apps = saveMutex.withLock { withContext(Dispatchers.IO) { repository.loadApps() } }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("ClipboardAccess", "Could not load applications", e)
            loadFailed = true
        } finally {
            loading = false
        }
    }

    val filtered = remember(apps, query, onlySelected, systemFilter) {
        val needle = query.trim()
        apps.filter {
            (!onlySelected || it.blocked) &&
                (systemFilter == null || it.isSystem == systemFilter) && (
                it.label.contains(needle, ignoreCase = true) ||
                    it.packageName.contains(needle, ignoreCase = true))
        }
    }

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item { AppExplanation(stringResource(R.string.clipboard_access_explanation)) }
        item {
            AppFilterBar(
                query = query,
                onQueryChange = { query = it },
                selectedCount = apps.count { it.blocked },
                onlySelected = onlySelected,
                onOnlySelectedChange = { onlySelected = it },
                systemFilter = systemFilter,
                onSystemFilterChange = { systemFilter = it },
            )
        }
        when {
            loading && apps.isEmpty() -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            loadFailed -> item {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.clipboard_access_load_error))
                    TextButton(onClick = { retryVersion++ }) {
                        Text(stringResource(R.string.clipboard_access_retry))
                    }
                }
            }
            filtered.isEmpty() -> item {
                Text(stringResource(if (onlySelected && query.isBlank())
                    R.string.clipboard_access_no_blocked_apps else R.string.clipboard_access_no_apps),
                    modifier = Modifier.padding(24.dp))
            }
            else -> itemsIndexed(filtered, key = { _, it -> it.packageName }) { index, app ->
                ClipboardAppRow(app, icons, Modifier.animateItem(), listItemShape(index, filtered.size)) { value ->
                    // Update optimistically so only this row recomposes; revert on failure.
                    apps = apps.map {
                        if (it.packageName == app.packageName) it.copy(blocked = value) else it
                    }
                    scope.launch {
                        try {
                            saveMutex.withLock {
                                withContext(Dispatchers.IO) { repository.setBlocked(app, value) }
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.e("ClipboardAccess", "Could not save app state", e)
                            apps = apps.map {
                                if (it.packageName == app.packageName) it.copy(blocked = app.blocked) else it
                            }
                            Toast.makeText(context, R.string.clipboard_access_save_error,
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardAppRow(
    app: ClipboardApp,
    icons: MutableMap<String, ImageBitmap?>,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(4.dp),
    onChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val icon = rememberAppIcon(app.packageName, icons) {
        app.info.loadIcon(context.packageManager).toBitmap(96, 96).asImageBitmap()
    }
    val description = stringResource(R.string.clipboard_access_block_app, app.label)
    ExtraFeaturesCard(modifier, shape) {
        AppRowContent(
            icon = icon,
            label = app.label,
            summary = app.packageName,
            modifier = Modifier
                .toggleable(app.blocked, role = Role.Checkbox, onValueChange = onChange)
                .semantics { contentDescription = description }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // The whole row owns the semantics and click target.
            Checkbox(checked = app.blocked, onCheckedChange = null)
        }
    }
}
