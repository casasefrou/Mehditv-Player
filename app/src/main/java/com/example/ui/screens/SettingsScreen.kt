package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.model.AppLanguage
import com.example.core.model.BufferPreset
import com.example.core.model.DecoderMode
import com.example.core.model.StartupScreen
import com.example.core.model.ThemeMode
import com.example.core.model.UserPreferencesState
import com.example.data.local.entities.CategoryEntity
import com.example.data.local.entities.PlaylistEntity
import com.example.ui.components.ParentalPinDialog
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalMehdiColors
import com.example.ui.theme.ModernPurple
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TurquoiseAccent
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    isWideScreen: Boolean,
    preferences: UserPreferencesState,
    playlists: List<PlaylistEntity>,
    categories: List<CategoryEntity>,
    isOnline: Boolean,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onShowClockChange: (Boolean) -> Unit,
    onStartupScreenChange: (StartupScreen) -> Unit,
    onSetDefaultPlaylist: (Long) -> Unit,
    onOpenPlaylists: () -> Unit,
    onRefreshPlaylist: () -> Unit,
    onRefreshEpg: () -> Unit,
    onGlobalUserAgentSave: (String, Boolean) -> Unit,
    onEpgTimezoneChange: (Int) -> Unit,
    onBufferPresetChange: (BufferPreset) -> Unit,
    onDecoderModeChange: (DecoderMode) -> Unit,
    onSubtitleStyleChange: (Float, Float) -> Unit,
    onPreferredAudioChange: (String) -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onSetParentalPin: (String) -> Unit,
    onClearParentalPin: () -> Unit,
    onVerifyPin: suspend (String) -> Boolean,
    onLockSettingsChange: (Boolean) -> Unit,
    onLockPlaylistEditsChange: (Boolean) -> Unit,
    onCategoryHiddenChange: (String, Boolean) -> Unit,
    onCategoryLockedChange: (String, Boolean) -> Unit,
    onBackendUrlSave: (String, (Boolean) -> Unit) -> Unit,
    onClearImageCache: (() -> Unit) -> Unit,
    onExportSettingsJson: suspend () -> String,
    onRestoreSettingsJson: (String, (Result<Unit>) -> Unit) -> Unit,
    onBuildDiagnostics: () -> String,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val mehdiColors = LocalMehdiColors.current
    val copiedMsg = stringResource(R.string.copied_to_clipboard)

    var settingsUnlocked by remember(preferences.hasParentalPin, preferences.lockSettings) {
        mutableStateOf(!(preferences.hasParentalPin && preferences.lockSettings))
    }
    var unlockError by remember { mutableStateOf<String?>(null) }

    if (!settingsUnlocked) {
        val incorrectText = stringResource(R.string.pin_incorrect)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = ModernPurple, modifier = Modifier.size(48.dp))
                Text(stringResource(R.string.settings_lock_settings), style = MaterialTheme.typography.titleLarge)
                ParentalPinDialog(
                    title = stringResource(R.string.pin_enter_title),
                    isCreation = false,
                    errorText = unlockError,
                    onDismiss = {},
                    onSubmitPin = { entered ->
                        scope.launch {
                            if (onVerifyPin(entered)) {
                                settingsUnlocked = true
                            } else {
                                unlockError = incorrectText
                            }
                        }
                    }
                )
            }
        }
        return
    }

    var uaDraft by remember(preferences.globalUserAgent) { mutableStateOf(preferences.globalUserAgent) }
    var backendUrlDraft by remember(preferences.backendBaseUrl) { mutableStateOf(preferences.backendBaseUrl) }
    var audioLangDraft by remember(preferences.preferredAudioLang) { mutableStateOf(preferences.preferredAudioLang) }

    var showCreatePinDialog by remember { mutableStateOf(false) }
    var showCategoryManager by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = if (isWideScreen) 28.dp else 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 1. GENERAL
        item {
            SettingsSectionCard(title = stringResource(R.string.settings_sec_general)) {
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = preferences.language == AppLanguage.ARABIC,
                        onClick = { onLanguageChange(AppLanguage.ARABIC) },
                        label = { Text(stringResource(R.string.settings_lang_ar)) },
                        modifier = Modifier.testTag("settings_lang_ar_chip")
                    )
                    FilterChip(
                        selected = preferences.language == AppLanguage.ENGLISH,
                        onClick = { onLanguageChange(AppLanguage.ENGLISH) },
                        label = { Text(stringResource(R.string.settings_lang_en)) },
                        modifier = Modifier.testTag("settings_lang_en_chip")
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = preferences.themeMode == ThemeMode.DARK,
                        onClick = { onThemeChange(ThemeMode.DARK) },
                        label = { Text(stringResource(R.string.settings_theme_dark)) }
                    )
                    FilterChip(
                        selected = preferences.themeMode == ThemeMode.LIGHT,
                        onClick = { onThemeChange(ThemeMode.LIGHT) },
                        label = { Text(stringResource(R.string.settings_theme_light)) }
                    )
                    FilterChip(
                        selected = preferences.themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeChange(ThemeMode.SYSTEM) },
                        label = { Text(stringResource(R.string.settings_theme_system)) }
                    )
                }

                SettingsToggleRow(
                    title = stringResource(R.string.settings_show_clock),
                    checked = preferences.showClockOnHome,
                    onCheckedChange = onShowClockChange
                )

                Text(stringResource(R.string.settings_startup_screen), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StartupScreen.entries.forEach { scr ->
                        val label = when (scr) {
                            StartupScreen.HOME -> stringResource(R.string.nav_home)
                            StartupScreen.LIVE -> stringResource(R.string.nav_live)
                            StartupScreen.MOVIES -> stringResource(R.string.nav_movies)
                            StartupScreen.SERIES -> stringResource(R.string.nav_series)
                        }
                        FilterChip(
                            selected = preferences.startupScreen == scr,
                            onClick = { onStartupScreenChange(scr) },
                            label = { Text(label) }
                        )
                    }
                }

                if (playlists.isNotEmpty()) {
                    Text(stringResource(R.string.settings_default_playlist), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        playlists.forEach { pl ->
                            FilterChip(
                                selected = pl.isDefault,
                                onClick = { onSetDefaultPlaylist(pl.id) },
                                label = { Text(pl.name) }
                            )
                        }
                    }
                }
            }
        }

        // 2. PLAYLIST SETTINGS
        item {
            SettingsSectionCard(title = stringResource(R.string.settings_sec_playlist)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onOpenPlaylists) {
                        Text(stringResource(R.string.home_manage_playlists))
                    }
                    OutlinedButton(onClick = onRefreshPlaylist) {
                        Text(stringResource(R.string.settings_refresh_all))
                    }
                }

                SettingsToggleRow(
                    title = stringResource(R.string.settings_global_ua_toggle),
                    checked = preferences.useGlobalCustomUserAgent,
                    onCheckedChange = { onGlobalUserAgentSave(uaDraft, it) }
                )

                OutlinedTextField(
                    value = uaDraft,
                    onValueChange = { uaDraft = it },
                    label = { Text(stringResource(R.string.settings_global_ua)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    onGlobalUserAgentSave(uaDraft, preferences.useGlobalCustomUserAgent)
                    onShowMessage(uaDraft)
                }) {
                    Text(stringResource(R.string.action_save))
                }

                val tzHours = preferences.epgTimezoneOffsetMinutes / 60
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.settings_epg_tz)}: ${if (tzHours >= 0) "+$tzHours" else "$tzHours"}h",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = {
                            onEpgTimezoneChange((preferences.epgTimezoneOffsetMinutes - 60).coerceIn(-720, 840))
                        }) { Text("-1h") }
                        OutlinedButton(onClick = {
                            onEpgTimezoneChange((preferences.epgTimezoneOffsetMinutes + 60).coerceIn(-720, 840))
                        }) { Text("+1h") }
                        OutlinedButton(onClick = onRefreshEpg) {
                            Text(stringResource(R.string.epg_refresh))
                        }
                    }
                }
            }
        }

        // 3. PLAYBACK SETTINGS
        item {
            SettingsSectionCard(title = stringResource(R.string.settings_sec_playback)) {
                Text(stringResource(R.string.settings_buffer_preset), style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        BufferPreset.LOW_LATENCY to stringResource(R.string.settings_buffer_low),
                        BufferPreset.BALANCED to stringResource(R.string.settings_buffer_balanced),
                        BufferPreset.HIGH_STABILITY to stringResource(R.string.settings_buffer_high)
                    ).forEach { (preset, label) ->
                        FilterChip(
                            selected = preferences.bufferPreset == preset,
                            onClick = { onBufferPresetChange(preset) },
                            label = { Text(label) }
                        )
                    }
                }

                Text(stringResource(R.string.settings_decoder_mode), style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = preferences.decoderMode == DecoderMode.HARDWARE_FIRST,
                        onClick = { onDecoderModeChange(DecoderMode.HARDWARE_FIRST) },
                        label = { Text(stringResource(R.string.settings_decoder_hw)) }
                    )
                    FilterChip(
                        selected = preferences.decoderMode == DecoderMode.SOFTWARE_FALLBACK,
                        onClick = { onDecoderModeChange(DecoderMode.SOFTWARE_FALLBACK) },
                        label = { Text(stringResource(R.string.settings_decoder_sw)) }
                    )
                }

                Text(
                    text = "${stringResource(R.string.settings_subtitle_size)}: ${preferences.subtitleSizeSp.toInt()}sp",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = preferences.subtitleSizeSp,
                    onValueChange = { onSubtitleStyleChange(it, preferences.subtitleBgOpacity) },
                    valueRange = 12f..32f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = audioLangDraft,
                        onValueChange = { audioLangDraft = it },
                        label = { Text("Audio ISO (ar/en/fr)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { onPreferredAudioChange(audioLangDraft.trim()) }) {
                        Text(stringResource(R.string.action_save))
                    }
                }

                SettingsToggleRow(
                    title = stringResource(R.string.series_autoplay_next),
                    checked = preferences.autoplayNextEpisode,
                    onCheckedChange = onAutoplayChange
                )
            }
        }

        // 4. PRIVACY & SECURITY
        item {
            SettingsSectionCard(title = stringResource(R.string.settings_sec_privacy)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_parental_pin), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showCreatePinDialog = true },
                            modifier = Modifier.testTag("settings_pin_btn")
                        ) {
                            Text(
                                stringResource(
                                    if (preferences.hasParentalPin) R.string.settings_parental_change
                                    else R.string.settings_parental_set
                                )
                            )
                        }
                        if (preferences.hasParentalPin) {
                            OutlinedButton(onClick = onClearParentalPin) {
                                Text(stringResource(R.string.settings_parental_remove), color = StatusError)
                            }
                        }
                    }
                }

                if (preferences.hasParentalPin) {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_lock_settings),
                        checked = preferences.lockSettings,
                        onCheckedChange = onLockSettingsChange
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_lock_playlists),
                        checked = preferences.lockPlaylistEdits,
                        onCheckedChange = onLockPlaylistEditsChange
                    )
                }

                OutlinedButton(
                    onClick = { showCategoryManager = true },
                    enabled = categories.isNotEmpty()
                ) {
                    Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_manage_categories))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = TurquoiseAccent, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.settings_encryption_status), style = MaterialTheme.typography.bodyMedium)
                    }
                    StatusPill(stringResource(R.string.settings_encryption_active), StatusSuccess)
                }

                TextButton(onClick = { showPrivacyDialog = true }) {
                    Text(stringResource(R.string.settings_privacy_policy))
                }
            }
        }

        // 5. DEVICE INFORMATION
        item {
            SettingsSectionCard(title = stringResource(R.string.settings_sec_device)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_device_id_label), style = MaterialTheme.typography.labelSmall, color = mehdiColors.secondaryText)
                        Text(
                            text = preferences.deviceId,
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = TurquoiseAccent
                        )
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(preferences.deviceId))
                        onShowMessage(copiedMsg)
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.action_copy))
                    }
                }
                Text(
                    text = stringResource(R.string.device_id_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = mehdiColors.secondaryText
                )

                val pkgInfo = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
                }
                Text(
                    text = "${stringResource(R.string.settings_app_version)}: ${pkgInfo?.versionName ?: "1.0.0"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${stringResource(R.string.settings_android_version)}: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${stringResource(R.string.activation_status_label)}: ${activationLabel(preferences.activationStatus)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                val expLabel = preferences.licenseExpiresAtMs?.let {
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                } ?: stringResource(R.string.home_expiration_unavailable)
                Text(
                    text = "${stringResource(R.string.home_expiration_label)}: $expLabel",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${stringResource(R.string.home_connection_online)}: ${if (isOnline) "OK" else "Offline"}",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = backendUrlDraft,
                    onValueChange = { backendUrlDraft = it },
                    label = { Text(stringResource(R.string.settings_backend_url)) },
                    placeholder = { Text("https://api.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    onBackendUrlSave(backendUrlDraft) { ok ->
                        onShowMessage(if (ok) "Saved" else "HTTPS URL required")
                    }
                }) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }

        // 6. MAINTENANCE
        item {
            SettingsSectionCard(title = stringResource(R.string.settings_sec_maintenance)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onClearImageCache {
                                onShowMessage("Cache cleared")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_clear_cache))
                    }
                    OutlinedButton(
                        onClick = { showBackupRestoreDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_backup_export))
                    }
                }
                OutlinedButton(
                    onClick = { showDiagnosticsDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_diagnostics))
                }
            }
        }
    }

    if (showCreatePinDialog) {
        ParentalPinDialog(
            title = stringResource(R.string.pin_create_title),
            isCreation = true,
            onDismiss = { showCreatePinDialog = false },
            onSubmitPin = { newPin ->
                onSetParentalPin(newPin)
                showCreatePinDialog = false
            }
        )
    }

    if (showCategoryManager) {
        AlertDialog(
            onDismissRequest = { showCategoryManager = false },
            title = { Text(stringResource(R.string.settings_manage_categories)) },
            text = {
                LazyColumn(
                    modifier = Modifier.height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(categories, key = { it.id }) { cat ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                                Text(cat.section, style = MaterialTheme.typography.labelSmall, color = mehdiColors.secondaryText)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Hide", style = MaterialTheme.typography.labelSmall)
                                Checkbox(
                                    checked = cat.isHidden,
                                    onCheckedChange = { onCategoryHiddenChange(cat.id, it) }
                                )
                                Text("Lock", style = MaterialTheme.typography.labelSmall)
                                Checkbox(
                                    checked = cat.isLocked,
                                    onCheckedChange = { onCategoryLockedChange(cat.id, it) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryManager = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(stringResource(R.string.settings_privacy_policy)) },
            text = {
                Text(
                    "MehdiTv Player is a standalone media player. Playlist credentials and pairing tokens are encrypted locally via Android Keystore (AES-256-GCM). No personal media viewing history or playlists are collected or shared unless you explicitly pair with your own HTTPS activation/pairing server.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (showDiagnosticsDialog) {
        val diagText = remember(showDiagnosticsDialog) { onBuildDiagnostics() }
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            title = { Text(stringResource(R.string.settings_diagnostics)) },
            text = {
                Text(
                    text = diagText,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(diagText))
                    onShowMessage(copiedMsg)
                }) {
                    Text(stringResource(R.string.action_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiagnosticsDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (showBackupRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showBackupRestoreDialog = false },
            title = { Text(stringResource(R.string.settings_backup_export)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                val exported = onExportSettingsJson()
                                clipboard.setText(AnnotatedString(exported))
                                restoreJsonInput = exported
                                onShowMessage(copiedMsg)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_backup_export))
                    }
                    OutlinedTextField(
                        value = restoreJsonInput,
                        onValueChange = { restoreJsonInput = it },
                        label = { Text(stringResource(R.string.settings_backup_restore)) },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreSettingsJson(restoreJsonInput) { res ->
                            onShowMessage(if (res.isSuccess) "Restored" else "Invalid JSON")
                            if (res.isSuccess) showBackupRestoreDialog = false
                        }
                    },
                    enabled = restoreJsonInput.isNotBlank()
                ) {
                    Text(stringResource(R.string.settings_backup_restore))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupRestoreDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val mehdiColors = LocalMehdiColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = mehdiColors.cardSurface,
        border = BorderStroke(1.dp, mehdiColors.subtleBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = ModernPurple
            )
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
