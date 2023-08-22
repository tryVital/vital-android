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

enum class SensorState(val rawValue: UByte) {
    unknown     (0x00u),

    notActivated(0x01u),
    warmingUp   (0x02u),    // 60 minutes
    active      (0x03u),    // ≈ 14.5 days
    expired     (0x04u),    // 12 hours more; Libre 2: Bluetooth shutdown
    shutdown    (0x05u),    // 15th day onwards
    failure     (0x06u);

    companion object {
        fun valueOf(rawValue: UByte) = values().first { it.rawValue == rawValue }
    }
}

enum class SensorRegion(val rawValue: UByte) {
    Unknown(0x00u),

    European(0x01u),
    USA(0x02u),
    AustralianCanadian(0x04u),
    EasternROW(0x08u);

    companion object {
        fun valueOf(rawValue: UByte) = values().first { it.rawValue == rawValue }
    }
}

data class CalibrationInfo(
    val i1: UInt = 0u,
    val i2: UInt = 0u,
    val i3: Int = 0,
    val i4: UInt = 0u,
    val i5: UInt = 0u,
    val i6: UInt = 0u,
)

class Sensor(
    val patchInfo: UByteArray,
    val uid: UByteArray,
) {
    var lastReadingDate: Instant = Instant.MIN
    var fram: UByteArray? = null

    var trend: List<Glucose> = emptyList()
    var history: List<Glucose> = emptyList()

    val factoryTrend: List<Glucose>
        get() = trend.map { factoryGlucose(it, calibrationInfo) }

    val factoryHistory: List<Glucose>
        get() = history.map { factoryGlucose(it, calibrationInfo) }

    var initializations: UByte = 0u
    var calibrationInfo = CalibrationInfo()
    var region: SensorRegion = SensorRegion.Unknown
    var maxLife: Int = 0

    fun setFRAM(fram: UByteArray) {
        this.fram = fram

        val state = SensorState.valueOf(fram[4])

        if (fram.size < 320) { return }

        val age = fram[316].toUInt() + fram[317].toUInt() shl 8    // body[-4]
        val startDate = lastReadingDate.minusSeconds((age * 60u).toLong())
        initializations = fram[318]

        val trend = mutableListOf<Glucose>().also { this.trend = it }
        val history = mutableListOf<Glucose>().also { this.history = it }
        val trendIndex = fram[26]      // body[2]
        val historyIndex = fram[27]    // body[3]

        for (i in 0u until 16u) {
            var j = trendIndex - 1u - i
            if (j < 0u) { j += 16u }
            val offset = 28u + j * 6u         // body[4 ..< 100]
            val rawValue = readBits(fram, offset, 0, 0xe)
            val quality = readBits(fram, offset, 0xe, 0xb).toUShort() and 0x1FFu
            val qualityFlags = (readBits(fram, offset, 0xe, 0xb) and 0x600u) shr 9
            val hasError = readBits(fram, offset, 0x19, 0x1) != 0u
            val rawTemperature = readBits(fram, offset, 0x1a, 0xc) shl 2
            var temperatureAdjustment = (readBits(fram, offset, 0x26, 0x9) shl 2).toInt()
            val negativeAdjustment = readBits(fram, offset, 0x2f, 0x1)
            if (negativeAdjustment != 0u) { temperatureAdjustment = -temperatureAdjustment }
            val id = age.toInt() - i.toInt()
            val date = startDate.plusSeconds(((age - i) * 60u).toLong())
            trend.add(
                Glucose(
                    rawValue = rawValue.toInt(),
                    rawTemperature = rawTemperature.toInt(),
                    temperatureAdjustment = temperatureAdjustment,
                    id = id,
                    date = date,
                    hasError = hasError,
                    dataQuality = DataQuality(quality),
                    dataQualityFlags = qualityFlags.toInt(),
                )
            )
        }

        // FRAM is updated with a 3 minutes delay:
        // https://github.com/UPetersen/LibreMonitor/blob/Swift4/LibreMonitor/Model/SensorData.swift

        val preciseHistoryIndex = ((age - 3u) / 15u) % 32u
        val delay = (age - 3u) % 15u + 3u
        var readingDate = lastReadingDate
        readingDate = if (preciseHistoryIndex == historyIndex.toUInt()) {
            readingDate.minusSeconds((60u * delay).toLong())
        } else {
            readingDate.plusSeconds((60u * (delay - 15u)).toLong())
        }

        for (i in 0u until 32u) {
            var j = historyIndex - 1u - i
            if (j < 0u) { j += 32u }
            val offset = 124u + j * 6u    // body[100 ..< 292]
            val rawValue = readBits(fram, offset, 0, 0xe)
            val quality = readBits(fram, offset, 0xe, 0xb).toUShort() and 0x1FFu
            val qualityFlags = (readBits(fram, offset, 0xe, 0xb) and 0x600u) shr 9
            val hasError = readBits(fram, offset, 0x19, 0x1) != 0u
            val rawTemperature = readBits(fram, offset, 0x1a, 0xc) shl 2
            var temperatureAdjustment = (readBits(fram, offset, 0x26, 0x9) shl 2).toInt()
            val negativeAdjustment = readBits(fram, offset, 0x2f, 0x1)
            if (negativeAdjustment != 0u) { temperatureAdjustment = -temperatureAdjustment }
            val id = age.toInt() - delay.toInt() - i.toInt() * 15
            val date = if (id > -1) readingDate.minusSeconds((i * 15u * 60u).toLong()) else startDate
            history.add(
                Glucose(
                    rawValue = rawValue.toInt(),
                    rawTemperature = rawTemperature.toInt(),
                    temperatureAdjustment = temperatureAdjustment,
                    id = id,
                    date = date,
                    hasError = hasError,
                    dataQuality = DataQuality(quality),
                    dataQualityFlags = qualityFlags.toInt(),
                )
            )
        }

        if (fram.size < 344) { return }

        // fram[322...323] (footer[2..3]) corresponds to patchInfo[2...3]
        region = runCatching { SensorRegion.valueOf(fram[323]) }.getOrNull() ?: SensorRegion.Unknown
        maxLife = fram[326].toInt() or (fram[327].toInt() shl 8)

        val i1 = readBits(fram, 2u, 0, 3)
        val i2 = readBits(fram, 2u, 3, 0xa)
        val i3 = readBits(fram, 0x150u, 0, 8)
        val i4 = readBits(fram, 0x150u, 8, 0xe)
        val negativei3 = readBits(fram, 0x150u, 0x21, 1) != 0u
        val i5 = readBits(fram, 0x150u, 0x28, 0xc) shl 2
        val i6 = readBits(fram, 0x150u, 0x34, 0xc) shl 2

        calibrationInfo = CalibrationInfo(i1, i2, if (negativei3) -(i3.toInt()) else i3.toInt(), i4, i5, i6)
    }
}

fun readBits(buffer: UByteArray, byteOffset: UInt, bitOffset: Int, bitCount: Int): UInt {
    if (bitCount == 0) {
        return 0u
    }

    var res = 0
    for (i in 0 until bitCount) {
        val totalBitOffset = byteOffset.toInt() * 8 + bitOffset + i
        val byteIndex = totalBitOffset / 8
        val bit = totalBitOffset % 8
        if (totalBitOffset >= 0 && ((buffer[byteIndex].toUInt() shr bit) and 0x1u) == 1u) {
            res = res or (1 shl i)
        }
    }
    return res.toUInt()
}
