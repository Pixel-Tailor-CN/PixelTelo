package vip.mystery0.pixel.telo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.repository.BlockedCallRepository

class HomeViewModel() : ViewModel(), KoinComponent {
    private val repository: BlockedCallRepository by inject()
    val blockedCalls: StateFlow<List<BlockedCall>> = repository.allBlockedCalls
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addDummyData() {
        viewModelScope.launch {
            repository.insert(
                phoneNumber = "10086",
                remark = "Spam/Harassment"
            )
            repository.insert(
                phoneNumber = "12345678901",
                remark = "Real Estate Agent"
            )
        }
    }
}
