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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceViewModel(
    private val vitalDeviceManager: VitalDeviceManager,
    private val deviceId: String
) :
    ViewModel() {
    private val viewModelState =
        MutableStateFlow(
            BluetoothViewModelState(
                device = devices().first { it.id == deviceId },
                scannedDevices = emptyList(),
                samples = emptyList(),
                bloodPressureSamples = emptyList(),
                isConnected = false
            )
        )
    val uiState = viewModelState.asStateFlow()

    fun scan(context: Context) {
        viewModelScope.launch {
            vitalDeviceManager.search(uiState.value.device).catch {
                Log.i("DeviceViewModel", "Error scanning ${it.message}", it.cause)
                Toast.makeText(context, "Error scanning ${it.message}", Toast.LENGTH_SHORT).show()
            }
                .collect { scannedDevice ->
                    viewModelState.update { it.copy(scannedDevices = it.scannedDevices.toMutableList() + scannedDevice) }

                    vitalDeviceManager.monitorConnection(scannedDevice).collect { connectionState ->
                        viewModelState.update { it.copy(isConnected = connectionState) }
                    }
                }
        }
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

    override fun onCleared() {
        vitalDeviceManager.stopSearch()
        super.onCleared()
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
    val isConnected: Boolean,
)