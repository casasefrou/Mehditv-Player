package com.example.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.core.model.MediaHistoryType
import com.example.core.model.TrackOption
import com.example.core.model.UserPreferencesState
import com.example.data.local.entities.ChannelEntity
import com.example.data.local.entities.EpisodeEntity
import com.example.data.local.entities.SeriesEntity
import com.example.player.MehdiPlayerController
import com.example.player.PlayerUiState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ModernPurple
import com.example.ui.theme.StatusError
import com.example.ui.theme.TurquoiseAccent
import com.example.ui.viewmodel.ChannelNowNext
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    playerController: MehdiPlayerController,
    playerState: PlayerUiState,
    preferences: UserPreferencesState,
    channels: List<ChannelEntity>,
    epgMap: Map<String, ChannelNowNext>,
    isInPipMode: Boolean,
    onSwitchChannel: (ChannelEntity) -> Unit,
    onFindNextEpisode: suspend () -> Pair<SeriesEntity, EpisodeEntity>?,
    onPlayNextEpisode: (SeriesEntity, EpisodeEntity) -> Unit,
    onSubtitleStyleChange: (Float, Float) -> Unit,
    onSubtitleDelayChange: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    BackHandler {
        playerController.pause()
        onBack()
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var showChannelOverlay by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Brightness & Volume state
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVolume = remember { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15 }
    var volumeFraction by remember {
        mutableFloatStateOf(
            (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)?.toFloat() ?: 8f) / maxVolume.toFloat()
        )
    }
    var brightnessFraction by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it in 0f..1f } ?: 0.6f
        )
    }

    // Next Episode Autoplay countdown
    var nextEpisodePair by remember { mutableStateOf<Pair<SeriesEntity, EpisodeEntity>?>(null) }
    var autoplayCountdownSec by remember { mutableIntStateOf(0) }

    LaunchedEffect(playerState.hasEnded, playerState.target?.mediaId, preferences.autoplayNextEpisode) {
        if (playerState.hasEnded &&
            preferences.autoplayNextEpisode &&
            playerState.target?.mediaType == MediaHistoryType.EPISODE
        ) {
            val found = onFindNextEpisode()
            if (found != null) {
                nextEpisodePair = found
                for (sec in 5 downTo 1) {
                    autoplayCountdownSec = sec
                    delay(1000L)
                }
                val pair = nextEpisodePair
                nextEpisodePair = null
                if (pair != null) {
                    onPlayNextEpisode(pair.first, pair.second)
                }
            }
        } else {
            nextEpisodePair = null
        }
    }

    // Immersive full-screen mode while in PlayerScreen
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            playerController.saveProgressNow(force = true)
        }
    }

    // Auto-hide controls after 4.5s while playing
    LaunchedEffect(controlsVisible, playerState.isPlaying, showChannelOverlay) {
        if (controlsVisible && playerState.isPlaying && !showChannelOverlay) {
            delay(4500L)
            controlsVisible = false
        }
    }

    val target = playerState.target
    val isLive = target?.mediaType == MediaHistoryType.LIVE
    val currentEpg = if (isLive && target != null) epgMap[target.epgChannelId] else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
            }
    ) {
        // Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = playerController.exoPlayer
                }
            },
            update = { view ->
                if (view.player !== playerController.exoPlayer) {
                    view.player = playerController.exoPlayer
                }
                view.resizeMode = resizeMode
                view.subtitleView?.apply {
                    setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, preferences.subtitleSizeSp)
                    val bgAlpha = (preferences.subtitleBgOpacity * 255).toInt().coerceIn(0, 255)
                    setStyle(
                        CaptionStyleCompat(
                            AndroidColor.WHITE,
                            AndroidColor.argb(bgAlpha, 12, 18, 36),
                            AndroidColor.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                            AndroidColor.BLACK,
                            null
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering / Reconnecting Center Indicator
        if ((playerState.isBuffering || playerState.isReconnecting) && !isInPipMode) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(color = TurquoiseAccent)
                if (playerState.isReconnecting) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.72f)
                    ) {
                        Text(
                            text = stringResource(R.string.player_reconnecting),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Error overlay with Retry
        if (!playerState.errorMessage.isNullOrBlank() && !isInPipMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xE6111F43),
                border = BorderStroke(1.dp, StatusError)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.player_error_playback),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = playerState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    Button(onClick = { playerController.retry() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        }

        // Next Episode Autoplay Overlay
        if (nextEpisodePair != null && !isInPipMode) {
            val (nextSeries, nextEp) = nextEpisodePair!!
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xEB111F43),
                border = BorderStroke(1.dp, TurquoiseAccent)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.player_next_episode_in, autoplayCountdownSec),
                        style = MaterialTheme.typography.labelLarge,
                        color = TurquoiseAccent
                    )
                    Text(
                        text = "S${nextEp.seasonNumber}E${nextEp.episodeNumber} • ${nextEp.title}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            nextEpisodePair = null
                            onPlayNextEpisode(nextSeries, nextEp)
                        }) {
                            Text(stringResource(R.string.player_play_now))
                        }
                        TextButton(onClick = { nextEpisodePair = null }) {
                            Text(stringResource(R.string.action_cancel), color = Color.White)
                        }
                    }
                }
            }
        }

        // Interactive Controls Overlay (hidden in PiP)
        AnimatedVisibility(
            visible = controlsVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.78f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.86f)
                            )
                        )
                    )
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                playerController.pause()
                                onBack()
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = Color.White
                            )
                        }
                        if (!target?.posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = target?.posterUrl,
                                contentDescription = target?.title,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = target?.title.orEmpty(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val epgSummary = when {
                                currentEpg?.current != null -> buildString {
                                    append("${stringResource(R.string.live_now_playing)}: ${currentEpg.current.title}")
                                    if (currentEpg.next != null) {
                                        append("  •  ${stringResource(R.string.live_next_playing)}: ${currentEpg.next.title}")
                                    }
                                }
                                else -> target?.subtitle.orEmpty()
                            }
                            if (epgSummary.isNotBlank()) {
                                Text(
                                    text = epgSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TurquoiseAccent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isLive && channels.isNotEmpty()) {
                            IconButton(
                                onClick = { showChannelOverlay = !showChannelOverlay },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.FormatListBulleted,
                                    contentDescription = stringResource(R.string.player_channels),
                                    tint = Color.White
                                )
                            }
                        }
                        IconButton(
                            onClick = { showAudioDialog = true },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.Filled.Audiotrack,
                                contentDescription = stringResource(R.string.player_audio_tracks),
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = { showSubtitleDialog = true },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.Filled.ClosedCaption,
                                contentDescription = stringResource(R.string.player_subtitles),
                                tint = Color.White
                            )
                        }
                        if (!isLive) {
                            IconButton(
                                onClick = { showSpeedDialog = true },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.Filled.Speed,
                                    contentDescription = stringResource(R.string.player_speed),
                                    tint = Color.White
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                resizeMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.Filled.AspectRatio,
                                contentDescription = "Aspect Ratio",
                                tint = Color.White
                            )
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                            IconButton(
                                onClick = {
                                    val params = PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(16, 9))
                                        .build()
                                    activity.enterPictureInPictureMode(params)
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.Filled.PictureInPictureAlt,
                                    contentDescription = stringResource(R.string.player_pip),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                // Side Brightness & Volume Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Brightness pill
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Brightness6,
                                contentDescription = stringResource(R.string.player_brightness),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Slider(
                                value = brightnessFraction,
                                onValueChange = { v ->
                                    brightnessFraction = v
                                    activity?.window?.let { w ->
                                        val lp = w.attributes
                                        lp.screenBrightness = v.coerceIn(0.05f, 1f)
                                        w.attributes = lp
                                    }
                                },
                                modifier = Modifier.width(86.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = TurquoiseAccent
                                )
                            )
                        }
                    }

                    // Center Transport Controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLive && channels.isNotEmpty()) {
                            val idx = channels.indexOfFirst { it.id == target?.mediaId }
                            IconButton(
                                onClick = {
                                    if (idx >= 0) {
                                        val prevIdx = if (idx == 0) channels.lastIndex else idx - 1
                                        onSwitchChannel(channels[prevIdx])
                                    }
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.16f))
                            ) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                            }
                        } else if (!isLive) {
                            IconButton(
                                onClick = { playerController.seekBy(-10_000L) },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.16f))
                            ) {
                                Icon(Icons.Filled.Replay10, contentDescription = "-10s", tint = Color.White)
                            }
                        }

                        IconButton(
                            onClick = { playerController.togglePlayPause() },
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(ModernPurple)
                                .testTag("player_play_pause_btn")
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        if (isLive && channels.isNotEmpty()) {
                            val idx = channels.indexOfFirst { it.id == target?.mediaId }
                            IconButton(
                                onClick = {
                                    if (idx >= 0) {
                                        val nextIdx = (idx + 1) % channels.size
                                        onSwitchChannel(channels[nextIdx])
                                    }
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.16f))
                            ) {
                                Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White)
                            }
                        } else if (!isLive) {
                            IconButton(
                                onClick = { playerController.seekBy(10_000L) },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.16f))
                            ) {
                                Icon(Icons.Filled.Forward10, contentDescription = "+10s", tint = Color.White)
                            }
                        }
                    }

                    // Volume pill
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = stringResource(R.string.player_volume),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Slider(
                                value = volumeFraction,
                                onValueChange = { v ->
                                    volumeFraction = v
                                    audioManager?.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        (v * maxVolume).toInt().coerceIn(0, maxVolume),
                                        0
                                    )
                                },
                                modifier = Modifier.width(86.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = ModernPurple
                                )
                            )
                        }
                    }
                }

                // Bottom Seek Bar (for VOD/Series)
                if (!isLive && playerState.durationMs > 0L) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Slider(
                            value = playerState.positionMs.toFloat().coerceIn(0f, playerState.durationMs.toFloat()),
                            onValueChange = { playerController.seekTo(it.toLong()) },
                            valueRange = 0f..playerState.durationMs.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = TurquoiseAccent,
                                activeTrackColor = TurquoiseAccent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDurationMs(playerState.positionMs),
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = Color.White
                            )
                            Text(
                                text = formatDurationMs(playerState.durationMs),
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = Color.White.copy(alpha = 0.78f)
                            )
                        }
                    }
                }
            }
        }

        // Slide-over Live Channel List Overlay
        if (showChannelOverlay && isLive && !isInPipMode) {
            Surface(
                modifier = Modifier
                    .width(290.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart),
                color = Color(0xEB111F43),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.player_channels),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        TextButton(onClick = { showChannelOverlay = false }) {
                            Text(stringResource(R.string.action_close), color = TurquoiseAccent)
                        }
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(channels, key = { it.id }) { ch ->
                            val isSelected = ch.id == target?.mediaId
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onSwitchChannel(ch)
                                        showChannelOverlay = false
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) ModernPurple.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.06f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = ch.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAudioDialog) {
        TrackSelectionDialog(
            title = stringResource(R.string.player_audio_tracks),
            tracks = playerState.audioTracks,
            includeOffOption = false,
            onSelect = { opt ->
                playerController.selectTrack(C.TRACK_TYPE_AUDIO, opt)
                showAudioDialog = false
            },
            onDismiss = { showAudioDialog = false }
        )
    }

    if (showSubtitleDialog) {
        SubtitleSettingsAndTrackDialog(
            tracks = playerState.subtitleTracks,
            sizeSp = preferences.subtitleSizeSp,
            delayMs = preferences.subtitleDelayMs,
            bgOpacity = preferences.subtitleBgOpacity,
            onSelectTrack = { opt -> playerController.selectTrack(C.TRACK_TYPE_TEXT, opt) },
            onSizeChange = { newSize -> onSubtitleStyleChange(newSize, preferences.subtitleBgOpacity) },
            onOpacityChange = { newOp -> onSubtitleStyleChange(preferences.subtitleSizeSp, newOp) },
            onDelayChange = onSubtitleDelayChange,
            onDismiss = { showSubtitleDialog = false }
        )
    }

    if (showSpeedDialog) {
        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text(stringResource(R.string.player_speed)) },
            text = {
                Column {
                    speeds.forEach { spd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playerController.setPlaybackSpeed(spd)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = playerState.playbackSpeed == spd, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text("${spd}x")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }
}

@Composable
private fun TrackSelectionDialog(
    title: String,
    tracks: List<TrackOption>,
    includeOffOption: Boolean,
    onSelect: (TrackOption?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (includeOffOption) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(null) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = tracks.none { it.isSelected }, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.player_off))
                    }
                }
                if (tracks.isEmpty()) {
                    Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    tracks.forEach { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(track) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = track.isSelected, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(track.label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun SubtitleSettingsAndTrackDialog(
    tracks: List<TrackOption>,
    sizeSp: Float,
    delayMs: Long,
    bgOpacity: Float,
    onSelectTrack: (TrackOption?) -> Unit,
    onSizeChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onDelayChange: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_subtitles)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTrack(null) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = tracks.none { it.isSelected }, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.player_off))
                }
                tracks.forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTrack(track) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = track.isSelected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(track.label)
                    }
                }

                Text(
                    text = "${stringResource(R.string.player_sub_size)}: ${sizeSp.toInt()}sp",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = sizeSp,
                    onValueChange = onSizeChange,
                    valueRange = 12f..32f
                )

                Text(
                    text = "Background Opacity: ${(bgOpacity * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = bgOpacity,
                    onValueChange = onOpacityChange,
                    valueRange = 0f..1f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.player_sub_delay)}: ${delayMs}ms",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { onDelayChange(delayMs - 250L) }) { Text("-0.25s") }
                        OutlinedButton(onClick = { onDelayChange(delayMs + 250L) }) { Text("+0.25s") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return if (hrs > 0) "%d:%02d:%02d".format(hrs, mins, secs) else "%02d:%02d".format(mins, secs)
}
