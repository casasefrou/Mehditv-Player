package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.local.entities.CategoryEntity
import com.example.data.local.entities.ChannelEntity
import com.example.data.local.entities.EpisodeEntity
import com.example.data.local.entities.MovieEntity
import com.example.data.local.entities.SeriesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    // --- Counts ---
    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId AND isHidden = 0 AND isRadio = 0")
    fun observeChannelCount(playlistId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId AND isHidden = 0 AND isRadio = 1")
    fun observeRadioCount(playlistId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM movies WHERE playlistId = :playlistId")
    fun observeMovieCount(playlistId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM series WHERE playlistId = :playlistId")
    fun observeSeriesCount(playlistId: Long): Flow<Int>

    // --- Categories ---
    @Query(
        "SELECT * FROM categories WHERE playlistId = :playlistId AND section = :section " +
            "AND (:includeHidden = 1 OR isHidden = 0) ORDER BY sortOrder ASC, name ASC"
    )
    fun observeCategories(
        playlistId: Long,
        section: String,
        includeHidden: Boolean = false
    ): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId")
    suspend fun getCategoriesOnce(playlistId: Long): List<CategoryEntity>

    @Query("UPDATE categories SET isHidden = :isHidden WHERE id = :categoryId")
    suspend fun setCategoryHidden(categoryId: String, isHidden: Boolean)

    @Query("UPDATE categories SET isLocked = :isLocked WHERE id = :categoryId")
    suspend fun setCategoryLocked(categoryId: String, isLocked: Boolean)

    // --- Channels ---
    @Query(
        "SELECT * FROM channels WHERE playlistId = :playlistId " +
            "AND (:includeHidden = 1 OR isHidden = 0) " +
            "ORDER BY sortOrder ASC, channelNumber ASC, name ASC"
    )
    fun observeAllChannels(playlistId: Long, includeHidden: Boolean = false): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND isFavorite = 1 AND isHidden = 0 ORDER BY sortOrder ASC, name ASC")
    fun observeFavoriteChannels(playlistId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :channelId LIMIT 1")
    suspend fun getChannelById(channelId: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId")
    suspend fun getChannelsOnce(playlistId: Long): List<ChannelEntity>

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE id = :channelId")
    suspend fun setChannelFavorite(channelId: String, isFavorite: Boolean)

    @Query("UPDATE channels SET isHidden = :isHidden WHERE id = :channelId")
    suspend fun setChannelHidden(channelId: String, isHidden: Boolean)

    // --- Movies ---
    @Query("SELECT * FROM movies WHERE playlistId = :playlistId ORDER BY addedAtMs DESC, name ASC")
    fun observeMovies(playlistId: Long): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId ORDER BY addedAtMs DESC LIMIT :limit")
    fun observeRecentMovies(playlistId: Long, limit: Int = 15): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movies WHERE id = :movieId LIMIT 1")
    fun observeMovieById(movieId: String): Flow<MovieEntity?>

    @Query("SELECT * FROM movies WHERE id = :movieId LIMIT 1")
    suspend fun getMovieById(movieId: String): MovieEntity?

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId")
    suspend fun getMoviesOnce(playlistId: Long): List<MovieEntity>

    @Query("UPDATE movies SET isFavorite = :isFavorite WHERE id = :movieId")
    suspend fun setMovieFavorite(movieId: String, isFavorite: Boolean)

    @Query(
        "UPDATE movies SET plot = :plot, backdropUrl = :backdropUrl, duration = :duration, " +
            "genre = :genre, year = :year, rating = :rating WHERE id = :movieId"
    )
    suspend fun updateMovieDetails(
        movieId: String,
        plot: String,
        backdropUrl: String,
        duration: String,
        genre: String,
        year: String,
        rating: String
    )

    // --- Series & Episodes ---
    @Query("SELECT * FROM series WHERE playlistId = :playlistId ORDER BY lastUpdatedMs DESC, name ASC")
    fun observeSeries(playlistId: Long): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId ORDER BY lastUpdatedMs DESC LIMIT :limit")
    fun observeRecentSeries(playlistId: Long, limit: Int = 15): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE id = :seriesId LIMIT 1")
    fun observeSeriesById(seriesId: String): Flow<SeriesEntity?>

    @Query("SELECT * FROM series WHERE id = :seriesId LIMIT 1")
    suspend fun getSeriesById(seriesId: String): SeriesEntity?

    @Query("SELECT * FROM series WHERE playlistId = :playlistId")
    suspend fun getSeriesOnce(playlistId: Long): List<SeriesEntity>

    @Query("UPDATE series SET isFavorite = :isFavorite WHERE id = :seriesId")
    suspend fun setSeriesFavorite(seriesId: String, isFavorite: Boolean)

    @Query(
        "UPDATE series SET plot = :plot, backdropUrl = :backdropUrl, genre = :genre, " +
            "cast = :cast, year = :year, rating = :rating WHERE id = :seriesId"
    )
    suspend fun updateSeriesDetails(
        seriesId: String,
        plot: String,
        backdropUrl: String,
        genre: String,
        cast: String,
        year: String,
        rating: String
    )

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY seasonNumber ASC, episodeNumber ASC")
    fun observeEpisodesForSeries(seriesId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY seasonNumber ASC, episodeNumber ASC")
    suspend fun getEpisodesForSeriesOnce(seriesId: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE id = :episodeId LIMIT 1")
    suspend fun getEpisodeById(episodeId: String): EpisodeEntity?

    // --- Transactional inserts & refreshes ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(items: List<ChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(items: List<MovieEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(items: List<SeriesEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(items: List<EpisodeEntity>)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    suspend fun deleteCategoriesByPlaylist(playlistId: Long)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsByPlaylist(playlistId: Long)

    @Query("DELETE FROM movies WHERE playlistId = :playlistId")
    suspend fun deleteMoviesByPlaylist(playlistId: Long)

    @Query("DELETE FROM series WHERE playlistId = :playlistId")
    suspend fun deleteSeriesByPlaylist(playlistId: Long)

    @Query("DELETE FROM episodes WHERE playlistId = :playlistId")
    suspend fun deleteEpisodesByPlaylist(playlistId: Long)

    @Query("DELETE FROM episodes WHERE seriesId = :seriesId")
    suspend fun deleteEpisodesForSeries(seriesId: String)

    @Transaction
    suspend fun replaceSeriesEpisodes(seriesId: String, episodes: List<EpisodeEntity>) {
        deleteEpisodesForSeries(seriesId)
        if (episodes.isNotEmpty()) {
            insertEpisodes(episodes)
        }
    }

    /**
     * Atomically replaces playlist content while preserving user favorites, hidden flags,
     * and locked category flags. Never called if fetching/parsing failed!
     */
    @Transaction
    suspend fun replacePlaylistCatalogPreservingUserState(
        playlistId: Long,
        newCategories: List<CategoryEntity>,
        newChannels: List<ChannelEntity>,
        newMovies: List<MovieEntity>,
        newSeries: List<SeriesEntity>,
        newEpisodes: List<EpisodeEntity>
    ) {
        val oldCategories = getCategoriesOnce(playlistId).associateBy { it.id }
        val oldChannels = getChannelsOnce(playlistId).associateBy { it.id }
        val oldMovies = getMoviesOnce(playlistId).associateBy { it.id }
        val oldSeries = getSeriesOnce(playlistId).associateBy { it.id }

        val mergedCategories = newCategories.map { cat ->
            val prev = oldCategories[cat.id]
            if (prev != null) cat.copy(isLocked = prev.isLocked, isHidden = prev.isHidden) else cat
        }
        val mergedChannels = newChannels.map { ch ->
            val prev = oldChannels[ch.id]
            if (prev != null) ch.copy(isFavorite = prev.isFavorite, isHidden = prev.isHidden) else ch
        }
        val mergedMovies = newMovies.map { m ->
            val prev = oldMovies[m.id]
            if (prev != null) m.copy(isFavorite = prev.isFavorite) else m
        }
        val mergedSeries = newSeries.map { s ->
            val prev = oldSeries[s.id]
            if (prev != null) s.copy(isFavorite = prev.isFavorite) else s
        }

        deleteCategoriesByPlaylist(playlistId)
        deleteChannelsByPlaylist(playlistId)
        deleteMoviesByPlaylist(playlistId)
        deleteSeriesByPlaylist(playlistId)
        if (newEpisodes.isNotEmpty()) {
            deleteEpisodesByPlaylist(playlistId)
        }

        mergedCategories.chunked(500).forEach { insertCategories(it) }
        mergedChannels.chunked(500).forEach { insertChannels(it) }
        mergedMovies.chunked(500).forEach { insertMovies(it) }
        mergedSeries.chunked(500).forEach { insertSeries(it) }
        newEpisodes.chunked(500).forEach { insertEpisodes(it) }
    }
}
