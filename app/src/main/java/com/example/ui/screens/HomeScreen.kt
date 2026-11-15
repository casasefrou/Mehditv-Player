package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.model.ActivationStatus
import com.example.core.model.PlaylistStatus
import com.example.core.model.UserPreferencesState
import com.example.core.security.DeviceIdentityManager
import com.example.data.local.entities.ChannelEntity
import com.example.data.local.entities.MovieEntity
import com.example.data.local.entities.PlaylistEntity
import com.example.data.local.entities.SeriesEntity
import com.example.data.local.entities.WatchHistoryEntity
import com.example.ui.components.MehdiTvBrandMark
import com.example.ui.components.PosterMediaCard
import com.example.ui.components.StatusPill
import com.example.ui.components.TvFocusCard
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalMehdiColors
import com.example.ui.theme.ModernPurple
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusLiveRed
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TurquoiseAccent
import com.example.ui.viewmodel.HomeCatalogSnapshot
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FocusYellowAccent = Color(0xFFFACC15)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    isWideScreen: Boolean,
    preferences: UserPreferencesState,
    activePlaylist: PlaylistEntity?,
    allPlaylists: List<PlaylistEntity>,
    snapshot: HomeCatalogSnapshot,
    isRefreshing: Boolean,
    isOnline: Boolean,
    onRefreshClick: () -> Unit,
    onSelectActivePlaylist: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleLanguage: () -> Unit,
    onOpenLive: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    onOpenRadio: () -> Unit,
    onOpenCatchUp: () -> Unit,
    onOpenImportPlaylist: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenQrPairing: () -> Unit,
    onPlayHistoryItem: (WatchHistoryEntity) -> Unit,
    onPlayChannel: (ChannelEntity) -> Unit,
    onOpenMovieDetail: (MovieEntity) -> Unit,
    onOpenSeriesDetail: (SeriesEntity) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val mehdiColors = LocalMehdiColors.current
    val clipboard = LocalClipboardManager.current
    val copiedMsg = stringResource(R.string.copied_to_clipboard)

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var showPlaylistSwitcher by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    val backgroundBrush = if (mehdiColors.isDark) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF0B142C),
                Color(0xFF111F43),
                Color(0xFF0A1226)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                MaterialTheme.colorScheme.background
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .testTag("home_lazy_column"),
        contentPadding = PaddingValues(
            horizontal = if (isWideScreen) 32.dp else 16.dp,
            vertical = 18.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // A. Application Header (Top Center)
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 980.dp)
                    .fillMaxWidth()
                    .testTag("home_brand_header"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    MehdiTvBrandMark(compact = false)
                }
                if (preferences.showClockOnHome) {
                    val timeText = remember(nowMs, preferences.language) {
                        val fmt = SimpleDateFormat("HH:mm • dd MMM yyyy", Locale(preferences.language.code))
                        fmt.format(Date(nowMs))
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = mehdiColors.secondaryText
                    )
                }
            }
        }

        // B. Playlist Information Panel (Wide Info Banner below Logo)
        item {
            PlaylistInformationBanner(
                activePlaylist = activePlaylist,
                isOnline = isOnline,
                nowMs = nowMs,
                modifier = Modifier.widthIn(max = 980.dp)
            )
        }

        // C. Four Main Navigation Cards: LIVE TV, MOVIES, SERIES, RADIO
        item {
            val cards = listOf(
                HomeMainCategoryCard(
                    title = stringResource(R.string.home_card_live_title),
                    countLabel = stringResource(R.string.home_count_channels, snapshot.channelCount),
                    icon = Icons.Filled.Tv,
                    accent = ModernPurple,
                    testTag = "home_card_live",
                    onClick = onOpenLive
                ),
                HomeMainCategoryCard(
                    title = stringResource(R.string.home_card_movies_title),
                    countLabel = stringResource(R.string.home_count_movies, snapshot.movieCount),
                    icon = Icons.Filled.Videocam,
                    accent = TurquoiseAccent,
                    testTag = "home_card_movies",
                    onClick = onOpenMovies
                ),
                HomeMainCategoryCard(
                    title = stringResource(R.string.home_card_series_title),
                    countLabel = stringResource(R.string.home_count_series, snapshot.seriesCount),
                    icon = Icons.Filled.Movie,
                    accent = Color(0xFF3B82F6),
                    testTag = "home_card_series",
                    onClick = onOpenSeries
                ),
                HomeMainCategoryCard(
                    title = stringResource(R.string.home_card_radio_title),
                    countLabel = stringResource(R.string.home_count_radio, snapshot.radioCount),
                    icon = Icons.Filled.Radio,
                    accent = TurquoiseAccent,
                    testTag = "home_card_radio",
                    onClick = onOpenRadio
                )
            )

            Box(modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth()) {
                if (isWideScreen) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        cards.forEach { card ->
                            HotStyleCategoryCard(
                                card = card,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HotStyleCategoryCard(card = cards[0], modifier = Modifier.weight(1f))
                            HotStyleCategoryCard(card = cards[1], modifier = Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HotStyleCategoryCard(card = cards[2], modifier = Modifier.weight(1f))
                            HotStyleCategoryCard(card = cards[3], modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // D. Centered CATCH UP Button (Enabled ONLY when active playlist supports catch-up)
        item {
            val catchUpSupported = activePlaylist?.supportsCatchup == true
            Column(
                modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                var catchUpFocused by remember { mutableStateOf(false) }
                val catchUpBorder by animateColorAsState(
                    targetValue = when {
                        catchUpFocused && catchUpSupported -> FocusYellowAccent
                        catchUpSupported -> TurquoiseAccent.copy(alpha = 0.7f)
                        else -> mehdiColors.subtleBorder.copy(alpha = 0.5f)
                    },
                    label = "catchup_border"
                )
                OutlinedButton(
                    onClick = onOpenCatchUp,
                    enabled = catchUpSupported,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(if (catchUpFocused) 2.dp else 1.2.dp, catchUpBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (catchUpSupported) {
                            mehdiColors.cardSurface.copy(alpha = 0.9f)
                        } else {
                            mehdiColors.cardSurface.copy(alpha = 0.35f)
                        },
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContentColor = mehdiColors.secondaryText.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier
                        .widthIn(min = 210.dp)
                        .minimumInteractiveComponentSize()
                        .onFocusChanged { catchUpFocused = it.isFocused }
                        .testTag("home_catch_up_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_catch_up),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                if (!catchUpSupported) {
                    Text(
                        text = stringResource(R.string.home_catch_up_unavailable),
                        style = MaterialTheme.typography.labelSmall,
                        color = mehdiColors.secondaryText.copy(alpha = 0.78f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // E. Dual Playlist Import System (Method 1: Website QR + Method 2: Device Import)
        item {
            Box(modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth()) {
                if (isWideScreen) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        WebsiteQrImportCard(
                            onCopyWebsite = {
                                clipboard.setText(AnnotatedString(DeviceIdentityManager.MEHDITV_WEBSITE_URL))
                                onShowMessage(copiedMsg)
                            },
                            modifier = Modifier.weight(1.15f)
                        )
                        DirectDeviceImportCard(
                            onOpenImportFromDevice = onOpenImportPlaylist,
                            onOpenQrPairing = onOpenQrPairing,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        WebsiteQrImportCard(
                            onCopyWebsite = {
                                clipboard.setText(AnnotatedString(DeviceIdentityManager.MEHDITV_WEBSITE_URL))
                                onShowMessage(copiedMsg)
                            }
                        )
                        DirectDeviceImportCard(
                            onOpenImportFromDevice = onOpenImportPlaylist,
                            onOpenQrPairing = onOpenQrPairing
                        )
                    }
                }
            }
        }

        // F. Bottom Action Pills (Refresh, Change Playlist, Settings, Language AR/EN)
        item {
            Box(modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth()) {
                val actions = listOf(
                    BottomPillAction(
                        label = stringResource(R.string.home_action_refresh_playlist),
                        icon = Icons.Filled.Refresh,
                        isLoading = isRefreshing,
                        enabled = !isRefreshing && activePlaylist != null,
                        testTag = "home_action_refresh",
                        onClick = onRefreshClick
                    ),
                    BottomPillAction(
                        label = stringResource(R.string.home_action_change_playlist),
                        icon = Icons.Filled.PlaylistPlay,
                        isLoading = false,
                        enabled = true,
                        testTag = "home_action_change_playlist",
                        onClick = {
                            if (allPlaylists.isEmpty()) {
                                onOpenPlaylists()
                            } else {
                                showPlaylistSwitcher = true
                            }
                        }
                    ),
                    BottomPillAction(
                        label = stringResource(R.string.home_action_settings),
                        icon = Icons.Filled.Settings,
                        isLoading = false,
                        enabled = true,
                        testTag = "home_action_settings",
                        onClick = onOpenSettings
                    ),
                    BottomPillAction(
                        label = stringResource(R.string.home_action_language),
                        icon = Icons.Filled.Language,
                        isLoading = false,
                        enabled = true,
                        testTag = "home_action_language",
                        onClick = onToggleLanguage
                    )
                )

                if (isWideScreen) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        actions.forEach { action ->
                            HomeBottomActionPill(action = action, modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HomeBottomActionPill(action = actions[0], modifier = Modifier.weight(1f))
                            HomeBottomActionPill(action = actions[1], modifier = Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HomeBottomActionPill(action = actions[2], modifier = Modifier.weight(1f))
                            HomeBottomActionPill(action = actions[3], modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // G. Device Information Footer
        item {
            DeviceIdFooterCard(
                deviceId = preferences.deviceId,
                onCopyDeviceId = {
                    clipboard.setText(AnnotatedString(preferences.deviceId))
                    onShowMessage(copiedMsg)
                },
                modifier = Modifier.widthIn(max = 980.dp)
            )
        }

        // Preserved Dynamic Shelves (shown automatically when populated)
        if (snapshot.continueWatching.isNotEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth()) {
                    HomeSectionTitle(stringResource(R.string.home_section_continue_watching))
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(snapshot.continueWatching, key = { it.mediaId }) { item ->
                            ContinueWatchingCard(item = item, onClick = { onPlayHistoryItem(item) })
                        }
                    }
                }
            }
        }

        if (snapshot.favoriteChannels.isNotEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth()) {
                    HomeSectionTitle(stringResource(R.string.home_section_favorite_channels))
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(snapshot.favoriteChannels, key = { it.id }) { ch ->
                            FavoriteChannelHomeCard(channel = ch, onClick = { onPlayChannel(ch) })
                        }
                    }
                }
            }
        }

        if (snapshot.recentlyWatched.isNotEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth()) {
                    HomeSectionTitle(stringResource(R.string.home_section_recently_watched))
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(snapshot.recentlyWatched, key = { it.mediaId }) { item ->
                            ContinueWatchingCard(item = item, onClick = { onPlayHistoryItem(item) })
                        }
                    }
                }
            }
        }

        if (snapshot.recentMovies.isNotEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth()) {
                    HomeSectionTitle(stringResource(R.string.home_section_recent_movies))
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(snapshot.recentMovies, key = { it.id }) { movie ->
                            PosterMediaCard(
                                title = movie.name,
                                posterUrl = movie.posterUrl,
                                subtitle = movie.categoryName,
                                rating = movie.rating,
                                year = movie.year,
                                isFavorite = movie.isFavorite,
                                onClick = { onOpenMovieDetail(movie) },
                                onToggleFavorite = {},
                                modifier = Modifier.width(142.dp)
                            )
                        }
                    }
                }
            }
        }

        if (snapshot.recentSeries.isNotEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth()) {
                    HomeSectionTitle(stringResource(R.string.home_section_recent_series))
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(snapshot.recentSeries, key = { it.id }) { series ->
                            PosterMediaCard(
                                title = series.name,
                                posterUrl = series.posterUrl,
                                subtitle = series.categoryName,
                                rating = series.rating,
                                year = series.year,
                                isFavorite = series.isFavorite,
                                onClick = { onOpenSeriesDetail(series) },
                                onToggleFavorite = {},
                                modifier = Modifier.width(142.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPlaylistSwitcher) {
        AlertDialog(
            onDismissRequest = { showPlaylistSwitcher = false },
            title = { Text(stringResource(R.string.home_action_change_playlist)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    allPlaylists.forEach { playlist ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSelectActivePlaylist(playlist.id)
                                    showPlaylistSwitcher = false
                                }
                                .testTag("home_switch_playlist_${playlist.id}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (playlist.isActive) {
                                ModernPurple.copy(alpha = 0.16f)
                            } else {
                                mehdiColors.cardElevated
                            },
                            border = BorderStroke(
                                1.dp,
                                if (playlist.isActive) ModernPurple else mehdiColors.subtleBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = playlist.type,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = mehdiColors.secondaryText
                                    )
                                }
                                if (playlist.isActive) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = stringResource(R.string.playlist_active_badge),
                                        tint = StatusSuccess,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showPlaylistSwitcher = false
                    onOpenPlaylists()
                }) {
                    Text(stringResource(R.string.home_manage_playlists))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlaylistSwitcher = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaylistInformationBanner(
    activePlaylist: PlaylistEntity?,
    isOnline: Boolean,
    nowMs: Long,
    modifier: Modifier = Modifier
) {
    val mehdiColors = LocalMehdiColors.current

    // Subscription expiration comes ONLY from the active playlist provider (never invented).
    val expMs = activePlaylist?.expirationTimestampMs
    val isExpiredByProvider = expMs != null && expMs > 0L && expMs < nowMs

    val (statusLabel, statusColor) = when {
        activePlaylist == null -> stringResource(R.string.home_status_unavailable) to mehdiColors.secondaryText
        activePlaylist.status == PlaylistStatus.ERROR.name -> stringResource(R.string.home_status_conn_error) to StatusError
        isExpiredByProvider -> stringResource(R.string.home_status_expired) to StatusError
        activePlaylist.status == PlaylistStatus.SYNCING.name -> stringResource(R.string.playlist_status_syncing) to ModernPurple
        else -> stringResource(R.string.home_status_active) to StatusSuccess
    }

    val expText = if (expMs != null && expMs > 0L) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expMs))
    } else {
        stringResource(R.string.home_expiration_unavailable)
    }
    val remainingDays = if (expMs != null && expMs > 0L) {
        ((expMs - nowMs) / 86_400_000L).toInt()
    } else null

    val lastRefreshText = activePlaylist?.lastRefreshedAtMs?.let {
        SimpleDateFormat("HH:mm • dd/MM", Locale.getDefault()).format(Date(it))
    } ?: stringResource(R.string.home_never_refreshed)

    val bannerBg = if (mehdiColors.isDark) {
        Color(0xFF282B26).copy(alpha = 0.72f)
    } else {
        mehdiColors.cardSurface.copy(alpha = 0.94f)
    }
    val accentGold = if (mehdiColors.isDark) FocusYellowAccent else ModernPurple

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_playlist_info_banner"),
        shape = RoundedCornerShape(16.dp),
        color = bannerBg,
        border = BorderStroke(1.dp, accentGold.copy(alpha = 0.35f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${stringResource(R.string.home_playlist_info_title)}: ${activePlaylist?.name ?: stringResource(R.string.home_no_active_playlist)}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = accentGold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusPill(label = statusLabel, color = statusColor)

                StatusPill(
                    label = stringResource(
                        if (isOnline) R.string.home_connection_online else R.string.home_connection_offline
                    ),
                    color = if (isOnline) TurquoiseAccent else StatusWarning
                )

                Surface(
                    shape = RoundedCornerShape(50),
                    color = mehdiColors.cardElevated.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = "${stringResource(R.string.home_expiration_label)}: $expText" +
                            if (remainingDays != null && remainingDays >= 0) {
                                " (${stringResource(R.string.home_remaining_days, remainingDays)})"
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = mehdiColors.cardElevated.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = "${stringResource(R.string.home_last_refresh)}: $lastRefreshText",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = mehdiColors.secondaryText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            if (!activePlaylist?.lastError.isNullOrBlank()) {
                Text(
                    text = activePlaylist!!.lastError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusError,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = mehdiColors.secondaryText,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.home_playlist_info_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = mehdiColors.secondaryText,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private data class HomeMainCategoryCard(
    val title: String,
    val countLabel: String,
    val icon: ImageVector,
    val accent: Color,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
private fun HotStyleCategoryCard(
    card: HomeMainCategoryCard,
    modifier: Modifier = Modifier
) {
    val mehdiColors = LocalMehdiColors.current
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.035f else 1f,
        animationSpec = spring(stiffness = 420f),
        label = "home_card_scale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) FocusYellowAccent else mehdiColors.subtleBorder.copy(alpha = 0.7f),
        label = "home_card_border"
    )
    val containerColor = if (mehdiColors.isDark) {
        Color(0xFF1A2D54).copy(alpha = if (isFocused) 0.92f else 0.76f)
    } else {
        mehdiColors.cardSurface
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(142.dp)
                .scale(scale)
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = card.onClick)
                .testTag(card.testTag),
            shape = RoundedCornerShape(20.dp),
            color = containerColor,
            shadowElevation = if (isFocused) 10.dp else 3.dp,
            border = BorderStroke(if (isFocused) 2.5.dp else 1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(card.accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = card.icon,
                        contentDescription = card.title,
                        tint = if (isFocused) FocusYellowAccent else Color.White.takeIf { mehdiColors.isDark } ?: card.accent,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = card.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Count pill underneath the card, inspired by the Hot IPTV reference screenshot
        Surface(
            shape = RoundedCornerShape(50),
            color = if (isFocused) FocusYellowAccent.copy(alpha = 0.2f) else mehdiColors.cardSurface.copy(alpha = 0.85f),
            border = BorderStroke(
                1.dp,
                if (isFocused) FocusYellowAccent else mehdiColors.subtleBorder.copy(alpha = 0.75f)
            )
        ) {
            Text(
                text = card.countLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isFocused) FocusYellowAccent else mehdiColors.secondaryText,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebsiteQrImportCard(
    onCopyWebsite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mehdiColors = LocalMehdiColors.current
    val websiteBitmap = remember {
        DeviceIdentityManager.generateWebsiteQrBitmap(420)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_website_qr_card"),
        shape = RoundedCornerShape(18.dp),
        color = mehdiColors.cardSurface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, mehdiColors.subtleBorder),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, mehdiColors.subtleBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (websiteBitmap != null) {
                        Image(
                            bitmap = websiteBitmap,
                            contentDescription = DeviceIdentityManager.MEHDITV_WEBSITE_URL,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.QrCode2,
                            contentDescription = DeviceIdentityManager.MEHDITV_WEBSITE_URL,
                            tint = DeepNavy,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCode2,
                        contentDescription = null,
                        tint = mehdiColors.purpleBrand,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.home_qr_website_label),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = stringResource(R.string.home_qr_website_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = mehdiColors.secondaryText
                )

                FlowRow(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = mehdiColors.cardElevated
                    ) {
                        Text(
                            text = DeviceIdentityManager.MEHDITV_WEBSITE_URL,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = TurquoiseAccent,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("home_website_url_text")
                        )
                    }
                    IconButton(
                        onClick = onCopyWebsite,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_copy_website_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.home_copy_website),
                            tint = mehdiColors.secondaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectDeviceImportCard(
    onOpenImportFromDevice: () -> Unit,
    onOpenQrPairing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mehdiColors = LocalMehdiColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = mehdiColors.cardSurface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, mehdiColors.subtleBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.home_add_from_device),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.playlists_empty_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = mehdiColors.secondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Button(
                onClick = onOpenImportFromDevice,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("home_add_from_device_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.AddCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_add_from_device))
            }
            TextButton(
                onClick = onOpenQrPairing,
                modifier = Modifier
                    .align(Alignment.End)
                    .minimumInteractiveComponentSize()
                    .testTag("home_qr_pairing_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCode2,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.home_qr_pairing_title))
            }
        }
    }
}

private data class BottomPillAction(
    val label: String,
    val icon: ImageVector,
    val isLoading: Boolean,
    val enabled: Boolean,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
private fun HomeBottomActionPill(
    action: BottomPillAction,
    modifier: Modifier = Modifier
) {
    val mehdiColors = LocalMehdiColors.current
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) FocusYellowAccent else mehdiColors.subtleBorder,
        label = "bottom_pill_border"
    )

    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(enabled = action.enabled)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = action.enabled, onClick = action.onClick)
            .testTag(action.testTag),
        shape = RoundedCornerShape(12.dp),
        color = if (isFocused) {
            ModernPurple.copy(alpha = 0.25f)
        } else {
            mehdiColors.cardSurface.copy(alpha = if (action.enabled) 0.88f else 0.45f)
        },
        border = BorderStroke(if (isFocused) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (action.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = if (isFocused) FocusYellowAccent else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = action.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (action.enabled) MaterialTheme.colorScheme.onSurface else mehdiColors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DeviceIdFooterCard(
    deviceId: String,
    onCopyDeviceId: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mehdiColors = LocalMehdiColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${stringResource(R.string.home_device_id_label)}: ",
                style = MaterialTheme.typography.labelMedium,
                color = mehdiColors.secondaryText
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = mehdiColors.cardSurface
            ) {
                Text(
                    text = deviceId.ifBlank { "--:--:--:--:--:--" },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = TurquoiseAccent,
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("home_device_id_text")
                )
            }
            IconButton(
                onClick = onCopyDeviceId,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("home_copy_device_id_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.action_copy),
                    tint = mehdiColors.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = stringResource(R.string.device_id_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = mehdiColors.secondaryText.copy(alpha = 0.85f),
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.home_media_only_notice),
            style = MaterialTheme.typography.bodySmall,
            color = mehdiColors.secondaryText,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HomeSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun ContinueWatchingCard(
    item: WatchHistoryEntity,
    onClick: () -> Unit
) {
    val mehdiColors = LocalMehdiColors.current
    val progress = if (item.durationMs > 0L) {
        (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    TvFocusCard(
        onClick = onClick,
        modifier = Modifier.width(190.dp),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(106.dp)
                .background(mehdiColors.cardElevated)
        ) {
            if (item.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Tv,
                        contentDescription = null,
                        tint = mehdiColors.secondaryText
                    )
                }
            }
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter),
                    color = TurquoiseAccent,
                    trackColor = Color.Black.copy(alpha = 0.45f)
                )
            }
        }
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = mehdiColors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FavoriteChannelHomeCard(
    channel: ChannelEntity,
    onClick: () -> Unit
) {
    val mehdiColors = LocalMehdiColors.current
    TvFocusCard(
        onClick = onClick,
        modifier = Modifier.width(148.dp),
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(mehdiColors.cardElevated),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.LiveTv,
                    contentDescription = null,
                    tint = mehdiColors.purpleBrand
                )
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                shape = CircleShape,
                color = StatusLiveRed
            ) {
                Box(modifier = Modifier.size(7.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = channel.categoryName,
            style = MaterialTheme.typography.labelSmall,
            color = mehdiColors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun activationLabel(status: ActivationStatus): String {
    return when (status) {
        ActivationStatus.LOCAL_MODE -> stringResource(R.string.activation_local_mode)
        ActivationStatus.ACTIVE -> stringResource(R.string.activation_active)
        ActivationStatus.TRIAL -> stringResource(R.string.activation_trial)
        ActivationStatus.UNACTIVATED -> stringResource(R.string.activation_unactivated)
        ActivationStatus.EXPIRED -> stringResource(R.string.activation_expired)
        ActivationStatus.SUSPENDED -> stringResource(R.string.activation_suspended)
    }
}
