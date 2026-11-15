package com.example.core.model

import kotlinx.serialization.Serializable

enum class AppLanguage(val code: String) {
    ARABIC("ar"),
    ENGLISH("en");

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ARABIC
    }
}

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

enum class StartupScreen {
    HOME,
    LIVE,
    MOVIES,
    SERIES
}

enum class BufferPreset(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int
) {
    LOW_LATENCY(1500, 8000, 500, 1000),
    BALANCED(5000, 25000, 1500, 3000),
    HIGH_STABILITY(15000, 60000, 3000, 6000)
}

enum class DecoderMode {
    HARDWARE_FIRST,
    SOFTWARE_FALLBACK
}

enum class LiveViewMode {
    GRID,
    LIST
}

enum class PlaylistType {
    XTREAM,
    M3U_URL,
    M3U_LOCAL
}

enum class PlaylistStatus {
    READY,
    SYNCING,
    ERROR
}

enum class ActivationStatus {
    LOCAL_MODE,
    UNACTIVATED,
    TRIAL,
    ACTIVE,
    EXPIRED,
    SUSPENDED
}

enum class MediaSection {
    LIVE,
    MOVIE,
    SERIES
}

enum class MediaHistoryType {
    LIVE,
    MOVIE,
    EPISODE
}

enum class ChannelSortOrder {
    DEFAULT,
    A_TO_Z,
    Z_TO_A
}

enum class VodSortOrder {
    RECENT,
    NAME_AZ,
    YEAR_DESC
}

@Serializable
data class HttpHeaderEntry(
    val key: String,
    val value: String
)

data class PlaybackTarget(
    val mediaId: String,
    val playlistId: Long,
    val mediaType: MediaHistoryType,
    val title: String,
    val subtitle: String = "",
    val streamUrl: String,
    val posterUrl: String = "",
    val epgChannelId: String = "",
    val seriesId: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val resumePositionMs: Long = 0L,
    val userAgent: String = "",
    val customHeaders: Map<String, String> = emptyMap()
)

data class TrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean
)

data class UserPreferencesState(
    val deviceId: String = "",
    val language: AppLanguage = AppLanguage.ARABIC,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val showClockOnHome: Boolean = true,
    val startupScreen: StartupScreen = StartupScreen.HOME,
    val globalUserAgent: String = DEFAULT_USER_AGENT,
    val useGlobalCustomUserAgent: Boolean = false,
    val bufferPreset: BufferPreset = BufferPreset.BALANCED,
    val decoderMode: DecoderMode = DecoderMode.HARDWARE_FIRST,
    val subtitleSizeSp: Float = 18f,
    val subtitleBgOpacity: Float = 0.55f,
    val subtitleDelayMs: Long = 0L,
    val preferredAudioLang: String = "ar",
    val autoplayNextEpisode: Boolean = true,
    val epgTimezoneOffsetMinutes: Int = 0,
    val liveViewMode: LiveViewMode = LiveViewMode.GRID,
    val lastLiveCategoryId: String = SPECIAL_CAT_ALL,
    val lastMovieCategoryId: String = SPECIAL_CAT_ALL,
    val lastSeriesCategoryId: String = SPECIAL_CAT_ALL,
    val hasParentalPin: Boolean = false,
    val lockSettings: Boolean = false,
    val lockPlaylistEdits: Boolean = false,
    val backendBaseUrl: String = "",
    val activationStatus: ActivationStatus = ActivationStatus.LOCAL_MODE,
    val licenseExpiresAtMs: Long? = null,
    val pairingToken: String = "",
    val pairingTokenExpiresAtMs: Long = 0L
) {
    companion object {
        const val DEFAULT_USER_AGENT = "MehdiTvPlayer/1.0 (Android; Media3)"
        const val SPECIAL_CAT_ALL = "__ALL__"
        const val SPECIAL_CAT_FAVORITES = "__FAVORITES__"
        const val SPECIAL_CAT_RECENT = "__RECENT__"
        const val SPECIAL_CAT_RECENT_ADDED = "__RECENT_ADDED__"
        const val SPECIAL_CAT_RADIO = "__RADIO__"
    }
}

data class PlaylistImportPreview(
    val name: String,
    val format: PlaylistType,
    val channelCount: Int,
    val movieCount: Int,
    val seriesCount: Int,
    val radioCount: Int = 0,
    val supportsCatchup: Boolean = false,
    val discoveredEpgUrl: String? = null,
    val expirationTimestampMs: Long? = null,
    val rawLocalContent: String = ""
)

sealed interface UiResult<out T> {
    data object Idle : UiResult<Nothing>
    data object Loading : UiResult<Nothing>
    data class Success<T>(val data: T) : UiResult<T>
    data class Error(val message: String) : UiResult<Nothing>
}
