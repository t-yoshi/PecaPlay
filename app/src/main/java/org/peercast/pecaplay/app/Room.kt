package org.peercast.pecaplay.app

import android.app.Application
import android.arch.lifecycle.LiveData
import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.*
import android.arch.persistence.room.migration.Migration
import android.net.Uri
import kotlinx.coroutines.experimental.launch
import org.peercast.pecaplay.AppSQLiteUpdater_v4
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*


@Dao
interface YellowPageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(item: YellowPage)

    @Update
    fun update(item: YellowPage)

    @Delete
    fun remove(item: YellowPage)

    @Query("SELECT * FROM YellowPage ORDER BY Name")
    fun get(): LiveData<List<YellowPage>>

    @Query("SELECT * FROM YellowPage WHERE enabled ORDER BY Name")
    fun getEnabled(): LiveData<List<YellowPage>>
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(item: Favorite)

    @Update
    fun update(item: Favorite)

    @Delete
    fun remove(item: Favorite)

    @Query("SELECT * FROM Favorite")
    fun get(): LiveData<List<Favorite>>

    @Query("SELECT * FROM Favorite WHERE enabled")
    fun getEnabled(): LiveData<List<Favorite>>
}

@Dao
interface YpIndexDao {
    @Query("SELECT * FROM YpIndex WHERE isLatest")
    fun get(): LiveData<List<YpIndex>>

    @Query("SELECT genre FROM YpIndex UNION ALL SELECT genre FROM YpHistory")
    fun getGenre(): LiveData<List<String>>

    /**最後の読み込みからの経過(秒)*/
    @Query("SELECT STRFTIME('%s')-IFNULL(STRFTIME('%s',MAX(lastLoadedTime)),0) FROM YpIndex")
    fun getLastLoadedSince(): Int

    @Query("SELECT MAX(lastLoadedTime) FROM YpIndex")
    fun getLastLoaded(): Date?

}


@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun add(ch: YpHistory)

    @Query("SELECT * FROM YpHistory ORDER BY lastPlay DESC")
    fun get(): LiveData<List<YpHistory>>
}


private class Converters {
    @TypeConverter
    fun dateFromString(s: String?) : Date = newDateFormat().parse(s)

    @TypeConverter
    fun dateToString(d: Date) : String = newDateFormat().format(d)

    @TypeConverter
    fun stringToUri(s: String) : Uri = Uri.parse(s)

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
            YpIndex::class, YpHistory::class
        ],
        version = 50000)
@TypeConverters(Converters::class)
abstract class AppRoomDatabase : RoomDatabase() {
    abstract fun getYellowPageDao(): YellowPageDao
    abstract fun getFavoriteDao(): FavoriteDao
    abstract fun getYpIndexDao(): YpIndexDao
    abstract fun getHistoryDao(): HistoryDao

    private fun truncate() {
        runInTransaction {
            var r = compileStatement("""
DELETE FROM YpHistory WHERE rowid NOT IN (
  SELECT rowid FROM YpHistory ORDER BY LastPlay DESC LIMIT 100
)""".trimIndent()).use {
                it.executeUpdateDelete()
            }

            r += compileStatement("""
DELETE FROM YpIndex
  WHERE lastLoadedTime < DATETIME('now', '-12 hours', PRINTF('-%d hours', age))
                """.trimIndent()).use {
                it.executeUpdateDelete()
            }
            Timber.d("OK: truncate() $r")
        }
    }

    companion object {
        fun create(a: Application): AppRoomDatabase {
            return Room.databaseBuilder(a,
                    AppRoomDatabase::class.java,
                    "pecaplay-5")
                    //.fallbackToDestructiveMigration()
                    .addCallback(AppSQLiteUpdater_v4(a))
                    .addMigrations(*MIGRATIONS)
                    //.allowMainThreadQueries()
                    .build().also {
                        //if (it.isOpen)
                        launch { it.truncate() }
                    }
        }
    }


}

private val MIGRATIONS = arrayOf<Migration>(
        object : Migration(50000, -50001) {
            override fun migrate(database: SupportSQLiteDatabase) {
            }
        }
)

private const val TAG = "AppRoom"