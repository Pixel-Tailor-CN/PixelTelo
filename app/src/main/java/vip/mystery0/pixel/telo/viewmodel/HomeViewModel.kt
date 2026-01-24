package vip.mystery0.pixel.telo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository
import vip.mystery0.pixel.telo.data.repository.SyncRepository

class HomeViewModel() : ViewModel(), KoinComponent {
    private val repository: BlockedCallRepository by inject()
    private val syncRepository: SyncRepository by inject()

    val blockedCalls: StateFlow<List<BlockedCall>> = repository.allBlockedCalls
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Companion.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isDatabaseReady: StateFlow<Boolean> = syncRepository.versionFlow
        .map { it.isNotBlank() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true // Assume ready initially to avoid flash, or false if safer
        )

    fun delete(blockedCall: BlockedCall) {
        viewModelScope.launch {
            repository.delete(blockedCall)
        }
    }
}