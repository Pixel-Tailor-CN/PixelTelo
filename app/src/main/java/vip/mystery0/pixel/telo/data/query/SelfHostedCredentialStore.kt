package vip.mystery0.pixel.telo.data.query

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 使用 Android Keystore 保护自建服务 Token 的存储。
 *
 * 密文始终按版本化格式保存，并为每份候选凭据使用独立槽位。配置仓库先写入新槽位，
 * 完整保存非敏感配置后才切换活动指针，从而避免配置提交失败时破坏旧凭据。
 */
class SelfHostedCredentialStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(
        CREDENTIAL_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    /** 保存默认凭据槽位，供不需要配置切换的调用方使用。 */
    fun save(token: CharArray): Result<Unit> = save(DEFAULT_SLOT, token)

    /** 读取默认凭据槽位。 */
    fun load(): Result<CharArray> = load(DEFAULT_SLOT)

    /** 清除默认凭据槽位。 */
    fun clear() {
        clear(DEFAULT_SLOT)
    }

    /** 保存指定候选槽位的凭据；调用结束后会清空 [token]。 */
    internal fun save(slot: String, token: CharArray): Result<Unit> {
        var plaintext: ByteArray? = null
        var iv: ByteArray? = null
        var ciphertext: ByteArray? = null
        var encoded: ByteBuffer? = null

        return try {
            requireValidSlot(slot)
            encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(token))
            plaintext = ByteArray(encoded.remaining()).also(encoded::get)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            iv = cipher.iv
            require(iv.size == GCM_IV_LENGTH_BYTES) {
                "Invalid self-hosted credential IV"
            }
            ciphertext = cipher.doFinal(plaintext)

            val serialized = listOf(
                CIPHERTEXT_VERSION,
                Base64.encodeToString(iv, Base64.NO_WRAP),
                Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ).joinToString(SEPARATOR)
            check(preferences.edit().putString(preferenceKey(slot), serialized).commit()) {
                "Unable to persist self-hosted credentials"
            }
            Result.success(Unit)
        } catch (exception: Exception) {
            Result.failure(exception)
        } finally {
            token.fill('\u0000')
            plaintext?.fill(0)
            iv?.fill(0)
            ciphertext?.fill(0)
            encoded?.clearSensitiveContents()
        }
    }

    /** 读取指定候选槽位的凭据。调用方取得结果后必须自行清空返回数组。 */
    internal fun load(slot: String): Result<CharArray> {
        var iv: ByteArray? = null
        var ciphertext: ByteArray? = null
        var plaintext: ByteArray? = null
        var decoded: CharBuffer? = null

        return try {
            requireValidSlot(slot)
            val serialized = requireNotNull(preferences.getString(preferenceKey(slot), null)) {
                "Self-hosted credentials are unavailable"
            }
            val parts = serialized.split(SEPARATOR)
            require(parts.size == 3 && parts[0] == CIPHERTEXT_VERSION) {
                "Unsupported self-hosted credential format"
            }
            iv = Base64.decode(parts[1], Base64.NO_WRAP)
            require(iv.size == GCM_IV_LENGTH_BYTES) {
                "Invalid self-hosted credential IV"
            }
            ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
            require(ciphertext.isNotEmpty()) {
                "Invalid self-hosted credential ciphertext"
            }

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
            )
            plaintext = cipher.doFinal(ciphertext)
            decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(plaintext))
            val token = CharArray(decoded.remaining()).also(decoded::get)
            Result.success(token)
        } catch (exception: Exception) {
            Result.failure(exception)
        } finally {
            iv?.fill(0)
            ciphertext?.fill(0)
            plaintext?.fill(0)
            decoded?.clearSensitiveContents()
        }
    }

    /** 清除指定候选槽位的密文。 */
    internal fun clear(slot: String): Result<Unit> = runCatching {
        requireValidSlot(slot)
        check(preferences.edit().remove(preferenceKey(slot)).commit()) {
            "Unable to clear self-hosted credentials"
        }
    }

    /**
     * 安全阻止哨兵无法落盘时销毁 Keystore 主密钥。
     *
     * Android 10–11（API 29–30）的旧 Keystore 会把服务错误折叠为 `containsAlias=false`，
     * Android 12–15（API 31–35）的 Keystore2 也会把读取元数据时的异常折叠为相同结果，
     * 因此存在性查询不能证明主密钥已经缺失。
     * 这里无条件调用 [KeyStore.deleteEntry]：平台会把 alias 不存在视为幂等成功，并把其他删除错误
     * 作为异常抛出；只有删除调用正常返回，才证明 alias 已删除或原本不存在。删除成功后的密文清理
     * 仅为 best-effort，其返回值或异常都不能改变主密钥已失效的安全证明。
     *
     * 即使密文 SharedPreferences 清理失败，旧密文也会因密钥不存在而在下次启动无法解密；
     * 用户只能重新输入 Token 并完成完整验证。
     */
    internal fun invalidateAllCredentials(): Result<Unit> {
        val invalidationResult = runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
        }
        if (invalidationResult.isFailure) return invalidationResult

        // 密钥删除是安全线性化边界；密文清理只用于减少无效数据残留。
        runCatching { preferences.edit().clear().commit() }
        return Result.success(Unit)
    }

    /** 启动恢复时回收所有未被活动配置引用的候选凭据。 */
    internal fun clearInactiveSlots(activeSlot: String?) {
        preferences.all.keys
            .asSequence()
            .filter { it.startsWith(CREDENTIAL_KEY_PREFIX) }
            .map { it.removePrefix(CREDENTIAL_KEY_PREFIX) }
            .filter { it != DEFAULT_SLOT && it != activeSlot }
            .forEach { slot -> clear(slot) }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setKeySize(KEY_SIZE_BITS)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
            }
            .generateKey()
    }

    private fun requireValidSlot(slot: String) {
        require(SLOT_PATTERN.matches(slot)) { "Invalid self-hosted credential slot" }
    }

    private fun preferenceKey(slot: String): String = "$CREDENTIAL_KEY_PREFIX$slot"

    private fun ByteBuffer.clearSensitiveContents() {
        if (hasArray()) {
            array().fill(0)
        }
    }

    private fun CharBuffer.clearSensitiveContents() {
        if (hasArray()) {
            array().fill('\u0000')
        }
    }

    private companion object {
        const val CREDENTIAL_PREFERENCES_NAME = "self_hosted_credentials"
        const val KEY_ALIAS = "vip.mystery0.pixel.telo.self_hosted_credentials"
        const val DEFAULT_SLOT = "default"
        const val CREDENTIAL_KEY_PREFIX = "credential_"
        const val CIPHERTEXT_VERSION = "v1"
        const val SEPARATOR = "|"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_SIZE_BITS = 256
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        val SLOT_PATTERN = Regex("[a-zA-Z0-9_-]{1,80}")
    }
}
