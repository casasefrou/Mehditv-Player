package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.model.ActivationStatus
import com.example.core.model.UserPreferencesState
import com.example.core.security.DeviceIdentityManager
import com.example.data.remote.ActivationPairingClient
import com.example.ui.components.QrCodePreviewBox
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalMehdiColors
import com.example.ui.theme.ModernPurple
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TurquoiseAccent
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrPairingScreen(
    preferences: UserPreferencesState,
    onRegenerateToken: () -> Unit,
    onSyncPairedPlaylists: () -> Unit,
    onVerifyActivation: () -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val mehdiColors = LocalMehdiColors.current
    val clipboard = LocalClipboardManager.current
    val copiedMsg = stringResource(R.string.copied_to_clipboard)
    var showApiSpecDialog by remember { mutableStateOf(false) }

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val remainingSec = ((preferences.pairingTokenExpiresAtMs - nowMs) / 1000L).coerceAtLeast(0L)
    val timerStr = "%02d:%02d".format(remainingSec / 60L, remainingSec % 60L)

    val qrPayload = remember(
        preferences.backendBaseUrl,
        preferences.deviceId,
        preferences.pairingToken,
        preferences.pairingTokenExpiresAtMs
    ) {
        DeviceIdentityManager.buildPairingQrPayload(
            backendBaseUrl = preferences.backendBaseUrl,
            deviceId = preferences.deviceId,
            pairingToken = preferences.pairingToken,
            expiresAtMs = preferences.pairingTokenExpiresAtMs
        )
    }

    val activationColor = when (preferences.activationStatus) {
        ActivationStatus.ACTIVE -> StatusSuccess
        ActivationStatus.TRIAL -> ModernPurple
        ActivationStatus.EXPIRED, ActivationStatus.SUSPENDED -> StatusError
        ActivationStatus.UNACTIVATED -> StatusWarning
        ActivationStatus.LOCAL_MODE -> mehdiColors.secondaryText
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_qr_pairing_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = mehdiColors.cardSurface,
                    border = BorderStroke(1.dp, mehdiColors.subtleBorder),
                    shadowElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        QrCodePreviewBox(payload = qrPayload, size = 220.dp)

                        // Device ID & Copy
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.home_device_id_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = mehdiColors.secondaryText
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = mehdiColors.cardElevated
                                ) {
                                    Text(
                                        text = preferences.deviceId,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = TurquoiseAccent,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(preferences.deviceId))
                                        onShowMessage(copiedMsg)
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.action_copy))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.device_id_disclaimer),
                                style = MaterialTheme.typography.bodySmall,
                                color = mehdiColors.secondaryText
                            )
                        }

                        // Short-lived pairing token
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = mehdiColors.cardElevated
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.pairing_token_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = mehdiColors.secondaryText
                                    )
                                    Text(
                                        text = preferences.pairingToken,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        )
                                    )
                                    Text(
                                        text = stringResource(R.string.pairing_token_expires, timerStr),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TurquoiseAccent
                                    )
                                }
                                OutlinedButton(onClick = onRegenerateToken) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.pairing_regenerate_token))
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onSyncPairedPlaylists,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("qr_sync_playlists_btn")
                            ) {
                                Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.pairing_fetch_playlists))
                            }
                            OutlinedButton(
                                onClick = onVerifyActivation,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("qr_verify_activation_btn")
                            ) {
                                Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.pairing_verify_status))
                            }
                        }
                    }
                }
            }

            // Activation & License Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = mehdiColors.cardSurface,
                    border = BorderStroke(1.dp, mehdiColors.subtleBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.activation_status_label),
                                style = MaterialTheme.typography.titleMedium
                            )
                            StatusPill(
                                label = activationLabel(preferences.activationStatus),
                                color = activationColor
                            )
                        }
                        val expStr = preferences.licenseExpiresAtMs?.let {
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                        } ?: stringResource(R.string.home_expiration_unavailable)
                        Text(
                            text = "${stringResource(R.string.home_expiration_label)}: $expStr",
                            style = MaterialTheme.typography.bodyMedium,
                            color = mehdiColors.secondaryText
                        )
                    }
                }
            }

            // Instructions & Backend API Spec
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = mehdiColors.cardSurface,
                    border = BorderStroke(1.dp, mehdiColors.subtleBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.pairing_steps_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.pairing_steps_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = mehdiColors.secondaryText
                        )
                        TextButton(onClick = { showApiSpecDialog = true }) {
                            Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.pairing_api_spec_btn))
                        }
                    }
                }
            }
        }
    }

    if (showApiSpecDialog) {
        AlertDialog(
            onDismissRequest = { showApiSpecDialog = false },
            title = { Text(stringResource(R.string.pairing_api_spec_btn)) },
            text = {
                Text(
                    text = ActivationPairingClient.BACKEND_API_DOCUMENTATION,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { showApiSpecDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}
