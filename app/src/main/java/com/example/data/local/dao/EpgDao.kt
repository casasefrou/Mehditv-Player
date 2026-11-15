package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entities.EpgProgramEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {

    @Query(
        "SELECT * FROM epg_programs WHERE playlistId = :playlistId " +
            "AND endTimeMs >= :fromTimeMs AND startTimeMs <= :toTimeMs " +
            "ORDER BY startTimeMs ASC"
    )
    fun observeProgramsInWindow(
        playlistId: Long,
        fromTimeMs: Long,
        toTimeMs: Long
    ): Flow<List<EpgProgramEntity>>

    @Query(
        "SELECT * FROM epg_programs WHERE playlistId = :playlistId AND epgChannelId = :epgChannelId " +
            "AND endTimeMs >= :fromTimeMs ORDER BY startTimeMs ASC LIMIT 20"
    )
    fun observeProgramsForChannel(
        playlistId: Long,
        epgChannelId: String,
        fromTimeMs: Long
    ): Flow<List<EpgProgramEntity>>

    @Query("SELECT COUNT(*) FROM epg_programs WHERE playlistId = :playlistId")
    fun observeEpgCount(playlistId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Transaction
    suspend fun replaceEpgForPlaylist(playlistId: Long, programs: List<EpgProgramEntity>) {
        if (programs.isEmpty()) return
        deleteByPlaylist(playlistId)
        programs.chunked(500).forEach { insertPrograms(it) }
    }
}
