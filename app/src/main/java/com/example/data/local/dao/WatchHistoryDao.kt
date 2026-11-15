package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    @Query(
        "SELECT * FROM watch_history WHERE playlistId = :playlistId " +
            "AND mediaType IN ('MOVIE', 'EPISODE') AND durationMs > 60000 " +
            "AND positionMs > 5000 AND positionMs < (durationMs * 0.95) " +
            "ORDER BY lastWatchedAtMs DESC LIMIT 15"
    )
    fun observeContinueWatching(playlistId: Long): Flow<List<WatchHistoryEntity>>

    @Query(
        "SELECT * FROM watch_history WHERE playlistId = :playlistId " +
            "ORDER BY lastWatchedAtMs DESC LIMIT 20"
    )
    fun observeRecentlyWatched(playlistId: Long): Flow<List<WatchHistoryEntity>>

    @Query(
        "SELECT mediaId FROM watch_history WHERE playlistId = :playlistId AND mediaType = 'LIVE' " +
            "ORDER BY lastWatchedAtMs DESC LIMIT 30"
    )
    fun observeRecentLiveIds(playlistId: Long): Flow<List<String>>

    @Query(
        "SELECT * FROM watch_history WHERE seriesId = :seriesId " +
            "ORDER BY lastWatchedAtMs DESC LIMIT 1"
    )
    fun observeLastWatchedForSeries(seriesId: String): Flow<WatchHistoryEntity?>

    @Query("SELECT * FROM watch_history WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getHistoryForMedia(mediaId: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)
}
