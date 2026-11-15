package com.example.data.parser

import com.example.core.model.HttpHeaderEntry
import com.example.core.security.DeviceIdentityManager
import com.example.data.local.entities.CategoryEntity
import com.example.data.local.entities.ChannelEntity
import com.example.data.local.entities.EpisodeEntity
import com.example.data.local.entities.MovieEntity
import com.example.data.local.entities.SeriesEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

data class ParsedM3uResult(
    val discoveredEpgUrl: String?,
    val categories: List<CategoryEntity>,
    val channels: List<ChannelEntity>,
    val movies: List<MovieEntity>,
    val series: List<SeriesEntity>,
    val episodes: List<EpisodeEntity>
) {
    val totalItemCount: Int get() = channels.size + movies.size + episodes.size
    val radioCount: Int get() = channels.count { it.isRadio }
    val supportsCatchup: Boolean get() = channels.any { it.hasCatchup }
}

/**
 * Streaming, memory-efficient M3U / M3U8 parser with attribute recovery for malformed lines,
 * #EXTGRP, #EXTVLCOPT, EPG URL auto-discovery, and Live/Movie/Series classification.
 */
object M3uParser {

    private val attrRegex = Regex("""([a-zA-Z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""")
    private val seriesPattern = Regex("""^(.*?)\s+[Ss](\d{1,2})\s*[Ee](\d{1,3})\b(.*)$""")
    private val vodExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v")
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        inputStream: InputStream,
        playlistId: Long
    ): ParsedM3uResult {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 32 * 1024)
        var discoveredEpgUrl: String? = null

        val liveCategoryMap = linkedMapOf<String, CategoryEntity>()
        val movieCategoryMap = linkedMapOf<String, CategoryEntity>()
        val seriesCategoryMap = linkedMapOf<String, CategoryEntity>()

        val channels = mutableListOf<ChannelEntity>()
        val movies = mutableListOf<MovieEntity>()
        val seriesMap = linkedMapOf<String, SeriesEntity>()
        val episodes = mutableListOf<EpisodeEntity>()

        var pendingName = ""
        var pendingAttrs = emptyMap<String, String>()
        var pendingGroup = ""
        val pendingHeaders = mutableMapOf<String, String>()
        var pendingVlcUa: String? = null
        var hasPendingInf = false
        var itemIndex = 0

        reader.useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.trim().trimStart('\uFEFF')
                if (line.isEmpty()) continue

                when {
                    line.startsWith("#EXTM3U", ignoreCase = true) -> {
                        val headerAttrs = parseAttributes(line.substringAfter("#EXTM3U", ""))
                        val tvgUrl = headerAttrs["url-tvg"]
                            ?: headerAttrs["x-tvg-url"]
                            ?: headerAttrs["epg-url"]
                        if (!tvgUrl.isNullOrBlank()) {
                            discoveredEpgUrl = tvgUrl.split(",").firstOrNull()?.trim()
                        }
                    }

                    line.startsWith("#EXTINF:", ignoreCase = true) -> {
                        hasPendingInf = true
                        val body = line.substringAfter(":")
                        val commaIdx = findTitleCommaIndex(body)
                        val metaPart = if (commaIdx >= 0) body.substring(0, commaIdx) else body
                        pendingName = if (commaIdx >= 0) body.substring(commaIdx + 1).trim() else ""
                        pendingAttrs = parseAttributes(metaPart)
                        if (!pendingAttrs["group-title"].isNullOrBlank()) {
                            pendingGroup = pendingAttrs["group-title"].orEmpty()
                        }
                    }

                    line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                        val grp = line.substringAfter(":").trim()
                        if (grp.isNotEmpty()) pendingGroup = grp
                    }

                    line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                        val opt = line.substringAfter(":").trim()
                        when {
                            opt.startsWith("http-user-agent=", ignoreCase = true) -> {
                                pendingVlcUa = opt.substringAfter("=").trim()
                                if (!pendingVlcUa.isNullOrBlank()) {
                                    pendingHeaders["User-Agent"] = pendingVlcUa!!
                                }
                            }
                            opt.startsWith("http-referrer=", ignoreCase = true) -> {
                                val ref = opt.substringAfter("=").trim()
                                if (ref.isNotBlank()) pendingHeaders["Referer"] = ref
                            }
                        }
                    }

                    line.startsWith("#") -> {
                        // Ignore unknown comment / directive lines gracefully
                    }

                    else -> {
                        val streamUrl = line
                        if (DeviceIdentityManager.isValidStreamOrPlaylistUrl(streamUrl)) {
                            itemIndex++
                            val title = pendingName.ifBlank {
                                pendingAttrs["tvg-name"]?.takeIf { it.isNotBlank() }
                                    ?: streamUrl.substringAfterLast('/').substringBefore('?')
                                        .ifBlank { "Stream $itemIndex" }
                            }
                            val groupTitle = pendingAttrs["group-title"]
                                ?.takeIf { it.isNotBlank() }
                                ?: pendingGroup.takeIf { it.isNotBlank() }
                                ?: "General"
                            val logoUrl = pendingAttrs["tvg-logo"].orEmpty()
                            val epgId = pendingAttrs["tvg-id"]
                                ?.takeIf { it.isNotBlank() }
                                ?: pendingAttrs["tvg-name"].orEmpty()
                            val chNo = pendingAttrs["tvg-chno"]?.toIntOrNull()
                                ?: pendingAttrs["channel-number"]?.toIntOrNull()
                            val tvgType = pendingAttrs["tvg-type"]?.lowercase().orEmpty()
                            val ext = streamUrl.substringBefore('?').substringAfterLast('.', "").lowercase()
                            val seriesMatch = seriesPattern.matchEntire(title)
                            val headersJson = if (pendingHeaders.isNotEmpty()) {
                                json.encodeToString(
                                    pendingHeaders.entries.map { HttpHeaderEntry(it.key, it.value) }
                                )
                            } else {
                                ""
                            }

                            val isRadio = tvgType == "radio" ||
                                pendingAttrs["radio"].equals("true", ignoreCase = true) ||
                                groupTitle.contains("radio", ignoreCase = true) ||
                                groupTitle.contains("راديو")
                            val hasCatchup = !pendingAttrs["catchup"].isNullOrBlank() ||
                                !pendingAttrs["catchup-days"].isNullOrBlank() ||
                                !pendingAttrs["catchup-source"].isNullOrBlank() ||
                                (pendingAttrs["timeshift"]?.toIntOrNull() ?: 0) > 0

                            val isSeries = !isRadio && (
                                tvgType == "series" ||
                                    (seriesMatch != null && (ext in vodExtensions || groupTitle.contains("series", true)))
                                )
                            val isMovie = !isRadio && !isSeries && (
                                tvgType == "movie" ||
                                    tvgType == "vod" ||
                                    ext in vodExtensions ||
                                    streamUrl.contains("/movie/", ignoreCase = true)
                                )

                            when {
                                isSeries && seriesMatch != null -> {
                                    val seriesName = seriesMatch.groupValues[1].trim().ifBlank { title }
                                    val seasonNum = seriesMatch.groupValues[2].toIntOrNull() ?: 1
                                    val episodeNum = seriesMatch.groupValues[3].toIntOrNull() ?: 1
                                    val catId = "${playlistId}_SERIES_${slugify(groupTitle)}"
                                    seriesCategoryMap.getOrPut(catId) {
                                        CategoryEntity(
                                            id = catId,
                                            playlistId = playlistId,
                                            section = "SERIES",
                                            rawCategoryId = slugify(groupTitle),
                                            name = groupTitle,
                                            sortOrder = seriesCategoryMap.size
                                        )
                                    }
                                    val seriesId = "${playlistId}_ser_${slugify(seriesName)}"
                                    seriesMap.getOrPut(seriesId) {
                                        SeriesEntity(
                                            id = seriesId,
                                            playlistId = playlistId,
                                            categoryId = catId,
                                            categoryName = groupTitle,
                                            name = seriesName,
                                            posterUrl = logoUrl,
                                            lastUpdatedMs = System.currentTimeMillis()
                                        )
                                    }
                                    episodes.add(
                                        EpisodeEntity(
                                            id = "${playlistId}_ep_${stableHash("${seriesId}_${seasonNum}_${episodeNum}_$streamUrl")}",
                                            seriesId = seriesId,
                                            playlistId = playlistId,
                                            seasonNumber = seasonNum,
                                            episodeNumber = episodeNum,
                                            title = title,
                                            streamUrl = streamUrl,
                                            thumbnailUrl = logoUrl,
                                            containerExtension = ext.ifBlank { "mp4" }
                                        )
                                    )
                                }

                                isMovie -> {
                                    val catId = "${playlistId}_MOVIE_${slugify(groupTitle)}"
                                    movieCategoryMap.getOrPut(catId) {
                                        CategoryEntity(
                                            id = catId,
                                            playlistId = playlistId,
                                            section = "MOVIE",
                                            rawCategoryId = slugify(groupTitle),
                                            name = groupTitle,
                                            sortOrder = movieCategoryMap.size
                                        )
                                    }
                                    movies.add(
                                        MovieEntity(
                                            id = "${playlistId}_mov_${stableHash("$title|$streamUrl")}",
                                            playlistId = playlistId,
                                            categoryId = catId,
                                            categoryName = groupTitle,
                                            name = title,
                                            streamUrl = streamUrl,
                                            posterUrl = logoUrl,
                                            containerExtension = ext.ifBlank { "mp4" },
                                            addedAtMs = System.currentTimeMillis() - itemIndex
                                        )
                                    )
                                }

                                else -> {
                                    val catId = "${playlistId}_LIVE_${slugify(groupTitle)}"
                                    liveCategoryMap.getOrPut(catId) {
                                        CategoryEntity(
                                            id = catId,
                                            playlistId = playlistId,
                                            section = "LIVE",
                                            rawCategoryId = slugify(groupTitle),
                                            name = groupTitle,
                                            sortOrder = liveCategoryMap.size
                                        )
                                    }
                                    channels.add(
                                        ChannelEntity(
                                            id = "${playlistId}_ch_${stableHash("$title|$streamUrl|$itemIndex")}",
                                            playlistId = playlistId,
                                            categoryId = catId,
                                            categoryName = groupTitle,
                                            channelNumber = chNo,
                                            name = title,
                                            streamUrl = streamUrl,
                                            logoUrl = logoUrl,
                                            epgChannelId = epgId,
                                            tvgName = pendingAttrs["tvg-name"].orEmpty(),
                                            customHeadersJson = headersJson,
                                            isRadio = isRadio,
                                            hasCatchup = hasCatchup,
                                            sortOrder = itemIndex
                                        )
                                    )
                                }
                            }
                        }
                        // Reset per-entry state regardless of whether URL was valid
                        if (hasPendingInf || pendingHeaders.isNotEmpty()) {
                            pendingName = ""
                            pendingAttrs = emptyMap()
                            pendingGroup = ""
                            pendingHeaders.clear()
                            pendingVlcUa = null
                            hasPendingInf = false
                        }
                    }
                }
            }
        }

        val allCategories = buildList {
            addAll(liveCategoryMap.values)
            addAll(movieCategoryMap.values)
            addAll(seriesCategoryMap.values)
        }

        return ParsedM3uResult(
            discoveredEpgUrl = discoveredEpgUrl,
            categories = allCategories,
            channels = channels,
            movies = movies,
            series = seriesMap.values.toList(),
            episodes = episodes
        )
    }

    private fun parseAttributes(input: String): Map<String, String> {
        val result = HashMap<String, String>()
        attrRegex.findAll(input).forEach { match ->
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues[2].ifEmpty {
                match.groupValues[3].ifEmpty { match.groupValues[4] }
            }
            result[key] = value.trim()
        }
        return result
    }

    /**
     * Finds the comma separating #EXTINF attributes from the channel title,
     * ignoring commas inside quoted attribute values.
     */
    private fun findTitleCommaIndex(body: String): Int {
        var inQuotes = false
        var quoteChar = '"'
        for (i in body.indices) {
            val c = body[i]
            if ((c == '"' || c == '\'') && (i == 0 || body[i - 1] != '\\')) {
                if (!inQuotes) {
                    inQuotes = true
                    quoteChar = c
                } else if (c == quoteChar) {
                    inQuotes = false
                }
            } else if (c == ',' && !inQuotes) {
                return i
            }
        }
        return -1
    }

    private fun slugify(text: String): String {
        val cleaned = text.trim().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "_")
        return cleaned.trim('_').ifEmpty { "general" }
    }

    private fun stableHash(value: String): String {
        return value.hashCode().toUInt().toString(16)
    }
}
