package com.example.core.security

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.security.SecureRandom

/**
 * Generates privacy-compliant MAC-style installation identifiers, short-lived pairing tokens,
 * scannable QR code bitmaps, and handles URL validation & credential redaction.
 */
object DeviceIdentityManager {

    const val MEHDITV_WEBSITE_URL = "https://mehditv.com/"

    private val secureRandom = SecureRandom()

    /**
     * Generates a scannable QR code encoding ONLY the exact fixed MehdiTV website URL
     * (https://mehditv.com/) with no device IDs, tokens, or query parameters.
     */
    fun generateWebsiteQrBitmap(sizePx: Int = 512): ImageBitmap? =
        generateQrBitmap(MEHDITV_WEBSITE_URL, sizePx)

    /**
     * Generates a stable installation-scoped identifier formatted as AA:BB:CC:DD:EE:FF.
     * Note: Locally administered bit set (0x02) so it is clearly an app installation ID
     * and never impersonates a real hardware NIC MAC address.
     */
    fun generateMacStyleInstallationId(): String {
        val bytes = ByteArray(6)
        secureRandom.nextBytes(bytes)
        bytes[0] = ((bytes[0].toInt() and 0xFC) or 0x02).toByte()
        return bytes.joinToString(":") { byte ->
            "%02X".format(byte.toInt() and 0xFF)
        }
    }

    fun generateShortLivedPairingToken(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(12) {
            repeat(12) { idx ->
                if (idx == 4 || idx == 8) append('-')
                append(alphabet[secureRandom.nextInt(alphabet.length)])
            }
        }
    }

    /**
     * Builds the QR pairing payload URL. Never embeds passwords or permanent credentials.
     */
    fun buildPairingQrPayload(
        backendBaseUrl: String,
        deviceId: String,
        pairingToken: String,
        expiresAtMs: Long
    ): String {
        val sanitizedBase = normalizeHttpsBaseUrl(backendBaseUrl)
            ?: "https://pair.mehditv.app"
        return Uri.parse(sanitizedBase).buildUpon()
            .appendPath("pair")
            .appendQueryParameter("device_id", deviceId)
            .appendQueryParameter("token", pairingToken)
            .appendQueryParameter("exp", (expiresAtMs / 1000L).toString())
            .build()
            .toString()
    }

    fun normalizeHttpsBaseUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null
        val parsed = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase() ?: return null
        val host = parsed.host?.takeIf { it.isNotBlank() } ?: return null
        if (scheme != "https") return null
        return "https://$host${if (parsed.port != -1) ":${parsed.port}" else ""}"
    }

    fun isValidStreamOrPlaylistUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return false
        val parsed = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase() ?: return false
        val host = parsed.host
        return (scheme == "http" || scheme == "https" || scheme == "rtsp" || scheme == "rtmp") &&
            !host.isNullOrBlank()
    }

    /**
     * Redacts passwords and sensitive query parameters before any diagnostic display.
     */
    fun redactUrl(url: String): String {
        return url
            .replace(Regex("(password=)[^&\\s]+", RegexOption.IGNORE_CASE), "$1***")
            .replace(Regex("(pass=)[^&\\s]+", RegexOption.IGNORE_CASE), "$1***")
            .replace(Regex("(token=)[^&\\s]+", RegexOption.IGNORE_CASE), "$1***")
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): ImageBitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) Color.parseColor("#111F43") else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }
}
