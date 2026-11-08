package vip.mystery0.pixel.telo.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import vip.mystery0.pixel.telo.data.source.SupportConfigParser
import vip.mystery0.pixel.telo.data.model.DeveloperSupportConfig
import vip.mystery0.pixel.telo.data.model.SupportChannel
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DeveloperSupportRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val parser = SupportConfigParser()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun loadConfig(): DeveloperSupportConfig = withContext(Dispatchers.IO) {
        val bytes = download("https://pixel.mystery0.app/support/config.json", 64 * 1024)
        parser.parse(bytes.toString(Charsets.UTF_8))
    }

    suspend fun loadQrCode(channel: SupportChannel): ByteArray = withContext(Dispatchers.IO) {
        if (!SupportConfigParser.isTrustedQrUrl(channel.qrCodeUrl)) throw IOException("untrusted support image")
        download(channel.qrCodeUrl, 4 * 1024 * 1024).also(::validateImage)
    }

    suspend fun saveQrCode(channel: SupportChannel, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val mime = validateImage(bytes)
        val extension = if (mime == "image/png") "png" else "jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "pixel-telo-${channel.id.value}-${System.currentTimeMillis()}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PixelTelo")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("cannot create support image")
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(bytes)
            } ?: throw IOException("cannot write support image")
            coroutineContext.ensureActive()
            if (resolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null) != 1) throw IOException("cannot publish support image")
        } catch (e: Exception) {
            // 保存失败或取消时清理未完成的相册项，清理失败不能覆盖原异常。
            try { resolver.delete(uri, null, null) } catch (_: Exception) { }
            throw e
        }
    }

    private fun validateImage(bytes: ByteArray): String {
        if (bytes.isEmpty() || bytes.size > 4 * 1024 * 1024) throw IOException("invalid support image size")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096 ||
            bounds.outWidth.toLong() * bounds.outHeight > 4_000_000 ||
            bounds.outMimeType !in setOf("image/png", "image/jpeg")) {
            throw IOException("invalid support image")
        }
        return bounds.outMimeType
    }

    private suspend fun download(url: String, maxBytes: Int): ByteArray = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use {
                        if (!it.isSuccessful) throw IOException("support request failed status=${it.code}")
                        val body = it.body
                        if (body.contentLength() > maxBytes) throw IOException("support response too large")
                        body.byteStream().use { stream ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                if (call.isCanceled()) throw IOException("support request cancelled")
                                val count = stream.read(buffer)
                                if (count == -1) break
                                if (output.size() + count > maxBytes) throw IOException("support response too large")
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                    }
                    if (continuation.isActive) continuation.resume(bytes)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
}
