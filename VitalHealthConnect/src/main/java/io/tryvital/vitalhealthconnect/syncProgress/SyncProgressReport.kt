package io.tryvital.vitalhealthconnect.syncProgress

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
internal data class SyncProgressReport(
    @Json(name = "syncProgress") val syncProgress: SyncProgress,
    @Json(name = "deviceInfo") val deviceInfo: DeviceInfo
) {
    @JsonClass(generateAdapter = true)
    data class DeviceInfo(
        val osVersion: String,
        val model: String,
        val appBundle: String,
        val appVersion: String,
        val appBuild: String
    )
}
