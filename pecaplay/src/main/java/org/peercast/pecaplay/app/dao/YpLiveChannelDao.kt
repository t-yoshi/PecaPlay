package org.peercast.pecaplay.app.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.peercast.pecaplay.app.YpLiveChannel
import java.util.*

@Dao
interface YpLiveChannelDao {

    @Query("SELECT * FROM YpLiveChannel WHERE isLatest")
    fun query(): Flow<List<YpLiveChannel>>

    /**最後の読み込みからの経過(秒)*/
    @Query("SELECT STRFTIME('%s')-IFNULL(STRFTIME('%s',MAX(lastLoadedTime)),0) FROM YpLiveChannel")
    fun getLastLoadedSince(): Int

    @Query("SELECT MAX(lastLoadedTime) FROM YpLiveChannel")
    fun getLastLoaded(): Date?
}