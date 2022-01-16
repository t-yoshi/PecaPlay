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

    //配信者のidはランダム化されるようになったが、DBをいじりたくないので
    // SELECT ... GROUP BY name で対応する
    @Query("SELECT * FROM YpHistoryChannel GROUP BY name ORDER BY lastPlay DESC")
    fun query(): Flow<List<YpHistoryChannel>>
}