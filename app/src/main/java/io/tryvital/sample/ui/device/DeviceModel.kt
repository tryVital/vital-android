package io.tryvital.sample.ui.device

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.tryvital.client.services.data.LocalBloodPressureSample
import io.tryvital.client.services.data.LocalQuantitySample
import io.tryvital.vitaldevices.*
import io.tryvital.vitaldevices.devices.Libre1Reader
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
                isScanning = false,
                canScan = true,
                isReading = false,
            )
        )
    val uiState = viewModelState.asStateFlow()
    val toasts = _toasts.asSharedFlow()

    init {
        val deviceModel = viewModelState.value.device

        viewModelState.update {
            val scannedDevices = when (deviceModel.brand) {
                Brand.Libre -> listOf(
                    ScannedDevice(address = "", name = "Libre1", deviceModel = deviceModel)
                )
                else -> {
                    try {
                        vitalDeviceManager.connected(deviceModel)
                    } catch (e: PermissionMissing) {
                        emptyList()
                    }
                }
            }

            it.copy(
                scannedDevices = scannedDevices,
                canScan = deviceModel.brand != Brand.Libre,
            )
        }

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
        viewModelState.update { it.copy(isScanning = it.canScan) }
    }

    fun stopScanning() {
        viewModelState.update { it.copy(isScanning = false) }
    }

    fun cancelRead() {
        viewModelState.update { it.copy(isReading = false) }
    }

    fun connect(context: Context, activity: Activity, scannedDevice: ScannedDevice) {
        viewModelScope.launch {
            viewModelState.update { it.copy(isReading = true) }

            // Cancel the scope if isReading transitions back to false before the reading ends.
            viewModelState
                .asStateFlow()
                .filterNot { it.isReading }
                .take(1)
                .onEach { this.cancel() }
                .launchIn(this)

            try {
                when (uiState.value.device.kind) {
                    Kind.BloodPressure -> vitalDeviceManager.bloodPressure(context, scannedDevice)
                        .read()
                        .let { sample ->
                            viewModelState.update { it.copy(bloodPressureSamples = it.bloodPressureSamples + sample) }
                        }
                    Kind.GlucoseMeter -> when (uiState.value.device.brand) {
                        Brand.Libre -> Libre1Reader.create(activity)
                            .read()
                            .let { result ->
                                viewModelState.update { it.copy(samples = it.samples + result.samples) }
                            }
                        else -> vitalDeviceManager.glucoseMeter(context, scannedDevice)
                            .read()
                            .let { sample ->
                                viewModelState.update { it.copy(samples = it.samples + sample) }
                            }
                    }
                }
            } catch (e: Throwable) {
                Toast.makeText(context, "${e::class.simpleName}: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                viewModelState.update { it.copy(isReading = false) }
            }
        }
    }


    fun pair(context: Context, scannedDevice: ScannedDevice) {
        viewModelScope.launch {
            try {
                when (uiState.value.device.kind) {
                    Kind.BloodPressure -> vitalDeviceManager
                        .bloodPressure(context, scannedDevice)
                        .pair()
                    Kind.GlucoseMeter -> when (uiState.value.device.brand) {
                        Brand.Libre -> {}
                        else -> vitalDeviceManager
                            .glucoseMeter(context, scannedDevice)
                            .pair()
                    }
                }

                Toast.makeText(context, "Paired", Toast.LENGTH_SHORT).show()
            } catch (e: BluetoothError) {
                Toast.makeText(context, "Pairing failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
    val samples: List<LocalQuantitySample>,
    val bloodPressureSamples: List<LocalBloodPressureSample>,
    val canScan: Boolean,
    val isScanning: Boolean,
    val isReading: Boolean,
)