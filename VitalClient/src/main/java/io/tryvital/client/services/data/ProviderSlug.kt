@file:Suppress("unused")

package io.tryvital.client.services.data

import com.squareup.moshi.Json

enum class ManualProviderSlug {
    @Json(name = "beurer_ble")
    BeurerBLE,
    @Json(name = "omron_ble")
    OmronBLE,
    @Json(name = "accuchek_ble")
    AccuchekBLE,
    @Json(name = "contour_ble")
    ContourBLE,
    @Json(name = "freestyle_libre_ble")
    LibreBLE,
    @Json(name = "manual")
    Manual,
    @Json(name = "apple_health_kit")
    AppleHealthKit,
    @Json(name = "health_connect")
    HealthConnect,
}
