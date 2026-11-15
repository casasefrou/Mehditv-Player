package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.CatalogDao
import com.example.data.local.dao.EpgDao
import com.example.data.local.dao.PlaylistDao
import com.example.data.local.dao.WatchHistoryDao
import com.example.data.local.entities.CategoryEntity
import com.example.data.local.entities.ChannelEntity
import com.example.data.local.entities.EpgProgramEntity
import com.example.data.local.entities.EpisodeEntity
import com.example.data.local.entities.MovieEntity
import com.example.data.local.entities.PlaylistEntity
import com.example.data.local.entities.SeriesEntity
import com.example.data.local.entities.WatchHistoryEntity

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        CategoryEntity::class,
        ChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
        EpgProgramEntity::class,
        WatchHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MehdiTvDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun catalogDao(): CatalogDao
    abstract fun epgDao(): EpgDao
    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: MehdiTvDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN supportsCatchup INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE channels ADD COLUMN isRadio INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE channels ADD COLUMN hasCatchup INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): MehdiTvDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MehdiTvDatabase::class.java,
                    "mehditv_player.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
