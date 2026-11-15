package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.local.AppPreferencesRepository
import com.example.data.local.MehdiTvDatabase
import com.example.data.remote.ActivationPairingClient
import com.example.data.remote.XtreamClient
import com.example.data.repository.PlaylistRepository
import com.example.player.MehdiPlayerController
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MehdiTvApplication : Application(), ImageLoaderFactory {

    lateinit var database: MehdiTvDatabase
        private set
    lateinit var preferencesRepository: AppPreferencesRepository
        private set
    lateinit var okHttpClient: OkHttpClient
        private set
    lateinit var xtreamClient: XtreamClient
        private set
    lateinit var activationPairingClient: ActivationPairingClient
        private set
    lateinit var playlistRepository: PlaylistRepository
        private set
    lateinit var playerController: MehdiPlayerController
        private set

    override fun onCreate() {
        super.onCreate()
        database = MehdiTvDatabase.getInstance(this)
        preferencesRepository = AppPreferencesRepository(this)
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        xtreamClient = XtreamClient(okHttpClient)
        activationPairingClient = ActivationPairingClient(okHttpClient)
        playlistRepository = PlaylistRepository(
            context = this,
            database = database,
            prefsRepo = preferencesRepository,
            okHttpClient = okHttpClient,
            xtreamClient = xtreamClient
        )
        playerController = MehdiPlayerController(
            context = this,
            okHttpClient = okHttpClient,
            playlistRepository = playlistRepository
        )
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024L * 1024L)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
