@file:Suppress("unused")

package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.adapters.EnumJsonAdapter

@JsonClass(generateAdapter = false)
enum class ManualProviderSlug {
    @Json(name = "beurer_ble") BeurerBLE,
    @Json(name = "omron_ble") OmronBLE,
    @Json(name = "accuchek_ble") AccuchekBLE,
    @Json(name = "contour_ble") ContourBLE,
    @Json(name = "freestyle_libre_ble") LibreBLE,
    @Json(name = "manual") Manual,
    @Json(name = "apple_health_kit") AppleHealthKit,
    @Json(name = "health_connect") HealthConnect;

    // Use the Json name also when converting to string.
    // This is intended for Retrofit request parameter serialization.
    override fun toString() = getJsonName(this)

    companion object {
        val jsonAdapter: EnumJsonAdapter<ManualProviderSlug>
            get() = EnumJsonAdapter.create(ManualProviderSlug::class.java)
    }
}

@JsonClass(generateAdapter = false)
enum class ProviderSlug {
    Unrecognized,

    // Cloud
    @Json(name = "beurer_api") Beurer,
    @Json(name = "ihealth") IHealth,
    @Json(name = "freestyle_libre") Libre,
    @Json(name = "oura") Oura,
    @Json(name = "garmin") Garmin,
    @Json(name = "fitbit") Fitbit,
    @Json(name = "whoop") Whoop,
    @Json(name = "strava") Strava,
    @Json(name = "renpho") Renpho,
    @Json(name = "peloton") Peloton,
    @Json(name = "wahoo") Wahoo,
    @Json(name = "zwift") Zwift,
    @Json(name = "eight_sleep") EightSleep,
    @Json(name = "withings") Withings,
    @Json(name = "google_fit") GoogleFit,
    @Json(name = "hammerhead") Hammerhead,
    @Json(name = "dexcom") Dexcom,
    @Json(name = "my_fitness_pal") MyFitnessPal,

    // Manual
    @Json(name = "beurer_ble") BeurerBLE,
    @Json(name = "omron_ble") OmronBLE,
    @Json(name = "accuchek_ble") AccuchekBLE,
    @Json(name = "contour_ble") ContourBLE,
    @Json(name = "freestyle_libre_ble") LibreBLE,
    @Json(name = "manual") Manual,
    @Json(name = "apple_health_kit") AppleHealthKit,
    @Json(name = "health_connect") HealthConnect;

    // Use the Json name also when converting to string.
    // This is intended for Retrofit request parameter serialization.
    override fun toString() = getJsonName(this)

    companion object {
        val jsonAdapter: EnumJsonAdapter<ProviderSlug>
            get() = EnumJsonAdapter.create(ProviderSlug::class.java).withUnknownFallback(Unrecognized)
    }
}

private fun getJsonName(value: Enum<*>) = value.javaClass
    .getField(value.name).getAnnotation(Json::class.java)?.name ?: value.name
