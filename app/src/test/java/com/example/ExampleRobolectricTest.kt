package com.example

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.LayoutDirection
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.AppLanguage
import com.example.core.model.PlaylistStatus
import com.example.core.model.PlaylistType
import com.example.core.model.UserPreferencesState
import com.example.core.security.DeviceIdentityManager
import com.example.core.security.KeystoreCryptoManager
import com.example.data.local.MehdiTvDatabase
import com.example.data.local.entities.ChannelEntity
import com.example.data.local.entities.PlaylistEntity
import com.example.data.parser.M3uParser
import com.example.data.parser.XmltvParser
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MehdiTvTheme
import com.example.ui.viewmodel.HomeCatalogSnapshot
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `app name matches MehdiTv Player`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MehdiTv Player", appName)
    }

    @Test
    fun `website QR code encodes exact https mehditv com URL without any parameters`() {
        val bitmap = DeviceIdentityManager.generateWebsiteQrBitmap(256)?.asAndroidBitmap()
        assertNotNull(bitmap)
        val width = bitmap!!.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val decoded = QRCodeReader().decode(binaryBitmap)

        assertEquals("https://mehditv.com/", decoded.text)
        assertFalse(decoded.text.contains("device_id"))
        assertFalse(decoded.text.contains("token"))
        assertFalse(decoded.text.contains("?"))
    }

    @Test
    fun `device id follows MAC style and pairing QR payload excludes credentials`() {
        val deviceId = DeviceIdentityManager.generateMacStyleInstallationId()
        assertTrue(deviceId.matches(Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")))

        val token = DeviceIdentityManager.generateShortLivedPairingToken()
        val qrPayload = DeviceIdentityManager.buildPairingQrPayload(
            backendBaseUrl = "https://panel.mehditv.example",
            deviceId = deviceId,
            pairingToken = token,
            expiresAtMs = 1_800_000_000_000L
        )
        assertTrue(qrPayload.startsWith("https://panel.mehditv.example/pair"))
        assertTrue(qrPayload.contains("device_id="))
        assertTrue(qrPayload.contains("token=$token"))
        assertFalse(qrPayload.contains("password"))

        val bitmap = DeviceIdentityManager.generateQrBitmap(qrPayload, 256)
        assertNotNull(bitmap)

        val redacted = DeviceIdentityManager.redactUrl("https://host/player_api.php?username=ali&password=secret123")
        assertFalse(redacted.contains("secret123"))
        assertTrue(redacted.contains("password=***"))
    }

    @Test
    fun `parental pin hashing verifies matching pin and rejects wrong pin`() {
        val salt = KeystoreCryptoManager.generateSalt()
        val hash = KeystoreCryptoManager.hashPin("2580", salt)
        assertFalse(hash == "2580")
        assertTrue(KeystoreCryptoManager.verifyPin("2580", salt, hash))
        assertFalse(KeystoreCryptoManager.verifyPin("1234", salt, hash))
    }

    @Test
    fun `m3u parser extracts live channels radio catchup movies series vlcopt headers and epg url`() {
        val sampleM3u = """
            #EXTM3U url-tvg="https://epg.example.com/guide.xml"
            #EXTINF:-1 tvg-id="ch1" tvg-chno="101" catchup="default" catchup-days="7" tvg-logo="https://cdn.example.com/ch1.png" group-title="Sports, Live",BeIN Sports 1
            #EXTVLCOPT:http-user-agent=CustomPlayer/2.0
            https://stream.example.com/live/ch1.m3u8
            #EXTINF:-1 tvg-type="radio" radio="true" group-title="راديو",Quran Radio FM
            https://stream.example.com/radio/quran.mp3
            #EXTINF:-1 tvg-logo="https://cdn.example.com/mov.jpg" group-title="Cinema",Inception (2010)
            https://stream.example.com/movie/inception.mp4
            #EXTINF:-1 tvg-type="series" group-title="Drama Series",Dark Winds S01E02
            https://stream.example.com/series/dw_s01e02.mkv
            #EXTINF:-1,Malformed Entry With Invalid Url
            not_a_valid_url
        """.trimIndent()

        val result = M3uParser.parse(ByteArrayInputStream(sampleM3u.toByteArray()), playlistId = 7L)
        assertEquals("https://epg.example.com/guide.xml", result.discoveredEpgUrl)
        assertEquals(2, result.channels.size)
        assertEquals(1, result.radioCount)
        assertTrue(result.supportsCatchup)

        val liveCh = result.channels.first { !it.isRadio }
        assertEquals("BeIN Sports 1", liveCh.name)
        assertEquals(101, liveCh.channelNumber)
        assertEquals("Sports, Live", liveCh.categoryName)
        assertTrue(liveCh.hasCatchup)
        assertTrue(liveCh.customHeadersJson.contains("CustomPlayer/2.0"))

        val radioCh = result.channels.first { it.isRadio }
        assertEquals("Quran Radio FM", radioCh.name)
        assertTrue(radioCh.isRadio)

        assertEquals(1, result.movies.size)
        assertEquals("Inception (2010)", result.movies[0].name)

        assertEquals(1, result.series.size)
        assertEquals("Dark Winds", result.series[0].name)
        assertEquals(1, result.episodes.size)
        assertEquals(1, result.episodes[0].seasonNumber)
        assertEquals(2, result.episodes[0].episodeNumber)
    }

    @Test
    fun `room database preserves multiple playlists and favorites across refreshes`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MehdiTvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val playlistDao = db.playlistDao()
            val catalogDao = db.catalogDao()

            val id1 = playlistDao.insert(
                PlaylistEntity(name = "Playlist One", type = PlaylistType.M3U_LOCAL.name, isActive = true)
            )
            val id2 = playlistDao.insert(
                PlaylistEntity(name = "Playlist Two", type = PlaylistType.M3U_URL.name, isActive = false)
            )

            val ch = ChannelEntity(
                id = "${id1}_ch_1",
                playlistId = id1,
                categoryId = "${id1}_LIVE_news",
                categoryName = "News",
                name = "Al Jazeera",
                streamUrl = "https://example.com/live.m3u8",
                isFavorite = true
            )
            catalogDao.insertChannels(listOf(ch))
            catalogDao.replacePlaylistCatalogPreservingUserState(
                playlistId = id1,
                newCategories = emptyList(),
                newChannels = listOf(ch.copy(isFavorite = false)),
                newMovies = emptyList(),
                newSeries = emptyList(),
                newEpisodes = emptyList()
            )
            val updated = catalogDao.getChannelById("${id1}_ch_1")
            assertTrue(updated?.isFavorite == true)

            playlistDao.setActivePlaylist(id2)
            val all = playlistDao.getAllOnce()
            assertEquals(2, all.size)
            assertEquals("Playlist Two", playlistDao.getActivePlaylistOnce()?.name)
        } finally {
            db.close()
        }
    }

    @Test
    fun `xmltv parser parses programme timestamps and timezone offsets`() {
        val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val now = System.currentTimeMillis()
        val startStr = fmt.format(Date(now - 10 * 60_000L)) + " +0000"
        val stopStr = fmt.format(Date(now + 50 * 60_000L)) + " +0000"

        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme start="$startStr" stop="$stopStr" channel="ch1">
                <title>Evening News</title>
                <desc>World bulletin</desc>
              </programme>
            </tv>
        """.trimIndent()

        val programs = XmltvParser.parse(ByteArrayInputStream(xml.toByteArray()), playlistId = 7L)
        assertEquals(1, programs.size)
        assertEquals("Evening News", programs[0].title)
        assertEquals("World bulletin", programs[0].description)
        assertEquals("ch1", programs[0].epgChannelId)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w1024dp-h720dp")
    fun `redesigned home screen renders all 4 cards dual import catchup state and rtl ltr actions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val arLocale = Locale.forLanguageTag("ar")
        val arConfig = Configuration(context.resources.configuration).apply {
            setLocale(arLocale)
            setLayoutDirection(arLocale)
        }
        val arContext = context.createConfigurationContext(arConfig)

        val samplePlaylist = PlaylistEntity(
            id = 1L,
            name = "Neo",
            type = PlaylistType.XTREAM.name,
            isActive = true,
            status = PlaylistStatus.READY.name,
            supportsCatchup = false,
            expirationTimestampMs = System.currentTimeMillis() + 30L * 86_400_000L
        )

        var importClicked = false
        var radioClicked = false
        var languageClicked = false

        composeRule.setContent {
            CompositionLocalProvider(
                LocalContext provides arContext,
                LocalConfiguration provides arConfig,
                LocalLayoutDirection provides LayoutDirection.Rtl
            ) {
                MehdiTvTheme(darkTheme = true) {
                    HomeScreen(
                        isWideScreen = true,
                        preferences = UserPreferencesState(
                            deviceId = "64:07:16:7A:99:88",
                            language = AppLanguage.ARABIC
                        ),
                        activePlaylist = samplePlaylist,
                        allPlaylists = listOf(samplePlaylist),
                        snapshot = HomeCatalogSnapshot(
                            channelCount = 120,
                            movieCount = 45,
                            seriesCount = 18,
                            radioCount = 12
                        ),
                        isRefreshing = false,
                        isOnline = true,
                        onRefreshClick = {},
                        onSelectActivePlaylist = {},
                        onOpenSettings = {},
                        onToggleLanguage = { languageClicked = true },
                        onOpenLive = {},
                        onOpenMovies = {},
                        onOpenSeries = {},
                        onOpenRadio = { radioClicked = true },
                        onOpenCatchUp = {},
                        onOpenImportPlaylist = { importClicked = true },
                        onOpenPlaylists = {},
                        onOpenQrPairing = {},
                        onPlayHistoryItem = {},
                        onPlayChannel = {},
                        onOpenMovieDetail = {},
                        onOpenSeriesDetail = {},
                        onShowMessage = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("home_brand_header").assertIsDisplayed()
        composeRule.onNodeWithTag("home_playlist_info_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("home_lazy_column").performScrollToNode(hasTestTag("home_card_live"))
        composeRule.onNodeWithTag("home_card_live").assertIsDisplayed()
        composeRule.onNodeWithTag("home_card_movies").assertIsDisplayed()
        composeRule.onNodeWithTag("home_card_series").assertIsDisplayed()
        composeRule.onNodeWithTag("home_card_radio").assertIsDisplayed()
        composeRule.onNodeWithTag("home_card_radio").performClick()
        assertTrue(radioClicked)

        composeRule.onNodeWithTag("home_lazy_column").performScrollToNode(hasTestTag("home_catch_up_btn"))
        composeRule.onNodeWithTag("home_catch_up_btn").assertIsNotEnabled()

        composeRule.onNodeWithTag("home_lazy_column").performScrollToNode(hasTestTag("home_website_qr_card"))
        composeRule.onNodeWithText("https://mehditv.com/").assertIsDisplayed()

        composeRule.onNodeWithTag("home_lazy_column").performScrollToNode(hasTestTag("home_add_from_device_btn"))
        composeRule.onNodeWithTag("home_add_from_device_btn").performClick()
        assertTrue(importClicked)

        composeRule.onNodeWithTag("home_lazy_column").performScrollToNode(hasTestTag("home_action_language"))
        composeRule.onNodeWithTag("home_action_language").performClick()
        assertTrue(languageClicked)

        composeRule.onNodeWithTag("home_lazy_column").performScrollToNode(hasText("64:07:16:7A:99:88"))
        composeRule.onNodeWithText("64:07:16:7A:99:88").assertIsDisplayed()
    }

    @Test
    fun `catchup button enables only when active playlist supports catchup`() {
        val catchupPlaylist = PlaylistEntity(
            id = 2L,
            name = "CatchUp Plus",
            type = PlaylistType.M3U_URL.name,
            isActive = true,
            status = PlaylistStatus.READY.name,
            supportsCatchup = true
        )
        var catchUpClicked = false

        composeRule.setContent {
            MehdiTvTheme(darkTheme = true) {
                HomeScreen(
                    isWideScreen = false,
                    preferences = UserPreferencesState(
                        deviceId = "A4:B2:C3:D5:E6:F7",
                        language = AppLanguage.ENGLISH
                    ),
                    activePlaylist = catchupPlaylist,
                    allPlaylists = listOf(catchupPlaylist),
                    snapshot = HomeCatalogSnapshot(),
                    isRefreshing = false,
                    isOnline = true,
                    onRefreshClick = {},
                    onSelectActivePlaylist = {},
                    onOpenSettings = {},
                    onToggleLanguage = {},
                    onOpenLive = {},
                    onOpenMovies = {},
                    onOpenSeries = {},
                    onOpenRadio = {},
                    onOpenCatchUp = { catchUpClicked = true },
                    onOpenImportPlaylist = {},
                    onOpenPlaylists = {},
                    onOpenQrPairing = {},
                    onPlayHistoryItem = {},
                    onPlayChannel = {},
                    onOpenMovieDetail = {},
                    onOpenSeriesDetail = {},
                    onShowMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_lazy_column").performScrollToNode(hasTestTag("home_catch_up_btn"))
        composeRule.onNodeWithTag("home_catch_up_btn").assertIsEnabled().performClick()
        assertTrue(catchUpClicked)
    }
}
