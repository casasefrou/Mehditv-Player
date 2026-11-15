package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.example.R
import com.example.core.model.HttpHeaderEntry
import com.example.core.model.PlaylistImportPreview
import com.example.core.model.PlaylistStatus
import com.example.core.model.PlaylistType
import com.example.core.model.UserPreferencesState
import com.example.core.security.DeviceIdentityManager
import com.example.core.security.KeystoreCryptoManager
import com.example.data.local.AppPreferencesRepository
import com.example.data.local.MehdiTvDatabase
import com.example.data.local.entities.CategoryEntity
import com.example.data.local.entities.ChannelEntity
import com.example.data.local.entities.EpisodeEntity
import com.example.data.local.entities.MovieEntity
import com.example.data.local.entities.PlaylistEntity
import com.example.data.local.entities.SeriesEntity
import com.example.data.local.entities.WatchHistoryEntity
import com.example.data.parser.M3uParser
import com.example.data.parser.XmltvParser
import com.example.data.remote.XtreamAuthException
import com.example.data.remote.XtreamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class PlaylistRepository(
    private val context: Context,
    private val database: MehdiTvDatabase,
    private val prefsRepo: AppPreferencesRepository,
    private val okHttpClient: OkHttpClient,
    private val xtreamClient: XtreamClient
) {
    private val playlistDao = database.playlistDao()
    private val catalogDao = database.catalogDao()
    private val epgDao = database.epgDao()
    private val watchHistoryDao = database.watchHistoryDao()
    private val json = Json { ignoreUnknownKeys = true }

    private val refreshLocks = ConcurrentHashMap<Long, Mutex>()

    fun observeAllPlaylists(): Flow<List<PlaylistEntity>> = playlistDao.observeAllPlaylists()
    fun observeActivePlaylist(): Flow<PlaylistEntity?> = playlistDao.observeActivePlaylist()

    fun observeChannelCount(playlistId: Long): Flow<Int> = catalogDao.observeChannelCount(playlistId)
    fun observeRadioCount(playlistId: Long): Flow<Int> = catalogDao.observeRadioCount(playlistId)
    fun observeMovieCount(playlistId: Long): Flow<Int> = catalogDao.observeMovieCount(playlistId)
    fun observeSeriesCount(playlistId: Long): Flow<Int> = catalogDao.observeSeriesCount(playlistId)

    fun observeCategories(playlistId: Long, section: String, includeHidden: Boolean = false) =
        catalogDao.observeCategories(playlistId, section, includeHidden)

    fun observeChannels(playlistId: Long) = catalogDao.observeAllChannels(playlistId)
    fun observeFavoriteChannels(playlistId: Long) = catalogDao.observeFavoriteChannels(playlistId)
    fun observeMovies(playlistId: Long) = catalogDao.observeMovies(playlistId)
    fun observeRecentMovies(playlistId: Long) = catalogDao.observeRecentMovies(playlistId)
    fun observeMovieById(movieId: String) = catalogDao.observeMovieById(movieId)
    fun observeSeries(playlistId: Long) = catalogDao.observeSeries(playlistId)
    fun observeRecentSeries(playlistId: Long) = catalogDao.observeRecentSeries(playlistId)
    fun observeSeriesById(seriesId: String) = catalogDao.observeSeriesById(seriesId)
    fun observeEpisodesForSeries(seriesId: String) = catalogDao.observeEpisodesForSeries(seriesId)

    fun observeEpgInWindow(playlistId: Long, fromMs: Long, toMs: Long) =
        epgDao.observeProgramsInWindow(playlistId, fromMs, toMs)

    fun observeEpgForChannel(playlistId: Long, epgChannelId: String, fromMs: Long) =
        epgDao.observeProgramsForChannel(playlistId, epgChannelId, fromMs)

    fun observeContinueWatching(playlistId: Long) = watchHistoryDao.observeContinueWatching(playlistId)
    fun observeRecentlyWatched(playlistId: Long) = watchHistoryDao.observeRecentlyWatched(playlistId)
    fun observeRecentLiveIds(playlistId: Long) = watchHistoryDao.observeRecentLiveIds(playlistId)
    fun observeLastWatchedForSeries(seriesId: String) = watchHistoryDao.observeLastWatchedForSeries(seriesId)

    suspend fun getPlaylistById(id: Long): PlaylistEntity? = playlistDao.getById(id)
    suspend fun findPlaylistByName(name: String): PlaylistEntity? =
        playlistDao.getAllOnce().firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }
    suspend fun getChannelById(id: String): ChannelEntity? = catalogDao.getChannelById(id)
    suspend fun getMovieById(id: String): MovieEntity? = catalogDao.getMovieById(id)
    suspend fun getSeriesById(id: String): SeriesEntity? = catalogDao.getSeriesById(id)
    suspend fun getEpisodeById(id: String): EpisodeEntity? = catalogDao.getEpisodeById(id)
    suspend fun getEpisodesForSeriesOnce(seriesId: String) = catalogDao.getEpisodesForSeriesOnce(seriesId)
    suspend fun getWatchHistoryForMedia(mediaId: String) = watchHistoryDao.getHistoryForMedia(mediaId)

    suspend fun setActivePlaylist(id: Long) = playlistDao.setActivePlaylist(id)
    suspend fun setDefaultPlaylist(id: Long) = playlistDao.setDefaultPlaylist(id)
    suspend fun toggleChannelFavorite(channelId: String, fav: Boolean) = catalogDao.setChannelFavorite(channelId, fav)
    suspend fun toggleChannelHidden(channelId: String, hidden: Boolean) = catalogDao.setChannelHidden(channelId, hidden)
    suspend fun toggleMovieFavorite(movieId: String, fav: Boolean) = catalogDao.setMovieFavorite(movieId, fav)
    suspend fun toggleSeriesFavorite(seriesId: String, fav: Boolean) = catalogDao.setSeriesFavorite(seriesId, fav)
    suspend fun setCategoryHidden(categoryId: String, hidden: Boolean) = catalogDao.setCategoryHidden(categoryId, hidden)
    suspend fun setCategoryLocked(categoryId: String, locked: Boolean) = catalogDao.setCategoryLocked(categoryId, locked)

    suspend fun recordWatchProgress(entry: WatchHistoryEntity) {
        watchHistoryDao.upsert(entry)
    }

    suspend fun deletePlaylist(id: Long) {
        withContext(Dispatchers.IO) {
            catalogDao.deleteCategoriesByPlaylist(id)
            catalogDao.deleteChannelsByPlaylist(id)
            catalogDao.deleteMoviesByPlaylist(id)
            catalogDao.deleteSeriesByPlaylist(id)
            catalogDao.deleteEpisodesByPlaylist(id)
            epgDao.deleteByPlaylist(id)
            watchHistoryDao.deleteByPlaylist(id)
            playlistDao.deleteById(id)
            val remaining = playlistDao.getAllOnce()
            if (remaining.isNotEmpty() && remaining.none { it.isActive }) {
                playlistDao.setActivePlaylist(remaining.first().id)
            }
        }
    }

    suspend fun saveXtreamPlaylist(
        existingId: Long? = null,
        name: String,
        serverUrl: String,
        port: String,
        username: String,
        password: String,
        customUserAgent: String,
        useCustomUserAgent: Boolean,
        rawHeadersText: String,
        activateNow: Boolean = true
    ): Result<Long> = withContext(Dispatchers.IO) {
        val deviceId = prefsRepo.ensureDeviceIdentityInitialized()
        val encPass = KeystoreCryptoManager.encrypt(password)
        val encHeaders = encodeHeadersEncrypted(rawHeadersText)
        val entity = if (existingId != null) {
            val prev = playlistDao.getById(existingId) ?: return@withContext Result.failure(
                IllegalStateException("Playlist not found")
            )
            prev.copy(
                name = name.trim(),
                type = PlaylistType.XTREAM.name,
                serverUrl = serverUrl.trim(),
                port = port.trim(),
                username = username.trim(),
                encryptedPassword = if (password.isNotEmpty()) encPass else prev.encryptedPassword,
                customUserAgent = customUserAgent.trim(),
                useCustomUserAgent = useCustomUserAgent,
                encryptedCustomHeadersJson = encHeaders
            )
        } else {
            PlaylistEntity(
                name = name.trim(),
                type = PlaylistType.XTREAM.name,
                serverUrl = serverUrl.trim(),
                port = port.trim(),
                username = username.trim(),
                encryptedPassword = encPass,
                customUserAgent = customUserAgent.trim(),
                useCustomUserAgent = useCustomUserAgent,
                encryptedCustomHeadersJson = encHeaders,
                boundDeviceId = deviceId
            )
        }
        val id = if (existingId != null) {
            playlistDao.update(entity)
            if (activateNow) playlistDao.setActivePlaylist(existingId)
            existingId
        } else {
            val newId = playlistDao.insert(entity)
            val all = playlistDao.getAllOnce()
            if (all.size == 1) {
                playlistDao.setDefaultPlaylist(newId)
                playlistDao.setActivePlaylist(newId)
            } else if (activateNow) {
                playlistDao.setActivePlaylist(newId)
            }
            newId
        }
        val refreshResult = refreshPlaylist(id)
        if (refreshResult.isSuccess) Result.success(id) else Result.failure(refreshResult.exceptionOrNull()!!)
    }

    suspend fun saveM3uUrlPlaylist(
        existingId: Long? = null,
        name: String,
        m3uUrl: String,
        epgUrl: String,
        customUserAgent: String,
        useCustomUserAgent: Boolean,
        rawHeadersText: String,
        activateNow: Boolean = true
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (!DeviceIdentityManager.isValidStreamOrPlaylistUrl(m3uUrl)) {
            return@withContext Result.failure(IllegalArgumentException(context.getString(R.string.playlist_error_invalid)))
        }
        val deviceId = prefsRepo.ensureDeviceIdentityInitialized()
        val encHeaders = encodeHeadersEncrypted(rawHeadersText)
        val entity = if (existingId != null) {
            val prev = playlistDao.getById(existingId) ?: return@withContext Result.failure(
                IllegalStateException("Playlist not found")
            )
            prev.copy(
                name = name.trim(),
                type = PlaylistType.M3U_URL.name,
                m3uUrl = m3uUrl.trim(),
                epgUrl = epgUrl.trim(),
                customUserAgent = customUserAgent.trim(),
                useCustomUserAgent = useCustomUserAgent,
                encryptedCustomHeadersJson = encHeaders
            )
        } else {
            PlaylistEntity(
                name = name.trim(),
                type = PlaylistType.M3U_URL.name,
                m3uUrl = m3uUrl.trim(),
                epgUrl = epgUrl.trim(),
                customUserAgent = customUserAgent.trim(),
                useCustomUserAgent = useCustomUserAgent,
                encryptedCustomHeadersJson = encHeaders,
                boundDeviceId = deviceId
            )
        }
        val id = if (existingId != null) {
            playlistDao.update(entity)
            if (activateNow) playlistDao.setActivePlaylist(existingId)
            existingId
        } else {
            val newId = playlistDao.insert(entity)
            val all = playlistDao.getAllOnce()
            if (all.size == 1) {
                playlistDao.setDefaultPlaylist(newId)
                playlistDao.setActivePlaylist(newId)
            } else if (activateNow) {
                playlistDao.setActivePlaylist(newId)
            }
            newId
        }
        val refreshResult = refreshPlaylist(id)
        if (refreshResult.isSuccess) Result.success(id) else Result.failure(refreshResult.exceptionOrNull()!!)
    }

    suspend fun importLocalM3uPlaylist(
        name: String,
        fileUri: Uri,
        epgUrl: String = "",
        customUserAgent: String = "",
        useCustomUserAgent: Boolean = false
    ): Result<Long> = withContext(Dispatchers.IO) {
        val previewRes = previewLocalM3u(name, fileUri)
        val preview = previewRes.getOrElse { return@withContext Result.failure(it) }
        saveConfirmedLocalM3u(
            existingId = null,
            preview = preview,
            epgUrl = epgUrl,
            customUserAgent = customUserAgent,
            useCustomUserAgent = useCustomUserAgent,
            activateNow = true
        )
    }

    suspend fun previewLocalM3u(name: String, fileUri: Uri): Result<PlaylistImportPreview> =
        withContext(Dispatchers.IO) {
            try {
                val rawContent = context.contentResolver.openInputStream(fileUri)?.use { stream ->
                    stream.bufferedReader().readText()
                } ?: return@withContext Result.failure(
                    IllegalArgumentException(context.getString(R.string.playlist_error_invalid))
                )
                if (rawContent.isBlank()) {
                    return@withContext Result.failure(Exception(context.getString(R.string.playlist_error_empty)))
                }
                val parsed = ByteArrayInputStream(rawContent.toByteArray()).use {
                    M3uParser.parse(it, playlistId = 0L)
                }
                if (parsed.totalItemCount == 0) {
                    return@withContext Result.failure(Exception(context.getString(R.string.playlist_error_empty)))
                }
                val resolvedName = name.trim().ifBlank {
                    fileUri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                        ?.takeIf { it.isNotBlank() } ?: "Local M3U"
                }
                Result.success(
                    PlaylistImportPreview(
                        name = resolvedName,
                        format = PlaylistType.M3U_LOCAL,
                        channelCount = parsed.channels.count { !it.isRadio },
                        movieCount = parsed.movies.size,
                        seriesCount = parsed.series.size,
                        radioCount = parsed.radioCount,
                        supportsCatchup = parsed.supportsCatchup,
                        discoveredEpgUrl = parsed.discoveredEpgUrl,
                        rawLocalContent = rawContent
                    )
                )
            } catch (t: Throwable) {
                Result.failure(Exception(mapFriendlyError(t), t))
            }
        }

    suspend fun saveConfirmedLocalM3u(
        existingId: Long? = null,
        preview: PlaylistImportPreview,
        epgUrl: String = "",
        customUserAgent: String = "",
        useCustomUserAgent: Boolean = false,
        activateNow: Boolean = true
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (preview.rawLocalContent.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException(context.getString(R.string.playlist_error_empty)))
        }
        val deviceId = prefsRepo.ensureDeviceIdentityInitialized()
        val effectiveEpg = epgUrl.trim().ifBlank { preview.discoveredEpgUrl.orEmpty() }
        val id = if (existingId != null) {
            val prev = playlistDao.getById(existingId) ?: return@withContext Result.failure(
                IllegalStateException("Playlist not found")
            )
            playlistDao.update(
                prev.copy(
                    name = preview.name.trim().ifBlank { prev.name },
                    type = PlaylistType.M3U_LOCAL.name,
                    localM3uContent = preview.rawLocalContent,
                    epgUrl = effectiveEpg,
                    customUserAgent = customUserAgent.trim(),
                    useCustomUserAgent = useCustomUserAgent,
                    supportsCatchup = preview.supportsCatchup
                )
            )
            if (activateNow) playlistDao.setActivePlaylist(existingId)
            existingId
        } else {
            val entity = PlaylistEntity(
                name = preview.name.trim().ifBlank { "Local M3U" },
                type = PlaylistType.M3U_LOCAL.name,
                localM3uContent = preview.rawLocalContent,
                epgUrl = effectiveEpg,
                customUserAgent = customUserAgent.trim(),
                useCustomUserAgent = useCustomUserAgent,
                supportsCatchup = preview.supportsCatchup,
                boundDeviceId = deviceId
            )
            val newId = playlistDao.insert(entity)
            val all = playlistDao.getAllOnce()
            if (all.size == 1) {
                playlistDao.setDefaultPlaylist(newId)
                playlistDao.setActivePlaylist(newId)
            } else if (activateNow) {
                playlistDao.setActivePlaylist(newId)
            }
            newId
        }
        val refreshResult = refreshPlaylist(id)
        if (refreshResult.isSuccess) Result.success(id) else Result.failure(refreshResult.exceptionOrNull()!!)
    }

    suspend fun testM3uUrlConnection(
        name: String,
        m3uUrl: String,
        epgUrl: String,
        customUserAgent: String,
        useCustomUserAgent: Boolean
    ): Result<PlaylistImportPreview> = withContext(Dispatchers.IO) {
        try {
            if (!DeviceIdentityManager.isValidStreamOrPlaylistUrl(m3uUrl)) {
                return@withContext Result.failure(IllegalArgumentException(context.getString(R.string.playlist_error_invalid)))
            }
            if (!isNetworkAvailable()) throw NetworkUnavailableException()
            val prefs = prefsRepo.preferencesFlow.first()
            val effectiveUa = when {
                useCustomUserAgent && customUserAgent.isNotBlank() -> customUserAgent.trim()
                prefs.useGlobalCustomUserAgent && prefs.globalUserAgent.isNotBlank() -> prefs.globalUserAgent
                else -> UserPreferencesState.DEFAULT_USER_AGENT
            }
            val req = Request.Builder()
                .url(m3uUrl.trim())
                .header("User-Agent", effectiveUa)
                .build()
            val parsed = okHttpClient.newBuilder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .build()
                .newCall(req)
                .execute()
                .use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val stream = resp.body?.byteStream() ?: throw EmptyPlaylistException()
                    M3uParser.parse(stream, playlistId = 0L)
                }
            if (parsed.totalItemCount == 0) throw EmptyPlaylistException()
            Result.success(
                PlaylistImportPreview(
                    name = name.trim().ifBlank { "M3U Playlist" },
                    format = PlaylistType.M3U_URL,
                    channelCount = parsed.channels.count { !it.isRadio },
                    movieCount = parsed.movies.size,
                    seriesCount = parsed.series.size,
                    radioCount = parsed.radioCount,
                    supportsCatchup = parsed.supportsCatchup,
                    discoveredEpgUrl = epgUrl.trim().ifBlank { parsed.discoveredEpgUrl.orEmpty() }.ifBlank { null }
                )
            )
        } catch (t: Throwable) {
            Result.failure(Exception(mapFriendlyError(t), t))
        }
    }

    suspend fun testXtreamConnection(
        name: String,
        serverUrl: String,
        port: String,
        username: String,
        password: String,
        customUserAgent: String,
        useCustomUserAgent: Boolean
    ): Result<PlaylistImportPreview> = withContext(Dispatchers.IO) {
        try {
            if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException(context.getString(R.string.playlist_error_invalid)))
            }
            if (!isNetworkAvailable()) throw NetworkUnavailableException()
            val prefs = prefsRepo.preferencesFlow.first()
            val effectiveUa = when {
                useCustomUserAgent && customUserAgent.isNotBlank() -> customUserAgent.trim()
                prefs.useGlobalCustomUserAgent && prefs.globalUserAgent.isNotBlank() -> prefs.globalUserAgent
                else -> UserPreferencesState.DEFAULT_USER_AGENT
            }
            val baseUrl = xtreamClient.normalizeBaseUrl(serverUrl, port)
            val authResp = xtreamClient.authenticate(
                baseUrl = baseUrl,
                username = username.trim(),
                password = password,
                userAgent = effectiveUa,
                headers = emptyMap()
            )
            val liveStreams = xtreamClient.fetchLiveStreams(baseUrl, username.trim(), password, effectiveUa, emptyMap())
            val vodStreams = xtreamClient.fetchVodStreams(baseUrl, username.trim(), password, effectiveUa, emptyMap())
            val seriesList = xtreamClient.fetchSeriesList(baseUrl, username.trim(), password, effectiveUa, emptyMap())

            val radioCount = liveStreams.count { dto ->
                dto.streamType.equals("radio_streams", true) ||
                    dto.name.contains("radio", true)
            }
            val supportsCatchup = liveStreams.any { (it.tvArchive ?: 0) == 1 }
            val expSec = authResp.userInfo?.expDate?.toLongOrNull()
            val expMs = if (expSec != null && expSec > 0L) expSec * 1000L else null

            Result.success(
                PlaylistImportPreview(
                    name = name.trim().ifBlank { "Xtream IPTV" },
                    format = PlaylistType.XTREAM,
                    channelCount = (liveStreams.size - radioCount).coerceAtLeast(0),
                    movieCount = vodStreams.size,
                    seriesCount = seriesList.size,
                    radioCount = radioCount,
                    supportsCatchup = supportsCatchup,
                    expirationTimestampMs = expMs
                )
            )
        } catch (t: Throwable) {
            Result.failure(Exception(mapFriendlyError(t), t))
        }
    }

    /**
     * Refreshes a playlist cleanly in the background.
     * Prevents duplicate concurrent syncs on the same playlistId and NEVER deletes existing working
     * data if network/parsing fails!
     */
    suspend fun refreshPlaylist(playlistId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val mutex = refreshLocks.getOrPut(playlistId) { Mutex() }
        if (mutex.isLocked) {
            return@withContext Result.success(Unit)
        }
        mutex.withLock {
            val playlist = playlistDao.getById(playlistId)
                ?: return@withLock Result.failure(IllegalStateException("Playlist not found"))

            playlistDao.updateStatus(playlistId, PlaylistStatus.SYNCING.name, null)

            return@withLock try {
                val prefs = prefsRepo.preferencesFlow.first()
                val effectiveUa = resolveEffectiveUserAgent(playlist, prefs)
                val headers = decodePlaylistHeaders(playlist)

                when (PlaylistType.valueOf(playlist.type)) {
                    PlaylistType.M3U_LOCAL -> {
                        if (playlist.localM3uContent.isBlank()) {
                            throw EmptyPlaylistException()
                        }
                        val parsed = ByteArrayInputStream(playlist.localM3uContent.toByteArray())
                            .use { M3uParser.parse(it, playlistId) }
                        if (parsed.totalItemCount == 0) throw EmptyPlaylistException()

                        catalogDao.replacePlaylistCatalogPreservingUserState(
                            playlistId = playlistId,
                            newCategories = parsed.categories,
                            newChannels = parsed.channels,
                            newMovies = parsed.movies,
                            newSeries = parsed.series,
                            newEpisodes = parsed.episodes
                        )
                        val epgToUse = playlist.epgUrl.ifBlank { parsed.discoveredEpgUrl.orEmpty() }
                        if (epgToUse.isNotBlank() && playlist.epgUrl.isBlank()) {
                            playlistDao.update(playlist.copy(epgUrl = epgToUse))
                        }
                        playlistDao.markRefreshSuccess(
                            id = playlistId,
                            refreshedAtMs = System.currentTimeMillis(),
                            expirationMs = null,
                            maxConnections = null,
                            activeConnections = null,
                            supportsCatchup = parsed.supportsCatchup
                        )
                        if (epgToUse.isNotBlank() && isNetworkAvailable()) {
                            runCatching { refreshEpg(playlistId, epgToUse, effectiveUa, headers, prefs.epgTimezoneOffsetMinutes) }
                        }
                        Result.success(Unit)
                    }

                    PlaylistType.M3U_URL -> {
                        if (!isNetworkAvailable()) throw NetworkUnavailableException()
                        if (!DeviceIdentityManager.isValidStreamOrPlaylistUrl(playlist.m3uUrl)) {
                            throw IllegalArgumentException(context.getString(R.string.playlist_error_invalid))
                        }
                        val reqBuilder = Request.Builder()
                            .url(playlist.m3uUrl)
                            .header("User-Agent", effectiveUa)
                        headers.forEach { (k, v) ->
                            if (!k.equals("User-Agent", true)) reqBuilder.header(k, v)
                        }
                        val parsed = okHttpClient.newBuilder()
                            .connectTimeout(25, TimeUnit.SECONDS)
                            .readTimeout(60, TimeUnit.SECONDS)
                            .build()
                            .newCall(reqBuilder.build())
                            .execute()
                            .use { resp ->
                                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                                val stream = resp.body?.byteStream() ?: throw EmptyPlaylistException()
                                M3uParser.parse(stream, playlistId)
                            }
                        if (parsed.totalItemCount == 0) throw EmptyPlaylistException()

                        catalogDao.replacePlaylistCatalogPreservingUserState(
                            playlistId = playlistId,
                            newCategories = parsed.categories,
                            newChannels = parsed.channels,
                            newMovies = parsed.movies,
                            newSeries = parsed.series,
                            newEpisodes = parsed.episodes
                        )
                        val epgToUse = playlist.epgUrl.ifBlank { parsed.discoveredEpgUrl.orEmpty() }
                        if (epgToUse.isNotBlank() && playlist.epgUrl.isBlank()) {
                            playlistDao.update(playlist.copy(epgUrl = epgToUse))
                        }
                        playlistDao.markRefreshSuccess(
                            id = playlistId,
                            refreshedAtMs = System.currentTimeMillis(),
                            expirationMs = null,
                            maxConnections = null,
                            activeConnections = null,
                            supportsCatchup = parsed.supportsCatchup
                        )
                        if (epgToUse.isNotBlank()) {
                            runCatching { refreshEpg(playlistId, epgToUse, effectiveUa, headers, prefs.epgTimezoneOffsetMinutes) }
                        }
                        Result.success(Unit)
                    }

                    PlaylistType.XTREAM -> {
                        if (!isNetworkAvailable()) throw NetworkUnavailableException()
                        val password = KeystoreCryptoManager.decrypt(playlist.encryptedPassword)
                        val baseUrl = xtreamClient.normalizeBaseUrl(playlist.serverUrl, playlist.port)
                        val authResp = xtreamClient.authenticate(
                            baseUrl = baseUrl,
                            username = playlist.username,
                            password = password,
                            userAgent = effectiveUa,
                            headers = headers
                        )
                        val liveCats = xtreamClient.fetchLiveCategories(baseUrl, playlist.username, password, effectiveUa, headers)
                        val liveStreams = xtreamClient.fetchLiveStreams(baseUrl, playlist.username, password, effectiveUa, headers)
                        val vodCats = xtreamClient.fetchVodCategories(baseUrl, playlist.username, password, effectiveUa, headers)
                        val vodStreams = xtreamClient.fetchVodStreams(baseUrl, playlist.username, password, effectiveUa, headers)
                        val serCats = xtreamClient.fetchSeriesCategories(baseUrl, playlist.username, password, effectiveUa, headers)
                        val seriesList = xtreamClient.fetchSeriesList(baseUrl, playlist.username, password, effectiveUa, headers)

                        val categories = mutableListOf<CategoryEntity>()
                        val liveCatNames = mutableMapOf<String, String>()
                        liveCats.forEachIndexed { idx, dto ->
                            val id = "${playlistId}_LIVE_${dto.categoryId}"
                            liveCatNames[dto.categoryId] = dto.categoryName
                            categories.add(CategoryEntity(id, playlistId, "LIVE", dto.categoryId, dto.categoryName, idx))
                        }
                        val vodCatNames = mutableMapOf<String, String>()
                        vodCats.forEachIndexed { idx, dto ->
                            val id = "${playlistId}_MOVIE_${dto.categoryId}"
                            vodCatNames[dto.categoryId] = dto.categoryName
                            categories.add(CategoryEntity(id, playlistId, "MOVIE", dto.categoryId, dto.categoryName, idx))
                        }
                        val serCatNames = mutableMapOf<String, String>()
                        serCats.forEachIndexed { idx, dto ->
                            val id = "${playlistId}_SERIES_${dto.categoryId}"
                            serCatNames[dto.categoryId] = dto.categoryName
                            categories.add(CategoryEntity(id, playlistId, "SERIES", dto.categoryId, dto.categoryName, idx))
                        }

                        val channels = liveStreams.mapIndexed { idx, dto ->
                            val rawCat = dto.categoryId ?: "0"
                            val catName = liveCatNames[rawCat] ?: "Live"
                            val isRadioStream = dto.streamType.equals("radio_streams", ignoreCase = true) ||
                                catName.contains("radio", ignoreCase = true) ||
                                catName.contains("راديو")
                            ChannelEntity(
                                id = "${playlistId}_ch_${dto.streamId}",
                                playlistId = playlistId,
                                categoryId = "${playlistId}_LIVE_$rawCat",
                                categoryName = catName,
                                channelNumber = dto.num,
                                name = dto.name,
                                streamUrl = xtreamClient.buildLiveStreamUrl(baseUrl, playlist.username, password, dto.streamId),
                                logoUrl = dto.streamIcon.orEmpty(),
                                epgChannelId = dto.epgChannelId.orEmpty(),
                                isRadio = isRadioStream,
                                hasCatchup = (dto.tvArchive ?: 0) == 1,
                                sortOrder = idx
                            )
                        }

                        val movies = vodStreams.map { dto ->
                            val rawCat = dto.categoryId ?: "0"
                            val ext = dto.containerExtension?.ifBlank { "mp4" } ?: "mp4"
                            val addedMs = (dto.added?.toLongOrNull() ?: 0L) * 1000L
                            MovieEntity(
                                id = "${playlistId}_mov_${dto.streamId}",
                                playlistId = playlistId,
                                categoryId = "${playlistId}_MOVIE_$rawCat",
                                categoryName = vodCatNames[rawCat] ?: "Movies",
                                streamId = dto.streamId.toString(),
                                name = dto.name,
                                streamUrl = xtreamClient.buildMovieStreamUrl(baseUrl, playlist.username, password, dto.streamId, ext),
                                posterUrl = dto.streamIcon.orEmpty(),
                                rating = dto.rating.orEmpty(),
                                containerExtension = ext,
                                addedAtMs = addedMs
                            )
                        }

                        val series = seriesList.map { dto ->
                            val rawCat = dto.categoryId ?: "0"
                            val updatedMs = (dto.lastModified?.toLongOrNull() ?: 0L) * 1000L
                            SeriesEntity(
                                id = "${playlistId}_ser_${dto.seriesId}",
                                playlistId = playlistId,
                                categoryId = "${playlistId}_SERIES_$rawCat",
                                categoryName = serCatNames[rawCat] ?: "Series",
                                remoteSeriesId = dto.seriesId.toString(),
                                name = dto.name,
                                posterUrl = dto.cover.orEmpty(),
                                plot = dto.plot.orEmpty(),
                                year = dto.releaseDate.orEmpty().take(4),
                                rating = dto.rating.orEmpty(),
                                genre = dto.genre.orEmpty(),
                                cast = dto.cast.orEmpty(),
                                lastUpdatedMs = updatedMs
                            )
                        }

                        if (channels.isEmpty() && movies.isEmpty() && series.isEmpty()) {
                            throw EmptyPlaylistException()
                        }

                        catalogDao.replacePlaylistCatalogPreservingUserState(
                            playlistId = playlistId,
                            newCategories = categories,
                            newChannels = channels,
                            newMovies = movies,
                            newSeries = series,
                            newEpisodes = emptyList()
                        )

                        val expSeconds = authResp.userInfo?.expDate?.toLongOrNull()
                        val expMs = if (expSeconds != null && expSeconds > 0) expSeconds * 1000L else null
                        playlistDao.markRefreshSuccess(
                            id = playlistId,
                            refreshedAtMs = System.currentTimeMillis(),
                            expirationMs = expMs,
                            maxConnections = authResp.userInfo?.maxConnections?.toIntOrNull(),
                            activeConnections = authResp.userInfo?.activeCons?.toIntOrNull(),
                            supportsCatchup = channels.any { it.hasCatchup }
                        )
                        val xmltvUrl = playlist.epgUrl.ifBlank {
                            xtreamClient.buildXmltvUrl(baseUrl, playlist.username, password)
                        }
                        runCatching { refreshEpg(playlistId, xmltvUrl, effectiveUa, headers, prefs.epgTimezoneOffsetMinutes) }
                        Result.success(Unit)
                    }
                }
            } catch (t: Throwable) {
                val friendlyMsg = mapFriendlyError(t)
                playlistDao.updateStatus(playlistId, PlaylistStatus.ERROR.name, friendlyMsg)
                Result.failure(Exception(friendlyMsg, t))
            }
        }
    }

    suspend fun refreshEpgForActivePlaylist(): Result<Int> = withContext(Dispatchers.IO) {
        val playlist = playlistDao.getActivePlaylistOnce()
            ?: return@withContext Result.failure(IllegalStateException("No active playlist"))
        val prefs = prefsRepo.preferencesFlow.first()
        val ua = resolveEffectiveUserAgent(playlist, prefs)
        val headers = decodePlaylistHeaders(playlist)
        val epgUrl = when {
            playlist.epgUrl.isNotBlank() -> playlist.epgUrl
            playlist.type == PlaylistType.XTREAM.name -> {
                val base = xtreamClient.normalizeBaseUrl(playlist.serverUrl, playlist.port)
                val pass = KeystoreCryptoManager.decrypt(playlist.encryptedPassword)
                xtreamClient.buildXmltvUrl(base, playlist.username, pass)
            }
            else -> ""
        }
        if (epgUrl.isBlank()) {
            return@withContext Result.failure(IllegalStateException(context.getString(R.string.epg_no_data)))
        }
        runCatching {
            refreshEpg(playlist.id, epgUrl, ua, headers, prefs.epgTimezoneOffsetMinutes)
        }
    }

    private suspend fun refreshEpg(
        playlistId: Long,
        epgUrl: String,
        userAgent: String,
        headers: Map<String, String>,
        tzOffsetMinutes: Int
    ): Int {
        val reqBuilder = Request.Builder()
            .url(epgUrl)
            .header("User-Agent", userAgent)
        headers.forEach { (k, v) -> if (!k.equals("User-Agent", true)) reqBuilder.header(k, v) }
        val programs = okHttpClient.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
            .newCall(reqBuilder.build())
            .execute()
            .use { resp ->
                if (!resp.isSuccessful) throw IOException("EPG HTTP ${resp.code}")
                val stream = resp.body?.byteStream() ?: return@use emptyList()
                XmltvParser.parse(stream, playlistId, tzOffsetMinutes)
            }
        if (programs.isNotEmpty()) {
            epgDao.replaceEpgForPlaylist(playlistId, programs)
        }
        return programs.size
    }

    suspend fun loadMovieDetailsIfNeeded(movieId: String) = withContext(Dispatchers.IO) {
        val movie = catalogDao.getMovieById(movieId) ?: return@withContext
        if (movie.streamId.isBlank()) return@withContext
        val playlist = playlistDao.getById(movie.playlistId) ?: return@withContext
        if (playlist.type != PlaylistType.XTREAM.name) return@withContext

        val prefs = prefsRepo.preferencesFlow.first()
        val ua = resolveEffectiveUserAgent(playlist, prefs)
        val headers = decodePlaylistHeaders(playlist)
        val pass = KeystoreCryptoManager.decrypt(playlist.encryptedPassword)
        val baseUrl = xtreamClient.normalizeBaseUrl(playlist.serverUrl, playlist.port)

        runCatching {
            xtreamClient.fetchMovieDetail(baseUrl, playlist.username, pass, movie.streamId, ua, headers)
        }.getOrNull()?.let { detail ->
            catalogDao.updateMovieDetails(
                movieId = movie.id,
                plot = detail.plot.ifBlank { movie.plot },
                backdropUrl = detail.backdropUrl.ifBlank { movie.backdropUrl },
                duration = detail.duration.ifBlank { movie.duration },
                genre = detail.genre.ifBlank { movie.genre },
                year = detail.year.ifBlank { movie.year },
                rating = detail.rating.ifBlank { movie.rating }
            )
        }
    }

    suspend fun loadSeriesDetailsIfNeeded(seriesId: String) = withContext(Dispatchers.IO) {
        val series = catalogDao.getSeriesById(seriesId) ?: return@withContext
        if (series.remoteSeriesId.isBlank()) return@withContext
        val playlist = playlistDao.getById(series.playlistId) ?: return@withContext
        if (playlist.type != PlaylistType.XTREAM.name) return@withContext

        val prefs = prefsRepo.preferencesFlow.first()
        val ua = resolveEffectiveUserAgent(playlist, prefs)
        val headers = decodePlaylistHeaders(playlist)
        val pass = KeystoreCryptoManager.decrypt(playlist.encryptedPassword)
        val baseUrl = xtreamClient.normalizeBaseUrl(playlist.serverUrl, playlist.port)

        runCatching {
            xtreamClient.fetchSeriesDetail(baseUrl, playlist.username, pass, series.remoteSeriesId, ua, headers)
        }.getOrNull()?.let { detail ->
            catalogDao.updateSeriesDetails(
                seriesId = series.id,
                plot = detail.plot.ifBlank { series.plot },
                backdropUrl = detail.backdropUrl.ifBlank { series.backdropUrl },
                genre = detail.genre.ifBlank { series.genre },
                cast = detail.cast.ifBlank { series.cast },
                year = detail.year.ifBlank { series.year },
                rating = detail.rating.ifBlank { series.rating }
            )
            if (detail.episodes.isNotEmpty()) {
                val entities = detail.episodes.map { ep ->
                    EpisodeEntity(
                        id = "${series.playlistId}_ep_${ep.episodeId}",
                        seriesId = series.id,
                        playlistId = series.playlistId,
                        seasonNumber = ep.seasonNumber,
                        episodeNumber = ep.episodeNumber,
                        title = ep.title,
                        streamUrl = xtreamClient.buildSeriesStreamUrl(
                            baseUrl,
                            playlist.username,
                            pass,
                            ep.episodeId,
                            ep.containerExtension
                        ),
                        thumbnailUrl = ep.thumbnailUrl.ifBlank { series.posterUrl },
                        plot = ep.plot,
                        duration = ep.duration,
                        containerExtension = ep.containerExtension
                    )
                }
                catalogDao.replaceSeriesEpisodes(series.id, entities)
            }
        }
    }

    suspend fun resolveHeadersAndUaForPlaylist(playlistId: Long): Pair<String, Map<String, String>> {
        val prefs = prefsRepo.preferencesFlow.first()
        val playlist = playlistDao.getById(playlistId)
        val ua = if (playlist != null) resolveEffectiveUserAgent(playlist, prefs) else prefs.globalUserAgent
        val headers = if (playlist != null) decodePlaylistHeaders(playlist) else emptyMap()
        return ua to headers
    }

    fun resolveEffectiveUserAgent(playlist: PlaylistEntity, prefs: UserPreferencesState): String {
        return when {
            playlist.useCustomUserAgent && playlist.customUserAgent.isNotBlank() -> playlist.customUserAgent
            prefs.useGlobalCustomUserAgent && prefs.globalUserAgent.isNotBlank() -> prefs.globalUserAgent
            else -> UserPreferencesState.DEFAULT_USER_AGENT
        }
    }

    fun decodePlaylistHeaders(playlist: PlaylistEntity): Map<String, String> {
        if (playlist.encryptedCustomHeadersJson.isBlank()) return emptyMap()
        val raw = KeystoreCryptoManager.decrypt(playlist.encryptedCustomHeadersJson)
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString<List<HttpHeaderEntry>>(raw)
                .associate { it.key to it.value }
        }.getOrDefault(emptyMap())
    }

    fun decodePlaylistHeadersAsText(playlist: PlaylistEntity): String {
        return decodePlaylistHeaders(playlist).entries.joinToString("\n") { "${it.key}: ${it.value}" }
    }

    private fun encodeHeadersEncrypted(rawText: String): String {
        val entries = rawText.lines()
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx <= 0) null
                else {
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) HttpHeaderEntry(key, value) else null
                }
            }
        if (entries.isEmpty()) return ""
        return KeystoreCryptoManager.encrypt(json.encodeToString(entries))
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun mapFriendlyError(t: Throwable): String {
        return when (t) {
            is NetworkUnavailableException -> context.getString(R.string.playlist_error_network)
            is XtreamAuthException -> context.getString(R.string.playlist_error_credentials)
            is EmptyPlaylistException -> context.getString(R.string.playlist_error_empty)
            is UnknownHostException -> context.getString(R.string.playlist_error_unreachable)
            is IllegalArgumentException -> t.message ?: context.getString(R.string.playlist_error_invalid)
            is IOException -> context.getString(R.string.playlist_error_unreachable)
            else -> t.message ?: context.getString(R.string.playlist_error_invalid)
        }
    }

    private class NetworkUnavailableException : Exception()
    private class EmptyPlaylistException : Exception()
}
