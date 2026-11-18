package com.example.vpn

import com.example.data.model.ActiveNetworkType
import com.example.data.model.ServerConfig
import com.example.data.model.VpnStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VpnRuntimeState(
    val status: VpnStatus = VpnStatus.DISCONNECTED,
    val activeServer: ServerConfig? = null,
    val elapsedSeconds: Long = 0L,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val rxSpeedBytesPerSec: Long = 0L,
    val txSpeedBytesPerSec: Long = 0L,
    val activeNetwork: ActiveNetworkType = ActiveNetworkType.OTHER,
    val xrayVersion: String = "26.6.27",
    val lastError: String? = null,
    val sleepPausedServerId: String? = null,
    val autoConnectedOnMobile: Boolean = false
)

object VpnStateHolder {
    private val _state = MutableStateFlow(VpnRuntimeState())
    val state: StateFlow<VpnRuntimeState> = _state.asStateFlow()

    fun update(transform: (VpnRuntimeState) -> VpnRuntimeState) {
        _state.value = transform(_state.value)
    }

    fun clearError() {
        _state.value = _state.value.copy(lastError = null)
    }
}
