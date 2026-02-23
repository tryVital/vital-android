package io.tryvital.vitalhealthcore.syncProgress

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SyncProgressReport(
    @Json(name = "sync_progress") val syncProgress: SyncProgress,
    @Json(name = "device_info") val deviceInfo: DeviceInfo
) {
    @JsonClass(generateAdapter = true)
    data class DeviceInfo(
        @Json(name = "os_version")
        val osVersion: String,
        val model: String,
        @Json(name = "app_bundle")
        val appBundle: String,
        @Json(name = "app_version")
        val appVersion: String,
        @Json(name = "app_build")
        val appBuild: String
    )
}
