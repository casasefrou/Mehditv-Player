package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entities.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY isDefault DESC, createdAtMs ASC")
    fun observeAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    fun observeActivePlaylist(): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePlaylistOnce(): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY createdAtMs ASC")
    suspend fun getAllOnce(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE playlists SET isActive = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setActivePlaylist(id: Long)

    @Query("UPDATE playlists SET isDefault = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setDefaultPlaylist(id: Long)

    @Query("UPDATE playlists SET status = :status, lastError = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String?)

    @Query(
        "UPDATE playlists SET status = 'READY', lastError = NULL, lastRefreshedAtMs = :refreshedAtMs, " +
            "expirationTimestampMs = :expirationMs, maxConnections = :maxConnections, activeConnections = :activeConnections, " +
            "supportsCatchup = :supportsCatchup " +
            "WHERE id = :id"
    )
    suspend fun markRefreshSuccess(
        id: Long,
        refreshedAtMs: Long,
        expirationMs: Long?,
        maxConnections: Int?,
        activeConnections: Int?,
        supportsCatchup: Boolean = false
    )

    @Transaction
    suspend fun activateAndSetDefaultIfFirst(id: Long) {
        val all = getAllOnce()
        if (all.size == 1) {
            setDefaultPlaylist(id)
        }
        setActivePlaylist(id)
    }
}
