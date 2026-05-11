package vip.mystery0.pixel.telo.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import vip.mystery0.pixel.telo.data.entity.ListType
import vip.mystery0.pixel.telo.data.entity.UserListEntry

@Dao
interface UserListDao {
    /** 实时监听指定类型的名单，供 UI 展示 */
    @Query("SELECT * FROM user_list WHERE listType = :type ORDER BY addedAt DESC")
    fun observeByType(type: ListType): Flow<List<UserListEntry>>

    /** 获取指定类型的全部条目，供备份用 */
    @Query("SELECT * FROM user_list WHERE listType = :type")
    suspend fun getAllByType(type: ListType): List<UserListEntry>

    /** 插入，若 (phoneNumber, listType) 已存在则忽略 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: UserListEntry): Long

    @Delete
    suspend fun delete(entry: UserListEntry)

    /** 查询是否存在匹配的条目（精确或前缀），用于拦截判断。
     *  前缀匹配使用 SUBSTR 而非 LIKE，避免 phoneNumber 中含有 '%'/'_' 等 LIKE 通配符时产生语义错误。
     *  tagMatch=0 时按号码匹配，tagMatch=1 时忽略（标签匹配由 Repository 层处理）。
     */
    @Query("""
        SELECT * FROM user_list
        WHERE listType = :type
        AND tagMatch = 0
        AND (
            (isPrefix = 0 AND phoneNumber = :phone) OR
            (isPrefix = 1 AND SUBSTR(:phone, 1, LENGTH(phoneNumber)) = phoneNumber)
        )
        LIMIT 1
    """)
    suspend fun findMatch(phone: String, type: ListType): UserListEntry?

    /** 查询指定类型的所有标签匹配规则 */
    @Query("SELECT * FROM user_list WHERE listType = :type AND tagMatch = 1")
    suspend fun getTagRules(type: ListType): List<UserListEntry>
}
