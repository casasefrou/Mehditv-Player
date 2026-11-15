package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable data object Home : AppRoute
    @Serializable data object LiveTv : AppRoute
    @Serializable data object Movies : AppRoute
    @Serializable data object Series : AppRoute
    @Serializable data object Settings : AppRoute
    @Serializable data class MovieDetail(val movieId: String) : AppRoute
    @Serializable data class SeriesDetail(val seriesId: String) : AppRoute
    @Serializable data object Player : AppRoute
    @Serializable data object Playlists : AppRoute
    @Serializable data object ImportPlaylist : AppRoute
    @Serializable data object QrPairing : AppRoute
    @Serializable data object EpgGuide : AppRoute
}
