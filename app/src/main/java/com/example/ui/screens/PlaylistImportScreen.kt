package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.model.PlaylistImportPreview
import com.example.core.model.PlaylistType
import com.example.core.model.UserPreferencesState
import com.example.data.local.entities.PlaylistEntity
import com.example.ui.components.ParentalPinDialog
import com.example.ui.components.StatusPill
import com.example.ui.theme.LocalMehdiColors
import com.example.ui.theme.ModernPurple
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TurquoiseAccent
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private enum class ImportSourceTab {
    LOCAL_M3U,
    M3U_URL,
    XTREAM
}

private data class SavedImportSummary(
    val playlistId: Long,
    val name: String,
    val format: PlaylistType,
    val channelCount: Int,
    val movieCount: Int,
    val seriesCount: Int,
    val radioCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportScreen(
    preferences: UserPreferencesState,
    isRefreshing: Boolean,
    hasExistingPlaylists: Boolean,
    onFindPlaylistByName: suspend (String) -> PlaylistEntity?,
    onVerifyPin: suspend (String) -> Boolean,
    onPreviewLocalM3u: (name: String, uri: Uri, onResult: (Result<PlaylistImportPreview>) -> Unit) -> Unit,
    onSaveConfirmedLocalM3u: (
        existingId: Long?,
        preview: PlaylistImportPreview,
        epgUrl: String,
        customUa: String,
        useCustomUa: Boolean,
        activateNow: Boolean,
        onDone: (Result<Long>) -> Unit
    ) -> Unit,
    onTestM3uUrl: (
        name: String,
        m3uUrl: String,
        epgUrl: String,
        customUa: String,
        useCustomUa: Boolean,
        onResult: (Result<PlaylistImportPreview>) -> Unit
    ) -> Unit,
    onSaveM3uUrl: (
        existingId: Long?,
        name: String,
        m3uUrl: String,
        epgUrl: String,
        customUa: String,
        useCustomUa: Boolean,
        headersText: String,
        activateNow: Boolean,
        onDone: (Result<Long>) -> Unit
    ) -> Unit,
    onTestXtream: (
        name: String,
        serverUrl: String,
        port: String,
        username: String,
        password: String,
        customUa: String,
        useCustomUa: Boolean,
        onResult: (Result<PlaylistImportPreview>) -> Unit
    ) -> Unit,
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
        activateNow: Boolean,
        onDone: (Result<Long>) -> Unit
    ) -> Unit,
    onActivatePlaylist: (Long) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val mehdiColors = LocalMehdiColors.current
    val scope = rememberCoroutineScope()

    var isUnlocked by remember(preferences.hasParentalPin, preferences.lockPlaylistEdits) {
        mutableStateOf(!(preferences.hasParentalPin && preferences.lockPlaylistEdits))
    }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }

    var activeTab by remember { mutableStateOf(ImportSourceTab.LOCAL_M3U) }
    var isTesting by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var previewState by remember { mutableStateOf<PlaylistImportPreview?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var savedSummary by remember { mutableStateOf<SavedImportSummary?>(null) }
    var overwriteConfirmAction by remember { mutableStateOf<Pair<String, (Long) -> Unit>?>(null) }

    // Option A: Local M3U fields
    var localName by remember { mutableStateOf("") }
    var localEpgUrl by remember { mutableStateOf("") }
    var localCustomUa by remember { mutableStateOf("") }
    var localUseCustomUa by remember { mutableStateOf(false) }

    // Option B: M3U URL fields
    var urlName by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    var urlEpg by remember { mutableStateOf("") }
    var urlCustomUa by remember { mutableStateOf("") }
    var urlUseCustomUa by remember { mutableStateOf(false) }

    // Option C: Xtream fields
    var xtreamName by remember { mutableStateOf("") }
    var xtreamServer by remember { mutableStateOf("") }
    var xtreamPort by remember { mutableStateOf("") }
    var xtreamUsername by remember { mutableStateOf("") }
    var xtreamPassword by remember { mutableStateOf("") }
    var xtreamCustomUa by remember { mutableStateOf("") }
    var xtreamUseCustomUa by remember { mutableStateOf(false) }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            errorMessage = null
            previewState = null
            isTesting = true
            val suggestedName = localName.trim().ifBlank {
                uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                    ?.takeIf { it.isNotBlank() } ?: "Local M3U"
            }
            if (localName.isBlank()) localName = suggestedName
            onPreviewLocalM3u(suggestedName, uri) { res ->
                isTesting = false
                res.onSuccess { preview ->
                    previewState = preview
                    if (localEpgUrl.isBlank() && !preview.discoveredEpgUrl.isNullOrBlank()) {
                        localEpgUrl = preview.discoveredEpgUrl
                    }
                }.onFailure { err ->
                    errorMessage = err.message
                }
            }
        }
    }

    fun resetForAnother() {
        savedSummary = null
        previewState = null
        errorMessage = null
        localName = ""
        localEpgUrl = ""
        urlName = ""
        m3uUrl = ""
        urlEpg = ""
        xtreamName = ""
        xtreamServer = ""
        xtreamPort = ""
        xtreamUsername = ""
        xtreamPassword = ""
    }

    fun checkDuplicateAndExecute(
        desiredName: String,
        onProceed: (existingId: Long?) -> Unit
    ) {
        scope.launch {
            val conflict = onFindPlaylistByName(desiredName)
            if (conflict != null) {
                overwriteConfirmAction = conflict.name to { existingId ->
                    onProceed(existingId)
                }
            } else {
                onProceed(null)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_screen_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("import_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (!isUnlocked) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = mehdiColors.purpleBrand,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_lock_playlists),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        pinError = null
                        showPinDialog = true
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(stringResource(R.string.pin_enter_title))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val summary = savedSummary
                    if (summary != null) {
                        ImportConfirmationCard(
                            summary = summary,
                            onActivateNow = {
                                onActivatePlaylist(summary.playlistId)
                                onBack()
                            },
                            onAddAnother = ::resetForAnother,
                            onDone = onBack
                        )
                    } else {
                        // Source selector chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = activeTab == ImportSourceTab.LOCAL_M3U,
                                onClick = {
                                    activeTab = ImportSourceTab.LOCAL_M3U
                                    previewState = null
                                    errorMessage = null
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = {
                                    Text(
                                        stringResource(R.string.import_tab_local_m3u),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_tab_local")
                            )
                            FilterChip(
                                selected = activeTab == ImportSourceTab.M3U_URL,
                                onClick = {
                                    activeTab = ImportSourceTab.M3U_URL
                                    previewState = null
                                    errorMessage = null
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = {
                                    Text(
                                        stringResource(R.string.import_tab_m3u_url),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_tab_url")
                            )
                            FilterChip(
                                selected = activeTab == ImportSourceTab.XTREAM,
                                onClick = {
                                    activeTab = ImportSourceTab.XTREAM
                                    previewState = null
                                    errorMessage = null
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Dns, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = {
                                    Text(
                                        stringResource(R.string.import_tab_xtream),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_tab_xtream")
                            )
                        }

                        // Form card for active tab
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = mehdiColors.cardSurface,
                            border = BorderStroke(1.dp, mehdiColors.subtleBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                when (activeTab) {
                                    ImportSourceTab.LOCAL_M3U -> {
                                        OutlinedTextField(
                                            value = localName,
                                            onValueChange = { localName = it },
                                            label = { Text(stringResource(R.string.playlist_field_name)) },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("import_local_name_field")
                                        )
                                        OutlinedTextField(
                                            value = localEpgUrl,
                                            onValueChange = { localEpgUrl = it },
                                            label = { Text(stringResource(R.string.playlist_field_epg_url)) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = localUseCustomUa,
                                                onCheckedChange = { localUseCustomUa = it }
                                            )
                                            Text(stringResource(R.string.playlist_use_custom_ua))
                                        }
                                        if (localUseCustomUa) {
                                            OutlinedTextField(
                                                value = localCustomUa,
                                                onValueChange = { localCustomUa = it },
                                                label = { Text(stringResource(R.string.playlist_field_user_agent)) },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                openDocLauncher.launch(
                                                    arrayOf(
                                                        "audio/x-mpegurl",
                                                        "application/vnd.apple.mpegurl",
                                                        "text/plain",
                                                        "*/*"
                                                    )
                                                )
                                            },
                                            enabled = !isTesting && !isSaving,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .minimumInteractiveComponentSize()
                                                .testTag("import_pick_m3u_file_btn")
                                        ) {
                                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(R.string.import_pick_file))
                                        }
                                    }

                                    ImportSourceTab.M3U_URL -> {
                                        OutlinedTextField(
                                            value = urlName,
                                            onValueChange = { urlName = it },
                                            label = { Text(stringResource(R.string.playlist_field_name)) },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("import_url_name_field")
                                        )
                                        OutlinedTextField(
                                            value = m3uUrl,
                                            onValueChange = {
                                                m3uUrl = it
                                                previewState = null
                                            },
                                            label = { Text(stringResource(R.string.playlist_field_m3u_url)) },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("import_url_field")
                                        )
                                        OutlinedTextField(
                                            value = urlEpg,
                                            onValueChange = { urlEpg = it },
                                            label = { Text(stringResource(R.string.playlist_field_epg_url)) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = urlUseCustomUa,
                                                onCheckedChange = { urlUseCustomUa = it }
                                            )
                                            Text(stringResource(R.string.playlist_use_custom_ua))
                                        }
                                        if (urlUseCustomUa) {
                                            OutlinedTextField(
                                                value = urlCustomUa,
                                                onValueChange = { urlCustomUa = it },
                                                label = { Text(stringResource(R.string.playlist_field_user_agent)) },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                errorMessage = null
                                                previewState = null
                                                isTesting = true
                                                onTestM3uUrl(
                                                    urlName.ifBlank { "M3U Playlist" },
                                                    m3uUrl,
                                                    urlEpg,
                                                    urlCustomUa,
                                                    urlUseCustomUa
                                                ) { res ->
                                                    isTesting = false
                                                    res.onSuccess { previewState = it }
                                                        .onFailure { errorMessage = it.message }
                                                }
                                            },
                                            enabled = !isTesting && !isSaving && m3uUrl.isNotBlank(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .minimumInteractiveComponentSize()
                                                .testTag("import_test_m3u_url_btn")
                                        ) {
                                            Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(if (isTesting) R.string.import_testing else R.string.import_test_connection))
                                        }
                                    }

                                    ImportSourceTab.XTREAM -> {
                                        OutlinedTextField(
                                            value = xtreamName,
                                            onValueChange = { xtreamName = it },
                                            label = { Text(stringResource(R.string.playlist_field_name)) },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("import_xtream_name_field")
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = xtreamServer,
                                                onValueChange = {
                                                    xtreamServer = it
                                                    previewState = null
                                                },
                                                label = { Text(stringResource(R.string.playlist_field_server_url)) },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .weight(1.7f)
                                                    .testTag("import_xtream_server_field")
                                            )
                                            OutlinedTextField(
                                                value = xtreamPort,
                                                onValueChange = {
                                                    xtreamPort = it
                                                    previewState = null
                                                },
                                                label = { Text(stringResource(R.string.playlist_field_port)) },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("import_xtream_port_field")
                                            )
                                        }
                                        OutlinedTextField(
                                            value = xtreamUsername,
                                            onValueChange = {
                                                xtreamUsername = it
                                                previewState = null
                                            },
                                            label = { Text(stringResource(R.string.playlist_field_username)) },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("import_xtream_user_field")
                                        )
                                        OutlinedTextField(
                                            value = xtreamPassword,
                                            onValueChange = {
                                                xtreamPassword = it
                                                previewState = null
                                            },
                                            label = { Text(stringResource(R.string.playlist_field_password)) },
                                            singleLine = true,
                                            visualTransformation = PasswordVisualTransformation(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("import_xtream_pass_field")
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = xtreamUseCustomUa,
                                                onCheckedChange = { xtreamUseCustomUa = it }
                                            )
                                            Text(stringResource(R.string.playlist_use_custom_ua))
                                        }
                                        if (xtreamUseCustomUa) {
                                            OutlinedTextField(
                                                value = xtreamCustomUa,
                                                onValueChange = { xtreamCustomUa = it },
                                                label = { Text(stringResource(R.string.playlist_field_user_agent)) },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                errorMessage = null
                                                previewState = null
                                                isTesting = true
                                                onTestXtream(
                                                    xtreamName.ifBlank { "Xtream IPTV" },
                                                    xtreamServer,
                                                    xtreamPort,
                                                    xtreamUsername,
                                                    xtreamPassword,
                                                    xtreamCustomUa,
                                                    xtreamUseCustomUa
                                                ) { res ->
                                                    isTesting = false
                                                    res.onSuccess { previewState = it }
                                                        .onFailure { errorMessage = it.message }
                                                }
                                            },
                                            enabled = !isTesting && !isSaving &&
                                                xtreamServer.isNotBlank() &&
                                                xtreamUsername.isNotBlank() &&
                                                xtreamPassword.isNotBlank(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .minimumInteractiveComponentSize()
                                                .testTag("import_test_xtream_btn")
                                        ) {
                                            Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(if (isTesting) R.string.import_testing else R.string.import_test_connection))
                                        }
                                    }
                                }

                                if (isTesting || isSaving || isRefreshing) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text(
                                            text = stringResource(if (isTesting) R.string.import_testing else R.string.loading),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = mehdiColors.secondaryText
                                        )
                                    }
                                }

                                if (!errorMessage.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = StatusError.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, StatusError.copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            text = errorMessage!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = StatusError,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Preview Card (shown after local file parse or connection test)
                        previewState?.let { preview ->
                            ImportPreviewCard(
                                preview = preview,
                                overrideName = when (activeTab) {
                                    ImportSourceTab.LOCAL_M3U -> localName.ifBlank { preview.name }
                                    ImportSourceTab.M3U_URL -> urlName.ifBlank { preview.name }
                                    ImportSourceTab.XTREAM -> xtreamName.ifBlank { preview.name }
                                }
                            )
                        }

                        // Confirm & Save CTA
                        val canConfirmSave = when (activeTab) {
                            ImportSourceTab.LOCAL_M3U -> previewState != null && previewState?.format == PlaylistType.M3U_LOCAL
                            ImportSourceTab.M3U_URL -> m3uUrl.isNotBlank()
                            ImportSourceTab.XTREAM -> xtreamServer.isNotBlank() && xtreamUsername.isNotBlank() && xtreamPassword.isNotBlank()
                        }

                        Button(
                            onClick = {
                                errorMessage = null
                                when (activeTab) {
                                    ImportSourceTab.LOCAL_M3U -> {
                                        val p = previewState ?: return@Button
                                        val finalName = localName.trim().ifBlank { p.name }
                                        val finalPreview = p.copy(name = finalName)
                                        checkDuplicateAndExecute(finalName) { existingId ->
                                            isSaving = true
                                            onSaveConfirmedLocalM3u(
                                                existingId,
                                                finalPreview,
                                                localEpgUrl,
                                                localCustomUa,
                                                localUseCustomUa,
                                                !hasExistingPlaylists
                                            ) { res ->
                                                isSaving = false
                                                res.onSuccess { savedId ->
                                                    savedSummary = SavedImportSummary(
                                                        playlistId = savedId,
                                                        name = finalName,
                                                        format = PlaylistType.M3U_LOCAL,
                                                        channelCount = finalPreview.channelCount,
                                                        movieCount = finalPreview.movieCount,
                                                        seriesCount = finalPreview.seriesCount,
                                                        radioCount = finalPreview.radioCount
                                                    )
                                                }.onFailure { errorMessage = it.message }
                                            }
                                        }
                                    }

                                    ImportSourceTab.M3U_URL -> {
                                        val finalName = urlName.trim().ifBlank { "M3U Playlist" }
                                        checkDuplicateAndExecute(finalName) { existingId ->
                                            isSaving = true
                                            onSaveM3uUrl(
                                                existingId,
                                                finalName,
                                                m3uUrl.trim(),
                                                urlEpg.trim(),
                                                urlCustomUa.trim(),
                                                urlUseCustomUa,
                                                "",
                                                !hasExistingPlaylists
                                            ) { res ->
                                                isSaving = false
                                                res.onSuccess { savedId ->
                                                    savedSummary = SavedImportSummary(
                                                        playlistId = savedId,
                                                        name = finalName,
                                                        format = PlaylistType.M3U_URL,
                                                        channelCount = previewState?.channelCount ?: 0,
                                                        movieCount = previewState?.movieCount ?: 0,
                                                        seriesCount = previewState?.seriesCount ?: 0,
                                                        radioCount = previewState?.radioCount ?: 0
                                                    )
                                                }.onFailure { errorMessage = it.message }
                                            }
                                        }
                                    }

                                    ImportSourceTab.XTREAM -> {
                                        val finalName = xtreamName.trim().ifBlank { "Xtream IPTV" }
                                        checkDuplicateAndExecute(finalName) { existingId ->
                                            isSaving = true
                                            onSaveXtream(
                                                existingId,
                                                finalName,
                                                xtreamServer.trim(),
                                                xtreamPort.trim(),
                                                xtreamUsername.trim(),
                                                xtreamPassword,
                                                xtreamCustomUa.trim(),
                                                xtreamUseCustomUa,
                                                "",
                                                !hasExistingPlaylists
                                            ) { res ->
                                                isSaving = false
                                                res.onSuccess { savedId ->
                                                    savedSummary = SavedImportSummary(
                                                        playlistId = savedId,
                                                        name = finalName,
                                                        format = PlaylistType.XTREAM,
                                                        channelCount = previewState?.channelCount ?: 0,
                                                        movieCount = previewState?.movieCount ?: 0,
                                                        seriesCount = previewState?.seriesCount ?: 0,
                                                        radioCount = previewState?.radioCount ?: 0
                                                    )
                                                }.onFailure { errorMessage = it.message }
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = canConfirmSave && !isTesting && !isSaving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("import_confirm_save_btn")
                        ) {
                            Text(stringResource(R.string.import_confirm_save))
                        }
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        val incorrectMsg = stringResource(R.string.pin_incorrect)
        ParentalPinDialog(
            title = stringResource(R.string.pin_enter_title),
            isCreation = false,
            errorText = pinError,
            onDismiss = { showPinDialog = false },
            onSubmitPin = { pin ->
                scope.launch {
                    if (onVerifyPin(pin)) {
                        isUnlocked = true
                        showPinDialog = false
                    } else {
                        pinError = incorrectMsg
                    }
                }
            }
        )
    }

    overwriteConfirmAction?.let { (conflictName, action) ->
        AlertDialog(
            onDismissRequest = { overwriteConfirmAction = null },
            title = { Text(stringResource(R.string.playlist_confirm_overwrite_title)) },
            text = { Text(stringResource(R.string.import_overwrite_existing_msg, conflictName)) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val existing = onFindPlaylistByName(conflictName)
                        overwriteConfirmAction = null
                        if (existing != null) action(existing.id)
                    }
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { overwriteConfirmAction = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportPreviewCard(
    preview: PlaylistImportPreview,
    overrideName: String
) {
    val mehdiColors = LocalMehdiColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("import_preview_card"),
        shape = RoundedCornerShape(18.dp),
        color = mehdiColors.cardSurface,
        border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.5f))
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
                Text(
                    text = stringResource(R.string.import_preview_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = StatusSuccess
                )
                StatusPill(label = preview.format.name, color = ModernPurple)
            }
            Text(
                text = overrideName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(label = stringResource(R.string.home_count_channels, preview.channelCount), color = ModernPurple)
                StatusPill(label = stringResource(R.string.home_count_movies, preview.movieCount), color = TurquoiseAccent)
                StatusPill(label = stringResource(R.string.home_count_series, preview.seriesCount), color = TurquoiseAccent)
                if (preview.radioCount > 0) {
                    StatusPill(label = stringResource(R.string.home_count_radio, preview.radioCount), color = StatusSuccess)
                }
            }
            if (preview.expirationTimestampMs != null) {
                val expText = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(preview.expirationTimestampMs))
                Text(
                    text = "${stringResource(R.string.home_expiration_label)}: $expText",
                    style = MaterialTheme.typography.bodySmall,
                    color = mehdiColors.secondaryText
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportConfirmationCard(
    summary: SavedImportSummary,
    onActivateNow: () -> Unit,
    onAddAnother: () -> Unit,
    onDone: () -> Unit
) {
    val mehdiColors = LocalMehdiColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("import_confirmation_card"),
        shape = RoundedCornerShape(22.dp),
        color = mehdiColors.cardSurface,
        border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = StatusSuccess,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = stringResource(R.string.import_success_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${summary.name} (${summary.format.name})",
                style = MaterialTheme.typography.titleMedium,
                color = mehdiColors.secondaryText
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (summary.channelCount > 0) {
                    StatusPill(label = stringResource(R.string.home_count_channels, summary.channelCount), color = ModernPurple)
                }
                if (summary.movieCount > 0) {
                    StatusPill(label = stringResource(R.string.home_count_movies, summary.movieCount), color = TurquoiseAccent)
                }
                if (summary.seriesCount > 0) {
                    StatusPill(label = stringResource(R.string.home_count_series, summary.seriesCount), color = TurquoiseAccent)
                }
                if (summary.radioCount > 0) {
                    StatusPill(label = stringResource(R.string.home_count_radio, summary.radioCount), color = StatusSuccess)
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onActivateNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("import_activate_now_btn")
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.import_activate_now))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onAddAnother,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("import_add_another_btn")
                ) {
                    Text(stringResource(R.string.import_add_another))
                }
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("import_done_btn")
                ) {
                    Text(stringResource(R.string.action_back))
                }
            }
        }
    }
}
