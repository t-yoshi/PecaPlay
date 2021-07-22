package org.peercast.pecaplay.app

import android.app.Application
import android.os.AsyncTask
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
import timber.log.Timber


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


    private fun truncate() {
        runInTransaction {
            var r = compileStatement(
                """
DELETE FROM YpHistoryChannel WHERE rowid NOT IN (
  SELECT rowid FROM YpHistoryChannel ORDER BY LastPlay DESC LIMIT 100
)""".trimIndent()
            ).use {
                it.executeUpdateDelete()
            }

            r += compileStatement(
                """
DELETE FROM YpLiveChannel
  WHERE lastLoadedTime < DATETIME('now', '-12 hours', PRINTF('-%d hours', age))
                """.trimIndent()
            ).use {
                it.executeUpdateDelete()
            }
            Timber.d("OK: truncate() $r")
        }
    }

    companion object {
        fun createInstance(a: Application, dbName: String): AppRoomDatabase {
            return Room.databaseBuilder(
                a,
                AppRoomDatabase::class.java,
                dbName
            )
                .addMigrations(*MIGRATIONS)
                .build().also {
                    AsyncTask.execute {
                        it.truncate()
                    }
                }
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