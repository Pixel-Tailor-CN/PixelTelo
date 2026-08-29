package vip.mystery0.pixel.telo.data

/**
 * 本地号码规则的统一匹配器。
 *
 * 明确的非中国国际号码保留国际标记，只在 + 号码与 00 号码之间建立等价关系；
 * 没有国际标记的号码继续使用国内查询域的现有标准化逻辑。
 */
object PhoneNumberRuleMatcher {
    /** 将输入中的安全格式字符移除，但保留号码开头的 +。 */
    private fun compact(value: String): String = value.trim().filterNot { char ->
        char.isWhitespace() || char == '-' || char == '(' || char == ')'
    }

    /** 判断号码是否为带有明确国际标记的非中国号码。 */
    fun isExplicitInternational(phoneNumber: String): Boolean {
        val compactNumber = compact(phoneNumber)
        return (compactNumber.startsWith('+') && !compactNumber.startsWith("+86")) ||
            (compactNumber.startsWith("00") && !compactNumber.startsWith("0086"))
    }

    /** 将号码规则转换为新增名单条目的规范存储形式。 */
    fun normalizeRuleForStorage(value: String): String {
        val compactRule = compact(value)
        return when {
            compactRule.startsWith("00") && !compactRule.startsWith("0086") ->
                "+${compactRule.removePrefix("00")}"
            compactRule.startsWith('+') && !compactRule.startsWith("+86") -> compactRule
            else -> PhoneNumberNormalizer.normalizeForLookup(compactRule)
        }
    }

    /**
     * 生成 DAO 查询使用的有序候选集。
     *
     * 明确国际号码只返回 + 规范形式和 00 兼容形式，禁止降级为无标记的纯数字；
     * 国内号码则保留标准化号码以及和多号前缀兼容所需的旧格式。
     */
    fun matchCandidates(phoneNumber: String): List<String> {
        val compactNumber = compact(phoneNumber)
        if (isExplicitInternational(compactNumber)) {
            val canonical = if (compactNumber.startsWith("00")) {
                "+${compactNumber.removePrefix("00")}"
            } else {
                compactNumber
            }
            val zeroZeroCompatible = canonical
                .takeIf { it.startsWith('+') }
                ?.let { "00${it.removePrefix("+")}" }
            return listOf(canonical, zeroZeroCompatible)
                .filterNotNull()
                .filter { it.isNotBlank() }
                .distinct()
        }

        val normalizedNumber = PhoneNumberNormalizer.normalizeForLookup(compactNumber)
        val originalNumber = PhoneNumberNormalizer.normalizeCountryCode(compactNumber)
        return listOf(normalizedNumber, originalNumber)
            .filter { it.isNotBlank() }
            .distinct()
    }

    /** 判断号码规则是否精确命中或前缀命中来电号码。 */
    fun ruleMatches(rule: String, isPrefix: Boolean, phoneNumber: String): Boolean {
        val compactRule = compact(rule)
        if (compactRule.isBlank()) return false
        return matchCandidates(phoneNumber).any { candidate ->
            if (candidate.isBlank()) return false
            if (isPrefix) candidate.startsWith(compactRule) else candidate == compactRule
        }
    }
}
