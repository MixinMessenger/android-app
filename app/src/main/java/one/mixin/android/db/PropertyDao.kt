package one.mixin.android.db

import androidx.room.Dao
import androidx.room.Query
import one.mixin.android.extension.nowInUtc
import one.mixin.android.vo.Property

@Dao
interface PropertyDao : BaseDao<Property> {

    @Query("SELECT * FROM properties WHERE `key` = :key")
    suspend fun findByKey(key: String): Property?

    @Query("SELECT value FROM properties WHERE `key` = :key")
    suspend fun findValueByKey(key: String): String?

    @Query("UPDATE properties SET value = :value, updated_at = :updatedAt WHERE `key` = :key")
    suspend fun updateValueByKey(key: String, value: String, updatedAt: String = nowInUtc())
}
