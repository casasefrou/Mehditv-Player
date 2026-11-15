package com.example.data.remote

import android.net.Uri
import com.example.core.model.UserPreferencesState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class XtreamAuthResponse(
    @SerialName("user_info") val userInfo: XtreamUserInfo? = null,
    @SerialName("server_info") val serverInfo: XtreamServerInfo? = null
)

@Serializable
data class XtreamUserInfo(
    val username: String? = null,
    val status: String? = null,
    @SerialName("exp_date") val expDate: String? = null,
    @SerialName("max_connections") val maxConnections: String? = null,
    @SerialName("active_cons") val activeCons: String? = null,
    @SerialName("is_trial") val isTrial: String? = null,
    val auth: Int? = null
)

@Serializable
data class XtreamServerInfo(
    val url: String? = null,
    val port: String? = null,
    @SerialName("https_port") val httpsPort: String? = null,
    @SerialName("server_protocol") val serverProtocol: String? = null
)

@Serializable
data class XtreamCategoryDto(
    @SerialName("category_id") val categoryId: String = "",
    @SerialName("category_name") val categoryName: String = ""
)

@Serializable
data class XtreamLiveStreamDto(
    @SerialName("stream_id") val streamId: Int = 0,
    val num: Int? = null,
    val name: String = "",
    @SerialName("stream_icon") val streamIcon: String? = null,
    @SerialName("epg_channel_id") val epgChannelId: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("stream_type") val streamType: String? = null,
    @SerialName("tv_archive") val tvArchive: Int? = null
)

@Serializable
data class XtreamVodDto(
    @SerialName("stream_id") val streamId: Int = 0,
    val name: String = "",
    @SerialName("stream_icon") val streamIcon: String? = null,
    val rating: String? = null,
    val added: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("container_extension") val containerExtension: String? = null
)

@Serializable
data class XtreamSeriesDto(
    @SerialName("series_id") val seriesId: Int = 0,
    val name: String = "",
    val cover: String? = null,
    val plot: String? = null,
    val cast: String? = null,
    val genre: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    val rating: String? = null,
    @SerialName("last_modified") val lastModified: String? = null,
    @SerialName("category_id") val categoryId: String? = null
)

data class XtreamMovieDetailResult(
    val plot: String = "",
    val backdropUrl: String = "",
    val duration: String = "",
    val genre: String = "",
    val year: String = "",
    val rating: String = ""
)

data class XtreamEpisodeParsed(
    val episodeId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val thumbnailUrl: String,
    val plot: String,
    val duration: String,
    val containerExtension: String
)

data class XtreamSeriesDetailResult(
    val plot: String = "",
    val backdropUrl: String = "",
    val genre: String = "",
    val cast: String = "",
    val year: String = "",
    val rating: String = "",
    val episodes: List<XtreamEpisodeParsed> = emptyList()
)

class XtreamAuthException(message: String) : Exception(message)

/**
 * Real Xtream Codes API client using OkHttp + Kotlinx Serialization (lenient parsing for provider quirks)
 * with per-playlist User-Agent and custom headers.
 */
class XtreamClient(
    private val baseOkHttpClient: OkHttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun normalizeBaseUrl(serverUrl: String, port: String): String {
        val trimmed = serverUrl.trim().trimEnd('/')
        val withScheme = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "http://$trimmed"
        }
        val uri = Uri.parse(withScheme)
        val host = uri.host ?: return withScheme
        val cleanPort = port.trim()
        val effectivePort = when {
            uri.port != -1 -> ":${uri.port}"
            cleanPort.isNotEmpty() && cleanPort.all { it.isDigit() } -> ":$cleanPort"
            else -> ""
        }
        return "${uri.scheme}://$host$effectivePort"
    }

    private fun buildApiUrl(
        baseUrl: String,
        username: String,
        password: String,
        action: String? = null,
        extraParams: Map<String, String> = emptyMap()
    ): String {
        val builder = Uri.parse(baseUrl).buildUpon()
            .appendPath("player_api.php")
            .appendQueryParameter("username", username)
            .appendQueryParameter("password", password)
        if (!action.isNullOrBlank()) {
            builder.appendQueryParameter("action", action)
        }
        extraParams.forEach { (k, v) -> builder.appendQueryParameter(k, v) }
        return builder.build().toString()
    }

    private fun executeGet(
        url: String,
        userAgent: String,
        headers: Map<String, String>
    ): String {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent.ifBlank { UserPreferencesState.DEFAULT_USER_AGENT })
        headers.forEach { (k, v) ->
            if (!k.equals("User-Agent", ignoreCase = true) && k.isNotBlank()) {
                reqBuilder.header(k, v)
            }
        }
        val client = baseOkHttpClient.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()

        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403 || response.code == 404) {
                    throw XtreamAuthException("HTTP ${response.code}")
                }
                throw IOException("HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    fun authenticate(
        baseUrl: String,
        username: String,
        password: String,
        userAgent: String,
        headers: Map<String, String>
    ): XtreamAuthResponse {
        val body = executeGet(
            url = buildApiUrl(baseUrl, username, password),
            userAgent = userAgent,
            headers = headers
        )
        val parsed = json.decodeFromString<XtreamAuthResponse>(body)
        val info = parsed.userInfo
        val isAuthed = info?.auth == 1 ||
            info?.status.equals("Active", ignoreCase = true) ||
            info?.status.equals("Trial", ignoreCase = true)
        if (info == null || (!isAuthed && info.auth == 0)) {
            throw XtreamAuthException("Invalid credentials or inactive Xtream account")
        }
        return parsed
    }

    fun fetchLiveCategories(
        baseUrl: String,
        username: String,
        password: String,
        userAgent: String,
        headers: Map<String, String>
    ): List<XtreamCategoryDto> {
        val body = executeGet(
            buildApiUrl(baseUrl, username, password, "get_live_categories"),
            userAgent,
            headers
        )
        return decodeArrayLenient(body)
    }

    fun fetchLiveStreams(
        baseUrl: String,
        username: String,
        password: String,
        userAgent: String,
        headers: Map<String, String>
    ): List<XtreamLiveStreamDto> {
        val body = executeGet(
            buildApiUrl(baseUrl, username, password, "get_live_streams"),
            userAgent,
            headers
        )
        return decodeArrayLenient(body)
    }

    fun fetchVodCategories(
        baseUrl: String,
        username: String,
        password: String,
        userAgent: String,
        headers: Map<String, String>
    ): List<XtreamCategoryDto> {
        val body = executeGet(
            buildApiUrl(baseUrl, username, password, "get_vod_categories"),
            userAgent,
            headers
        )
        return decodeArrayLenient(body)
    }

    fun fetchVodStreams(
        baseUrl: String,
        username: String,
        password: String,
        userAgent: String,
        headers: Map<String, String>
    ): List<XtreamVodDto> {
        val body = executeGet(
            buildApiUrl(baseUrl, username, password, "get_vod_streams"),
            userAgent,
            headers
        )
        return decodeArrayLenient(body)
    }

    fun fetchSeriesCategories(
        baseUrl: String,
        username: String,
        password: String,
        userAgent: String,
        headers: Map<String, String>
    ): List<XtreamCategoryDto> {
        val body = executeGet(
            buildApiUrl(baseUrl, username, password, "get_series_categories"),
            userAgent,
            headers
        )
        return decodeArrayLenient(body)
    }

    fun fetchSeriesList(
        baseUrl: String,
        username: String,
        password: String,
        userAgent: String,
        headers: Map<String, String>
    ): List<XtreamSeriesDto> {
        val body = executeGet(
            buildApiUrl(baseUrl, username, password, "get_series"),
            userAgent,
            headers
        )
        return decodeArrayLenient(body)
    }

    fun fetchMovieDetail(
        baseUrl: String,
        username: String,
        password: String,
        vodId: String,
        userAgent: String,
        headers: Map<String, String>
    ): XtreamMovieDetailResult? {
        val body = executeGet(
            buildApiUrl(baseUrl, username, password, "get_vod_info", mapOf("vod_id" to vodId)),
            userAgent,
            headers
        )
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val info = root["info"] as? JsonObject ?: return null
        return XtreamMovieDetailResult(
            plot = info.str("plot").ifBlank { info.str("description") },
            backdropUrl = info.firstBackdrop(),
            duration = info.str("duration"),
            genre = info.str("genre"),
            year = info.str("releasedate").take(4).ifBlank { info.str("year") },
            rating = info.str("rating")
        )
    }

    fun fetchSeriesDetail(
        baseUrl: String,
        username: String,
        password: String,
        seriesId: String,
        userAgent: String,
        headers: Map<String, String>
    ): XtreamSeriesDetailResult? {
        val body = executeGet(
            buildApiUrl(baseUrl, username, password, "get_series_info", mapOf("series_id" to seriesId)),
            userAgent,
            headers
        )
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val info = root["info"] as? JsonObject
        val episodesObj = root["episodes"] as? JsonObject
        val parsedEpisodes = mutableListOf<XtreamEpisodeParsed>()

        episodesObj?.entries?.forEach { (seasonKey, seasonValue) ->
            val seasonNum = seasonKey.toIntOrNull() ?: 1
            val epArray = seasonValue as? JsonArray ?: return@forEach
            epArray.forEachIndexed { idx, epEl ->
                val epObj = epEl as? JsonObject ?: return@forEachIndexed
                val epId = epObj.str("id")
                if (epId.isNotBlank()) {
                    val epInfo = epObj["info"] as? JsonObject
                    parsedEpisodes.add(
                        XtreamEpisodeParsed(
                            episodeId = epId,
                            seasonNumber = epObj["season"]?.jsonPrimitive?.intOrNull ?: seasonNum,
                            episodeNumber = epObj["episode_num"]?.jsonPrimitive?.intOrNull ?: (idx + 1),
                            title = epObj.str("title").ifBlank { "Episode ${idx + 1}" },
                            thumbnailUrl = epInfo?.str("movie_image").orEmpty(),
                            plot = epInfo?.str("plot").orEmpty(),
                            duration = epInfo?.str("duration").orEmpty(),
                            containerExtension = epObj.str("container_extension").ifBlank { "mp4" }
                        )
                    )
                }
            }
        }

        return XtreamSeriesDetailResult(
            plot = info?.str("plot").orEmpty(),
            backdropUrl = info?.firstBackdrop().orEmpty(),
            genre = info?.str("genre").orEmpty(),
            cast = info?.str("cast").orEmpty(),
            year = info?.str("releaseDate").orEmpty().take(4),
            rating = info?.str("rating").orEmpty(),
            episodes = parsedEpisodes.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
        )
    }

    fun buildLiveStreamUrl(baseUrl: String, user: String, pass: String, streamId: Int): String =
        "$baseUrl/live/${Uri.encode(user)}/${Uri.encode(pass)}/$streamId.m3u8"

    fun buildMovieStreamUrl(baseUrl: String, user: String, pass: String, streamId: Int, ext: String): String =
        "$baseUrl/movie/${Uri.encode(user)}/${Uri.encode(pass)}/$streamId.${ext.ifBlank { "mp4" }}"

    fun buildSeriesStreamUrl(baseUrl: String, user: String, pass: String, episodeId: String, ext: String): String =
        "$baseUrl/series/${Uri.encode(user)}/${Uri.encode(pass)}/$episodeId.${ext.ifBlank { "mp4" }}"

    fun buildXmltvUrl(baseUrl: String, user: String, pass: String): String =
        Uri.parse(baseUrl).buildUpon()
            .appendPath("xmltv.php")
            .appendQueryParameter("username", user)
            .appendQueryParameter("password", pass)
            .build()
            .toString()

    private inline fun <reified T> decodeArrayLenient(raw: String): List<T> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null" || trimmed == "{}") return emptyList()
        if (!trimmed.startsWith("[")) return emptyList()
        return runCatching { json.decodeFromString<List<T>>(trimmed) }.getOrDefault(emptyList())
    }

    private fun JsonObject.str(key: String): String {
        val el: JsonElement = this[key] ?: return ""
        return (el as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
    }

    private fun JsonObject.firstBackdrop(): String {
        val el = this["backdrop_path"] ?: return ""
        return when (el) {
            is JsonArray -> el.firstOrNull()?.jsonPrimitive?.contentOrNull.orEmpty()
            is JsonPrimitive -> el.contentOrNull.orEmpty()
            else -> ""
        }
    }
}
