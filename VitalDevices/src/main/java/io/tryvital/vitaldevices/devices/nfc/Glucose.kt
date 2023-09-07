package io.tryvital.vitaldevices.devices.nfc

import java.time.Instant

data class Glucose(
    val id: Int,
    val date: Instant,
    val rawValue: Int,
    val rawTemperature: Int,
    val temperatureAdjustment: Int,
    val hasError: Boolean,
    val dataQuality: DataQuality,
    val dataQualityFlags: Int,
    val value: Long = 0,
)

@JvmInline
value class DataQuality(private val rawValue: UShort) {
    companion object {
        val OK = DataQuality(0x0u)

        // lower 9 of 11 bits in the measurement field 0xe/0xb
        val SD14_FIFO_OVERFLOW  = DataQuality(0x0001u)
        /// delta between two successive of 4 filament measurements (1-2, 2-3, 3-4) > fram[332] (Libre 1: 90)
        /// indicates too much jitter in measurement
        val FILTER_DELTA        = DataQuality(0x0002u)
        val WORK_VOLTAGE        = DataQuality(0x0004u)
        val PEAK_DELTA_EXCEEDED = DataQuality(0x0008u)
        val AVG_DELTA_EXCEEDED  = DataQuality(0x0010u)
        /// NFC activity detected during a measurement which was retried since corrupted by NFC power usage
        val RF                  = DataQuality(0x0020u)
        val REF_R               = DataQuality(0x0040u)
        /// measurement result exceeds 0x3FFF (14 bits)
        val SIGNAL_SATURATED    = DataQuality(0x0080u)
        /// 4 times averaged raw reading < fram[330] (minimumThreshold: 150)
        val SENSOR_SIGNAL_LOW   = DataQuality(0x0100u)

        /// as an error code it actually indicates that one or more errors occurred in the
        /// last measurement cycle and is stored in the measurement bit 0x19/0x1 ("hasError")
        val THERMISTOR_OUT_OF_RANGE = DataQuality(0x0800u)

        val TEMP_HIGH           = DataQuality(0x2000u)
        val TEMP_LOW            = DataQuality(0x4000u)
        val INVALID_DATA        = DataQuality(0x8000u)
    }

    fun contains(other: DataQuality): Boolean {
        return (this.rawValue and other.rawValue) != 0u.toUShort()
    }

    override fun toString(): String {
        val d = mutableMapOf<String, Boolean>()
        d["OK"]                  = this == OK
        d["SD14_FIFO_OVERFLOW"]  = this.contains(SD14_FIFO_OVERFLOW)
        d["FILTER_DELTA"]        = this.contains(FILTER_DELTA)
        d["WORK_VOLTAGE"]        = this.contains(WORK_VOLTAGE)
        d["PEAK_DELTA_EXCEEDED"] = this.contains(PEAK_DELTA_EXCEEDED)
        d["AVG_DELTA_EXCEEDED"]  = this.contains(AVG_DELTA_EXCEEDED)
        d["RF"]                  = this.contains(RF)
        d["REF_R"]               = this.contains(REF_R)
        d["SIGNAL_SATURATED"]    = this.contains(SIGNAL_SATURATED)
        d["SENSOR_SIGNAL_LOW"]   = this.contains(SENSOR_SIGNAL_LOW)
        d["THERMISTOR_OUT_OF_RANGE"] = this.contains(THERMISTOR_OUT_OF_RANGE)
        d["TEMP_HIGH"]           = this.contains(TEMP_HIGH)
        d["TEMP_LOW"]            = this.contains(TEMP_LOW)
        d["INVALID_DATA"]        = this.contains(INVALID_DATA)
        return "0x${rawValue.toString(16)}: ${d.filterValues { it }.keys.joinToString(", ")}"
    }
}
