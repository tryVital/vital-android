package io.tryvital.vitaldevices.devices

import android.bluetooth.BluetoothDevice
import android.content.Context
import io.tryvital.client.services.data.LocalBloodPressureSample
import io.tryvital.client.services.data.LocalQuantitySample
import io.tryvital.client.services.data.SampleType
import io.tryvital.vitaldevices.ScannedDevice
import no.nordicsemi.android.ble.common.callback.bps.BloodPressureMeasurementResponse
import no.nordicsemi.android.ble.data.Data
import java.util.*

private val bpsServiceUUID: UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")

private val bloodPressureMeasurementCharacteristicUUID =
    UUID.fromString("00002A35-0000-1000-8000-00805f9b34fb")

interface BloodPressureReader {
    suspend fun pair()
    suspend fun read(): List<LocalBloodPressureSample>
}

class BloodPressureReader1810(
    context: Context,
    scannedBluetoothDevice: BluetoothDevice,
    scannedDevice: ScannedDevice,
) : GATTMeterWithNoRACP<LocalBloodPressureSample>(
    context,
    serviceID = bpsServiceUUID,
    measurementCharacteristicID = bloodPressureMeasurementCharacteristicUUID,
    scannedBluetoothDevice = scannedBluetoothDevice,
    scannedDevice = scannedDevice
), BloodPressureReader {
    override fun onReceivedAll(samples: List<LocalBloodPressureSample>) {
        postBloodPressureSamples(context, scannedDevice.deviceModel.brand.toManualProviderSlug(), samples)
    }

    override fun mapRawData(
        device: BluetoothDevice, data: Data
    ): LocalBloodPressureSample? {
        val response = BloodPressureMeasurementResponse().apply {
            onDataReceived(device, data)
        }

        // We accept only stored Blood Pressure records with a timestamp (as suggested by BLE Blood
        // Pressure Service specification v1.1.1).
        val measurementTime = response.timestamp?.time?.toInstant() ?: return null
        val idPrefix = "${measurementTime.epochSecond}-"

        return LocalBloodPressureSample(
            systolic = LocalQuantitySample(
                id = idPrefix + "systolic",
                value = response.systolic.toDouble(),
                unit = SampleType.BloodPressureSystolic.unit,
                startDate = measurementTime,
                endDate = measurementTime,
                type = "cuff",
            ),
            diastolic = LocalQuantitySample(
                id = idPrefix + "diastolic",
                value = response.diastolic.toDouble(),
                unit = SampleType.BloodPressureDiastolic.unit,
                startDate = measurementTime,
                endDate = measurementTime,
                type = "cuff",
            ),
            pulse = response.pulseRate?.let { value ->
                LocalQuantitySample(
                    id = idPrefix + "pulse",
                    value = value.toDouble(),
                    unit = SampleType.HeartRate.unit,
                    startDate = measurementTime,
                    endDate = measurementTime,
                    type = "cuff",
                )
            }
        )
    }
}