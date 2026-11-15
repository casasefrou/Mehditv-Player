package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.model.UserPreferencesState
import com.example.core.model.VodSortOrder
import com.example.data.local.entities.MovieEntity
import com.example.ui.components.EmptyPlaylistHeroCard
import com.example.ui.components.InstantSearchBar
import com.example.ui.components.PosterMediaCard
import com.example.ui.theme.LocalMehdiColors
import com.example.ui.viewmodel.MoviesUiState

@Composable
fun MoviesScreen(
    isWideScreen: Boolean,
    hasActivePlaylist: Boolean,
    uiState: MoviesUiState,
    onSelectCategory: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onSortChange: (VodSortOrder) -> Unit,
    onToggleFavorite: (MovieEntity) -> Unit,
    onMovieClick: (MovieEntity) -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenQrPairing: () -> Unit
) {
    if (!hasActivePlaylist) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            EmptyPlaylistHeroCard(
                onAddPlaylistClick = onOpenPlaylists,
                onQrPairingClick = onOpenQrPairing
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = if (isWideScreen) 24.dp else 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.movies_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InstantSearchBar(
                query = uiState.searchQuery,
                onQueryChange = onSearchChange,
                placeholder = stringResource(R.string.movies_search_hint),
                modifier = Modifier.weight(1f),
                testTag = "movies_search_bar"
            )
            val nextSort = when (uiState.sortOrder) {
                VodSortOrder.RECENT -> VodSortOrder.NAME_AZ
                VodSortOrder.NAME_AZ -> VodSortOrder.YEAR_DESC
                VodSortOrder.YEAR_DESC -> VodSortOrder.RECENT
            }
            val sortLabel = when (uiState.sortOrder) {
                VodSortOrder.RECENT -> stringResource(R.string.movies_recently_added)
                VodSortOrder.NAME_AZ -> stringResource(R.string.movies_sort_name)
                VodSortOrder.YEAR_DESC -> stringResource(R.string.movies_sort_year)
            }
            OutlinedButton(
                onClick = { onSortChange(nextSort) },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Filled.SortByAlpha, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(sortLabel)
            }
        }

        val specialCats = listOf(
            UserPreferencesState.SPECIAL_CAT_ALL to stringResource(R.string.live_all_channels),
            UserPreferencesState.SPECIAL_CAT_FAVORITES to stringResource(R.string.live_favorites),
            UserPreferencesState.SPECIAL_CAT_RECENT_ADDED to stringResource(R.string.movies_recently_added),
            UserPreferencesState.SPECIAL_CAT_RECENT to stringResource(R.string.live_recently_watched)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(specialCats, key = { it.first }) { (id, label) ->
                FilterChip(
                    selected = uiState.selectedCategoryId == id,
                    onClick = { onSelectCategory(id) },
                    label = { Text(label) }
                )
            }
            items(uiState.categories, key = { it.id }) { cat ->
                FilterChip(
                    selected = uiState.selectedCategoryId == cat.id,
                    onClick = { onSelectCategory(cat.id) },
                    label = { Text(cat.name) }
                )
            }
        }

        if (uiState.movies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        tint = LocalMehdiColors.current.secondaryText,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = stringResource(R.string.movies_no_items),
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalMehdiColors.current.secondaryText
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (isWideScreen) 168.dp else 132.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 18.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.movies, key = { it.id }) { movie ->
                    PosterMediaCard(
                        title = movie.name,
                        posterUrl = movie.posterUrl,
                        subtitle = movie.categoryName,
                        rating = movie.rating,
                        year = movie.year,
                        isFavorite = movie.isFavorite,
                        onClick = { onMovieClick(movie) },
                        onToggleFavorite = { onToggleFavorite(movie) }
                    )
                }
            }
        }
    }
}
