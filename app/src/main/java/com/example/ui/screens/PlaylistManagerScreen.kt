package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.model.PlaylistStatus
import com.example.core.model.PlaylistType
import com.example.core.model.UserPreferencesState
import com.example.data.local.entities.PlaylistEntity
import com.example.ui.components.EmptyPlaylistHeroCard
import com.example.ui.components.ParentalPinDialog
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalMehdiColors
import com.example.ui.theme.ModernPurple
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TurquoiseAccent
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PlaylistDialogMode {
    XTREAM,
    M3U_URL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistManagerScreen(
    playlists: List<PlaylistEntity>,
    preferences: UserPreferencesState,
    isRefreshing: Boolean,
    decodeHeaders: (PlaylistEntity) -> String,
    onVerifyPin: suspend (String) -> Boolean,
    onSaveXtream: (
        existingId: Long?,
        name: String,
        serverUrl: String,
        port: String,
        username: String,
        password: String,
        customUa: String,
        useCustomUa: Boolean,
        headersText: String,
        onDone: (Result<Long>) -> Unit
    ) -> Unit,
    onSaveM3uUrl: (
        existingId: Long?,
        name: String,
        m3uUrl: String,
        epgUrl: String,
        customUa: String,
        useCustomUa: Boolean,
        headersText: String,
        onDone: (Result<Long>) -> Unit
    ) -> Unit,
    onImportLocalM3u: (name: String, uri: Uri, onDone: (Result<Long>) -> Unit) -> Unit,
    onActivatePlaylist: (Long) -> Unit,
    onSetDefaultPlaylist: (Long) -> Unit,
    onRefreshPlaylist: (Long) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onOpenQrPairing: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val mehdiColors = LocalMehdiColors.current
    val scope = rememberCoroutineScope()

    var isUnlocked by remember(preferences.hasParentalPin, preferences.lockPlaylistEdits) {
        mutableStateOf(!(preferences.hasParentalPin && preferences.lockPlaylistEdits))
    }
    var pendingPinAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    var dialogMode by remember { mutableStateOf<PlaylistDialogMode?>(null) }
    var editingPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var deletingPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Local M3U"
            onImportLocalM3u(fileName, uri) {}
        }
    }

    fun requireModificationUnlocked(action: () -> Unit) {
        if (isUnlocked) {
            action()
        } else {
            pinError = null
            pendingPinAction = action
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.playlists_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Action buttons row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                requireModificationUnlocked {
                                    editingPlaylist = null
                                    dialogMode = PlaylistDialogMode.XTREAM
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("add_xtream_btn")
                        ) {
                            Icon(Icons.Filled.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.playlist_add_xtream), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = {
                                requireModificationUnlocked {
                                    editingPlaylist = null
                                    dialogMode = PlaylistDialogMode.M3U_URL
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("add_m3u_url_btn")
                        ) {
                            Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.playlist_add_m3u_url), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            requireModificationUnlocked {
                                openDocLauncher.launch(
                                    arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain", "*/*")
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("import_local_m3u_btn")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.playlist_add_m3u_file))
                    }
                }
            }

            if (playlists.isEmpty()) {
                item {
                    EmptyPlaylistHeroCard(
                        onAddPlaylistClick = {
                            requireModificationUnlocked {
                                editingPlaylist = null
                                dialogMode = PlaylistDialogMode.XTREAM
                            }
                        },
                        onQrPairingClick = onOpenQrPairing
                    )
                }
            } else {
                items(playlists, key = { it.id }) { pl ->
                    PlaylistRowCard(
                        playlist = pl,
                        isRefreshing = isRefreshing && pl.status == PlaylistStatus.SYNCING.name,
                        onActivate = { onActivatePlaylist(pl.id) },
                        onSetDefault = { onSetDefaultPlaylist(pl.id) },
                        onRefresh = { onRefreshPlaylist(pl.id) },
                        onEdit = {
                            requireModificationUnlocked {
                                editingPlaylist = pl
                                dialogMode = if (pl.type == PlaylistType.XTREAM.name) {
                                    PlaylistDialogMode.XTREAM
                                } else {
                                    PlaylistDialogMode.M3U_URL
                                }
                            }
                        },
                        onDelete = {
                            requireModificationUnlocked { deletingPlaylist = pl }
                        }
                    )
                }
            }
        }
    }

    if (dialogMode != null) {
        AddOrEditPlaylistDialog(
            mode = dialogMode!!,
            existing = editingPlaylist,
            initialHeaders = editingPlaylist?.let(decodeHeaders).orEmpty(),
            onDismiss = {
                dialogMode = null
                editingPlaylist = null
            },
            onSaveXtream = { name, url, port, user, pass, ua, useUa, headers ->
                onSaveXtream(editingPlaylist?.id, name, url, port, user, pass, ua, useUa, headers) { res ->
                    if (res.isSuccess) {
                        dialogMode = null
                        editingPlaylist = null
                    }
                }
            },
            onSaveM3uUrl = { name, m3uUrl, epgUrl, ua, useUa, headers ->
                onSaveM3uUrl(editingPlaylist?.id, name, m3uUrl, epgUrl, ua, useUa, headers) { res ->
                    if (res.isSuccess) {
                        dialogMode = null
                        editingPlaylist = null
                    }
                }
            }
        )
    }

    deletingPlaylist?.let { pl ->
        AlertDialog(
            onDismissRequest = { deletingPlaylist = null },
            title = { Text(stringResource(R.string.playlist_confirm_delete_title)) },
            text = { Text(stringResource(R.string.playlist_confirm_delete_msg, pl.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePlaylist(pl.id)
                        deletingPlaylist = null
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPlaylist = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (pendingPinAction != null) {
        val incorrectText = stringResource(R.string.pin_incorrect)
        ParentalPinDialog(
            title = stringResource(R.string.pin_enter_title),
            isCreation = false,
            errorText = pinError,
            onDismiss = { pendingPinAction = null },
            onSubmitPin = { entered ->
                scope.launch {
                    if (onVerifyPin(entered)) {
                        isUnlocked = true
                        val act = pendingPinAction
                        pendingPinAction = null
                        act?.invoke()
                    } else {
                        pinError = incorrectText
                    }
                }
            }
        )
    }
}

@Composable
private fun PlaylistRowCard(
    playlist: PlaylistEntity,
    isRefreshing: Boolean,
    onActivate: () -> Unit,
    onSetDefault: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val mehdiColors = LocalMehdiColors.current
    val statusColor = when (playlist.status) {
        PlaylistStatus.READY.name -> StatusSuccess
        PlaylistStatus.SYNCING.name -> ModernPurple
        else -> StatusError
    }
    val statusLabel = when (playlist.status) {
        PlaylistStatus.READY.name -> stringResource(R.string.playlist_status_ready)
        PlaylistStatus.SYNCING.name -> stringResource(R.string.playlist_status_syncing)
        else -> stringResource(R.string.playlist_status_error)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = mehdiColors.cardSurface,
        border = BorderStroke(
            width = if (playlist.isActive) 1.5.dp else 1.dp,
            color = if (playlist.isActive) mehdiColors.purpleBrand else mehdiColors.subtleBorder
        ),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (playlist.isActive) {
                            StatusPill(stringResource(R.string.playlist_active_badge), ModernPurple)
                        }
                        if (playlist.isDefault) {
                            StatusPill(stringResource(R.string.playlist_default_badge), TurquoiseAccent)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val syncedAt = playlist.lastRefreshedAtMs?.let {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
                    } ?: stringResource(R.string.home_never_refreshed)
                    Text(
                        text = "${playlist.type} • $syncedAt",
                        style = MaterialTheme.typography.bodySmall,
                        color = mehdiColors.secondaryText
                    )
                }
                StatusPill(label = statusLabel, color = statusColor)
            }

            if (!playlist.lastError.isNullOrBlank()) {
                Text(
                    text = playlist.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusError
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!playlist.isActive) {
                    Button(
                        onClick = onActivate,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.playlist_set_active))
                    }
                }
                if (!playlist.isDefault) {
                    OutlinedButton(
                        onClick = onSetDefault,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.playlist_set_default))
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                }
                if (playlist.type != PlaylistType.M3U_LOCAL.name) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = StatusError
                    )
                }
            }
        }
    }
}

@Composable
private fun AddOrEditPlaylistDialog(
    mode: PlaylistDialogMode,
    existing: PlaylistEntity?,
    initialHeaders: String,
    onDismiss: () -> Unit,
    onSaveXtream: (
        name: String,
        serverUrl: String,
        port: String,
        username: String,
        password: String,
        customUa: String,
        useCustomUa: Boolean,
        headersText: String
    ) -> Unit,
    onSaveM3uUrl: (
        name: String,
        m3uUrl: String,
        epgUrl: String,
        customUa: String,
        useCustomUa: Boolean,
        headersText: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var serverUrl by remember { mutableStateOf(existing?.serverUrl.orEmpty()) }
    var port by remember { mutableStateOf(existing?.port.orEmpty()) }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf(existing?.m3uUrl.orEmpty()) }
    var epgUrl by remember { mutableStateOf(existing?.epgUrl.orEmpty()) }
    var customUa by remember { mutableStateOf(existing?.customUserAgent.orEmpty()) }
    var useCustomUa by remember { mutableStateOf(existing?.useCustomUserAgent ?: false) }
    var headersText by remember { mutableStateOf(initialHeaders) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }

    fun submitConfirmed() {
        if (mode == PlaylistDialogMode.XTREAM) {
            onSaveXtream(name, serverUrl, port, username, password, customUa, useCustomUa, headersText)
        } else {
            onSaveM3uUrl(name, m3uUrl, epgUrl, customUa, useCustomUa, headersText)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (mode == PlaylistDialogMode.XTREAM) {
                    stringResource(R.string.playlist_add_xtream)
                } else {
                    stringResource(R.string.playlist_add_m3u_url)
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.playlist_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("playlist_name_input")
                )

                if (mode == PlaylistDialogMode.XTREAM) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text(stringResource(R.string.playlist_field_server_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("playlist_server_input")
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text(stringResource(R.string.playlist_field_port)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.playlist_field_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("playlist_username_input")
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.playlist_field_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("playlist_password_input")
                    )
                } else {
                    OutlinedTextField(
                        value = m3uUrl,
                        onValueChange = { m3uUrl = it },
                        label = { Text(stringResource(R.string.playlist_field_m3u_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("playlist_m3u_url_input")
                    )
                    OutlinedTextField(
                        value = epgUrl,
                        onValueChange = { epgUrl = it },
                        label = { Text(stringResource(R.string.playlist_field_epg_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useCustomUa,
                        onCheckedChange = { useCustomUa = it }
                    )
                    Text(
                        text = stringResource(R.string.playlist_use_custom_ua),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (useCustomUa) {
                    OutlinedTextField(
                        value = customUa,
                        onValueChange = { customUa = it },
                        label = { Text(stringResource(R.string.playlist_field_user_agent)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = headersText,
                    onValueChange = { headersText = it },
                    label = { Text(stringResource(R.string.playlist_field_headers)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (existing != null) {
                        showOverwriteConfirm = true
                    } else {
                        submitConfirmed()
                    }
                },
                enabled = name.isNotBlank() && if (mode == PlaylistDialogMode.XTREAM) {
                    serverUrl.isNotBlank() && username.isNotBlank() && (password.isNotBlank() || existing != null)
                } else {
                    m3uUrl.isNotBlank()
                },
                modifier = Modifier.testTag("playlist_save_btn")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )

    if (showOverwriteConfirm && existing != null) {
        AlertDialog(
            onDismissRequest = { showOverwriteConfirm = false },
            title = { Text(stringResource(R.string.playlist_confirm_overwrite_title)) },
            text = { Text(stringResource(R.string.playlist_confirm_overwrite_msg, existing.name)) },
            confirmButton = {
                Button(onClick = {
                    showOverwriteConfirm = false
                    submitConfirmed()
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
