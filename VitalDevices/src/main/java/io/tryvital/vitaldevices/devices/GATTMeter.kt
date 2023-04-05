package io.tryvital.vitaldevices.devices

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import io.tryvital.client.services.data.QuantitySamplePayload
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitaldevices.BluetoothError
import io.tryvital.vitaldevices.NoMoreSamplesException
import io.tryvital.vitaldevices.ScannedDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.common.callback.RecordAccessControlPointResponse
import no.nordicsemi.android.ble.common.data.RecordAccessControlPointData
import no.nordicsemi.android.ble.data.Data
import java.util.*
import kotlin.time.Duration

// Standard Record Access Control Point characteristic as per BLE GATT
private val racpCharacteristicUUID =
    UUID.fromString("00002A52-0000-1000-8000-00805f9b34fb")

abstract class GATTMeter<Sample>(
    context: Context,
    private val serviceID: UUID,
    private val measurementCharacteristicID: UUID,
    private val scannedBluetoothDevice: BluetoothDevice,
    private val scannedDevice: ScannedDevice,
) : BleManager(context) {
    private val vitalLogger = VitalLogger.getOrCreate()

    // Keep all incoming measurements in memory until we get to process them.
    // Note that replay buffer is zero sized, so no value is kept in memory in absence of subscribers.
    private val measurements = MutableSharedFlow<Sample>(replay = 0, extraBufferCapacity = Int.MAX_VALUE)

    // Keep all incoming response in memory until we get to process them.
    // Note that replay buffer is zero sized, so no value is kept in memory in absence of subscribers.
    private val racpResponse = MutableSharedFlow<RecordAccessControlPointResponse>(replay = 0, extraBufferCapacity = Int.MAX_VALUE)

    private var measurementCharacteristic: BluetoothGattCharacteristic? = null
    private var racpCharacteristic: BluetoothGattCharacteristic? = null

    private var deviceReady = MutableStateFlow(false)

    abstract fun mapRawData(device: BluetoothDevice, data: Data): Sample?

    fun pair() = callbackFlow {
        // It has an active connection. Don't bother to connect again.
        if (isConnected) {
            send(true)
            return@callbackFlow
        }

        var inError = false
        connect(scannedBluetoothDevice).retry(3, 100).timeout(15000).useAutoConnect(false)
            .fail { _, status ->
                logError("connect", status)
                trySend(false)
                inError = true
                close()
            }.done {
                if (inError) return@done
                vitalLogger.logI("Successfully connected to ${scannedDevice.name}")
                vitalLogger.logI("Bonded state: $isBonded to ${scannedDevice.name}")
                if (!isBonded) {
                    bond()
                }
                trySend(true)
                close()
            }.enqueue()

        awaitClose { }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun read(): Flow<List<Sample>> = channelFlow {
        withTimeout(10 * 1000) {
            // Wait for deviceReady to be `true`, or timeout the flow after 10 seconds.
            deviceReady.filter { it }.first()
        }

        // (1) Start a new parallel job that listens to measurements and collect them into a list.
        val sampleCollector = launch {
            val samples = mutableListOf<Sample>()

            // `toCollection()` here does not returns normally. Either:
            // (1) `NoMoreSamplesException` is thrown by us upon receipt of a RACP success
            //      response code, and no more samples is expected to be delivered; OR
            // (2) other exceptions are thrown.
            measurements
                .onCompletion {e ->
                    if (e is NoMoreSamplesException) {
                        assert(!this@channelFlow.isClosedForSend)

                        // Send out the samples, and close the channel normally.
                        this@channelFlow.send(samples.toList())
                        close()
                    }
                }
                .toCollection(samples)
        }

        // (2) Start a parallel job that listens to RACP response indication.
        launch {
            val response = racpResponse.first()

            if (response.isOperationCompleted) {
                // Cancel the sample collector with `NoMoreSamplesException`, so that it stops
                // collecting samples & sends out what it has.
                sampleCollector.cancel(cause = NoMoreSamplesException)
            } else {
                // RACP operation has failed. Throw to error the whole flow.
                throw BluetoothError("Received RACP failure response with code ${response.errorCode}")
            }
        }

        // (3) Write to the RACP to initiate the Load All Records operation.
        writeCharacteristic(
            racpCharacteristic,
            RecordAccessControlPointData.reportAllStoredRecords(),
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        )
            .fail { _, status ->
                logError("writeCharacteristic", status)
                this.close(BluetoothError("Failed to write to the RACP characteristic."))
            }
            .done { vitalLogger.logI("Successfully initiated the read operation via an RACP write.") }
            .enqueue()
    }

    @SuppressLint("MissingPermission")
    private fun bond() {
        ensureBond().fail { _, status -> logError("bond", status) }
            .done { vitalLogger.logI("Bonded with ${scannedDevice.name}") }.enqueue()
    }

    override fun getGattCallback(): BleManagerGattCallback {
        return GattCallbackImpl()
    }

    private inner class GattCallbackImpl : BleManagerGattCallback() {

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val service = gatt.getService(serviceID)

            measurementCharacteristic = service.getCharacteristic(measurementCharacteristicID)
            racpCharacteristic = service.getCharacteristic(racpCharacteristicUUID)

            return measurementCharacteristic != null && racpCharacteristic != null
        }

        override fun initialize() {
            setNotificationCallback(measurementCharacteristic).with { device: BluetoothDevice, data: Data ->
                val sample = mapRawData(device, data) ?: return@with
                val success = measurements.tryEmit(sample)

                if (!success) {
                    vitalLogger.logI("Failed to emit measurement despite unlimited flow buffer size.")
                }
            }
            setNotificationCallback(racpCharacteristic).with { device: BluetoothDevice, data: Data ->
                val response = RecordAccessControlPointResponse().apply {
                    onDataReceived(device, data)
                }
                val success = racpResponse.tryEmit(response)

                if (!success) {
                    vitalLogger.logI("Failed to emit RACP response despite unlimited flow buffer size.")
                }
            }

            enableNotifications(measurementCharacteristic)
                .fail { _, status: Int ->
                    vitalLogger.logI("Failed to enabled measurement notifications (error $status)")
                }
                .enqueue()
            enableIndications(racpCharacteristic)
                .fail { _, status: Int ->
                    vitalLogger.logI("Failed to enabled RACP indications (error $status)")
                }
                .enqueue()
        }

        override fun onDeviceReady() {
            deviceReady.value = true
        }

        override fun onServicesInvalidated() {
            racpCharacteristic = null
            measurementCharacteristic = null
            deviceReady.value = false
        }
    }

    private fun logError(context: String, status: Int) {
        vitalLogger.logI("Error in $context for ${scannedDevice.name} with status $status")
    }
}