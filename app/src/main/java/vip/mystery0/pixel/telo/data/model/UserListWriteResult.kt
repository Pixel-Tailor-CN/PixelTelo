package vip.mystery0.pixel.telo.data.model

/** 校验失败不等于重复；只有数据库唯一键冲突才能向用户提示规则已存在。 */
enum class UserListWriteResult {
    SAVED,
    DUPLICATE,
    INVALID_INPUT,
    NOT_FOUND,
}
