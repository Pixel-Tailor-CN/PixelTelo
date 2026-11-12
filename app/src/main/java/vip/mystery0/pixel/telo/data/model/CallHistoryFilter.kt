package vip.mystery0.pixel.telo.data.model

import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.entity.isQueryFailure

/** 筛选历史实际处理结果；失败按 Fail Open 也属于已放行。 */
enum class CallHistoryFilter {
    ALL, BLOCKED, ALLOWED, FAILED;

    fun resultTypes(): List<String> = ResultType.entries.filter { result ->
        val blocked = result == ResultType.INTERCEPT || result == ResultType.BLACK_LIST
        when (this) {
            ALL -> true
            BLOCKED -> blocked
            ALLOWED -> !blocked
            FAILED -> result.isQueryFailure()
        }
    }.map { it.name }
}

/** 仅移除常见号码格式字符，保留字母以免混合查询误退化为纯数字。 */
fun phoneSearchKey(value: String): String = value.trim().filterNot {
    it.isWhitespace() || it in "-()+."
}
