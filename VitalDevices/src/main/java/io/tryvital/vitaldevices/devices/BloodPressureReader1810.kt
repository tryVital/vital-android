package io.tryvital.vitaldevices.devices

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import io.tryvital.client.services.data.QuantitySample
import io.tryvital.client.services.data.SampleType
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitaldevices.ScannedDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.common.callback.bps.BloodPressureMeasurementResponse
import no.nordicsemi.android.ble.data.Data
import java.util.*

private val bpsServiceUUID: UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")

private val bloodPressureMeasurementCharacteristicUUID =
    UUID.fromString("00002A35-0000-1000-8000-00805f9b34fb")

interface BloodPressureReader {
    fun connect()
    fun read(): Flow<BloodPressureSample>
}

class BloodPressureReader1810(
    context: Context,
    private val scannedBluetoothDevice: BluetoothDevice,
    private val scannedDevice: ScannedDevice,
) : BleManager(context), BloodPressureReader {
    private val vitalLogger = VitalLogger.create()
    private val measurements = MutableStateFlow<BloodPressureSample?>(null)

    private var bloodPressureMeasurementCharacteristic: BluetoothGattCharacteristic? = null

    override fun connect() {
        connect(scannedBluetoothDevice).retry(3, 100).timeout(15000).useAutoConnect(false)
            .fail { _, status -> logError("connect", status) }.done {
                vitalLogger.logI("Successfully connected to ${scannedDevice.name}")
                vitalLogger.logI("Bonded state: $isBonded to ${scannedDevice.name}")
                if (!isBonded) {
                    bond()
                }
            }.enqueue()
    }

    override fun read() = measurements.filterNotNull()

    @SuppressLint("MissingPermission")
    private fun bond() {
        ensureBond().fail { _, status -> logError("bond", status) }
            .done { vitalLogger.logI("Bonded with ${scannedDevice.name}") }.enqueue()
    }

    override fun getGattCallback(): BleManagerGattCallback {
        return GattCallbackImpl()
    }

    private inner class GattCallbackImpl :
        BleManagerGattCallback() {

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val service = gatt.getService(bpsServiceUUID)

            bloodPressureMeasurementCharacteristic =
                service.getCharacteristic(bloodPressureMeasurementCharacteristicUUID)

            bloodPressureMeasurementCharacteristic
            return bloodPressureMeasurementCharacteristic != null
        }

        override fun initialize() {
            setIndicationCallback(bloodPressureMeasurementCharacteristic).with { device: BluetoothDevice, data: Data ->
                measurements.value = mapRawData(device, data)
            }

            enableIndications(bloodPressureMeasurementCharacteristic).enqueue()
        }

        override fun onServicesInvalidated() {
            bloodPressureMeasurementCharacteristic = null
        }
    }

    private fun mapRawData(
        device: BluetoothDevice, data: Data
    ): BloodPressureSample {
        val response = BloodPressureMeasurementResponse().apply {
            onDataReceived(device, data)
        }

        val measurementTime = response.timestamp?.time ?: Date(0)
        return BloodPressureSample(
            systolic = QuantitySample(
                id = "systolic-${measurementTime.time}",
                value = response.systolic.toString(),
                unit = SampleType.BloodPressureSystolic.unit,
                startDate = measurementTime,
                endDate = measurementTime,
            ),
            diastolic = QuantitySample(
                id = "diastolic-${measurementTime.time}",
                value = response.diastolic.toString(),
                unit = SampleType.BloodPressureDiastolic.unit,
                startDate = measurementTime,
                endDate = measurementTime,
            ),
            pulse = QuantitySample(
                id = "pulseRate-${measurementTime.time}",
                value = response.pulseRate.toString(),
                unit = SampleType.HeartRate.unit,
                startDate = measurementTime,
                endDate = measurementTime,
            )
        )
    }

    private fun logError(context: String, status: Int) {
        vitalLogger.logI("Error in $context for ${scannedDevice.name} with status $status")
    }
}