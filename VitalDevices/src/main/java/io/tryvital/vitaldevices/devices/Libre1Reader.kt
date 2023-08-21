package io.tryvital.vitaldevices.devices

import android.content.Context
import io.tryvital.client.services.data.QuantitySamplePayload

enum class Libre1SensorState {
    Unknown,
    NotActivated,
    WarmingUp,
    Active,
    Expired,
    Shutdown,
    Failure;
}

data class Libre1Sensor(
    val serial: String,
    val maxLife: Int,
    val age: Int,
    val state: Libre1SensorState
)

data class Libre1Read(
    val samples: List<QuantitySamplePayload>,
    val sensor: Libre1Sensor,
)

interface Libre1Reader {
    suspend fun read(): Libre1Read
}

internal class Libre1ReaderImpl(
    context: Context,
): Libre1Reader {
    override suspend fun read(): Libre1Read {
        TODO("Not yet implemented")
    }
}
