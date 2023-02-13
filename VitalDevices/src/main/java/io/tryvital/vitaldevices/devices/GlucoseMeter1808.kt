package io.tryvital.vitaldevices.devices

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import io.tryvital.client.services.data.QuantitySamplePayload
import io.tryvital.client.services.data.SampleType
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitaldevices.ScannedDevice
import io.tryvital.vitaldevices.chunked
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.common.callback.glucose.GlucoseMeasurementResponse
import no.nordicsemi.android.ble.common.data.RecordAccessControlPointData
import no.nordicsemi.android.ble.data.Data
import java.util.*

private val glsServiceUUID: UUID = UUID.fromString("00001808-0000-1000-8000-00805f9b34fb")

private val glucoseMeasurementCharacteristicUUID =
    UUID.fromString("00002A18-0000-1000-8000-00805f9b34fb")
private val recordAccessControlPointCharacteristicUUID =
    UUID.fromString("00002A52-0000-1000-8000-00805f9b34fb")

interface GlucoseMeter {
    fun pair(): Flow<Boolean>
    fun read(): Flow<List<QuantitySamplePayload>>
}

class GlucoseMeter1808(
    context: Context,
    private val scannedBluetoothDevice: BluetoothDevice,
    private val scannedDevice: ScannedDevice,
) : BleManager(context), GlucoseMeter {
    private val vitalLogger = VitalLogger.getOrCreate()

    // Keep all incoming measurements in memory until we get to process them.
    // Note that replay buffer is zero sized, so no value is kept in memory in absence of subscribers.
    private val measurements = MutableSharedFlow<QuantitySamplePayload>(replay = 0, extraBufferCapacity = Int.MAX_VALUE)

    private var glucoseMeasurementCharacteristic: BluetoothGattCharacteristic? = null
    private var recordAccessControlPointCharacteristic: BluetoothGattCharacteristic? = null

    override fun pair() = callbackFlow {
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


    override fun read(): Flow<List<QuantitySamplePayload>> {
        writeCharacteristic(
            recordAccessControlPointCharacteristic,
            RecordAccessControlPointData.reportAllStoredRecords(),
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        )
            .fail { _, status -> logError("writeCharacteristic", status) }
            .done { vitalLogger.logI("Successfully requested all data from ${scannedDevice.name}") }
            .enqueue()

        return measurements.chunked(50, 300)
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
            val service = gatt.getService(glsServiceUUID)

            glucoseMeasurementCharacteristic =
                service.getCharacteristic(glucoseMeasurementCharacteristicUUID)
            recordAccessControlPointCharacteristic = service.getCharacteristic(
                recordAccessControlPointCharacteristicUUID
            )

            return glucoseMeasurementCharacteristic != null && recordAccessControlPointCharacteristic != null
        }

        override fun initialize() {
            setNotificationCallback(glucoseMeasurementCharacteristic).with { device: BluetoothDevice, data: Data ->
                val success = measurements.tryEmit(mapRawData(device, data))

                if (!success) {
                    vitalLogger.logI("Failed to emit GlucoseMeter1080 measurement despite unlimited flow buffer size.")
                }
            }

            enableNotifications(glucoseMeasurementCharacteristic).enqueue()
            enableIndications(recordAccessControlPointCharacteristic)
                .fail { _, status: Int ->
                    vitalLogger.logI("Failed to enabled RAC indications (error $status)")
                }
                .enqueue()
        }

        override fun onServicesInvalidated() {
            recordAccessControlPointCharacteristic = null
            glucoseMeasurementCharacteristic = null
        }
    }

    private fun mapRawData(
        device: BluetoothDevice, data: Data
    ): QuantitySamplePayload {
        val response = GlucoseMeasurementResponse().apply {
            onDataReceived(device, data)
        }

        val measurementTime = response.time?.time ?: Date(0)
        return QuantitySamplePayload(
            id = "glucose-${measurementTime.time}",
            value = (response.glucoseConcentration?.times(100000)).toString(),
            unit = SampleType.GlucoseConcentration.unit,
            startDate = measurementTime,
            endDate = measurementTime,
            type = "fingerprick",
        )
    }

    private fun logError(context: String, status: Int) {
        vitalLogger.logI("Error in $context for ${scannedDevice.name} with status $status")
    }
}