package vip.mystery0.pixel.telo.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.model.SupportChannel
import vip.mystery0.pixel.telo.data.model.SupportChannelId
import vip.mystery0.pixel.telo.data.repository.DeveloperSupportRepository

data class SupportQrState(
    val loading: Boolean = true,
    val bitmap: Bitmap? = null,
    val bytes: ByteArray? = null,
    val error: Boolean = false,
    val saving: Boolean = false,
)

data class DeveloperSupportUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val enabled: Boolean = true,
    val channels: List<SupportChannel> = emptyList(),
    val images: Map<SupportChannelId, SupportQrState> = emptyMap(),
)

class DeveloperSupportViewModel : ViewModel(), KoinComponent {
    private val repository: DeveloperSupportRepository by inject()
    private val _state = MutableStateFlow(DeveloperSupportUiState())
    val state = _state.asStateFlow()
    private val _message = MutableStateFlow<Int?>(null)
    val message = _message.asStateFlow()
    private var loadJob: Job? = null
    private var generation = 0

    init { reload() }

    fun reload() {
        if (loadJob?.isActive == true) return
        val requestGeneration = ++generation
        loadJob = viewModelScope.launch {
            _state.value = DeveloperSupportUiState()
            try {
                val config = repository.loadConfig()
                _state.value = DeveloperSupportUiState(
                    loading = false, enabled = config.enabled, channels = config.channels,
                    images = config.channels.associate { it.id to SupportQrState() },
                )
                coroutineScope {
                    config.channels.forEach { channel -> launch { loadImage(channel, requestGeneration) } }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = DeveloperSupportUiState(loading = false, error = true)
            }
        }
    }

    fun retryImage(channel: SupportChannel) {
        if (_state.value.images[channel.id]?.loading != false) return
        updateImage(channel.id) { SupportQrState() }
        val requestGeneration = generation
        viewModelScope.launch { loadImage(channel, requestGeneration) }
    }

    private suspend fun loadImage(channel: SupportChannel, requestGeneration: Int) {
        try {
            val bytes = repository.loadQrCode(channel)
            val bitmap = withContext(Dispatchers.IO) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw IOException("cannot decode support image")
            }
            // 配置重载后，旧渠道图片或重试结果不得覆盖新配置。
            if (requestGeneration == generation) {
                updateImage(channel.id) { SupportQrState(loading = false, bitmap = bitmap, bytes = bytes) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (requestGeneration == generation) {
                updateImage(channel.id) { SupportQrState(loading = false, error = true) }
            }
        }
    }

    fun saveImage(channel: SupportChannel) {
        val image = _state.value.images[channel.id] ?: return
        val bytes = image.bytes ?: return
        if (image.saving) return
        updateImage(channel.id) { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                repository.saveQrCode(channel, bytes)
                _message.value = R.string.support_saved
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _message.value = R.string.support_save_failed
            } finally {
                updateImage(channel.id) { it.copy(saving = false) }
            }
        }
    }

    fun showMessage(message: Int) { _message.value = message }
    fun consumeMessage() { _message.value = null }

    private fun updateImage(id: SupportChannelId, transform: (SupportQrState) -> SupportQrState) {
        _state.update { current ->
            val image = current.images[id] ?: return@update current
            current.copy(images = current.images + (id to transform(image)))
        }
    }
}
