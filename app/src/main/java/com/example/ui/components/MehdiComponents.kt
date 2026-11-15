package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.security.DeviceIdentityManager
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalMehdiColors
import com.example.ui.theme.ModernPurple
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.ui.theme.TurquoiseAccent

@Composable
fun MehdiTvBrandMark(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 36.dp else 42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(DeepNavy, ModernPurple)
                    )
                )
                .border(1.dp, TurquoiseAccent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.app_name),
                tint = Color.White,
                modifier = Modifier.size(if (compact) 22.dp else 26.dp)
            )
        }
        Column {
            Text(
                text = "MehdiTv",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!compact) {
                Text(
                    text = "PLAYER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = PlusJakartaFontFamily,
                        letterSpacing = 1.8.sp
                    ),
                    color = LocalMehdiColors.current.turquoiseAccent
                )
            }
        }
    }
}

/**
 * TV-remote (D-pad) and Touch-friendly card with subtle scale + high-contrast focus ring.
 */
@Composable
fun TvFocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    cornerRadius: Dp = 18.dp,
    containerColor: Color = LocalMehdiColors.current.cardSurface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val mehdiColors = LocalMehdiColors.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = spring(stiffness = 450f),
        label = "tv_focus_scale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) mehdiColors.focusRing else mehdiColors.subtleBorder,
        label = "tv_focus_border"
    )

    Surface(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        tonalElevation = if (isFocused) 6.dp else 1.dp,
        shadowElevation = if (isFocused) 8.dp else 2.dp,
        border = BorderStroke(if (isFocused) 2.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun StatusPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun InstantSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String = "search_input"
) {
    val mehdiColors = LocalMehdiColors.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = mehdiColors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = mehdiColors.secondaryText
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("${testTag}_clear")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_clear),
                        tint = mehdiColors.secondaryText
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = mehdiColors.purpleBrand,
            unfocusedBorderColor = mehdiColors.subtleBorder,
            focusedContainerColor = mehdiColors.cardSurface,
            unfocusedContainerColor = mehdiColors.cardSurface
        )
    )
}

@Composable
fun PosterMediaCard(
    title: String,
    posterUrl: String,
    subtitle: String?,
    rating: String?,
    year: String?,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val mehdiColors = LocalMehdiColors.current
    TvFocusCard(
        onClick = onClick,
        modifier = modifier,
        testTag = testTag,
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .background(mehdiColors.cardElevated)
        ) {
            if (posterUrl.isNotBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tv,
                        contentDescription = null,
                        tint = mehdiColors.secondaryText.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Bottom subtle gradient for legibility of badges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
            )

            if (!rating.isNullOrBlank() && rating != "0" && rating != "0.0") {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.72f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = rating.take(4),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.details_favorite_add),
                    tint = if (isFavorite) Color(0xFFFB7185) else Color.White.copy(alpha = 0.9f)
                )
            }

            if (!year.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = mehdiColors.purpleBrand.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = year,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = mehdiColors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun QrCodePreviewBox(
    payload: String,
    size: Dp = 156.dp,
    modifier: Modifier = Modifier
) {
    val bitmap: ImageBitmap? = remember(payload) {
        DeviceIdentityManager.generateQrBitmap(payload, 512)
    }
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, LocalMehdiColors.current.subtleBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.home_qr_pairing_title),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = "QR",
                    style = MaterialTheme.typography.titleMedium,
                    color = DeepNavy
                )
            }
        }
    }
}

@Composable
fun EmptyPlaylistHeroCard(
    onAddPlaylistClick: () -> Unit,
    onQrPairingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mehdiColors = LocalMehdiColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = mehdiColors.cardSurface,
        border = BorderStroke(1.dp, mehdiColors.subtleBorder),
        shadowElevation = 3.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_stream_1794700393351),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                DeepNavy.copy(alpha = 0.55f),
                                DeepNavy.copy(alpha = 0.92f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.playlists_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.playlists_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.84f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onAddPlaylistClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("empty_add_playlist_btn")
                    ) {
                        Text(text = stringResource(R.string.home_add_playlist_cta))
                    }
                    OutlinedButton(
                        onClick = onQrPairingClick,
                        border = BorderStroke(1.dp, TurquoiseAccent),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("empty_qr_pair_btn")
                    ) {
                        Text(
                            text = stringResource(R.string.home_qr_pairing_title),
                            color = TurquoiseAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ParentalPinDialog(
    title: String,
    isCreation: Boolean,
    errorText: String? = null,
    onDismiss: () -> Unit,
    onSubmitPin: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val lengthErr = stringResource(R.string.pin_length_error)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = LocalMehdiColors.current.purpleBrand
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 4-dot visual indicator
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(4) { idx ->
                        val filled = idx < pin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) LocalMehdiColors.current.purpleBrand
                                    else LocalMehdiColors.current.subtleBorder
                                )
                        )
                    }
                }

                OutlinedTextField(
                    value = pin,
                    onValueChange = { next ->
                        if (next.length <= 4 && next.all { it.isDigit() }) {
                            pin = next
                            localError = null
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .width(160.dp)
                        .testTag("pin_input_field"),
                    placeholder = { Text("••••", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                )

                // Quick touch/D-pad friendly keypad
                val digits = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "⌫")
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    digits.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { key ->
                                Surface(
                                    modifier = Modifier
                                        .size(width = 56.dp, height = 44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            when (key) {
                                                "C" -> pin = ""
                                                "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                                else -> if (pin.length < 4) {
                                                    pin += key
                                                    localError = null
                                                }
                                            }
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = LocalMehdiColors.current.cardElevated
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (key == "⌫") {
                                            Icon(
                                                imageVector = Icons.Filled.Backspace,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Text(
                                                text = key,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontFamily = PlusJakartaFontFamily
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val shownErr = localError ?: errorText
                if (!shownErr.isNullOrBlank()) {
                    Text(
                        text = shownErr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length != 4) {
                        localError = lengthErr
                    } else {
                        onSubmitPin(pin)
                    }
                },
                modifier = Modifier.testTag("pin_confirm_btn")
            ) {
                Text(stringResource(if (isCreation) R.string.action_save else R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
