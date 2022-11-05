package io.tryvital.sample.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.tryvital.vitaldevices.DeviceModel
import io.tryvital.vitaldevices.Kind
import io.tryvital.vitaldevices.devices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DevicesViewModel : ViewModel() {
    private val viewModelState = MutableStateFlow(
        DevicesViewModelState(
            glucoseDevices = devices()
                .filter { it.kind == Kind.GlucoseMeter },
            bloodPressureDevices = devices()
                .filter { it.kind == Kind.BloodPressure }
        )
    )
    val uiState = viewModelState.asStateFlow()

    companion object {
        fun provideFactory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DevicesViewModel() as T
                }
            }
    }
}

data class DevicesViewModelState(
    val glucoseDevices: List<DeviceModel>,
    val bloodPressureDevices: List<DeviceModel>,
)