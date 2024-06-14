package io.tryvital.vitaldevices.devices

import android.app.Activity
import android.content.pm.PackageManager.PERMISSION_GRANTED
import io.tryvital.client.services.data.ManualProviderSlug
import io.tryvital.client.services.data.LocalQuantitySample
import io.tryvital.client.services.data.SourceType
import io.tryvital.vitaldevices.PermissionMissing
import io.tryvital.vitaldevices.devices.nfc.Glucose
import io.tryvital.vitaldevices.devices.nfc.NFC
import io.tryvital.vitaldevices.devices.nfc.Sensor
import io.tryvital.vitaldevices.devices.nfc.SensorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

enum class Libre1SensorState {
    Unknown,
    NotActivated,
    WarmingUp,
    Active,
    Expired,
    Shutdown,
    Failure;

    companion object {
        internal fun fromSensorState(state: SensorState): Libre1SensorState = when (state) {
            SensorState.unknown -> Unknown
            SensorState.notActivated -> NotActivated
            SensorState.warmingUp -> WarmingUp
            SensorState.active -> Active
            SensorState.expired -> Expired
            SensorState.shutdown -> Shutdown
            SensorState.failure -> Failure
        }
    }
}

data class Libre1Sensor(
    val serial: String,
    val maxLife: Int,
    val age: Int,
    val state: Libre1SensorState,
) {
    companion object {
        internal fun fromSensor(sensor: Sensor) = Libre1Sensor(
            serial = sensor.serial,
            maxLife = sensor.maxLife,
            age = sensor.age.toInt(),
            state = Libre1SensorState.fromSensorState(sensor.state),
        )
    }
}

data class Libre1Read(
    val samples: List<LocalQuantitySample>,
    val sensor: Libre1Sensor,
)

interface Libre1Reader {
    suspend fun read(): Libre1Read

    companion object {
        fun create(activity: Activity): Libre1Reader = Libre1ReaderImpl(activity)
    }
}

internal class Libre1ReaderImpl(private val activity: Activity): Libre1Reader {
    override suspend fun read(): Libre1Read {
        if (activity.checkCallingOrSelfPermission(android.Manifest.permission.NFC) != PERMISSION_GRANTED) {
            throw PermissionMissing(android.Manifest.permission.NFC)
        }

        var nfc: NFC? = null

        val (sensor, glucose) = suspendCancellableCoroutine { continuation ->
            CoroutineScope(Dispatchers.IO).launch {
                nfc = NFC(continuation).also { it.startSession(activity) }
            }

            continuation.invokeOnCancellation { nfc?.close() }
        }

        val samples = glucose.map { quantitySampleFromGlucose(it) }
        postGlucoseSamples(activity.applicationContext, ManualProviderSlug.LibreBLE, samples)

        return Libre1Read(
            samples = glucose.map { quantitySampleFromGlucose(it) },
            sensor = Libre1Sensor.fromSensor(sensor),
        )
    }
}

private fun quantitySampleFromGlucose(glucose: Glucose): LocalQuantitySample {
    return LocalQuantitySample(
        id = glucose.id.toString(),
        value = glucose.valueUnit,
        startDate = glucose.date,
        endDate = glucose.date,
        type = SourceType.ManualScan,
        unit = "mmol/L",
    )
}