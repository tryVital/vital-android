package io.tryvital.vitalsamsunghealth.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.tryvital.vitalsamsunghealth.syncProgress.VitalGistStorage.Companion.moshi

@JsonClass(generateAdapter = false)
enum class ConnectionPolicy {
    @Json(name = "autoConnect")
    AutoConnect,
    @Json(name = "explicit")
    Explicit;

    companion object {
        internal val adapter = moshi.adapter(ConnectionPolicy::class.java)
    }
}
