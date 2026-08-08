package vip.mystery0.pixel.telo.data.query

/**
 * 严格遵循 SemVer 2.0.0 的不可变版本值对象。
 *
 * 构造函数保持私有，所有实例都必须经由 [parse] 创建，以保证主版本、次版本和修订版本以及
 * prerelease、build metadata 均已通过格式校验。
 */
class SemanticVersion private constructor(
    private val major: String,
    private val minor: String,
    private val patch: String,
    private val preReleaseIdentifiers: List<String>?,
    private val buildMetadata: String?,
) : Comparable<SemanticVersion> {
    /**
     * 按 SemVer 优先级比较版本。build metadata 不影响比较结果；稳定版本的优先级高于同一核心
     * 版本的 prerelease，prerelease 标识符再按数值或 ASCII 字典序逐项比较。
     */
    override fun compareTo(other: SemanticVersion): Int {
        compareNumericIdentifier(major, other.major).takeIf { it != 0 }?.let { return it }
        compareNumericIdentifier(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareNumericIdentifier(patch, other.patch).takeIf { it != 0 }?.let { return it }

        val thisPreRelease = preReleaseIdentifiers
        val otherPreRelease = other.preReleaseIdentifiers
        if (thisPreRelease == null || otherPreRelease == null) {
            return when {
                thisPreRelease == null && otherPreRelease == null -> 0
                thisPreRelease == null -> 1
                else -> -1
            }
        }

        val sharedCount = minOf(thisPreRelease.size, otherPreRelease.size)
        for (index in 0 until sharedCount) {
            comparePreReleaseIdentifier(
                thisPreRelease[index],
                otherPreRelease[index],
            ).takeIf { it != 0 }?.let { return it }
        }
        return thisPreRelease.size.compareTo(otherPreRelease.size)
    }

    override fun equals(other: Any?): Boolean =
        other is SemanticVersion &&
            major == other.major &&
            minor == other.minor &&
            patch == other.patch &&
            preReleaseIdentifiers == other.preReleaseIdentifiers &&
            buildMetadata == other.buildMetadata

    override fun hashCode(): Int =
        arrayOf(major, minor, patch, preReleaseIdentifiers, buildMetadata).contentHashCode()

    override fun toString(): String = buildString {
        append(major)
        append('.')
        append(minor)
        append('.')
        append(patch)
        preReleaseIdentifiers?.let {
            append('-')
            append(it.joinToString("."))
        }
        buildMetadata?.let {
            append('+')
            append(it)
        }
    }

    private fun comparePreReleaseIdentifier(left: String, right: String): Int {
        val leftIsNumeric = left.all(Char::isDigit)
        val rightIsNumeric = right.all(Char::isDigit)
        return when {
            leftIsNumeric && rightIsNumeric -> compareNumericIdentifier(left, right)
            leftIsNumeric -> -1
            rightIsNumeric -> 1
            else -> left.compareTo(right)
        }
    }

    private fun compareNumericIdentifier(left: String, right: String): Int =
        when {
            left.length != right.length -> left.length.compareTo(right.length)
            else -> left.compareTo(right)
        }

    companion object {
        private val semanticVersionPattern = Regex(
            """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|[0-9A-Za-z-]*[A-Za-z-][0-9A-Za-z-]*)(?:\.(?:0|[1-9]\d*|[0-9A-Za-z-]*[A-Za-z-][0-9A-Za-z-]*))*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$""",
        )

        /**
         * 解析严格的 SemVer 字符串。
         *
         * @param value 待解析的版本字符串。
         * @param allowPreRelease 是否接受含 prerelease 的版本。
         * @return 格式合法且符合 prerelease 策略时返回版本对象，否则返回 null。
         */
        fun parse(value: String, allowPreRelease: Boolean): SemanticVersion? {
            val match = semanticVersionPattern.matchEntire(value) ?: return null
            val preRelease = match.groups[4]?.value
            if (preRelease != null && !allowPreRelease) return null

            return SemanticVersion(
                major = match.groups[1]!!.value,
                minor = match.groups[2]!!.value,
                patch = match.groups[3]!!.value,
                preReleaseIdentifiers = preRelease?.split('.'),
                buildMetadata = match.groups[5]?.value,
            )
        }
    }
}
