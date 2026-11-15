package com.example

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.model.AppLanguage
import com.example.core.model.StartupScreen
import com.example.core.model.ThemeMode
import com.example.core.model.UserPreferencesState
import com.example.ui.components.MehdiTvBrandMark
import com.example.ui.navigation.AppRoute
import com.example.ui.screens.EpgGuideScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LiveTvScreen
import com.example.ui.screens.MovieDetailScreen
import com.example.ui.screens.MoviesScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.PlaylistImportScreen
import com.example.ui.screens.PlaylistManagerScreen
import com.example.ui.screens.QrPairingScreen
import com.example.ui.screens.SeriesDetailScreen
import com.example.ui.screens.SeriesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MehdiTvTheme
import com.example.ui.viewmodel.CatalogViewModel
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var isInPipModeState by mutableStateOf(false)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val widthSizeClass = calculateWindowSizeClass(this).widthSizeClass
            val isWideScreen = widthSizeClass != WindowWidthSizeClass.Compact

            val mainViewModel: MainViewModel = viewModel()
            val catalogViewModel: CatalogViewModel = viewModel()

            val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
            val darkTheme = when (preferences.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            // Reactive Locale (Arabic RTL default, English LTR supported)
            val baseContext = LocalContext.current
            val baseConfig = LocalConfiguration.current
            val locale = remember(preferences.language) { Locale(preferences.language.code) }
            val localizedConfig = remember(baseConfig, locale) {
                Configuration(baseConfig).apply {
                    setLocale(locale)
                    setLayoutDirection(locale)
                }
            }
            val localizedContext = remember(baseContext, localizedConfig) {
                baseContext.createConfigurationContext(localizedConfig)
            }
            val layoutDirection = if (preferences.language == AppLanguage.ARABIC) {
                LayoutDirection.Rtl
            } else {
                LayoutDirection.Ltr
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfig,
                LocalLayoutDirection provides layoutDirection
            ) {
                MehdiTvTheme(darkTheme = darkTheme) {
                    MehdiTvRootApp(
                        isWideScreen = isWideScreen,
                        isInPipMode = isInPipModeState,
                        mainViewModel = mainViewModel,
                        catalogViewModel = catalogViewModel
                    )
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val app = application as? MehdiTvApplication ?: return
        val playerState = app.playerController.state.value
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playerState.isPlaying) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
}

private data class TopNavItem(
    val route: AppRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
private fun MehdiTvRootApp(
    isWideScreen: Boolean,
    isInPipMode: Boolean,
    mainViewModel: MainViewModel,
    catalogViewModel: CatalogViewModel
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()

    val preferences by mainViewModel.preferences.collectAsStateWithLifecycle()
    val playlists by mainViewModel.allPlaylists.collectAsStateWithLifecycle()
    val activePlaylist by mainViewModel.activePlaylist.collectAsStateWithLifecycle()
    val homeSnapshot by mainViewModel.homeSnapshot.collectAsStateWithLifecycle()
    val isRefreshing by mainViewModel.isRefreshing.collectAsStateWithLifecycle()
    val isOnline by mainViewModel.isOnline.collectAsStateWithLifecycle()
    val epgStatusMessage by mainViewModel.epgStatusMessage.collectAsStateWithLifecycle()

    val liveUiState by catalogViewModel.liveUiState.collectAsStateWithLifecycle()
    val moviesUiState by catalogViewModel.moviesUiState.collectAsStateWithLifecycle()
    val seriesUiState by catalogViewModel.seriesUiState.collectAsStateWithLifecycle()
    val allCategories by catalogViewModel.allCategoriesForActivePlaylist.collectAsStateWithLifecycle()
    val fullEpgSchedule by catalogViewModel.fullEpgSchedule.collectAsStateWithLifecycle()
    val playerState by mainViewModel.playerController.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        mainViewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Apply startup screen once after preferences load
    var didApplyStartup by remember { mutableStateOf(false) }
    LaunchedEffect(preferences.deviceId, preferences.startupScreen) {
        if (!didApplyStartup && preferences.deviceId.isNotBlank()) {
            didApplyStartup = true
            val initialRoute: AppRoute = when (preferences.startupScreen) {
                StartupScreen.HOME -> AppRoute.Home
                StartupScreen.LIVE -> AppRoute.LiveTv
                StartupScreen.MOVIES -> AppRoute.Movies
                StartupScreen.SERIES -> AppRoute.Series
            }
            if (initialRoute != AppRoute.Home) {
                navController.navigate(initialRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                }
            }
        }
    }

    val topNavItems = remember {
        listOf(
            TopNavItem(AppRoute.Home, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
            TopNavItem(AppRoute.LiveTv, R.string.nav_live, Icons.Filled.LiveTv, Icons.Outlined.LiveTv, "nav_live"),
            TopNavItem(AppRoute.Movies, R.string.nav_movies, Icons.Filled.Movie, Icons.Outlined.Movie, "nav_movies"),
            TopNavItem(AppRoute.Series, R.string.nav_series, Icons.Filled.Tv, Icons.Outlined.Tv, "nav_series"),
            TopNavItem(AppRoute.Settings, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val isTopLevelRoute = topNavItems.any { item ->
        currentDest?.hasRoute(item.route::class) == true
    }
    val showNavigationChrome = isTopLevelRoute && !isInPipMode

    fun navigateTopLevel(route: AppRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = if (currentDest?.hasRoute(AppRoute.Player::class) == true) {
            WindowInsets(0, 0, 0, 0)
        } else {
            WindowInsets.safeDrawing
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showNavigationChrome && !isWideScreen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    topNavItems.forEach { item ->
                        val selected = currentDest?.hasRoute(item.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTopLevel(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = stringResource(item.labelRes)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(item.labelRes),
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showNavigationChrome && isWideScreen) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        Box(modifier = Modifier.padding(vertical = 12.dp)) {
                            MehdiTvBrandMark(compact = true)
                        }
                    }
                ) {
                    Spacer(Modifier.weight(0.2f))
                    topNavItems.forEach { item ->
                        val selected = currentDest?.hasRoute(item.route::class) == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopLevel(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = stringResource(item.labelRes)
                                )
                            },
                            label = { Text(stringResource(item.labelRes)) },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = AppRoute.Home
                ) {
                    composable<AppRoute.Home> {
                        HomeScreen(
                            isWideScreen = isWideScreen,
                            preferences = preferences,
                            activePlaylist = activePlaylist,
                            allPlaylists = playlists,
                            snapshot = homeSnapshot,
                            isRefreshing = isRefreshing,
                            isOnline = isOnline,
                            onRefreshClick = { mainViewModel.refreshActivePlaylist() },
                            onSelectActivePlaylist = { mainViewModel.setActivePlaylist(it) },
                            onOpenSettings = { navigateTopLevel(AppRoute.Settings) },
                            onToggleLanguage = { mainViewModel.toggleLanguage() },
                            onOpenLive = {
                                if (catalogViewModel.liveUiState.value.selectedCategoryId == UserPreferencesState.SPECIAL_CAT_RADIO) {
                                    catalogViewModel.selectLiveCategory(UserPreferencesState.SPECIAL_CAT_ALL)
                                }
                                navigateTopLevel(AppRoute.LiveTv)
                            },
                            onOpenMovies = { navigateTopLevel(AppRoute.Movies) },
                            onOpenSeries = { navigateTopLevel(AppRoute.Series) },
                            onOpenRadio = {
                                catalogViewModel.selectLiveCategory(UserPreferencesState.SPECIAL_CAT_RADIO)
                                navigateTopLevel(AppRoute.LiveTv)
                            },
                            onOpenCatchUp = { navController.navigate(AppRoute.EpgGuide) },
                            onOpenImportPlaylist = { navController.navigate(AppRoute.ImportPlaylist) },
                            onOpenPlaylists = { navController.navigate(AppRoute.Playlists) },
                            onOpenQrPairing = { navController.navigate(AppRoute.QrPairing) },
                            onPlayHistoryItem = { item ->
                                mainViewModel.playFromHistory(item) {
                                    navController.navigate(AppRoute.Player)
                                }
                            },
                            onPlayChannel = { ch ->
                                mainViewModel.playChannel(ch) {
                                    navController.navigate(AppRoute.Player)
                                }
                            },
                            onOpenMovieDetail = { mov ->
                                navController.navigate(AppRoute.MovieDetail(mov.id))
                            },
                            onOpenSeriesDetail = { ser ->
                                navController.navigate(AppRoute.SeriesDetail(ser.id))
                            },
                            onShowMessage = { msg ->
                                uiScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }

                    composable<AppRoute.LiveTv> {
                        LiveTvScreen(
                            isWideScreen = isWideScreen,
                            hasActivePlaylist = activePlaylist != null,
                            uiState = liveUiState,
                            viewMode = preferences.liveViewMode,
                            hasParentalPin = preferences.hasParentalPin,
                            onSelectCategory = catalogViewModel::selectLiveCategory,
                            onUnlockCategory = catalogViewModel::unlockCategory,
                            onVerifyPin = mainViewModel::verifyParentalPin,
                            onSearchChange = catalogViewModel::setLiveSearch,
                            onSortChange = catalogViewModel::setLiveSort,
                            onToggleViewMode = mainViewModel::setLiveViewMode,
                            onToggleFavorite = catalogViewModel::toggleChannelFavorite,
                            onChannelClick = { ch ->
                                mainViewModel.playChannel(ch) {
                                    navController.navigate(AppRoute.Player)
                                }
                            },
                            onOpenEpgGuide = { navController.navigate(AppRoute.EpgGuide) },
                            onOpenPlaylists = { navController.navigate(AppRoute.Playlists) },
                            onOpenQrPairing = { navController.navigate(AppRoute.QrPairing) }
                        )
                    }

                    composable<AppRoute.Movies> {
                        MoviesScreen(
                            isWideScreen = isWideScreen,
                            hasActivePlaylist = activePlaylist != null,
                            uiState = moviesUiState,
                            onSelectCategory = catalogViewModel::selectMovieCategory,
                            onSearchChange = catalogViewModel::setMovieSearch,
                            onSortChange = catalogViewModel::setMovieSort,
                            onToggleFavorite = catalogViewModel::toggleMovieFavorite,
                            onMovieClick = { mov ->
                                navController.navigate(AppRoute.MovieDetail(mov.id))
                            },
                            onOpenPlaylists = { navController.navigate(AppRoute.Playlists) },
                            onOpenQrPairing = { navController.navigate(AppRoute.QrPairing) }
                        )
                    }

                    composable<AppRoute.Series> {
                        SeriesScreen(
                            isWideScreen = isWideScreen,
                            hasActivePlaylist = activePlaylist != null,
                            uiState = seriesUiState,
                            onSelectCategory = catalogViewModel::selectSeriesCategory,
                            onSearchChange = catalogViewModel::setSeriesSearch,
                            onSortChange = catalogViewModel::setSeriesSort,
                            onToggleFavorite = catalogViewModel::toggleSeriesFavorite,
                            onSeriesClick = { ser ->
                                navController.navigate(AppRoute.SeriesDetail(ser.id))
                            },
                            onOpenPlaylists = { navController.navigate(AppRoute.Playlists) },
                            onOpenQrPairing = { navController.navigate(AppRoute.QrPairing) }
                        )
                    }

                    composable<AppRoute.Settings> {
                        SettingsScreen(
                            isWideScreen = isWideScreen,
                            preferences = preferences,
                            playlists = playlists,
                            categories = allCategories,
                            isOnline = isOnline,
                            onLanguageChange = mainViewModel::setLanguage,
                            onThemeChange = mainViewModel::setThemeMode,
                            onShowClockChange = mainViewModel::setShowClockOnHome,
                            onStartupScreenChange = mainViewModel::setStartupScreen,
                            onSetDefaultPlaylist = mainViewModel::setDefaultPlaylist,
                            onOpenPlaylists = { navController.navigate(AppRoute.Playlists) },
                            onRefreshPlaylist = mainViewModel::refreshActivePlaylist,
                            onRefreshEpg = mainViewModel::refreshEpg,
                            onGlobalUserAgentSave = mainViewModel::setGlobalUserAgent,
                            onEpgTimezoneChange = mainViewModel::setEpgTimezoneOffsetMinutes,
                            onBufferPresetChange = mainViewModel::setBufferPreset,
                            onDecoderModeChange = mainViewModel::setDecoderMode,
                            onSubtitleStyleChange = mainViewModel::setSubtitleAppearance,
                            onPreferredAudioChange = mainViewModel::setPreferredAudioLang,
                            onAutoplayChange = mainViewModel::setAutoplayNextEpisode,
                            onSetParentalPin = mainViewModel::setParentalPin,
                            onClearParentalPin = mainViewModel::clearParentalPin,
                            onVerifyPin = mainViewModel::verifyParentalPin,
                            onLockSettingsChange = mainViewModel::setLockSettings,
                            onLockPlaylistEditsChange = mainViewModel::setLockPlaylistEdits,
                            onCategoryHiddenChange = mainViewModel::setCategoryHidden,
                            onCategoryLockedChange = mainViewModel::setCategoryLocked,
                            onBackendUrlSave = mainViewModel::saveBackendBaseUrl,
                            onClearImageCache = mainViewModel::clearImageCache,
                            onExportSettingsJson = mainViewModel::exportSettingsJson,
                            onRestoreSettingsJson = mainViewModel::restoreSettingsJson,
                            onBuildDiagnostics = mainViewModel::buildDiagnosticsText,
                            onShowMessage = { msg ->
                                uiScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }

                    composable<AppRoute.MovieDetail> { entry ->
                        val args = entry.toRoute<AppRoute.MovieDetail>()
                        val movie by catalogViewModel.observeMovieDetail(args.movieId)
                            .collectAsStateWithLifecycle(initialValue = null)
                        val canResume = homeSnapshot.continueWatching.any { it.mediaId == args.movieId }
                        MovieDetailScreen(
                            movie = movie,
                            canResume = canResume,
                            onPlay = { resume ->
                                movie?.let { mov ->
                                    mainViewModel.playMovie(mov, resume) {
                                        navController.navigate(AppRoute.Player)
                                    }
                                }
                            },
                            onToggleFavorite = catalogViewModel::toggleMovieFavorite,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<AppRoute.SeriesDetail> { entry ->
                        val args = entry.toRoute<AppRoute.SeriesDetail>()
                        val series by catalogViewModel.observeSeriesDetail(args.seriesId)
                            .collectAsStateWithLifecycle(initialValue = null)
                        val episodes by catalogViewModel.observeEpisodes(args.seriesId)
                            .collectAsStateWithLifecycle(initialValue = emptyList())
                        val lastWatched by catalogViewModel.observeLastWatchedForSeries(args.seriesId)
                            .collectAsStateWithLifecycle(initialValue = null)
                        SeriesDetailScreen(
                            series = series,
                            episodes = episodes,
                            lastWatched = lastWatched,
                            autoplayEnabled = preferences.autoplayNextEpisode,
                            onToggleAutoplay = mainViewModel::setAutoplayNextEpisode,
                            onToggleFavorite = catalogViewModel::toggleSeriesFavorite,
                            onPlayEpisode = { ser, ep, resume ->
                                mainViewModel.playEpisode(ser, ep, resume) {
                                    navController.navigate(AppRoute.Player)
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<AppRoute.Playlists> {
                        PlaylistManagerScreen(
                            playlists = playlists,
                            preferences = preferences,
                            isRefreshing = isRefreshing,
                            decodeHeaders = mainViewModel::decodePlaylistHeadersText,
                            onVerifyPin = mainViewModel::verifyParentalPin,
                            onSaveXtream = { existingId, name, serverUrl, port, username, password, customUa, useCustomUa, headersText, onDone ->
                                mainViewModel.saveXtreamPlaylist(
                                    existingId = existingId,
                                    name = name,
                                    serverUrl = serverUrl,
                                    port = port,
                                    username = username,
                                    password = password,
                                    customUserAgent = customUa,
                                    useCustomUserAgent = useCustomUa,
                                    headersText = headersText,
                                    onDone = onDone
                                )
                            },
                            onSaveM3uUrl = { existingId, name, m3uUrl, epgUrl, customUa, useCustomUa, headersText, onDone ->
                                mainViewModel.saveM3uUrlPlaylist(
                                    existingId = existingId,
                                    name = name,
                                    m3uUrl = m3uUrl,
                                    epgUrl = epgUrl,
                                    customUserAgent = customUa,
                                    useCustomUserAgent = useCustomUa,
                                    headersText = headersText,
                                    onDone = onDone
                                )
                            },
                            onImportLocalM3u = mainViewModel::importLocalM3u,
                            onActivatePlaylist = mainViewModel::setActivePlaylist,
                            onSetDefaultPlaylist = mainViewModel::setDefaultPlaylist,
                            onRefreshPlaylist = mainViewModel::refreshPlaylistById,
                            onDeletePlaylist = mainViewModel::deletePlaylist,
                            onOpenQrPairing = { navController.navigate(AppRoute.QrPairing) },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<AppRoute.ImportPlaylist> {
                        PlaylistImportScreen(
                            preferences = preferences,
                            isRefreshing = isRefreshing,
                            hasExistingPlaylists = playlists.isNotEmpty(),
                            onFindPlaylistByName = mainViewModel::findPlaylistByName,
                            onVerifyPin = mainViewModel::verifyParentalPin,
                            onPreviewLocalM3u = mainViewModel::previewLocalM3u,
                            onSaveConfirmedLocalM3u = mainViewModel::saveConfirmedLocalM3u,
                            onTestM3uUrl = mainViewModel::testM3uUrlConnection,
                            onSaveM3uUrl = mainViewModel::saveM3uUrlPlaylist,
                            onTestXtream = mainViewModel::testXtreamConnection,
                            onSaveXtream = mainViewModel::saveXtreamPlaylist,
                            onActivatePlaylist = mainViewModel::setActivePlaylist,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<AppRoute.QrPairing> {
                        QrPairingScreen(
                            preferences = preferences,
                            onRegenerateToken = mainViewModel::regeneratePairingToken,
                            onSyncPairedPlaylists = mainViewModel::syncPairedPlaylists,
                            onVerifyActivation = mainViewModel::verifyActivationStatus,
                            onShowMessage = { msg ->
                                uiScope.launch { snackbarHostState.showSnackbar(msg) }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<AppRoute.EpgGuide> {
                        EpgGuideScreen(
                            isWideScreen = isWideScreen,
                            schedule = fullEpgSchedule,
                            timezoneOffsetMinutes = preferences.epgTimezoneOffsetMinutes,
                            epgStatusMessage = epgStatusMessage,
                            onRefreshEpg = mainViewModel::refreshEpg,
                            onChannelClick = { ch ->
                                mainViewModel.playChannel(ch) {
                                    navController.navigate(AppRoute.Player)
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<AppRoute.Player> {
                        PlayerScreen(
                            playerController = mainViewModel.playerController,
                            playerState = playerState,
                            preferences = preferences,
                            channels = liveUiState.channels,
                            epgMap = liveUiState.epgMap,
                            isInPipMode = isInPipMode,
                            onSwitchChannel = { ch ->
                                mainViewModel.playChannel(ch) {}
                            },
                            onFindNextEpisode = {
                                val tgt = playerState.target
                                if (tgt != null) mainViewModel.findNextEpisodeForTarget(tgt) else null
                            },
                            onPlayNextEpisode = { ser, ep ->
                                mainViewModel.playEpisode(ser, ep, resume = false) {}
                            },
                            onSubtitleStyleChange = mainViewModel::setSubtitleAppearance,
                            onSubtitleDelayChange = mainViewModel::setSubtitleDelayMs,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
