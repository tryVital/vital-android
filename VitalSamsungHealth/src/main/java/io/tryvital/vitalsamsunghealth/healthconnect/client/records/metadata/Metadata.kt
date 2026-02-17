package io.tryvital.vitalsamsunghealth.healthconnect.client.records.metadata

import java.time.Instant

class DataOrigin(
    val packageName: String,
)

class Device(
    val type: Int = TYPE_UNKNOWN,
    val manufacturer: String? = null,
    val model: String? = null,
) {
    companion object {
        const val TYPE_UNKNOWN = 0
        const val TYPE_WATCH = 1
        const val TYPE_PHONE = 2
        const val TYPE_SCALE = 3
        const val TYPE_RING = 4
        const val TYPE_HEAD_MOUNTED = 5
        const val TYPE_FITNESS_BAND = 6
        const val TYPE_CHEST_STRAP = 7
        const val TYPE_SMART_DISPLAY = 8
    }
}

class Metadata(
    val recordingMethod: Int = RECORDING_METHOD_UNKNOWN,
    val id: String = EMPTY_ID,
    val dataOrigin: DataOrigin = DataOrigin("com.sec.android.app.shealth"),
    val lastModifiedTime: Instant = Instant.EPOCH,
    val clientRecordId: String? = null,
    val clientRecordVersion: Long = 0,
    val device: Device? = null,
) {
    companion object {
        const val EMPTY_ID = ""

        const val RECORDING_METHOD_UNKNOWN = 0
        const val RECORDING_METHOD_ACTIVELY_RECORDED = 1
        const val RECORDING_METHOD_AUTOMATICALLY_RECORDED = 2
        const val RECORDING_METHOD_MANUAL_ENTRY = 3

        @JvmStatic
        fun manualEntry(device: Device? = null): Metadata = Metadata(
            recordingMethod = RECORDING_METHOD_MANUAL_ENTRY,
            device = device,
        )

        @JvmStatic
        fun manualEntryWithId(id: String, device: Device? = null): Metadata = Metadata(
            recordingMethod = RECORDING_METHOD_MANUAL_ENTRY,
            id = id,
            device = device,
        )

        @JvmStatic
        fun unknownRecordingMethod(): Metadata = Metadata(RECORDING_METHOD_UNKNOWN)
    }
}
