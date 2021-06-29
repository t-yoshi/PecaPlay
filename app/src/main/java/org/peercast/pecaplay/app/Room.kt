package org.peercast.pecaplay.app

import android.app.Application
import android.net.Uri
import android.os.AsyncTask
import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*


@Dao
interface YellowPageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: YellowPage)

    @Update
    suspend fun update(item: YellowPage)

    @Delete
    suspend fun remove(item: YellowPage)

    @Deprecated("")
    @Query("SELECT * FROM YellowPage WHERE NOT :isEnabled OR enabled ORDER BY Name")
    suspend fun queryAwait(isEnabled: Boolean = true): List<YellowPage>

    @Deprecated("")
    @Query("SELECT * FROM YellowPage WHERE NOT :isSelectEnabled OR enabled ORDER BY Name")
    fun query(isSelectEnabled: Boolean = true): LiveData<List<YellowPage>>

    @Deprecated("")
    @Query("SELECT * FROM YellowPage WHERE NOT :isSelectEnabled OR enabled ORDER BY Name")
    fun query2(isSelectEnabled: Boolean = true): Flow<List<YellowPage>>

}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: Favorite)

    @Update
    suspend fun update(item: Favorite)

    @Delete
    suspend fun remove(item: Favorite)

    @Query("SELECT * FROM Favorite WHERE NOT :isSelectEnabled OR enabled")
    suspend fun queryAwait(isSelectEnabled: Boolean = true): List<Favorite>

    @Deprecated("")
    @Query("SELECT * FROM Favorite WHERE NOT :isSelectEnabled OR enabled")
    fun query(isSelectEnabled: Boolean = true): LiveData<List<Favorite>>

    @Query("SELECT * FROM Favorite WHERE NOT :isSelectEnabled OR enabled")
    fun query2(isSelectEnabled: Boolean = true): Flow<List<Favorite>>
}


@Dao
interface YpLiveChannelDao {
    @Deprecated("")
    @Query("SELECT * FROM YpLiveChannel WHERE isLatest")
    fun query(): LiveData<List<YpLiveChannel>>

    @Query("SELECT * FROM YpLiveChannel WHERE isLatest")
    fun query2(): Flow<List<YpLiveChannel>>

    @Deprecated("")
    @Query("SELECT * FROM YpLiveChannel WHERE isLatest")
    suspend fun queryAwait(): List<YpLiveChannel>

    /**最後の読み込みからの経過(秒)*/
    @Query("SELECT STRFTIME('%s')-IFNULL(STRFTIME('%s',MAX(lastLoadedTime)),0) FROM YpLiveChannel")
    fun getLastLoadedSince(): Int

    @Query("SELECT MAX(lastLoadedTime) FROM YpLiveChannel")
    fun getLastLoaded(): Date?
}

@Dao
interface YpHistoryChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addHistory(ch: YpHistoryChannel)

    @Deprecated("")
    @Query("SELECT * FROM YpHistoryChannel ORDER BY lastPlay DESC")
    suspend fun queryAwait(): List<YpHistoryChannel>

    @Deprecated("")
    @Query("SELECT * FROM YpHistoryChannel ORDER BY lastPlay DESC")
    fun query(): LiveData<List<YpHistoryChannel>>

    @Query("SELECT * FROM YpHistoryChannel ORDER BY lastPlay DESC")
    fun query2(): Flow<List<YpHistoryChannel>>
}

private class Converters {
    @TypeConverter
    fun dateFromString(s: String?): Date = newDateFormat().parse(s)

    @TypeConverter
    fun dateToString(d: Date): String = newDateFormat().format(d)

    @TypeConverter
    fun stringToUri(s: String): Uri = Uri.parse(s)

    @TypeConverter
    fun uriToString(u: Uri) = u.toString()

    companion object {
        private val GMT_TIMEZONE = TimeZone.getTimeZone("GMT")
        private const val DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss"

        private fun newDateFormat() =
            SimpleDateFormat(DATETIME_FORMAT, Locale.US).apply {
                timeZone = GMT_TIMEZONE
            }

    }
}

@Database(
    entities = [
        YellowPage::class, Favorite::class,
        YpLiveChannel::class, YpHistoryChannel::class
    ],
    version = 50100,
    exportSchema = false
)
@TypeConverters(Converters::class)
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