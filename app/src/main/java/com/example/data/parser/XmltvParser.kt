package com.example.data.parser

import com.example.data.local.entities.EpgProgramEntity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/**
 * Streaming XMLTV EPG parser using Android XmlPullParser.
 * Automatically handles plain XML and gzipped (.xml.gz) streams.
 */
object XmltvParser {

    fun parse(
        rawStream: InputStream,
        playlistId: Long,
        timezoneOffsetMinutes: Int = 0
    ): List<EpgProgramEntity> {
        val buffered = BufferedInputStream(rawStream, 32 * 1024)
        buffered.mark(4)
        val header = ByteArray(2)
        val readCount = buffered.read(header)
        buffered.reset()
        val effectiveStream: InputStream = if (readCount == 2 &&
            header[0] == 0x1f.toByte() && header[1] == 0x8b.toByte()
        ) {
            GZIPInputStream(buffered)
        } else {
            buffered
        }

        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val parser = factory.newPullParser().apply {
            setInput(effectiveStream, "UTF-8")
        }

        val programs = mutableListOf<EpgProgramEntity>()
        val now = System.currentTimeMillis()
        val windowStart = now - 6 * 3600_000L
        val windowEnd = now + 72 * 3600_000L

        var currentChannelId = ""
        var currentStartMs = 0L
        var currentStopMs = 0L
        var currentTitle = ""
        var currentDesc = ""
        var inProgramme = false
        var currentTag = ""

        val offsetDeltaMs = timezoneOffsetMinutes * 60_000L
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name?.lowercase().orEmpty()
                    if (currentTag == "programme") {
                        inProgramme = true
                        currentChannelId = parser.getAttributeValue(null, "channel").orEmpty().trim()
                        currentStartMs = parseXmltvDate(parser.getAttributeValue(null, "start")) + offsetDeltaMs
                        currentStopMs = parseXmltvDate(parser.getAttributeValue(null, "stop")) + offsetDeltaMs
                        currentTitle = ""
                        currentDesc = ""
                    }
                }

                XmlPullParser.TEXT -> {
                    if (inProgramme) {
                        val text = parser.text.orEmpty().trim()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "title" -> if (currentTitle.isEmpty()) currentTitle = text
                                "desc" -> if (currentDesc.isEmpty()) currentDesc = text
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val tag = parser.name?.lowercase().orEmpty()
                    if (tag == "programme" && inProgramme) {
                        if (currentChannelId.isNotEmpty() &&
                            currentTitle.isNotEmpty() &&
                            currentStartMs > 0L &&
                            currentStopMs > currentStartMs &&
                            currentStopMs >= windowStart &&
                            currentStartMs <= windowEnd
                        ) {
                            programs.add(
                                EpgProgramEntity(
                                    id = "${playlistId}_${currentChannelId}_${currentStartMs}",
                                    playlistId = playlistId,
                                    epgChannelId = currentChannelId,
                                    title = currentTitle,
                                    description = currentDesc,
                                    startTimeMs = currentStartMs,
                                    endTimeMs = currentStopMs
                                )
                            )
                        }
                        inProgramme = false
                        currentTag = ""
                    } else if (tag == currentTag) {
                        currentTag = ""
                    }
                }
            }
            eventType = parser.next()
        }
        return programs
    }

    /**
     * Parses XMLTV timestamps such as "20261114183000 +0300" or "20261114183000".
     */
    internal fun parseXmltvDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val trimmed = raw.trim()
        val parts = trimmed.split(Regex("\\s+"))
        val digits = parts[0].filter { it.isDigit() }
        if (digits.length < 12) return 0L
        val normalizedDigits = digits.padEnd(14, '0').take(14)

        val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val baseUtc = runCatching { sdf.parse(normalizedDigits)?.time }.getOrNull() ?: return 0L

        if (parts.size >= 2) {
            val tz = parts[1].trim()
            val match = Regex("^([+-])(\\d{2}):?(\\d{2})$").matchEntire(tz)
            if (match != null) {
                val sign = if (match.groupValues[1] == "-") -1 else 1
                val hours = match.groupValues[2].toIntOrNull() ?: 0
                val mins = match.groupValues[3].toIntOrNull() ?: 0
                val tzOffsetMs = sign * (hours * 3600_000L + mins * 60_000L)
                return baseUtc - tzOffsetMs
            }
        }
        return baseUtc
    }
}
