package com.example.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.core.model.BufferPreset
import com.example.core.model.DecoderMode
import com.example.core.model.MediaHistoryType
import com.example.core.model.PlaybackTarget
import com.example.core.model.TrackOption
import com.example.core.model.UserPreferencesState
import com.example.data.local.entities.WatchHistoryEntity
import com.example.data.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class PlayerUiState(
    val target: PlaybackTarget? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isReconnecting: Boolean = false,
    val reconnectAttempt: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val errorMessage: String? = null,
    val hasEnded: Boolean = false
)

/**
 * Media3 ExoPlayer controller supporting HLS, DASH, progressive HTTP/MP4, custom User-Agent,
 * custom HTTP headers, buffer presets, hardware/software decoder fallback, track selection,
 * and automatic network reconnection.
 */
@OptIn(UnstableApi::class)
class MehdiPlayerController(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val playlistRepository: PlaylistRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer? get() = _exoPlayer

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var currentHeaders: Map<String, String> = emptyMap()
    private var currentUserAgent: String = UserPreferencesState.DEFAULT_USER_AGENT
    private var progressJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastSavedAtMs: Long = 0L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val buffering = playbackState == Player.STATE_BUFFERING
            val ended = playbackState == Player.STATE_ENDED
            if (playbackState == Player.STATE_READY) {
                reconnectJob?.cancel()
                _state.update {
                    it.copy(
                        isBuffering = false,
                        isReconnecting = false,
                        reconnectAttempt = 0,
                        errorMessage = null,
                        durationMs = (_exoPlayer?.duration ?: 0L).coerceAtLeast(0L)
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isBuffering = buffering,
                        hasEnded = ended
                    )
                }
            }
            if (ended) {
                saveProgressNow(force = true)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            _state.update {
                it.copy(
                    audioTracks = extractTracks(tracks, C.TRACK_TYPE_AUDIO),
                    subtitleTracks = extractTracks(tracks, C.TRACK_TYPE_TEXT)
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val isNetworkError = error.errorCode in setOf(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
            )
            if (isNetworkError && _state.value.reconnectAttempt < 4) {
                scheduleReconnect()
            } else {
                _state.update {
                    it.copy(
                        isBuffering = false,
                        isReconnecting = false,
                        errorMessage = error.localizedMessage ?: "Playback failed (${error.errorCodeName})"
                    )
                }
            }
        }
    }

    fun startPlayback(
        target: PlaybackTarget,
        bufferPreset: BufferPreset,
        decoderMode: DecoderMode,
        preferredAudioLang: String
    ) {
        reconnectJob?.cancel()
        currentUserAgent = target.userAgent.ifBlank { UserPreferencesState.DEFAULT_USER_AGENT }
        currentHeaders = target.customHeaders

        ensurePlayerCreated(bufferPreset, decoderMode, preferredAudioLang)
        val player = _exoPlayer ?: return

        _state.value = PlayerUiState(
            target = target,
            isBuffering = true,
            positionMs = target.resumePositionMs,
            playbackSpeed = 1.0f
        )

        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(target.streamUrl))
        val lowerUrl = target.streamUrl.lowercase()
        when {
            lowerUrl.contains(".m3u8") -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            lowerUrl.contains(".mpd") -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
        }

        player.setMediaItem(mediaItemBuilder.build())
        player.prepare()
        if (target.resumePositionMs > 2000L && target.mediaType != MediaHistoryType.LIVE) {
            player.seekTo(target.resumePositionMs)
        }
        player.playWhenReady = true
        startProgressTicker()
    }

    private fun ensurePlayerCreated(
        bufferPreset: BufferPreset,
        decoderMode: DecoderMode,
        preferredAudioLang: String
    ) {
        _exoPlayer?.let {
            it.removeListener(playerListener)
            it.release()
        }

        val dynamicOkHttp = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val reqBuilder = chain.request().newBuilder()
                    .header("User-Agent", currentUserAgent)
                currentHeaders.forEach { (k, v) ->
                    if (!k.equals("User-Agent", ignoreCase = true) && k.isNotBlank()) {
                        reqBuilder.header(k, v)
                    }
                }
                chain.proceed(reqBuilder.build())
            }
            .build()

        val httpFactory = OkHttpDataSource.Factory(dynamicOkHttp)
            .setUserAgent(currentUserAgent)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferPreset.minBufferMs,
                bufferPreset.maxBufferMs,
                bufferPreset.bufferForPlaybackMs,
                bufferPreset.bufferForPlaybackAfterRebufferMs
            )
            .build()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(
                if (decoderMode == DecoderMode.SOFTWARE_FALLBACK) {
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                } else {
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                }
            )
            setEnableDecoderFallback(true)
        }

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        if (preferredAudioLang.isNotBlank()) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setPreferredAudioLanguage(preferredAudioLang)
                .build()
        }

        player.addListener(playerListener)
        _exoPlayer = player
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        val nextAttempt = _state.value.reconnectAttempt + 1
        _state.update {
            it.copy(
                isReconnecting = true,
                reconnectAttempt = nextAttempt,
                errorMessage = null
            )
        }
        reconnectJob = scope.launch {
            delay(nextAttempt * 1500L)
            val player = _exoPlayer ?: return@launch
            val currentPos = player.currentPosition
            player.prepare()
            if (currentPos > 0 && _state.value.target?.mediaType != MediaHistoryType.LIVE) {
                player.seekTo(currentPos)
            }
            player.playWhenReady = true
        }
    }

    fun retry() {
        val player = _exoPlayer ?: return
        _state.update { it.copy(errorMessage = null, reconnectAttempt = 0, isBuffering = true) }
        player.prepare()
        player.playWhenReady = true
    }

    fun togglePlayPause() {
        val player = _exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun pause() {
        _exoPlayer?.pause()
        saveProgressNow(force = true)
    }

    fun seekTo(positionMs: Long) {
        _exoPlayer?.seekTo(positionMs.coerceAtLeast(0L))
        _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun seekBy(deltaMs: Long) {
        val player = _exoPlayer ?: return
        val next = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        player.seekTo(next)
        _state.update { it.copy(positionMs = next) }
    }

    fun setPlaybackSpeed(speed: Float) {
        _exoPlayer?.setPlaybackSpeed(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    fun selectTrack(trackType: Int, option: TrackOption?) {
        val player = _exoPlayer ?: return
        val builder = player.trackSelectionParameters.buildUpon()
        if (option == null) {
            builder.setTrackTypeDisabled(trackType, true)
        } else {
            val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
            builder.setTrackTypeDisabled(trackType, false)
            builder.setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex)
            )
        }
        player.trackSelectionParameters = builder.build()
    }

    private fun extractTracks(tracks: Tracks, trackType: Int): List<TrackOption> {
        val list = mutableListOf<TrackOption>()
        tracks.groups.forEachIndexed { groupIdx, group ->
            if (group.type == trackType && group.isSupported) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val lang = format.language
                    val label = buildString {
                        append(format.label ?: lang?.uppercase() ?: "Track ${list.size + 1}")
                        if (format.bitrate > 0) {
                            append(" (${format.bitrate / 1000} kbps)")
                        }
                    }
                    list.add(
                        TrackOption(
                            groupIndex = groupIdx,
                            trackIndex = i,
                            label = label,
                            language = lang,
                            isSelected = group.isTrackSelected(i)
                        )
                    )
                }
            }
        }
        return list
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = _exoPlayer
                if (player != null) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.coerceAtLeast(0L)
                    _state.update { it.copy(positionMs = pos, durationMs = dur) }
                    if (System.currentTimeMillis() - lastSavedAtMs >= 5000L) {
                        saveProgressNow(force = false)
                    }
                }
                delay(500L)
            }
        }
    }

    fun saveProgressNow(force: Boolean = false) {
        val target = _state.value.target ?: return
        val player = _exoPlayer ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.coerceAtLeast(0L)
        if (!force && pos < 1000L && target.mediaType != MediaHistoryType.LIVE) return
        lastSavedAtMs = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            playlistRepository.recordWatchProgress(
                WatchHistoryEntity(
                    mediaId = target.mediaId,
                    playlistId = target.playlistId,
                    mediaType = target.mediaType.name,
                    seriesId = target.seriesId,
                    seasonNumber = target.seasonNumber,
                    episodeNumber = target.episodeNumber,
                    title = target.title,
                    subtitle = target.subtitle,
                    posterUrl = target.posterUrl,
                    streamUrl = target.streamUrl,
                    positionMs = if (target.mediaType == MediaHistoryType.LIVE) 0L else pos,
                    durationMs = if (target.mediaType == MediaHistoryType.LIVE) 0L else dur,
                    lastWatchedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun stopAndRelease() {
        saveProgressNow(force = true)
        progressJob?.cancel()
        reconnectJob?.cancel()
        _exoPlayer?.let {
            it.removeListener(playerListener)
            it.release()
        }
        _exoPlayer = null
        _state.value = PlayerUiState()
    }
}
