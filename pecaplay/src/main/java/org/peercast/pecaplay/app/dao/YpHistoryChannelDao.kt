package org.peercast.pecaplay.app.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.peercast.pecaplay.app.YpHistoryChannel

@Dao
interface YpHistoryChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addHistory(ch: YpHistoryChannel)

    @Query("SELECT * FROM YpHistoryChannel ORDER BY lastPlay DESC")
    fun query(): Flow<List<YpHistoryChannel>>
}