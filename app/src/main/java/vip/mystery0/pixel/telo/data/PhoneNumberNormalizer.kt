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
        return phoneNumber.trim().removePrefix(CHINA_COUNTRY_CODE)
    }

    fun normalizeForLookup(phoneNumber: String): String {
        val domesticNumber = normalizeCountryCode(phoneNumber)
        val multiNumberPrefix = CHINA_MOBILE_MULTI_NUMBER_PREFIXES.firstOrNull {
            domesticNumber.startsWith(it)
        }
        if (multiNumberPrefix == null || domesticNumber.length == multiNumberPrefix.length) {
            return domesticNumber
        }
        return domesticNumber.removePrefix(multiNumberPrefix)
    }
}
