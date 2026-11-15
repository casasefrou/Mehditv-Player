package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // XTREAM, M3U_URL, M3U_LOCAL
    val serverUrl: String = "",
    val port: String = "",
    val username: String = "",
    val encryptedPassword: String = "",
    val m3uUrl: String = "",
    val localM3uContent: String = "",
    val epgUrl: String = "",
    val customUserAgent: String = "",
    val useCustomUserAgent: Boolean = false,
    val encryptedCustomHeadersJson: String = "",
    val isDefault: Boolean = false,
    val isActive: Boolean = false,
    val status: String = "READY", // READY, SYNCING, ERROR
    val lastError: String? = null,
    val lastRefreshedAtMs: Long? = null,
    val expirationTimestampMs: Long? = null,
    val maxConnections: Int? = null,
    val activeConnections: Int? = null,
    val boundDeviceId: String = "",
    val supportsCatchup: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["playlistId", "section"]),
        Index(value = ["playlistId", "section", "sortOrder"])
    ]
)
data class CategoryEntity(
    @PrimaryKey val id: String, // "${playlistId}_${section}_${rawCategoryId}"
    val playlistId: Long,
    val section: String, // LIVE, MOVIE, SERIES
    val rawCategoryId: String,
    val name: String,
    val sortOrder: Int = 0,
    val isLocked: Boolean = false,
    val isHidden: Boolean = false
)

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["playlistId", "categoryId"]),
        Index(value = ["playlistId", "isFavorite"]),
        Index(value = ["playlistId", "epgChannelId"])
    ]
)
data class ChannelEntity(
    @PrimaryKey val id: String, // "${playlistId}_ch_${uniqueKey}"
    val playlistId: Long,
    val categoryId: String,
    val categoryName: String,
    val channelNumber: Int? = null,
    val name: String,
    val streamUrl: String,
    val logoUrl: String = "",
    val epgChannelId: String = "",
    val tvgName: String = "",
    val customHeadersJson: String = "",
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val isRadio: Boolean = false,
    val hasCatchup: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "movies",
    indices = [
        Index(value = ["playlistId", "categoryId"]),
        Index(value = ["playlistId", "isFavorite"]),
        Index(value = ["playlistId", "addedAtMs"])
    ]
)
data class MovieEntity(
    @PrimaryKey val id: String,
    val playlistId: Long,
    val categoryId: String,
    val categoryName: String,
    val streamId: String = "",
    val name: String,
    val streamUrl: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val plot: String = "",
    val year: String = "",
    val rating: String = "",
    val duration: String = "",
    val genre: String = "",
    val containerExtension: String = "mp4",
    val addedAtMs: Long = 0L,
    val isFavorite: Boolean = false
)

@Entity(
    tableName = "series",
    indices = [
        Index(value = ["playlistId", "categoryId"]),
        Index(value = ["playlistId", "isFavorite"]),
        Index(value = ["playlistId", "lastUpdatedMs"])
    ]
)
data class SeriesEntity(
    @PrimaryKey val id: String,
    val playlistId: Long,
    val categoryId: String,
    val categoryName: String,
    val remoteSeriesId: String = "",
    val name: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val plot: String = "",
    val year: String = "",
    val rating: String = "",
    val genre: String = "",
    val cast: String = "",
    val lastUpdatedMs: Long = 0L,
    val isFavorite: Boolean = false
)

@Entity(
    tableName = "episodes",
    indices = [
        Index(value = ["seriesId", "seasonNumber", "episodeNumber"]),
        Index(value = ["playlistId"])
    ]
)
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val seriesId: String,
    val playlistId: Long,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val streamUrl: String,
    val thumbnailUrl: String = "",
    val plot: String = "",
    val duration: String = "",
    val containerExtension: String = "mp4"
)

@Entity(
    tableName = "epg_programs",
    indices = [
        Index(value = ["playlistId", "epgChannelId", "startTimeMs"]),
        Index(value = ["playlistId", "startTimeMs", "endTimeMs"])
    ]
)
data class EpgProgramEntity(
    @PrimaryKey val id: String,
    val playlistId: Long,
    val epgChannelId: String,
    val title: String,
    val description: String = "",
    val startTimeMs: Long,
    val endTimeMs: Long
)

@Entity(
    tableName = "watch_history",
    indices = [
        Index(value = ["playlistId", "mediaType", "lastWatchedAtMs"]),
        Index(value = ["seriesId"])
    ]
)
data class WatchHistoryEntity(
    @PrimaryKey val mediaId: String,
    val playlistId: Long,
    val mediaType: String, // LIVE, MOVIE, EPISODE
    val seriesId: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val title: String,
    val subtitle: String = "",
    val posterUrl: String = "",
    val streamUrl: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastWatchedAtMs: Long = System.currentTimeMillis()
)
