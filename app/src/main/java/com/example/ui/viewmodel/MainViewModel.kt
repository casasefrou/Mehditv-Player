package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.example.MehdiTvApplication
import com.example.R
import com.example.core.model.ActivationStatus
import com.example.core.model.AppLanguage
import com.example.core.model.BufferPreset
import com.example.core.model.DecoderMode
import com.example.core.model.HttpHeaderEntry
import com.example.core.model.LiveViewMode
import com.example.core.model.MediaHistoryType
import com.example.core.model.PlaybackTarget
import com.example.core.model.PlaylistImportPreview
import com.example.core.model.StartupScreen
import com.example.core.model.ThemeMode
import com.example.core.model.UserPreferencesState
import com.example.core.security.DeviceIdentityManager
import com.example.core.security.KeystoreCryptoManager
import com.example.data.local.entities.ChannelEntity
import com.example.data.local.entities.EpisodeEntity
import com.example.data.local.entities.MovieEntity
import com.example.data.local.entities.PlaylistEntity
import com.example.data.local.entities.SeriesEntity
import com.example.data.local.entities.WatchHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class HomeCatalogSnapshot(
    val channelCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val radioCount: Int = 0,
    val continueWatching: List<WatchHistoryEntity> = emptyList(),
    val recentlyWatched: List<WatchHistoryEntity> = emptyList(),
    val favoriteChannels: List<ChannelEntity> = emptyList(),
    val recentMovies: List<MovieEntity> = emptyList(),
    val recentSeries: List<SeriesEntity> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MehdiTvApplication
    private val prefsRepo = app.preferencesRepository
    private val playlistRepo = app.playlistRepository
    private val activationClient = app.activationPairingClient
    val playerController = app.playerController

    private val json = Json { ignoreUnknownKeys = true }

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isOnline = MutableStateFlow(playlistRepo.isNetworkAvailable())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _epgStatusMessage = MutableStateFlow<String?>(null)
    val epgStatusMessage: StateFlow<String?> = _epgStatusMessage.asStateFlow()

    val preferences: StateFlow<UserPreferencesState> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferencesState())

    val allPlaylists: StateFlow<List<PlaylistEntity>> = playlistRepo.observeAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePlaylist: StateFlow<PlaylistEntity?> = playlistRepo.observeActivePlaylist()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val homeSnapshot: StateFlow<HomeCatalogSnapshot> = activePlaylist.flatMapLatest { playlist ->
        if (playlist == null) {
            flowOf(HomeCatalogSnapshot())
        } else {
            val pid = playlist.id
            val countsFlow = combine(
                playlistRepo.observeChannelCount(pid),
                playlistRepo.observeMovieCount(pid),
                playlistRepo.observeSeriesCount(pid),
                playlistRepo.observeRadioCount(pid)
            ) { ch, mov, ser, rad -> listOf(ch, mov, ser, rad) }

            val shelvesFlow = combine(
                playlistRepo.observeContinueWatching(pid),
                playlistRepo.observeRecentlyWatched(pid),
                playlistRepo.observeFavoriteChannels(pid),
                playlistRepo.observeRecentMovies(pid),
                playlistRepo.observeRecentSeries(pid)
            ) { cw, rw, favCh, recMov, recSer ->
                HomeCatalogSnapshot(
                    continueWatching = cw,
                    recentlyWatched = rw,
                    favoriteChannels = favCh,
                    recentMovies = recMov,
                    recentSeries = recSer
                )
            }

            combine(countsFlow, shelvesFlow) { counts, shelves ->
                shelves.copy(
                    channelCount = counts[0],
                    movieCount = counts[1],
                    seriesCount = counts[2],
                    radioCount = counts[3]
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeCatalogSnapshot())

    init {
        viewModelScope.launch {
            prefsRepo.ensureDeviceIdentityInitialized()
            _isOnline.value = playlistRepo.isNetworkAvailable()
        }
    }

    fun checkConnectivity() {
        _isOnline.value = playlistRepo.isNetworkAvailable()
    }

    fun refreshActivePlaylist() {
        val current = activePlaylist.value ?: return
        refreshPlaylistById(current.id)
    }

    fun refreshPlaylistById(playlistId: Long) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _isOnline.value = playlistRepo.isNetworkAvailable()
            val result = playlistRepo.refreshPlaylist(playlistId)
            _isRefreshing.value = false
            result.onSuccess {
                _events.tryEmit(app.getString(R.string.home_refresh_success))
            }.onFailure { err ->
                _events.tryEmit(err.message ?: app.getString(R.string.playlist_error_unreachable))
            }
        }
    }

    fun refreshEpg() {
        viewModelScope.launch {
            _epgStatusMessage.value = app.getString(R.string.loading)
            val res = playlistRepo.refreshEpgForActivePlaylist()
            _epgStatusMessage.value = res.fold(
                onSuccess = { count -> "$count programs" },
                onFailure = { it.message }
            )
        }
    }

    fun saveXtreamPlaylist(
        existingId: Long?,
        name: String,
        serverUrl: String,
        port: String,
        username: String,
        password: String,
        customUserAgent: String,
        useCustomUserAgent: Boolean,
        headersText: String,
        activateNow: Boolean = true,
        onDone: (Result<Long>) -> Unit
    ) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val res = playlistRepo.saveXtreamPlaylist(
                existingId = existingId,
                name = name,
                serverUrl = serverUrl,
                port = port,
                username = username,
                password = password,
                customUserAgent = customUserAgent,
                useCustomUserAgent = useCustomUserAgent,
                rawHeadersText = headersText,
                activateNow = activateNow
            )
            _isRefreshing.value = false
            res.onFailure { _events.tryEmit(it.message ?: app.getString(R.string.playlist_error_invalid)) }
            onDone(res)
        }
    }

    fun saveM3uUrlPlaylist(
        existingId: Long?,
        name: String,
        m3uUrl: String,
        epgUrl: String,
        customUserAgent: String,
        useCustomUserAgent: Boolean,
        headersText: String,
        activateNow: Boolean = true,
        onDone: (Result<Long>) -> Unit
    ) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val res = playlistRepo.saveM3uUrlPlaylist(
                existingId = existingId,
                name = name,
                m3uUrl = m3uUrl,
                epgUrl = epgUrl,
                customUserAgent = customUserAgent,
                useCustomUserAgent = useCustomUserAgent,
                rawHeadersText = headersText,
                activateNow = activateNow
            )
            _isRefreshing.value = false
            res.onFailure { _events.tryEmit(it.message ?: app.getString(R.string.playlist_error_invalid)) }
            onDone(res)
        }
    }

    fun importLocalM3u(name: String, uri: Uri, onDone: (Result<Long>) -> Unit) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val res = playlistRepo.importLocalM3uPlaylist(name, uri)
            _isRefreshing.value = false
            res.onFailure { _events.tryEmit(it.message ?: app.getString(R.string.playlist_error_invalid)) }
            onDone(res)
        }
    }

    suspend fun findPlaylistByName(name: String): PlaylistEntity? =
        playlistRepo.findPlaylistByName(name)

    fun previewLocalM3u(name: String, uri: Uri, onResult: (Result<PlaylistImportPreview>) -> Unit) {
        viewModelScope.launch {
            val res = playlistRepo.previewLocalM3u(name, uri)
            onResult(res)
        }
    }

    fun saveConfirmedLocalM3u(
        existingId: Long?,
        preview: PlaylistImportPreview,
        epgUrl: String,
        customUserAgent: String,
        useCustomUserAgent: Boolean,
        activateNow: Boolean,
        onDone: (Result<Long>) -> Unit
    ) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val res = playlistRepo.saveConfirmedLocalM3u(
                existingId = existingId,
                preview = preview,
                epgUrl = epgUrl,
                customUserAgent = customUserAgent,
                useCustomUserAgent = useCustomUserAgent,
                activateNow = activateNow
            )
            _isRefreshing.value = false
            res.onFailure { _events.tryEmit(it.message ?: app.getString(R.string.playlist_error_invalid)) }
            onDone(res)
        }
    }

    fun testM3uUrlConnection(
        name: String,
        m3uUrl: String,
        epgUrl: String,
        customUserAgent: String,
        useCustomUserAgent: Boolean,
        onResult: (Result<PlaylistImportPreview>) -> Unit
    ) {
        viewModelScope.launch {
            val res = playlistRepo.testM3uUrlConnection(
                name = name,
                m3uUrl = m3uUrl,
                epgUrl = epgUrl,
                customUserAgent = customUserAgent,
                useCustomUserAgent = useCustomUserAgent
            )
            onResult(res)
        }
    }

    fun testXtreamConnection(
        name: String,
        serverUrl: String,
        port: String,
        username: String,
        password: String,
        customUserAgent: String,
        useCustomUserAgent: Boolean,
        onResult: (Result<PlaylistImportPreview>) -> Unit
    ) {
        viewModelScope.launch {
            val res = playlistRepo.testXtreamConnection(
                name = name,
                serverUrl = serverUrl,
                port = port,
                username = username,
                password = password,
                customUserAgent = customUserAgent,
                useCustomUserAgent = useCustomUserAgent
            )
            onResult(res)
        }
    }

    fun setActivePlaylist(id: Long) {
        viewModelScope.launch { playlistRepo.setActivePlaylist(id) }
    }

    fun setDefaultPlaylist(id: Long) {
        viewModelScope.launch { playlistRepo.setDefaultPlaylist(id) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { playlistRepo.deletePlaylist(id) }
    }

    fun decodePlaylistHeadersText(playlist: PlaylistEntity): String =
        playlistRepo.decodePlaylistHeadersAsText(playlist)

    // --- QR Pairing & Device Activation ---
    fun regeneratePairingToken() {
        viewModelScope.launch {
            val prefs = preferences.value
            if (prefs.backendBaseUrl.isNotBlank()) {
                val remote = activationClient.requestBackendPairingSession(prefs.backendBaseUrl, prefs.deviceId)
                remote.onSuccess { session ->
                    prefsRepo.updatePairingTokenFromBackend(session.pairingToken, session.expiresAtMs)
                    return@launch
                }
            }
            prefsRepo.regeneratePairingToken()
        }
    }

    fun verifyActivationStatus() {
        viewModelScope.launch {
            val prefs = preferences.value
            if (prefs.backendBaseUrl.isBlank()) {
                prefsRepo.updateActivationState(ActivationStatus.LOCAL_MODE, null)
                _events.tryEmit(app.getString(R.string.pairing_no_backend_error))
                return@launch
            }
            val pkgInfo = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
            val versionName = pkgInfo?.versionName ?: "1.0.0"
            val result = activationClient.fetchActivationStatus(
                backendBaseUrl = prefs.backendBaseUrl,
                deviceId = prefs.deviceId,
                appVersion = versionName,
                androidSdk = Build.VERSION.SDK_INT
            )
            result.onSuccess { (status, expMs) ->
                prefsRepo.updateActivationState(status, expMs)
            }.onFailure { err ->
                _events.tryEmit(err.message ?: app.getString(R.string.playlist_error_unreachable))
            }
        }
    }

    fun syncPairedPlaylists() {
        viewModelScope.launch {
            val prefs = preferences.value
            if (prefs.backendBaseUrl.isBlank()) {
                _events.tryEmit(app.getString(R.string.pairing_no_backend_error))
                return@launch
            }
            _isRefreshing.value = true
            val res = activationClient.fetchPairedPlaylists(
                backendBaseUrl = prefs.backendBaseUrl,
                deviceId = prefs.deviceId,
                pairingToken = prefs.pairingToken
            )
            res.onSuccess { items ->
                if (items.isEmpty()) {
                    _events.tryEmit(app.getString(R.string.playlist_error_empty))
                } else {
                    items.forEach { dto ->
                        if (dto.type.equals("XTREAM", ignoreCase = true)) {
                            playlistRepo.saveXtreamPlaylist(
                                existingId = null,
                                name = dto.name,
                                serverUrl = dto.serverUrl,
                                port = dto.port,
                                username = dto.username,
                                password = dto.password,
                                customUserAgent = dto.userAgent,
                                useCustomUserAgent = dto.userAgent.isNotBlank(),
                                rawHeadersText = ""
                            )
                        } else {
                            playlistRepo.saveM3uUrlPlaylist(
                                existingId = null,
                                name = dto.name,
                                m3uUrl = dto.m3uUrl,
                                epgUrl = dto.epgUrl,
                                customUserAgent = dto.userAgent,
                                useCustomUserAgent = dto.userAgent.isNotBlank(),
                                rawHeadersText = ""
                            )
                        }
                    }
                }
            }.onFailure { err ->
                _events.tryEmit(err.message ?: app.getString(R.string.playlist_error_unreachable))
            }
            _isRefreshing.value = false
        }
    }

    // --- Playback Launching ---
    fun playChannel(channel: ChannelEntity, onReady: () -> Unit) {
        viewModelScope.launch {
            val prefs = preferences.value
            val (ua, playlistHeaders) = playlistRepo.resolveHeadersAndUaForPlaylist(channel.playlistId)
            val channelHeaders = parseChannelHeaders(channel.customHeadersJson)
            val merged = playlistHeaders + channelHeaders
            val effectiveUa = channelHeaders["User-Agent"] ?: ua

            playerController.startPlayback(
                target = PlaybackTarget(
                    mediaId = channel.id,
                    playlistId = channel.playlistId,
                    mediaType = MediaHistoryType.LIVE,
                    title = channel.name,
                    subtitle = channel.categoryName,
                    streamUrl = channel.streamUrl,
                    posterUrl = channel.logoUrl,
                    epgChannelId = channel.epgChannelId,
                    userAgent = effectiveUa,
                    customHeaders = merged
                ),
                bufferPreset = prefs.bufferPreset,
                decoderMode = prefs.decoderMode,
                preferredAudioLang = prefs.preferredAudioLang
            )
            onReady()
        }
    }

    fun playMovie(movie: MovieEntity, resume: Boolean, onReady: () -> Unit) {
        viewModelScope.launch {
            val prefs = preferences.value
            val (ua, headers) = playlistRepo.resolveHeadersAndUaForPlaylist(movie.playlistId)
            val resumeMs = if (resume) {
                playlistRepo.getWatchHistoryForMedia(movie.id)?.positionMs ?: 0L
            } else {
                0L
            }
            playerController.startPlayback(
                target = PlaybackTarget(
                    mediaId = movie.id,
                    playlistId = movie.playlistId,
                    mediaType = MediaHistoryType.MOVIE,
                    title = movie.name,
                    subtitle = listOfNotNull(
                        movie.year.takeIf { it.isNotBlank() },
                        movie.genre.takeIf { it.isNotBlank() }
                    ).joinToString(" • "),
                    streamUrl = movie.streamUrl,
                    posterUrl = movie.posterUrl,
                    resumePositionMs = resumeMs,
                    userAgent = ua,
                    customHeaders = headers
                ),
                bufferPreset = prefs.bufferPreset,
                decoderMode = prefs.decoderMode,
                preferredAudioLang = prefs.preferredAudioLang
            )
            onReady()
        }
    }

    fun playEpisode(series: SeriesEntity, episode: EpisodeEntity, resume: Boolean, onReady: () -> Unit) {
        viewModelScope.launch {
            val prefs = preferences.value
            val (ua, headers) = playlistRepo.resolveHeadersAndUaForPlaylist(series.playlistId)
            val resumeMs = if (resume) {
                playlistRepo.getWatchHistoryForMedia(episode.id)?.positionMs ?: 0L
            } else {
                0L
            }
            playerController.startPlayback(
                target = PlaybackTarget(
                    mediaId = episode.id,
                    playlistId = series.playlistId,
                    mediaType = MediaHistoryType.EPISODE,
                    title = "${series.name} • S${episode.seasonNumber}E${episode.episodeNumber}",
                    subtitle = episode.title,
                    streamUrl = episode.streamUrl,
                    posterUrl = episode.thumbnailUrl.ifBlank { series.posterUrl },
                    seriesId = series.id,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    resumePositionMs = resumeMs,
                    userAgent = ua,
                    customHeaders = headers
                ),
                bufferPreset = prefs.bufferPreset,
                decoderMode = prefs.decoderMode,
                preferredAudioLang = prefs.preferredAudioLang
            )
            onReady()
        }
    }

    fun playFromHistory(entry: WatchHistoryEntity, onReady: () -> Unit) {
        viewModelScope.launch {
            val prefs = preferences.value
            val (ua, headers) = playlistRepo.resolveHeadersAndUaForPlaylist(entry.playlistId)
            val mediaType = runCatching { MediaHistoryType.valueOf(entry.mediaType) }
                .getOrDefault(MediaHistoryType.MOVIE)
            playerController.startPlayback(
                target = PlaybackTarget(
                    mediaId = entry.mediaId,
                    playlistId = entry.playlistId,
                    mediaType = mediaType,
                    title = entry.title,
                    subtitle = entry.subtitle,
                    streamUrl = entry.streamUrl,
                    posterUrl = entry.posterUrl,
                    seriesId = entry.seriesId,
                    seasonNumber = entry.seasonNumber,
                    episodeNumber = entry.episodeNumber,
                    resumePositionMs = entry.positionMs,
                    userAgent = ua,
                    customHeaders = headers
                ),
                bufferPreset = prefs.bufferPreset,
                decoderMode = prefs.decoderMode,
                preferredAudioLang = prefs.preferredAudioLang
            )
            onReady()
        }
    }

    suspend fun findNextEpisodeForTarget(target: PlaybackTarget): Pair<SeriesEntity, EpisodeEntity>? {
        if (target.mediaType != MediaHistoryType.EPISODE || target.seriesId.isBlank()) return null
        val series = playlistRepo.getSeriesById(target.seriesId) ?: return null
        val episodes = playlistRepo.getEpisodesForSeriesOnce(target.seriesId)
        val idx = episodes.indexOfFirst { it.id == target.mediaId }
        if (idx < 0 || idx + 1 >= episodes.size) return null
        return series to episodes[idx + 1]
    }

    // --- Settings Actions ---
    fun setLanguage(lang: AppLanguage) = viewModelScope.launch { prefsRepo.setLanguage(lang) }
    fun toggleLanguage() {
        val next = if (preferences.value.language == AppLanguage.ARABIC) {
            AppLanguage.ENGLISH
        } else {
            AppLanguage.ARABIC
        }
        setLanguage(next)
    }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    fun setShowClockOnHome(show: Boolean) = viewModelScope.launch { prefsRepo.setShowClockOnHome(show) }
    fun setStartupScreen(screen: StartupScreen) = viewModelScope.launch { prefsRepo.setStartupScreen(screen) }
    fun setGlobalUserAgent(ua: String, enabled: Boolean) =
        viewModelScope.launch { prefsRepo.setGlobalUserAgent(ua, enabled) }
    fun setBufferPreset(preset: BufferPreset) = viewModelScope.launch { prefsRepo.setBufferPreset(preset) }
    fun setDecoderMode(mode: DecoderMode) = viewModelScope.launch { prefsRepo.setDecoderMode(mode) }
    fun setSubtitleAppearance(sizeSp: Float, bgOpacity: Float) =
        viewModelScope.launch { prefsRepo.setSubtitleAppearance(sizeSp, bgOpacity) }
    fun setSubtitleDelayMs(delayMs: Long) = viewModelScope.launch { prefsRepo.setSubtitleDelayMs(delayMs) }
    fun setPreferredAudioLang(lang: String) = viewModelScope.launch { prefsRepo.setPreferredAudioLang(lang) }
    fun setAutoplayNextEpisode(enabled: Boolean) = viewModelScope.launch { prefsRepo.setAutoplayNextEpisode(enabled) }
    fun setEpgTimezoneOffsetMinutes(offsetMin: Int) =
        viewModelScope.launch { prefsRepo.setEpgTimezoneOffsetMinutes(offsetMin) }
    fun setLiveViewMode(mode: LiveViewMode) = viewModelScope.launch { prefsRepo.setLiveViewMode(mode) }

    fun setParentalPin(pin: String) = viewModelScope.launch { prefsRepo.setParentalPin(pin) }
    fun clearParentalPin() = viewModelScope.launch { prefsRepo.clearParentalPin() }
    suspend fun verifyParentalPin(pin: String): Boolean = prefsRepo.verifyParentalPin(pin)
    fun setLockSettings(locked: Boolean) = viewModelScope.launch { prefsRepo.setLockSettings(locked) }
    fun setLockPlaylistEdits(locked: Boolean) = viewModelScope.launch { prefsRepo.setLockPlaylistEdits(locked) }
    fun setCategoryHidden(categoryId: String, hidden: Boolean) =
        viewModelScope.launch { playlistRepo.setCategoryHidden(categoryId, hidden) }
    fun setCategoryLocked(categoryId: String, locked: Boolean) =
        viewModelScope.launch { playlistRepo.setCategoryLocked(categoryId, locked) }

    fun saveBackendBaseUrl(url: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = prefsRepo.setBackendBaseUrl(url)
            onResult(ok)
        }
    }

    fun clearImageCache(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            app.imageLoader.memoryCache?.clear()
            app.imageLoader.diskCache?.clear()
            onDone()
        }
    }

    suspend fun exportSettingsJson(): String = prefsRepo.exportSettingsBackupJson()

    fun restoreSettingsJson(raw: String, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val res = prefsRepo.restoreSettingsBackupJson(raw)
            onDone(res)
        }
    }

    fun buildDiagnosticsText(): String {
        val prefs = preferences.value
        val active = activePlaylist.value
        val snap = homeSnapshot.value
        return buildString {
            appendLine("MehdiTv Player Diagnostics")
            appendLine("Device ID: ${prefs.deviceId}")
            appendLine("Android SDK: ${Build.VERSION.SDK_INT} (${Build.MODEL})")
            appendLine("Keystore AES-GCM: ${if (KeystoreCryptoManager.isKeystoreAvailable()) "Active" else "Fallback"}")
            appendLine("Network Online: ${playlistRepo.isNetworkAvailable()}")
            appendLine("Active Playlist: ${active?.name ?: "None"} (${active?.type ?: "-"})")
            appendLine("Active Status: ${active?.status ?: "-"}")
            appendLine("Catalog: ${snap.channelCount} Live, ${snap.movieCount} Movies, ${snap.seriesCount} Series")
            appendLine("Buffer Preset: ${prefs.bufferPreset.name}")
            appendLine("Decoder Mode: ${prefs.decoderMode.name}")
            if (active != null) {
                val effectiveUa = playlistRepo.resolveEffectiveUserAgent(active, prefs)
                appendLine("Effective User-Agent: $effectiveUa")
                if (!active.lastError.isNullOrBlank()) {
                    appendLine("Last Error: ${DeviceIdentityManager.redactUrl(active.lastError)}")
                }
            }
        }
    }

    private fun parseChannelHeaders(jsonStr: String): Map<String, String> {
        if (jsonStr.isBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString<List<HttpHeaderEntry>>(jsonStr).associate { it.key to it.value }
        }.getOrDefault(emptyMap())
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            ViewModelProvider.AndroidViewModelFactory.getInstance(Application())
    }
}
