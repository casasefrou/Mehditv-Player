package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MehdiTvApplication
import com.example.core.model.ChannelSortOrder
import com.example.core.model.UserPreferencesState
import com.example.core.model.VodSortOrder
import com.example.data.local.entities.CategoryEntity
import com.example.data.local.entities.ChannelEntity
import com.example.data.local.entities.EpgProgramEntity
import com.example.data.local.entities.EpisodeEntity
import com.example.data.local.entities.MovieEntity
import com.example.data.local.entities.SeriesEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChannelNowNext(
    val current: EpgProgramEntity? = null,
    val next: EpgProgramEntity? = null
)

data class LiveTvUiState(
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: String = UserPreferencesState.SPECIAL_CAT_ALL,
    val searchQuery: String = "",
    val sortOrder: ChannelSortOrder = ChannelSortOrder.DEFAULT,
    val channels: List<ChannelEntity> = emptyList(),
    val epgMap: Map<String, ChannelNowNext> = emptyMap(),
    val lockedCategoryIds: Set<String> = emptySet(),
    val unlockedCategoryIds: Set<String> = emptySet(),
    val hasRadioStreams: Boolean = false
)

data class MoviesUiState(
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: String = UserPreferencesState.SPECIAL_CAT_ALL,
    val searchQuery: String = "",
    val sortOrder: VodSortOrder = VodSortOrder.RECENT,
    val movies: List<MovieEntity> = emptyList(),
    val lockedCategoryIds: Set<String> = emptySet()
)

data class SeriesUiState(
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategoryId: String = UserPreferencesState.SPECIAL_CAT_ALL,
    val searchQuery: String = "",
    val sortOrder: VodSortOrder = VodSortOrder.RECENT,
    val series: List<SeriesEntity> = emptyList(),
    val lockedCategoryIds: Set<String> = emptySet()
)

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MehdiTvApplication
    private val playlistRepo = app.playlistRepository
    private val prefsRepo = app.preferencesRepository

    private val activePlaylist = playlistRepo.observeActivePlaylist()

    // Live TV Filters
    private val _liveSearch = MutableStateFlow("")
    private val _liveCategory = MutableStateFlow(UserPreferencesState.SPECIAL_CAT_ALL)
    private val _liveSort = MutableStateFlow(ChannelSortOrder.DEFAULT)
    private val _unlockedCategories = MutableStateFlow<Set<String>>(emptySet())

    // Movies Filters
    private val _movieSearch = MutableStateFlow("")
    private val _movieCategory = MutableStateFlow(UserPreferencesState.SPECIAL_CAT_ALL)
    private val _movieSort = MutableStateFlow(VodSortOrder.RECENT)

    // Series Filters
    private val _seriesSearch = MutableStateFlow("")
    private val _seriesCategory = MutableStateFlow(UserPreferencesState.SPECIAL_CAT_ALL)
    private val _seriesSort = MutableStateFlow(VodSortOrder.RECENT)

    init {
        viewModelScope.launch {
            prefsRepo.preferencesFlow.collect { prefs ->
                if (_liveCategory.value == UserPreferencesState.SPECIAL_CAT_ALL &&
                    prefs.lastLiveCategoryId.isNotBlank()
                ) {
                    _liveCategory.value = prefs.lastLiveCategoryId
                }
            }
        }
    }

    val liveUiState: StateFlow<LiveTvUiState> = activePlaylist.flatMapLatest { playlist ->
        if (playlist == null) {
            flowOf(LiveTvUiState())
        } else {
            val pid = playlist.id
            val now = System.currentTimeMillis()
            val dataFlow = combine(
                playlistRepo.observeCategories(pid, "LIVE"),
                playlistRepo.observeChannels(pid),
                playlistRepo.observeRecentLiveIds(pid),
                playlistRepo.observeEpgInWindow(pid, now - 3600_000L, now + 8 * 3600_000L)
            ) { cats, channels, recentIds, epgList ->
                Quad(cats, channels, recentIds, epgList)
            }
            val filterFlow = combine(
                _liveCategory,
                _liveSearch,
                _liveSort,
                _unlockedCategories
            ) { cat, query, sort, unlocked ->
                Quad(cat, query, sort, unlocked)
            }

            combine(dataFlow, filterFlow) { data, filter ->
                val (cats, allChannels, recentIds, epgList) = data
                val (selCat, query, sort, unlocked) = filter

                val curTime = System.currentTimeMillis()
                val epgMap = epgList.groupBy { it.epgChannelId }.mapValues { (_, programs) ->
                    val sorted = programs.sortedBy { it.startTimeMs }
                    val current = sorted.firstOrNull { it.startTimeMs <= curTime && it.endTimeMs > curTime }
                    val next = sorted.firstOrNull { it.startTimeMs > curTime }
                    ChannelNowNext(current, next)
                }

                val hiddenCatIds = cats.filter { it.isHidden }.map { it.id }.toSet()
                val lockedCatIds = cats.filter { it.isLocked && it.id !in unlocked }.map { it.id }.toSet()

                val recentIndexMap = recentIds.withIndex().associate { it.value to it.index }
                val trimmedQuery = query.trim().lowercase()

                val filtered = allChannels.filter { ch ->
                    if (ch.categoryId in hiddenCatIds) return@filter false
                    if (ch.categoryId in lockedCatIds && selCat != ch.categoryId) return@filter false

                    val catMatches = when (selCat) {
                        UserPreferencesState.SPECIAL_CAT_ALL -> !ch.isRadio
                        UserPreferencesState.SPECIAL_CAT_FAVORITES -> ch.isFavorite
                        UserPreferencesState.SPECIAL_CAT_RECENT -> ch.id in recentIndexMap
                        UserPreferencesState.SPECIAL_CAT_RADIO -> ch.isRadio
                        else -> ch.categoryId == selCat
                    }
                    if (!catMatches) return@filter false

                    if (trimmedQuery.isEmpty()) true
                    else {
                        val epgInfo = epgMap[ch.epgChannelId]
                        ch.name.lowercase().contains(trimmedQuery) ||
                            ch.categoryName.lowercase().contains(trimmedQuery) ||
                            (ch.channelNumber?.toString()?.contains(trimmedQuery) == true) ||
                            (epgInfo?.current?.title?.lowercase()?.contains(trimmedQuery) == true)
                    }
                }

                val sorted = when (sort) {
                    ChannelSortOrder.A_TO_Z -> filtered.sortedBy { it.name.lowercase() }
                    ChannelSortOrder.Z_TO_A -> filtered.sortedByDescending { it.name.lowercase() }
                    ChannelSortOrder.DEFAULT -> {
                        if (selCat == UserPreferencesState.SPECIAL_CAT_RECENT) {
                            filtered.sortedBy { recentIndexMap[it.id] ?: Int.MAX_VALUE }
                        } else {
                            filtered
                        }
                    }
                }

                LiveTvUiState(
                    categories = cats,
                    selectedCategoryId = selCat,
                    searchQuery = query,
                    sortOrder = sort,
                    channels = sorted,
                    epgMap = epgMap,
                    lockedCategoryIds = lockedCatIds,
                    unlockedCategoryIds = unlocked,
                    hasRadioStreams = allChannels.any { it.isRadio }
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveTvUiState())

    val moviesUiState: StateFlow<MoviesUiState> = activePlaylist.flatMapLatest { playlist ->
        if (playlist == null) {
            flowOf(MoviesUiState())
        } else {
            val pid = playlist.id
            val dataFlow = combine(
                playlistRepo.observeCategories(pid, "MOVIE"),
                playlistRepo.observeMovies(pid),
                playlistRepo.observeRecentlyWatched(pid)
            ) { cats, movies, history -> Triple(cats, movies, history) }

            val filterFlow = combine(
                _movieCategory,
                _movieSearch,
                _movieSort,
                _unlockedCategories
            ) { cat, query, sort, unlocked -> Quad(cat, query, sort, unlocked) }

            combine(dataFlow, filterFlow) { data, filter ->
                val (cats, allMovies, history) = data
                val (selCat, query, sort, unlocked) = filter
                val recentMovieIds = history.filter { it.mediaType == "MOVIE" }.map { it.mediaId }.toSet()
                val hiddenCatIds = cats.filter { it.isHidden }.map { it.id }.toSet()
                val lockedCatIds = cats.filter { it.isLocked && it.id !in unlocked }.map { it.id }.toSet()
                val q = query.trim().lowercase()

                val filtered = allMovies.filter { m ->
                    if (m.categoryId in hiddenCatIds) return@filter false
                    if (m.categoryId in lockedCatIds) return@filter false
                    val catMatches = when (selCat) {
                        UserPreferencesState.SPECIAL_CAT_ALL -> true
                        UserPreferencesState.SPECIAL_CAT_FAVORITES -> m.isFavorite
                        UserPreferencesState.SPECIAL_CAT_RECENT -> m.id in recentMovieIds
                        UserPreferencesState.SPECIAL_CAT_RECENT_ADDED -> true
                        else -> m.categoryId == selCat
                    }
                    if (!catMatches) return@filter false
                    q.isEmpty() || m.name.lowercase().contains(q) || m.categoryName.lowercase().contains(q)
                }

                val sorted = when (sort) {
                    VodSortOrder.NAME_AZ -> filtered.sortedBy { it.name.lowercase() }
                    VodSortOrder.YEAR_DESC -> filtered.sortedByDescending { it.year.toIntOrNull() ?: 0 }
                    VodSortOrder.RECENT -> filtered.sortedByDescending { it.addedAtMs }
                }

                val finalList = if (selCat == UserPreferencesState.SPECIAL_CAT_RECENT_ADDED) {
                    sorted.take(40)
                } else {
                    sorted
                }

                MoviesUiState(
                    categories = cats,
                    selectedCategoryId = selCat,
                    searchQuery = query,
                    sortOrder = sort,
                    movies = finalList,
                    lockedCategoryIds = lockedCatIds
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MoviesUiState())

    val seriesUiState: StateFlow<SeriesUiState> = activePlaylist.flatMapLatest { playlist ->
        if (playlist == null) {
            flowOf(SeriesUiState())
        } else {
            val pid = playlist.id
            val dataFlow = combine(
                playlistRepo.observeCategories(pid, "SERIES"),
                playlistRepo.observeSeries(pid)
            ) { cats, series -> cats to series }

            val filterFlow = combine(
                _seriesCategory,
                _seriesSearch,
                _seriesSort,
                _unlockedCategories
            ) { cat, query, sort, unlocked -> Quad(cat, query, sort, unlocked) }

            combine(dataFlow, filterFlow) { data, filter ->
                val (cats, allSeries) = data
                val (selCat, query, sort, unlocked) = filter
                val hiddenCatIds = cats.filter { it.isHidden }.map { it.id }.toSet()
                val lockedCatIds = cats.filter { it.isLocked && it.id !in unlocked }.map { it.id }.toSet()
                val q = query.trim().lowercase()

                val filtered = allSeries.filter { s ->
                    if (s.categoryId in hiddenCatIds) return@filter false
                    if (s.categoryId in lockedCatIds) return@filter false
                    val catMatches = when (selCat) {
                        UserPreferencesState.SPECIAL_CAT_ALL -> true
                        UserPreferencesState.SPECIAL_CAT_FAVORITES -> s.isFavorite
                        else -> s.categoryId == selCat
                    }
                    if (!catMatches) return@filter false
                    q.isEmpty() || s.name.lowercase().contains(q) || s.categoryName.lowercase().contains(q)
                }

                val sorted = when (sort) {
                    VodSortOrder.NAME_AZ -> filtered.sortedBy { it.name.lowercase() }
                    VodSortOrder.YEAR_DESC -> filtered.sortedByDescending { it.year.toIntOrNull() ?: 0 }
                    VodSortOrder.RECENT -> filtered.sortedByDescending { it.lastUpdatedMs }
                }

                SeriesUiState(
                    categories = cats,
                    selectedCategoryId = selCat,
                    searchQuery = query,
                    sortOrder = sort,
                    series = sorted,
                    lockedCategoryIds = lockedCatIds
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeriesUiState())

    val allCategoriesForActivePlaylist: StateFlow<List<CategoryEntity>> = activePlaylist.flatMapLatest { pl ->
        if (pl == null) flowOf(emptyList())
        else combine(
            playlistRepo.observeCategories(pl.id, "LIVE", includeHidden = true),
            playlistRepo.observeCategories(pl.id, "MOVIE", includeHidden = true),
            playlistRepo.observeCategories(pl.id, "SERIES", includeHidden = true)
        ) { live, mov, ser -> live + mov + ser }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fullEpgSchedule: StateFlow<List<Pair<ChannelEntity, List<EpgProgramEntity>>>> =
        activePlaylist.flatMapLatest { pl ->
            if (pl == null) {
                flowOf(emptyList())
            } else {
                val now = System.currentTimeMillis()
                combine(
                    playlistRepo.observeChannels(pl.id),
                    playlistRepo.observeEpgInWindow(pl.id, now - 3600_000L, now + 24 * 3600_000L)
                ) { channels, programs ->
                    val grouped = programs.groupBy { it.epgChannelId }
                    channels.mapNotNull { ch ->
                        val chPrograms = grouped[ch.epgChannelId]
                            ?.sortedBy { it.startTimeMs }
                            ?.takeIf { it.isNotEmpty() }
                            ?: return@mapNotNull null
                        ch to chPrograms
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectLiveCategory(categoryId: String) {
        _liveCategory.value = categoryId
        viewModelScope.launch { prefsRepo.setLastLiveCategory(categoryId) }
    }

    fun setLiveSearch(query: String) { _liveSearch.value = query }
    fun setLiveSort(order: ChannelSortOrder) { _liveSort.value = order }

    fun unlockCategory(categoryId: String) {
        _unlockedCategories.value = _unlockedCategories.value + categoryId
    }

    fun selectMovieCategory(categoryId: String) {
        _movieCategory.value = categoryId
        viewModelScope.launch { prefsRepo.setLastMovieCategory(categoryId) }
    }

    fun setMovieSearch(query: String) { _movieSearch.value = query }
    fun setMovieSort(order: VodSortOrder) { _movieSort.value = order }

    fun selectSeriesCategory(categoryId: String) {
        _seriesCategory.value = categoryId
        viewModelScope.launch { prefsRepo.setLastSeriesCategory(categoryId) }
    }

    fun setSeriesSearch(query: String) { _seriesSearch.value = query }
    fun setSeriesSort(order: VodSortOrder) { _seriesSort.value = order }

    fun toggleChannelFavorite(channel: ChannelEntity) {
        viewModelScope.launch { playlistRepo.toggleChannelFavorite(channel.id, !channel.isFavorite) }
    }

    fun hideChannel(channel: ChannelEntity) {
        viewModelScope.launch { playlistRepo.toggleChannelHidden(channel.id, true) }
    }

    fun toggleMovieFavorite(movie: MovieEntity) {
        viewModelScope.launch { playlistRepo.toggleMovieFavorite(movie.id, !movie.isFavorite) }
    }

    fun toggleSeriesFavorite(series: SeriesEntity) {
        viewModelScope.launch { playlistRepo.toggleSeriesFavorite(series.id, !series.isFavorite) }
    }

    fun observeMovieDetail(movieId: String): Flow<MovieEntity?> {
        viewModelScope.launch { playlistRepo.loadMovieDetailsIfNeeded(movieId) }
        return playlistRepo.observeMovieById(movieId)
    }

    fun observeSeriesDetail(seriesId: String): Flow<SeriesEntity?> {
        viewModelScope.launch { playlistRepo.loadSeriesDetailsIfNeeded(seriesId) }
        return playlistRepo.observeSeriesById(seriesId)
    }

    fun observeEpisodes(seriesId: String): Flow<List<EpisodeEntity>> =
        playlistRepo.observeEpisodesForSeries(seriesId)

    fun observeLastWatchedForSeries(seriesId: String) =
        playlistRepo.observeLastWatchedForSeries(seriesId)

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
