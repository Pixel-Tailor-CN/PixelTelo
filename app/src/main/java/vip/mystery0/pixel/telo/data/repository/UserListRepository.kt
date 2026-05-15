package vip.mystery0.pixel.telo.data.repository

import kotlinx.coroutines.flow.Flow
import vip.mystery0.pixel.telo.data.dao.UserListDao
import vip.mystery0.pixel.telo.data.entity.ListType
import vip.mystery0.pixel.telo.data.entity.UserListEntry

/**
 * 用户自定义黑白名单的数据仓库。
 * 提供增删查接口，供 ViewModel 和拦截服务使用。
 */
class UserListRepository(private val dao: UserListDao) {

    /** 实时监听黑名单，供 UI 展示 */
    fun observeBlackList(): Flow<List<UserListEntry>> = dao.observeByType(ListType.BLACK)

    /** 实时监听白名单，供 UI 展示 */
    fun observeWhiteList(): Flow<List<UserListEntry>> = dao.observeByType(ListType.WHITE)

    /**
     * 查询来电号码是否命中黑名单（精确或前缀匹配）。
     * 命中则返回匹配的条目，未命中返回 null。
     */
    suspend fun findBlackListMatch(phone: String): UserListEntry? =
        dao.findMatch(phone, ListType.BLACK)

    /**
     * 查询来电号码是否命中白名单（精确或前缀匹配）。
     * 命中则返回匹配的条目，未命中返回 null。
     */
    suspend fun findWhiteListMatch(phone: String): UserListEntry? =
        dao.findMatch(phone, ListType.WHITE)

    /**
     * 查询标签是否命中白名单。
     * @param tag 来电标签（如"快递送餐"）
     * @return 匹配的条目，未命中返回 null
     */
    suspend fun findWhiteListTagMatch(tag: String): UserListEntry? {
        val tagRules = dao.getTagRules(ListType.WHITE)
        return tagRules.firstOrNull { it.phoneNumber == tag }
    }

    /**
     * 查询标签是否命中黑名单。
     * 黑名单标签规则用于强制拦截某一类已识别标签的来电。
     *
     * @param tag 来电标签（如"营销推广电话"）
     * @return 匹配的条目，未命中返回 null
     */
    suspend fun findBlackListTagMatch(tag: String): UserListEntry? {
        val tagRules = dao.getTagRules(ListType.BLACK)
        return tagRules.firstOrNull { it.phoneNumber == tag }
    }

    /**
     * 添加条目到指定名单。
     * 若 (phoneNumber, listType) 已存在则忽略并返回 false，成功插入返回 true。
     */
    suspend fun add(
        phoneNumber: String,
        isPrefix: Boolean,
        listType: ListType,
        remark: String?,
        tagMatch: Boolean = false
    ): Boolean {
        val entry = UserListEntry(
            phoneNumber = phoneNumber.trim(),
            isPrefix = isPrefix,
            listType = listType,
            remark = remark?.trim()?.takeIf { it.isNotBlank() },
            addedAt = System.currentTimeMillis(),
            tagMatch = tagMatch
        )
        return dao.insert(entry) != -1L
    }

    /** 删除指定条目 */
    suspend fun delete(entry: UserListEntry) = dao.delete(entry)

    /** 获取指定类型全部条目，供备份用 */
    suspend fun getAllByType(type: ListType): List<UserListEntry> = dao.getAllByType(type)
}
