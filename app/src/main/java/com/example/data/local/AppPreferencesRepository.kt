package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.model.ActivationStatus
import com.example.core.model.AppLanguage
import com.example.core.model.BufferPreset
import com.example.core.model.DecoderMode
import com.example.core.model.LiveViewMode
import com.example.core.model.StartupScreen
import com.example.core.model.ThemeMode
import com.example.core.model.UserPreferencesState
import com.example.core.security.DeviceIdentityManager
import com.example.core.security.KeystoreCryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mehditv_prefs")

@Serializable
data class SettingsBackupPayload(
    val formatVersion: Int = 1,
    val sourceDeviceId: String,
    val language: String,
    val themeMode: String,
    val showClockOnHome: Boolean,
    val startupScreen: String,
    val globalUserAgent: String,
    val useGlobalCustomUserAgent: Boolean,
    val bufferPreset: String,
    val decoderMode: String,
    val subtitleSizeSp: Float,
    val subtitleBgOpacity: Float,
    val preferredAudioLang: String,
    val autoplayNextEpisode: Boolean,
    val epgTimezoneOffsetMinutes: Int,
    val liveViewMode: String
)

class AppPreferencesRepository(private val context: Context) {

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SHOW_CLOCK = booleanPreferencesKey("show_clock")
        val STARTUP_SCREEN = stringPreferencesKey("startup_screen")
        val GLOBAL_USER_AGENT = stringPreferencesKey("global_user_agent")
        val USE_GLOBAL_UA = booleanPreferencesKey("use_global_ua")
        val BUFFER_PRESET = stringPreferencesKey("buffer_preset")
        val DECODER_MODE = stringPreferencesKey("decoder_mode")
        val SUBTITLE_SIZE_SP = floatPreferencesKey("subtitle_size_sp")
        val SUBTITLE_BG_OPACITY = floatPreferencesKey("subtitle_bg_opacity")
        val SUBTITLE_DELAY_MS = longPreferencesKey("subtitle_delay_ms")
        val PREFERRED_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
        val AUTOPLAY_NEXT_EPISODE = booleanPreferencesKey("autoplay_next_episode")
        val EPG_TIMEZONE_OFFSET_MIN = intPreferencesKey("epg_timezone_offset_min")
        val LIVE_VIEW_MODE = stringPreferencesKey("live_view_mode")
        val LAST_LIVE_CAT = stringPreferencesKey("last_live_cat")
        val LAST_MOVIE_CAT = stringPreferencesKey("last_movie_cat")
        val LAST_SERIES_CAT = stringPreferencesKey("last_series_cat")
        val PARENTAL_PIN_HASH = stringPreferencesKey("parental_pin_hash")
        val PARENTAL_PIN_SALT = stringPreferencesKey("parental_pin_salt")
        val LOCK_SETTINGS = booleanPreferencesKey("lock_settings")
        val LOCK_PLAYLIST_EDITS = booleanPreferencesKey("lock_playlist_edits")
        val BACKEND_BASE_URL = stringPreferencesKey("backend_base_url")
        val ACTIVATION_STATUS = stringPreferencesKey("activation_status")
        val LICENSE_EXPIRES_AT = longPreferencesKey("license_expires_at")
        val PAIRING_TOKEN_ENC = stringPreferencesKey("pairing_token_enc")
        val PAIRING_TOKEN_EXPIRES_AT = longPreferencesKey("pairing_token_expires_at")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    val preferencesFlow: Flow<UserPreferencesState> = context.dataStore.data.map { prefs ->
        val rawEncToken = prefs[Keys.PAIRING_TOKEN_ENC] ?: ""
        val decodedToken = if (rawEncToken.isNotBlank()) KeystoreCryptoManager.decrypt(rawEncToken) else ""
        UserPreferencesState(
            deviceId = prefs[Keys.DEVICE_ID] ?: "",
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE] ?: AppLanguage.ARABIC.code),
            themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name)
            }.getOrDefault(ThemeMode.DARK),
            showClockOnHome = prefs[Keys.SHOW_CLOCK] ?: true,
            startupScreen = runCatching {
                StartupScreen.valueOf(prefs[Keys.STARTUP_SCREEN] ?: StartupScreen.HOME.name)
            }.getOrDefault(StartupScreen.HOME),
            globalUserAgent = prefs[Keys.GLOBAL_USER_AGENT] ?: UserPreferencesState.DEFAULT_USER_AGENT,
            useGlobalCustomUserAgent = prefs[Keys.USE_GLOBAL_UA] ?: false,
            bufferPreset = runCatching {
                BufferPreset.valueOf(prefs[Keys.BUFFER_PRESET] ?: BufferPreset.BALANCED.name)
            }.getOrDefault(BufferPreset.BALANCED),
            decoderMode = runCatching {
                DecoderMode.valueOf(prefs[Keys.DECODER_MODE] ?: DecoderMode.HARDWARE_FIRST.name)
            }.getOrDefault(DecoderMode.HARDWARE_FIRST),
            subtitleSizeSp = prefs[Keys.SUBTITLE_SIZE_SP] ?: 18f,
            subtitleBgOpacity = prefs[Keys.SUBTITLE_BG_OPACITY] ?: 0.55f,
            subtitleDelayMs = prefs[Keys.SUBTITLE_DELAY_MS] ?: 0L,
            preferredAudioLang = prefs[Keys.PREFERRED_AUDIO_LANG] ?: "ar",
            autoplayNextEpisode = prefs[Keys.AUTOPLAY_NEXT_EPISODE] ?: true,
            epgTimezoneOffsetMinutes = prefs[Keys.EPG_TIMEZONE_OFFSET_MIN] ?: 0,
            liveViewMode = runCatching {
                LiveViewMode.valueOf(prefs[Keys.LIVE_VIEW_MODE] ?: LiveViewMode.GRID.name)
            }.getOrDefault(LiveViewMode.GRID),
            lastLiveCategoryId = prefs[Keys.LAST_LIVE_CAT] ?: UserPreferencesState.SPECIAL_CAT_ALL,
            lastMovieCategoryId = prefs[Keys.LAST_MOVIE_CAT] ?: UserPreferencesState.SPECIAL_CAT_ALL,
            lastSeriesCategoryId = prefs[Keys.LAST_SERIES_CAT] ?: UserPreferencesState.SPECIAL_CAT_ALL,
            hasParentalPin = !prefs[Keys.PARENTAL_PIN_HASH].isNullOrBlank(),
            lockSettings = prefs[Keys.LOCK_SETTINGS] ?: false,
            lockPlaylistEdits = prefs[Keys.LOCK_PLAYLIST_EDITS] ?: false,
            backendBaseUrl = prefs[Keys.BACKEND_BASE_URL] ?: "",
            activationStatus = runCatching {
                ActivationStatus.valueOf(prefs[Keys.ACTIVATION_STATUS] ?: ActivationStatus.LOCAL_MODE.name)
            }.getOrDefault(ActivationStatus.LOCAL_MODE),
            licenseExpiresAtMs = prefs[Keys.LICENSE_EXPIRES_AT],
            pairingToken = decodedToken,
            pairingTokenExpiresAtMs = prefs[Keys.PAIRING_TOKEN_EXPIRES_AT] ?: 0L
        )
    }

    /**
     * Ensures a unique MAC-style Device ID and an initial valid short-lived pairing token exist.
     */
    suspend fun ensureDeviceIdentityInitialized(): String {
        var resolvedId = ""
        context.dataStore.edit { prefs ->
            val existingId = prefs[Keys.DEVICE_ID]
            resolvedId = if (existingId.isNullOrBlank()) {
                val generated = DeviceIdentityManager.generateMacStyleInstallationId()
                prefs[Keys.DEVICE_ID] = generated
                generated
            } else {
                existingId
            }

            val tokenExp = prefs[Keys.PAIRING_TOKEN_EXPIRES_AT] ?: 0L
            val existingToken = prefs[Keys.PAIRING_TOKEN_ENC] ?: ""
            if (existingToken.isBlank() || tokenExp < System.currentTimeMillis() + 60_000L) {
                val newToken = DeviceIdentityManager.generateShortLivedPairingToken()
                prefs[Keys.PAIRING_TOKEN_ENC] = KeystoreCryptoManager.encrypt(newToken)
                prefs[Keys.PAIRING_TOKEN_EXPIRES_AT] = System.currentTimeMillis() + 15 * 60_000L
            }
        }
        return resolvedId
    }

    suspend fun regeneratePairingToken(): Pair<String, Long> {
        val newToken = DeviceIdentityManager.generateShortLivedPairingToken()
        val expiresAt = System.currentTimeMillis() + 15 * 60_000L
        context.dataStore.edit { prefs ->
            prefs[Keys.PAIRING_TOKEN_ENC] = KeystoreCryptoManager.encrypt(newToken)
            prefs[Keys.PAIRING_TOKEN_EXPIRES_AT] = expiresAt
        }
        return newToken to expiresAt
    }

    suspend fun updatePairingTokenFromBackend(token: String, expiresAtMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PAIRING_TOKEN_ENC] = KeystoreCryptoManager.encrypt(token)
            prefs[Keys.PAIRING_TOKEN_EXPIRES_AT] = expiresAtMs
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = themeMode.name }
    }

    suspend fun setShowClockOnHome(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_CLOCK] = show }
    }

    suspend fun setStartupScreen(screen: StartupScreen) {
        context.dataStore.edit { it[Keys.STARTUP_SCREEN] = screen.name }
    }

    suspend fun setGlobalUserAgent(userAgent: String, enabled: Boolean) {
        context.dataStore.edit {
            it[Keys.GLOBAL_USER_AGENT] = userAgent.ifBlank { UserPreferencesState.DEFAULT_USER_AGENT }
            it[Keys.USE_GLOBAL_UA] = enabled
        }
    }

    suspend fun setBufferPreset(preset: BufferPreset) {
        context.dataStore.edit { it[Keys.BUFFER_PRESET] = preset.name }
    }

    suspend fun setDecoderMode(mode: DecoderMode) {
        context.dataStore.edit { it[Keys.DECODER_MODE] = mode.name }
    }

    suspend fun setSubtitleAppearance(sizeSp: Float, bgOpacity: Float) {
        context.dataStore.edit {
            it[Keys.SUBTITLE_SIZE_SP] = sizeSp.coerceIn(12f, 32f)
            it[Keys.SUBTITLE_BG_OPACITY] = bgOpacity.coerceIn(0f, 1f)
        }
    }

    suspend fun setSubtitleDelayMs(delayMs: Long) {
        context.dataStore.edit { it[Keys.SUBTITLE_DELAY_MS] = delayMs }
    }

    suspend fun setPreferredAudioLang(lang: String) {
        context.dataStore.edit { it[Keys.PREFERRED_AUDIO_LANG] = lang }
    }

    suspend fun setAutoplayNextEpisode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTOPLAY_NEXT_EPISODE] = enabled }
    }

    suspend fun setEpgTimezoneOffsetMinutes(offsetMinutes: Int) {
        context.dataStore.edit { it[Keys.EPG_TIMEZONE_OFFSET_MIN] = offsetMinutes }
    }

    suspend fun setLiveViewMode(mode: LiveViewMode) {
        context.dataStore.edit { it[Keys.LIVE_VIEW_MODE] = mode.name }
    }

    suspend fun setLastLiveCategory(categoryId: String) {
        context.dataStore.edit { it[Keys.LAST_LIVE_CAT] = categoryId }
    }

    suspend fun setLastMovieCategory(categoryId: String) {
        context.dataStore.edit { it[Keys.LAST_MOVIE_CAT] = categoryId }
    }

    suspend fun setLastSeriesCategory(categoryId: String) {
        context.dataStore.edit { it[Keys.LAST_SERIES_CAT] = categoryId }
    }

    suspend fun setParentalPin(pin: String) {
        val salt = KeystoreCryptoManager.generateSalt()
        val hash = KeystoreCryptoManager.hashPin(pin, salt)
        context.dataStore.edit {
            it[Keys.PARENTAL_PIN_SALT] = salt
            it[Keys.PARENTAL_PIN_HASH] = hash
        }
    }

    suspend fun clearParentalPin() {
        context.dataStore.edit {
            it.remove(Keys.PARENTAL_PIN_SALT)
            it.remove(Keys.PARENTAL_PIN_HASH)
            it[Keys.LOCK_SETTINGS] = false
            it[Keys.LOCK_PLAYLIST_EDITS] = false
        }
    }

    suspend fun verifyParentalPin(pin: String): Boolean {
        val prefs = context.dataStore.data.first()
        val salt = prefs[Keys.PARENTAL_PIN_SALT] ?: return false
        val hash = prefs[Keys.PARENTAL_PIN_HASH] ?: return false
        return KeystoreCryptoManager.verifyPin(pin, salt, hash)
    }

    suspend fun setLockSettings(locked: Boolean) {
        context.dataStore.edit { it[Keys.LOCK_SETTINGS] = locked }
    }

    suspend fun setLockPlaylistEdits(locked: Boolean) {
        context.dataStore.edit { it[Keys.LOCK_PLAYLIST_EDITS] = locked }
    }

    suspend fun setBackendBaseUrl(rawUrl: String): Boolean {
        if (rawUrl.isBlank()) {
            context.dataStore.edit {
                it[Keys.BACKEND_BASE_URL] = ""
                it[Keys.ACTIVATION_STATUS] = ActivationStatus.LOCAL_MODE.name
                it.remove(Keys.LICENSE_EXPIRES_AT)
            }
            return true
        }
        val normalized = DeviceIdentityManager.normalizeHttpsBaseUrl(rawUrl) ?: return false
        context.dataStore.edit {
            it[Keys.BACKEND_BASE_URL] = normalized
        }
        return true
    }

    suspend fun updateActivationState(status: ActivationStatus, expiresAtMs: Long?) {
        context.dataStore.edit {
            it[Keys.ACTIVATION_STATUS] = status.name
            if (expiresAtMs != null) {
                it[Keys.LICENSE_EXPIRES_AT] = expiresAtMs
            } else {
                it.remove(Keys.LICENSE_EXPIRES_AT)
            }
        }
    }

    suspend fun exportSettingsBackupJson(): String {
        val current = preferencesFlow.first()
        val payload = SettingsBackupPayload(
            sourceDeviceId = current.deviceId,
            language = current.language.code,
            themeMode = current.themeMode.name,
            showClockOnHome = current.showClockOnHome,
            startupScreen = current.startupScreen.name,
            globalUserAgent = current.globalUserAgent,
            useGlobalCustomUserAgent = current.useGlobalCustomUserAgent,
            bufferPreset = current.bufferPreset.name,
            decoderMode = current.decoderMode.name,
            subtitleSizeSp = current.subtitleSizeSp,
            subtitleBgOpacity = current.subtitleBgOpacity,
            preferredAudioLang = current.preferredAudioLang,
            autoplayNextEpisode = current.autoplayNextEpisode,
            epgTimezoneOffsetMinutes = current.epgTimezoneOffsetMinutes,
            liveViewMode = current.liveViewMode.name
        )
        return json.encodeToString(payload)
    }

    /**
     * Restores display/player preferences only. Never imports Device ID, activation licenses,
     * or encrypted playlist credentials from another device.
     */
    suspend fun restoreSettingsBackupJson(rawJson: String): Result<Unit> = runCatching {
        val payload = json.decodeFromString<SettingsBackupPayload>(rawJson)
        context.dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = AppLanguage.fromCode(payload.language).code
            prefs[Keys.THEME_MODE] = runCatching { ThemeMode.valueOf(payload.themeMode) }
                .getOrDefault(ThemeMode.DARK).name
            prefs[Keys.SHOW_CLOCK] = payload.showClockOnHome
            prefs[Keys.STARTUP_SCREEN] = runCatching { StartupScreen.valueOf(payload.startupScreen) }
                .getOrDefault(StartupScreen.HOME).name
            prefs[Keys.GLOBAL_USER_AGENT] = payload.globalUserAgent.ifBlank { UserPreferencesState.DEFAULT_USER_AGENT }
            prefs[Keys.USE_GLOBAL_UA] = payload.useGlobalCustomUserAgent
            prefs[Keys.BUFFER_PRESET] = runCatching { BufferPreset.valueOf(payload.bufferPreset) }
                .getOrDefault(BufferPreset.BALANCED).name
            prefs[Keys.DECODER_MODE] = runCatching { DecoderMode.valueOf(payload.decoderMode) }
                .getOrDefault(DecoderMode.HARDWARE_FIRST).name
            prefs[Keys.SUBTITLE_SIZE_SP] = payload.subtitleSizeSp.coerceIn(12f, 32f)
            prefs[Keys.SUBTITLE_BG_OPACITY] = payload.subtitleBgOpacity.coerceIn(0f, 1f)
            prefs[Keys.PREFERRED_AUDIO_LANG] = payload.preferredAudioLang
            prefs[Keys.AUTOPLAY_NEXT_EPISODE] = payload.autoplayNextEpisode
            prefs[Keys.EPG_TIMEZONE_OFFSET_MIN] = payload.epgTimezoneOffsetMinutes
            prefs[Keys.LIVE_VIEW_MODE] = runCatching { LiveViewMode.valueOf(payload.liveViewMode) }
                .getOrDefault(LiveViewMode.GRID).name
        }
    }
}
