package org.peercast.pecaplay.app

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.peercast.pecaplay.app.dao.FavoriteDao
import org.peercast.pecaplay.app.dao.YellowPageDao
import org.peercast.pecaplay.app.dao.YpHistoryChannelDao
import org.peercast.pecaplay.app.dao.YpLiveChannelDao


@Database(
    entities = [
        YellowPage::class, Favorite::class,
        YpLiveChannel::class, YpHistoryChannel::class
    ],
    version = 50100,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppRoomDatabase : RoomDatabase() {
    abstract val yellowPageDao: YellowPageDao
    abstract val favoriteDao: FavoriteDao
    abstract val ypChannelDao: YpLiveChannelDao
    abstract val ypHistoryDao: YpHistoryChannelDao

    companion object {
        fun createInstance(a: Application, dbName: String): AppRoomDatabase {
            return Room.databaseBuilder(
                a,
                AppRoomDatabase::class.java,
                dbName
            )
                .addMigrations(*MIGRATIONS)
                .build()
        }
    }

}

private val MIGRATIONS = arrayOf(
    object : Migration(50000, -50001) {
        override fun migrate(database: SupportSQLiteDatabase) {
        }
    },
    object : Migration(50000, 50100) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE YpIndex RENAME TO YpLiveChannel")
            database.execSQL("ALTER TABLE YpHistory RENAME TO YpHistoryChannel")
        }
    }
)

private const val TAG = "AppRoom"