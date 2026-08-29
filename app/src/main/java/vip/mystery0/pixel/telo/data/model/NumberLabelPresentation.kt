package vip.mystery0.pixel.telo.data.model

/**
 * 纯展示组合模型：把本地标签与来源标签拼接成 Directory Provider 显示名。
 *
 * 空白标签会被丢弃；两者都有效时用 ` · ` 连接，都为空时返回 null。
 */
data class NumberLabelPresentation(
    val localLabel: String?,
    val sourceLabel: String?,
) {
    fun directoryDisplayName(): String? = listOfNotNull(
        localLabel.cleanLabel(),
        sourceLabel.cleanLabel(),
    ).joinToString(" · ").takeIf { it.isNotEmpty() }
}

private fun String?.cleanLabel(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
