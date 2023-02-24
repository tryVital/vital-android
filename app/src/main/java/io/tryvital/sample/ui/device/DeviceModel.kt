package io.tryvital.sample.ui.device

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.tryvital.client.services.data.QuantitySamplePayload
import io.tryvital.vitaldevices.*
import io.tryvital.vitaldevices.devices.BloodPressureSample
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceViewModel(
    private val vitalDeviceManager: VitalDeviceManager,
    private val deviceId: String,

    private val _toasts: MutableSharedFlow<String> = MutableSharedFlow(replay = 0)
) :
    ViewModel() {
    private val viewModelState =
        MutableStateFlow(
            BluetoothViewModelState(
                device = devices().first { it.id == deviceId },
                scannedDevices = emptyList(),
                samples = emptyList(),
                bloodPressureSamples = emptyList(),
                isScanning = false
            )
        )
    val uiState = viewModelState.asStateFlow()
    val toasts = _toasts.asSharedFlow()

    init {
        val deviceModel = viewModelState.value.device
        val bondedDevices = vitalDeviceManager.connected(deviceModel)
        viewModelState.update { it.copy(scannedDevices = bondedDevices) }

        viewModelState
            .map { it.isScanning }
            .flatMapLatest { isScanning ->
                when (isScanning) {
                    true -> vitalDeviceManager
                        .search(uiState.value.device)
                        .catch {
                            Log.i("DeviceViewModel", "Error scanning ${it.message}", it.cause)
                            _toasts.tryEmit("Error scanning ${it.message}")
                        }
                        .onEach { scannedDevice ->
                            viewModelState.update {
                                val devices = (it.scannedDevices.toMutableSet() + setOf(scannedDevice)).toList()
                                it.copy(scannedDevices = devices)
                            }
                        }
                    false -> emptyFlow()
                }
            }
            .launchIn(viewModelScope)
    }

    fun scan() {
        viewModelState.update { it.copy(isScanning = true) }
    }

    fun stopScanning() {
        viewModelState.update { it.copy(isScanning = false) }
    }

    fun connect(context: Context, scannedDevice: ScannedDevice) {
        viewModelScope.launch {
            when (uiState.value.device.kind) {
                Kind.BloodPressure -> vitalDeviceManager.bloodPressure(context, scannedDevice)
                    .collect { sample ->
                        viewModelState.update { it.copy(bloodPressureSamples = it.bloodPressureSamples + sample) }
                    }
                Kind.GlucoseMeter -> vitalDeviceManager.glucoseMeter(context, scannedDevice)
                    .collect { sample ->
                        viewModelState.update { it.copy(samples = it.samples + sample) }
                    }
            }
        }
    }


    fun pair(context: Context, scannedDevice: ScannedDevice) {
        viewModelScope.launch {
            vitalDeviceManager.pair(scannedDevice).collect {
                if (it) {
                    Toast.makeText(context, "Paired", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Pairing failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        fun provideFactory(
            vitalDeviceManager: VitalDeviceManager,
            deviceId: String
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DeviceViewModel(vitalDeviceManager, deviceId) as T
                }
            }
    }

}

data class BluetoothViewModelState(
    val device: DeviceModel,
    val scannedDevices: List<ScannedDevice>,
    val samples: List<QuantitySamplePayload>,
    val bloodPressureSamples: List<BloodPressureSample>,
    val isScanning: Boolean,
)