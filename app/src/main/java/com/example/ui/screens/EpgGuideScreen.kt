package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.entities.ChannelEntity
import com.example.data.local.entities.EpgProgramEntity
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalMehdiColors
import com.example.ui.theme.ModernPurple
import com.example.ui.theme.TurquoiseAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpgGuideScreen(
    isWideScreen: Boolean,
    schedule: List<Pair<ChannelEntity, List<EpgProgramEntity>>>,
    timezoneOffsetMinutes: Int,
    epgStatusMessage: String?,
    onRefreshEpg: () -> Unit,
    onChannelClick: (ChannelEntity) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val mehdiColors = LocalMehdiColors.current
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val offsetHours = timezoneOffsetMinutes / 60f
    val tzLabel = if (offsetHours >= 0) "+${offsetHours}h" else "${offsetHours}h"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.epg_title))
                        Text(
                            text = stringResource(R.string.epg_timezone_offset, tzLabel),
                            style = MaterialTheme.typography.labelSmall,
                            color = mehdiColors.secondaryText
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!epgStatusMessage.isNullOrBlank()) {
                        StatusPill(
                            label = epgStatusMessage,
                            color = TurquoiseAccent,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    IconButton(onClick = onRefreshEpg, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.epg_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        if (schedule.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.epg_no_data),
                    style = MaterialTheme.typography.bodyLarge,
                    color = mehdiColors.secondaryText
                )
            }
        } else {
            val nowMs = System.currentTimeMillis()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(schedule, key = { it.first.id }) { (channel, programs) ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = mehdiColors.cardSurface,
                        border = BorderStroke(1.dp, mehdiColors.subtleBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onChannelClick(channel) }
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(mehdiColors.cardElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (channel.logoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = channel.logoUrl,
                                            contentDescription = channel.name,
                                            modifier = Modifier.fillMaxSize().padding(4.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Icon(Icons.Filled.LiveTv, contentDescription = null, tint = ModernPurple)
                                    }
                                }
                                Text(
                                    text = channel.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(programs, key = { it.id }) { prog ->
                                    val isLiveNow = prog.startTimeMs <= nowMs && prog.endTimeMs > nowMs
                                    Surface(
                                        modifier = Modifier.width(if (isWideScreen) 230.dp else 195.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isLiveNow) ModernPurple.copy(alpha = 0.16f) else mehdiColors.cardElevated,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isLiveNow) ModernPurple else mehdiColors.subtleBorder
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "${timeFormat.format(Date(prog.startTimeMs))} - ${timeFormat.format(Date(prog.endTimeMs))}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = JetBrainsMonoFontFamily
                                                ),
                                                color = if (isLiveNow) TurquoiseAccent else mehdiColors.secondaryText
                                            )
                                            Text(
                                                text = prog.title,
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    fontWeight = if (isLiveNow) FontWeight.Bold else FontWeight.Medium
                                                ),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (prog.description.isNotBlank()) {
                                                Text(
                                                    text = prog.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = mehdiColors.secondaryText,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
