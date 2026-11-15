package com.example.data.remote

import com.example.core.model.ActivationStatus
import com.example.core.security.DeviceIdentityManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Serializable
data class RegisterDeviceRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("android_sdk") val androidSdk: Int
)

@Serializable
data class DeviceStatusResponse(
    @SerialName("device_id") val deviceId: String,
    val status: String, // ACTIVE, TRIAL, UNACTIVATED, EXPIRED, SUSPENDED
    @SerialName("license_expires_at_ms") val licenseExpiresAtMs: Long? = null
)

@Serializable
data class PairingSessionResponse(
    @SerialName("pairing_token") val pairingToken: String,
    @SerialName("expires_at_ms") val expiresAtMs: Long
)

@Serializable
data class RemotePairedPlaylistDto(
    val name: String,
    val type: String, // XTREAM or M3U_URL
    @SerialName("server_url") val serverUrl: String = "",
    val port: String = "",
    val username: String = "",
    val password: String = "",
    @SerialName("m3u_url") val m3uUrl: String = "",
    @SerialName("epg_url") val epgUrl: String = "",
    @SerialName("user_agent") val userAgent: String = ""
)

@Serializable
data class PairedPlaylistsResponse(
    val playlists: List<RemotePairedPlaylistDto> = emptyList()
)

/**
 * HTTPS-only client for an independent MehdiTv Device Activation & QR Playlist Pairing Backend.
 * Never hardcodes activation success; returns explicit failures when backend is unconfigured or unreachable.
 */
class ActivationPairingClient(private val okHttpClient: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchActivationStatus(
        backendBaseUrl: String,
        deviceId: String,
        appVersion: String,
        androidSdk: Int
    ): Result<Pair<ActivationStatus, Long?>> = runCatching {
        val base = DeviceIdentityManager.normalizeHttpsBaseUrl(backendBaseUrl)
            ?: throw IllegalStateException("HTTPS backend URL required")

        val registerBody = json.encodeToString(
            RegisterDeviceRequest(deviceId = deviceId, appVersion = appVersion, androidSdk = androidSdk)
        ).toRequestBody(jsonMediaType)

        val regRequest = Request.Builder()
            .url("$base/api/v1/devices/register")
            .post(registerBody)
            .build()
        okHttpClient.newCall(regRequest).execute().close()

        val statusRequest = Request.Builder()
            .url("$base/api/v1/devices/$deviceId/status")
            .get()
            .build()

        okHttpClient.newCall(statusRequest).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Activation server returned HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            val dto = json.decodeFromString<DeviceStatusResponse>(body)
            val mappedStatus = when (dto.status.uppercase()) {
                "ACTIVE" -> ActivationStatus.ACTIVE
                "TRIAL" -> ActivationStatus.TRIAL
                "EXPIRED" -> ActivationStatus.EXPIRED
                "SUSPENDED" -> ActivationStatus.SUSPENDED
                else -> ActivationStatus.UNACTIVATED
            }
            mappedStatus to dto.licenseExpiresAtMs
        }
    }

    suspend fun requestBackendPairingSession(
        backendBaseUrl: String,
        deviceId: String
    ): Result<PairingSessionResponse> = runCatching {
        val base = DeviceIdentityManager.normalizeHttpsBaseUrl(backendBaseUrl)
            ?: throw IllegalStateException("HTTPS backend URL required")

        val request = Request.Builder()
            .url("$base/api/v1/devices/$deviceId/pairing-session")
            .post("{}".toRequestBody(jsonMediaType))
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Pairing server returned HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            json.decodeFromString<PairingSessionResponse>(body)
        }
    }

    suspend fun fetchPairedPlaylists(
        backendBaseUrl: String,
        deviceId: String,
        pairingToken: String
    ): Result<List<RemotePairedPlaylistDto>> = runCatching {
        val base = DeviceIdentityManager.normalizeHttpsBaseUrl(backendBaseUrl)
            ?: throw IllegalStateException("HTTPS backend URL required")

        val request = Request.Builder()
            .url("$base/api/v1/devices/$deviceId/pairing-session/$pairingToken/playlists")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Pairing server returned HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            json.decodeFromString<PairedPlaylistsResponse>(body).playlists
        }
    }

    companion object {
        val BACKEND_API_DOCUMENTATION = """
            MehdiTv Activation & QR Pairing Backend Specification (HTTPS):
            
            1. POST /api/v1/devices/register
               Body: { "device_id": "AA:BB:CC:DD:EE:FF", "app_version": "1.0.0", "android_sdk": 36 }
               
            2. GET /api/v1/devices/{device_id}/status
               Response: { "device_id": "...", "status": "ACTIVE|TRIAL|UNACTIVATED|EXPIRED|SUSPENDED", "license_expires_at_ms": 1790000000000 }
               
            3. POST /api/v1/devices/{device_id}/pairing-session
               Response: { "pairing_token": "ABCD-1234-EFGH", "expires_at_ms": 1760000900000 }
               
            4. Web Portal (QR Target): GET /pair?device_id=...&token=...&exp=...
               User validates Device ID, enters M3U or Xtream credentials server-side (short-lived session).
               
            5. GET /api/v1/devices/{device_id}/pairing-session/{token}/playlists
               Response: { "playlists": [ { "name": "...", "type": "XTREAM|M3U_URL", ... } ] }
               
            DB Tables: devices(device_id PK, status, license_expires_at), pairing_sessions(token PK, device_id FK, expires_at), device_playlists(id PK, device_id FK, payload_encrypted).
        """.trimIndent()
    }
}
