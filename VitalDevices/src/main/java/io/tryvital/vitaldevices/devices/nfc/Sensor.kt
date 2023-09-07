@file:OptIn(ExperimentalUnsignedTypes::class)

package io.tryvital.vitaldevices.devices.nfc

import java.time.Instant


enum class SensorType {
    libre1      ,
    libreUS14day,
    libreProH   ,
    libre2      ,
    libre2US    ,
    libre2CA    ,
    libreSense  ,
    libre3      ,
    unknown     ;

    override fun toString(): String = when (this) {
        libre1       -> "Libre 1"
        libreUS14day -> "Libre US 14d"
        libreProH    -> "Libre Pro/H"
        libre2       -> "Libre 2"
        libre2US     -> "Libre 2 US"
        libre2CA     -> "Libre 2 CA"
        libreSense   -> "Libre Sense"
        libre3       -> "Libre 3"
        unknown      -> "Libre"
    }

    companion object {
        fun parseFromPatchInfo(patchInfo: UByteArray): SensorType = when (patchInfo[0].toUInt()) {
            0xDFu -> libre1
            0xA2u -> libre1
            0xE5u -> libreUS14day
            0x70u -> libreProH
            0x9Du -> libre2
            0x76u -> when {
                patchInfo[3].toUInt() == 0x02u -> libre2US
                patchInfo[3].toUInt() == 0x04u -> libre2CA
                (patchInfo[2].toUInt() shr 4) == 7u -> libreSense
                else -> unknown
            }
            else -> when {
                patchInfo.size > 6 -> libre3
                else -> unknown
            }
        }
    }
}

class Sensor(
    val patchInfo: UByteArray,
    val uid: UByteArray,
) {
    var lastReadingDate: Instant? = null
    var fram: UByteArray? = null

    var factoryTrend: List<Glucose> = emptyList()
    var factoryHistory: List<Glucose> = emptyList()
}