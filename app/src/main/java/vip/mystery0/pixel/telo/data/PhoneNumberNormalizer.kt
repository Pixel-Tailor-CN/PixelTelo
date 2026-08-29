package vip.mystery0.pixel.telo.data

/**
 * 将系统提供的来电号码转换为本地数据库与联网接口使用的查询号码。
 *
 * 中国移动“和多号/一卡多号”会在真实主叫号码前添加固定前缀。标准化时只处理已确认的
 * 125831、125832、125833，并保留前缀后的号码格式，例如境外号码的 00 国家代码。
 */
object PhoneNumberNormalizer {
    private const val CHINA_COUNTRY_CODE = "+86"

    private val CHINA_MOBILE_MULTI_NUMBER_PREFIXES = arrayOf(
        "125831",
        "125832",
        "125833",
    )

    internal fun normalizeCountryCode(phoneNumber: String): String {
        val trimmedNumber = phoneNumber.trim()
        if (trimmedNumber.startsWith(CHINA_COUNTRY_CODE)) {
            return trimmedNumber.removePrefix(CHINA_COUNTRY_CODE)
        }
        if (trimmedNumber.startsWith("0086")) {
            return trimmedNumber.removePrefix("0086")
        }
        if (
            trimmedNumber.length == 12 &&
            trimmedNumber.startsWith('+') &&
            trimmedNumber[1] == '1' &&
            trimmedNumber.drop(1).all(Char::isDigit)
        ) {
            return trimmedNumber.drop(1)
        }
        if (
            trimmedNumber.length == 13 &&
            trimmedNumber.startsWith("86") &&
            trimmedNumber[2] == '1'
        ) {
            return trimmedNumber.drop(2)
        }
        return trimmedNumber
    }

    fun normalizeForLookup(phoneNumber: String): String {
        val compactNumber = phoneNumber.trim().filterNot { char ->
            char.isWhitespace() || char == '-' || char == '(' || char == ')'
        }
        val domesticNumber = normalizeCountryCode(compactNumber)
        val multiNumberPrefix = CHINA_MOBILE_MULTI_NUMBER_PREFIXES.firstOrNull {
            domesticNumber.startsWith(it)
        }
        if (multiNumberPrefix == null || domesticNumber.length == multiNumberPrefix.length) {
            return domesticNumber
        }
        return domesticNumber.removePrefix(multiNumberPrefix)
    }
}
