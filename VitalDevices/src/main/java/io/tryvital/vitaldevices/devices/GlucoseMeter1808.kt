package io.tryvital.vitaldevices.devices

import android.bluetooth.BluetoothDevice
import android.content.Context
import io.tryvital.client.services.data.QuantitySamplePayload
import io.tryvital.client.services.data.SampleType
import io.tryvital.vitaldevices.ScannedDevice
import kotlinx.coroutines.flow.*
import no.nordicsemi.android.ble.common.callback.glucose.GlucoseMeasurementResponse
import no.nordicsemi.android.ble.common.profile.glucose.GlucoseMeasurementCallback.UNIT_kg_L
import no.nordicsemi.android.ble.common.profile.glucose.GlucoseMeasurementCallback.UNIT_mol_L
import no.nordicsemi.android.ble.data.Data
import java.util.*

private val glsServiceUUID: UUID = UUID.fromString("00001808-0000-1000-8000-00805f9b34fb")

private val glucoseMeasurementCharacteristicUUID =
    UUID.fromString("00002A18-0000-1000-8000-00805f9b34fb")

interface GlucoseMeter {
    suspend fun pair()
    suspend fun read(): List<QuantitySamplePayload>
}

class GlucoseMeter1808(
    context: Context,
    scannedBluetoothDevice: BluetoothDevice,
    scannedDevice: ScannedDevice,
) : GATTMeter<QuantitySamplePayload>(
    context,
    serviceID = glsServiceUUID,
    measurementCharacteristicID = glucoseMeasurementCharacteristicUUID,
    scannedBluetoothDevice = scannedBluetoothDevice,
    scannedDevice = scannedDevice
), GlucoseMeter {
    override fun mapRawData(
        device: BluetoothDevice, data: Data
    ): QuantitySamplePayload? {
        val response = GlucoseMeasurementResponse().apply {
            onDataReceived(device, data)
        }

        // A compliant record should have a value and a timestamp.
        val glucoseConcentration = response.glucoseConcentration ?: return null
        val measurementTime = response.time?.time ?: return null

        val (value, sampleType) = when (response.unit) {
            UNIT_mol_L ->
                Pair(glucoseConcentration.toDouble() * 1000, SampleType.GlucoseConcentrationMillimolePerLitre)
            UNIT_kg_L ->
                Pair(glucoseConcentration.toDouble() * 100000, SampleType.GlucoseConcentrationMilligramPerDecilitre)
            else -> throw IllegalStateException("Glucose monitor reports unexpected unit: ${response.unit}")
        }

        return QuantitySamplePayload(
            // Prefixed with epoch in seconds to avoid sequence number conflicts
            // (due to new device and/or device reset)
            id = "${measurementTime.time / 1000}-${response.sequenceNumber}",
            value = value,
            unit = sampleType.unit,
            startDate = measurementTime,
            endDate = measurementTime,
            type = "fingerprick",
        )
    }
}