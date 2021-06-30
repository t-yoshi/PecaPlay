package org.peercast.pecaplay.app.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.peercast.pecaplay.app.Favorite

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: Favorite)

    @Update
    suspend fun update(item: Favorite)

    @Delete
    suspend fun remove(item: Favorite)

    @Query("SELECT * FROM Favorite WHERE NOT :selectOnlyEnabledItems OR enabled")
    fun query(selectOnlyEnabledItems: Boolean = true): Flow<List<Favorite>>
}