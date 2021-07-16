package org.peercast.pecaplay.app.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.peercast.pecaplay.app.YellowPage

@Dao
interface YellowPageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: YellowPage)

    @Update
    suspend fun update(item: YellowPage)

    @Delete
    suspend fun remove(item: YellowPage)

    @Query("SELECT * FROM YellowPage WHERE NOT :selectOnlyEnabledItems OR enabled ORDER BY Name")
    fun query(selectOnlyEnabledItems: Boolean = true): Flow<List<YellowPage>>
}