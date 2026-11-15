package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.entities.EpisodeEntity
import com.example.data.local.entities.SeriesEntity
import com.example.data.local.entities.WatchHistoryEntity
import com.example.ui.components.TvFocusCard
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.LocalMehdiColors
import com.example.ui.theme.ModernPurple
import com.example.ui.theme.TurquoiseAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    series: SeriesEntity?,
    episodes: List<EpisodeEntity>,
    lastWatched: WatchHistoryEntity?,
    autoplayEnabled: Boolean,
    onToggleAutoplay: (Boolean) -> Unit,
    onToggleFavorite: (SeriesEntity) -> Unit,
    onPlayEpisode: (SeriesEntity, EpisodeEntity, Boolean) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val mehdiColors = LocalMehdiColors.current

    val seasons = remember(episodes) {
        episodes.map { it.seasonNumber }.distinct().sorted()
    }
    var selectedSeason by remember { mutableIntStateOf(1) }
    LaunchedEffect(seasons, lastWatched) {
        if (seasons.isNotEmpty()) {
            val lastSeason = lastWatched?.seasonNumber
            selectedSeason = if (lastSeason != null && lastSeason in seasons) lastSeason else seasons.first()
        }
    }

    val seasonEpisodes = remember(episodes, selectedSeason) {
        episodes.filter { it.seasonNumber == selectedSeason }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(series?.name.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        if (series == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.loading))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Backdrop + Poster Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(DeepNavy)
                ) {
                    val hero = series.backdropUrl.ifBlank { series.posterUrl }
                    if (hero.isNotBlank()) {
                        AsyncImage(
                            model = hero,
                            contentDescription = series.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (series.posterUrl.isNotBlank()) {
                            AsyncImage(
                                model = series.posterUrl,
                                contentDescription = series.name,
                                modifier = Modifier
                                    .width(92.dp)
                                    .height(132.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = series.name,
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = listOfNotNull(
                                    series.year.takeIf { it.isNotBlank() },
                                    series.genre.takeIf { it.isNotBlank() },
                                    series.rating.takeIf { it.isNotBlank() && it != "0" }?.let { "★ $it" }
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = mehdiColors.secondaryText
                            )
                        }
                    }
                }
            }

            // Action row: Resume last episode / Favorite / Autoplay toggle
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val resumeEp = episodes.firstOrNull { it.id == lastWatched?.mediaId }
                            ?: episodes.firstOrNull()
                        if (resumeEp != null) {
                            Button(
                                onClick = {
                                    onPlayEpisode(series, resumeEp, lastWatched?.mediaId == resumeEp.id)
                                },
                                modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (lastWatched != null) {
                                        "${stringResource(R.string.action_resume)} • S${resumeEp.seasonNumber}E${resumeEp.episodeNumber}"
                                    } else {
                                        "${stringResource(R.string.action_play)} • S${resumeEp.seasonNumber}E${resumeEp.episodeNumber}"
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { onToggleFavorite(series) },
                            border = BorderStroke(
                                1.dp,
                                if (series.isFavorite) MaterialTheme.colorScheme.error else mehdiColors.subtleBorder
                            ),
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (series.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = stringResource(R.string.details_favorite_add),
                                tint = if (series.isFavorite) MaterialTheme.colorScheme.error else mehdiColors.secondaryText
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = mehdiColors.cardSurface,
                        border = BorderStroke(1.dp, mehdiColors.subtleBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.series_autoplay_next),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = autoplayEnabled,
                                onCheckedChange = onToggleAutoplay
                            )
                        }
                    }
                }
            }

            if (series.plot.isNotBlank()) {
                item {
                    Text(
                        text = series.plot,
                        style = MaterialTheme.typography.bodyMedium,
                        color = mehdiColors.secondaryText,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            if (seasons.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(seasons, key = { it }) { seasonNum ->
                            FilterChip(
                                selected = selectedSeason == seasonNum,
                                onClick = { selectedSeason = seasonNum },
                                label = { Text(stringResource(R.string.series_season_label, seasonNum)) }
                            )
                        }
                    }
                }
            }

            items(seasonEpisodes, key = { it.id }) { ep ->
                val isLastWatchedEp = lastWatched?.mediaId == ep.id
                TvFocusCard(
                    onClick = { onPlayEpisode(series, ep, isLastWatchedEp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    cornerRadius = 14.dp,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(106.dp)
                                .height(64.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(mehdiColors.cardElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            if (ep.thumbnailUrl.isNotBlank()) {
                                AsyncImage(
                                    model = ep.thumbnailUrl,
                                    contentDescription = ep.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Filled.Tv, contentDescription = null, tint = ModernPurple)
                            }
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp),
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.72f)
                            ) {
                                Text(
                                    text = stringResource(R.string.series_episode_label, ep.episodeNumber),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = ep.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (isLastWatchedEp) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = if (isLastWatchedEp) TurquoiseAccent else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (ep.plot.isNotBlank()) {
                                Text(
                                    text = ep.plot,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = mehdiColors.secondaryText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (ep.duration.isNotBlank()) {
                                Text(
                                    text = ep.duration,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = mehdiColors.secondaryText
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.action_play),
                            tint = ModernPurple
                        )
                    }
                }
            }
        }
    }
}
