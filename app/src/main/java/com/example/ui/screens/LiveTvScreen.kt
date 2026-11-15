package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.model.ChannelSortOrder
import com.example.core.model.LiveViewMode
import com.example.core.model.UserPreferencesState
import com.example.data.local.entities.CategoryEntity
import com.example.data.local.entities.ChannelEntity
import com.example.ui.components.EmptyPlaylistHeroCard
import com.example.ui.components.InstantSearchBar
import com.example.ui.components.ParentalPinDialog
import com.example.ui.components.TvFocusCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalMehdiColors
import com.example.ui.theme.ModernPurple
import com.example.ui.theme.TurquoiseAccent
import com.example.ui.viewmodel.ChannelNowNext
import com.example.ui.viewmodel.LiveTvUiState
import kotlinx.coroutines.launch

@Composable
fun LiveTvScreen(
    isWideScreen: Boolean,
    hasActivePlaylist: Boolean,
    uiState: LiveTvUiState,
    viewMode: LiveViewMode,
    hasParentalPin: Boolean,
    onSelectCategory: (String) -> Unit,
    onUnlockCategory: (String) -> Unit,
    onVerifyPin: suspend (String) -> Boolean,
    onSearchChange: (String) -> Unit,
    onSortChange: (ChannelSortOrder) -> Unit,
    onToggleViewMode: (LiveViewMode) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
    onChannelClick: (ChannelEntity) -> Unit,
    onOpenEpgGuide: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenQrPairing: () -> Unit
) {
    val mehdiColors = LocalMehdiColors.current
    val scope = rememberCoroutineScope()
    var pendingLockedCategoryId by remember { mutableStateOf<String?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    fun handleCategoryTap(catId: String) {
        if (catId in uiState.lockedCategoryIds && hasParentalPin) {
            pinError = null
            pendingLockedCategoryId = catId
        } else {
            onSelectCategory(catId)
        }
    }

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
        // Header + Search + Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.live_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpenEpgGuide,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("live_open_epg_btn")
                ) {
                    Icon(Icons.Filled.CalendarViewDay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.live_epg_guide))
                }
                IconButton(
                    onClick = {
                        onToggleViewMode(
                            if (viewMode == LiveViewMode.GRID) LiveViewMode.LIST else LiveViewMode.GRID
                        )
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("live_toggle_view_btn")
                ) {
                    Icon(
                        imageVector = if (viewMode == LiveViewMode.GRID) Icons.Filled.ViewList else Icons.Filled.GridView,
                        contentDescription = "Toggle Grid/List"
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InstantSearchBar(
                query = uiState.searchQuery,
                onQueryChange = onSearchChange,
                placeholder = stringResource(R.string.live_search_hint),
                modifier = Modifier.weight(1f),
                testTag = "live_search_bar"
            )

            // Sort cycle pill
            val nextSort = when (uiState.sortOrder) {
                ChannelSortOrder.DEFAULT -> ChannelSortOrder.A_TO_Z
                ChannelSortOrder.A_TO_Z -> ChannelSortOrder.Z_TO_A
                ChannelSortOrder.Z_TO_A -> ChannelSortOrder.DEFAULT
            }
            val sortLabel = when (uiState.sortOrder) {
                ChannelSortOrder.DEFAULT -> stringResource(R.string.live_sort_default)
                ChannelSortOrder.A_TO_Z -> stringResource(R.string.live_sort_az)
                ChannelSortOrder.Z_TO_A -> stringResource(R.string.live_sort_za)
            }
            OutlinedButton(
                onClick = { onSortChange(nextSort) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("live_sort_btn")
            ) {
                Icon(Icons.Filled.SortByAlpha, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(sortLabel)
            }
        }

        if (isWideScreen) {
            // TV / Tablet Dual-Pane: Left Category Sidebar + Main Channels Area
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LiveCategorySidebar(
                    categories = uiState.categories,
                    selectedCategoryId = uiState.selectedCategoryId,
                    hasRadioStreams = uiState.hasRadioStreams,
                    lockedIds = uiState.lockedCategoryIds,
                    onSelectCategory = ::handleCategoryTap,
                    modifier = Modifier
                        .width(230.dp)
                        .fillMaxHeight()
                )
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    ChannelCollectionArea(
                        channels = uiState.channels,
                        epgMap = uiState.epgMap,
                        viewMode = viewMode,
                        isWideScreen = true,
                        onToggleFavorite = onToggleFavorite,
                        onChannelClick = onChannelClick
                    )
                }
            }
        } else {
            // Mobile: Compact Category Chips + Responsive Channel List/Grid
            LiveCategoryChipsRow(
                categories = uiState.categories,
                selectedCategoryId = uiState.selectedCategoryId,
                hasRadioStreams = uiState.hasRadioStreams,
                lockedIds = uiState.lockedCategoryIds,
                onSelectCategory = ::handleCategoryTap
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ChannelCollectionArea(
                    channels = uiState.channels,
                    epgMap = uiState.epgMap,
                    viewMode = viewMode,
                    isWideScreen = false,
                    onToggleFavorite = onToggleFavorite,
                    onChannelClick = onChannelClick
                )
            }
        }
    }

    pendingLockedCategoryId?.let { lockedCatId ->
        val incorrectMsg = stringResource(R.string.pin_incorrect)
        ParentalPinDialog(
            title = stringResource(R.string.pin_enter_title),
            isCreation = false,
            errorText = pinError,
            onDismiss = { pendingLockedCategoryId = null },
            onSubmitPin = { entered ->
                scope.launch {
                    if (onVerifyPin(entered)) {
                        onUnlockCategory(lockedCatId)
                        onSelectCategory(lockedCatId)
                        pendingLockedCategoryId = null
                    } else {
                        pinError = incorrectMsg
                    }
                }
            }
        )
    }
}

@Composable
private fun LiveCategorySidebar(
    categories: List<CategoryEntity>,
    selectedCategoryId: String,
    hasRadioStreams: Boolean,
    lockedIds: Set<String>,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val mehdiColors = LocalMehdiColors.current
    val specialCats = buildList {
        add(UserPreferencesState.SPECIAL_CAT_ALL to stringResource(R.string.live_all_channels))
        add(UserPreferencesState.SPECIAL_CAT_FAVORITES to stringResource(R.string.live_favorites))
        add(UserPreferencesState.SPECIAL_CAT_RECENT to stringResource(R.string.live_recently_watched))
        if (hasRadioStreams || selectedCategoryId == UserPreferencesState.SPECIAL_CAT_RADIO) {
            add(UserPreferencesState.SPECIAL_CAT_RADIO to stringResource(R.string.live_radio_streams))
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = mehdiColors.cardSurface,
        border = BorderStroke(1.dp, mehdiColors.subtleBorder)
    ) {
        LazyColumn(
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(specialCats, key = { it.first }) { (id, label) ->
                SidebarCategoryItem(
                    label = label,
                    selected = selectedCategoryId == id,
                    isLocked = false,
                    onClick = { onSelectCategory(id) }
                )
            }
            items(categories, key = { it.id }) { cat ->
                SidebarCategoryItem(
                    label = cat.name,
                    selected = selectedCategoryId == cat.id,
                    isLocked = cat.id in lockedIds,
                    onClick = { onSelectCategory(cat.id) }
                )
            }
        }
    }
}

@Composable
private fun SidebarCategoryItem(
    label: String,
    selected: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit
) {
    val mehdiColors = LocalMehdiColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .focusable()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) ModernPurple.copy(alpha = 0.16f) else mehdiColors.cardSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) ModernPurple else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isLocked) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = mehdiColors.secondaryText,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun LiveCategoryChipsRow(
    categories: List<CategoryEntity>,
    selectedCategoryId: String,
    hasRadioStreams: Boolean,
    lockedIds: Set<String>,
    onSelectCategory: (String) -> Unit
) {
    val specialCats = buildList {
        add(UserPreferencesState.SPECIAL_CAT_ALL to stringResource(R.string.live_all_channels))
        add(UserPreferencesState.SPECIAL_CAT_FAVORITES to stringResource(R.string.live_favorites))
        add(UserPreferencesState.SPECIAL_CAT_RECENT to stringResource(R.string.live_recently_watched))
        if (hasRadioStreams || selectedCategoryId == UserPreferencesState.SPECIAL_CAT_RADIO) {
            add(UserPreferencesState.SPECIAL_CAT_RADIO to stringResource(R.string.live_radio_streams))
        }
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(specialCats, key = { it.first }) { (id, label) ->
            FilterChip(
                selected = selectedCategoryId == id,
                onClick = { onSelectCategory(id) },
                label = { Text(label) }
            )
        }
        items(categories, key = { it.id }) { cat ->
            FilterChip(
                selected = selectedCategoryId == cat.id,
                onClick = { onSelectCategory(cat.id) },
                label = { Text(cat.name) },
                trailingIcon = if (cat.id in lockedIds) {
                    { Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) }
                } else null
            )
        }
    }
}

@Composable
private fun ChannelCollectionArea(
    channels: List<ChannelEntity>,
    epgMap: Map<String, ChannelNowNext>,
    viewMode: LiveViewMode,
    isWideScreen: Boolean,
    onToggleFavorite: (ChannelEntity) -> Unit,
    onChannelClick: (ChannelEntity) -> Unit
) {
    if (channels.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LiveTv,
                    contentDescription = null,
                    tint = LocalMehdiColors.current.secondaryText,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = stringResource(R.string.live_no_channels),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalMehdiColors.current.secondaryText
                )
            }
        }
        return
    }

    if (viewMode == LiveViewMode.GRID) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = if (isWideScreen) 210.dp else 156.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(channels, key = { it.id }) { ch ->
                ChannelGridCard(
                    channel = ch,
                    epg = epgMap[ch.epgChannelId],
                    onToggleFavorite = { onToggleFavorite(ch) },
                    onClick = { onChannelClick(ch) }
                )
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(channels, key = { it.id }) { ch ->
                ChannelListRowCard(
                    channel = ch,
                    epg = epgMap[ch.epgChannelId],
                    onToggleFavorite = { onToggleFavorite(ch) },
                    onClick = { onChannelClick(ch) }
                )
            }
        }
    }
}

@Composable
private fun ChannelGridCard(
    channel: ChannelEntity,
    epg: ChannelNowNext?,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    val mehdiColors = LocalMehdiColors.current
    TvFocusCard(
        onClick = onClick,
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(mehdiColors.cardElevated),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logoUrl.isNotBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.LiveTv,
                        contentDescription = null,
                        tint = ModernPurple
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel.channelNumber != null) {
                    Text(
                        text = "#${channel.channelNumber}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = mehdiColors.secondaryText
                    )
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.details_favorite_add),
                        tint = if (channel.isFavorite) MaterialTheme.colorScheme.error else mehdiColors.secondaryText
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = epg?.current?.title ?: channel.categoryName,
            style = MaterialTheme.typography.bodySmall,
            color = if (epg?.current != null) TurquoiseAccent else mehdiColors.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (epg?.current != null) {
            val total = (epg.current.endTimeMs - epg.current.startTimeMs).coerceAtLeast(1L)
            val elapsed = (System.currentTimeMillis() - epg.current.startTimeMs).coerceIn(0L, total)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { elapsed.toFloat() / total.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = TurquoiseAccent,
                trackColor = mehdiColors.subtleBorder
            )
        }
    }
}

@Composable
private fun ChannelListRowCard(
    channel: ChannelEntity,
    epg: ChannelNowNext?,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    val mehdiColors = LocalMehdiColors.current
    TvFocusCard(
        onClick = onClick,
        cornerRadius = 14.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (channel.channelNumber != null) {
                Text(
                    text = channel.channelNumber.toString().padStart(3, '0'),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = mehdiColors.secondaryText
                )
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(mehdiColors.cardElevated),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logoUrl.isNotBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.LiveTv,
                        contentDescription = null,
                        tint = ModernPurple,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (epg?.current != null) {
                    Text(
                        text = "${stringResource(R.string.live_now_playing)}: ${epg.current.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TurquoiseAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = channel.categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = mehdiColors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (channel.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.details_favorite_add),
                    tint = if (channel.isFavorite) MaterialTheme.colorScheme.error else mehdiColors.secondaryText
                )
            }
        }
    }
}
