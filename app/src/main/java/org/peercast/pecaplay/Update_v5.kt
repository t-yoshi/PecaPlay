package org.peercast.pecaplay

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.RoomDatabase
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.peercast.pecaplay.app.Favorite
import org.peercast.pecaplay.app.YellowPage
import org.peercast.pecaplay.prefs.AppPreferences
import timber.log.Timber


/**
 * PecaPlay Ver.4 からのSQLite移行ツール
 *
 * */
class AppSQLiteUpdater_v4(private val context: Context) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        val oldFile = context.getDatabasePath(DB_NAME)
        if (!oldFile.exists())
            return
        Timber.i( "Sqlite Update from v4")
        val oldDb = OpenHelper_v4().readableDatabase

        migrateYellowPage(oldDb, db)
        migrateFavorite(oldDb, db)
    }

    private fun migrateYellowPage(oldDb: SQLiteDatabase, newDb: SupportSQLiteDatabase) {
        val cv = ContentValues(3)
        val pref = AppPreferences(context)
        oldDb.rawQuery("SELECT name, url FROM YellowPage", null)
                .forEachFromFirst { cur ->
                    val name = cur.getString(0)
                    val url = cur.getString(1)
                    val enabled = pref.isYellowPageEnabled(name)
                    if (YellowPage.isValidUrl(url)) {
                        cv.put("name", name)
                        cv.put("url", url)
                        cv.put("enabled", enabled)
                        newDb.insert("YellowPage",
                                SQLiteDatabase.CONFLICT_IGNORE, cv)

                    }
                }
    }

    private fun migrateFavorite(oldDb: SQLiteDatabase, newDb: SupportSQLiteDatabase) {
// Ver.4 Table
//        CREATE TABLE IF NOT EXISTS Favorite (
//             name TEXT UNIQUE NOT NULL,
//             type TEXT NOT NULL,
//             pattern TEXT NOT NULL,
//             flags INTEGER NOT NULL
//        );

        val pref = AppPreferences(context)
        val cv = ContentValues(3)
        oldDb.rawQuery("SELECT * FROM Favorite", null)
                .forEachFromFirst { cur ->
                    var name = cur.getString(0)
                    val type = cur.getString(1)
                    val pattern = cur.getString(2)
                    val flags = cur.getInt(3)
                    var newFlags = Favorite.Flags(
                            isName = flags and FavoriteFlag_v4.NAME != 0,
                            isDescription = flags and FavoriteFlag_v4.DESCRIPTION != 0,
                            isComment = flags and FavoriteFlag_v4.COMMENT != 0,
                            isUrl = flags and FavoriteFlag_v4.URL != 0,
                            isNG = flags and FavoriteFlag_v4.NG != 0,
                            isNotification = flags and FavoriteFlag_v4.NOTIFICATION != 0,
                            isExactMatch = flags and FavoriteFlag_v4.EXACT_MATCH != 0,
                            isCaseSensitive = flags and FavoriteFlag_v4.CASE_SENSITIVE != 0
                    )
                    val enabled = pref.isFavoriteEnabled(name)

                    when (type) {
                        "PatternMatch" -> {
                            newFlags = newFlags.copy(isRegex = true)
                        }
                        "Starred" -> {
                            name = "[star]$name"
                        }
                    }

                    cv.put("name", name)
                    cv.put("pattern", pattern)
                    cv.put("flags", Favorite.Converter().flagsToString(newFlags))
                    cv.put("enabled", enabled)

                    val r = newDb.insert("Favorite",
                            SQLiteDatabase.CONFLICT_IGNORE, cv)

                    Timber.d( "$r: $cv")
                }


    }

    inner class OpenHelper_v4 :
            SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        }
    }

    companion object {
        private const val TAG = "AppSQLiteUpdater_v4"
        private const val DB_NAME = "pecaplay.db" //old db
        private const val DB_VERSION = 1
    }

}


object FavoriteFlag_v4 {
    /**Ch名にマッチする*/
    const val NAME = 0x0001

    /**Ch詳細にマッチする*/
    const val DESCRIPTION = 0x0002

    /**Chコメントにマッチする*/
    const val COMMENT = 0x0004

    /**Ch URLにマッチする*/
    const val URL = 0x0008

    /**NGである*/
    const val NG = 0x0010

    /**通知する対象*/
    const val NOTIFICATION = 0x0020

    /**
     * 完全一致のみ。
     */
    const val EXACT_MATCH = 0x0200

    /**
     * 大文字小文字の違いを区別する
     */
    const val CASE_SENSITIVE = 0x0400
}


private fun Cursor.forEachFromFirst(autoClose: Boolean = true, action: (Cursor) -> Unit) {
    try {
        if (moveToFirst()) {
            do {
                action(this)
            } while (moveToNext())
        }
    } finally {
        if (autoClose) close()
    }
}