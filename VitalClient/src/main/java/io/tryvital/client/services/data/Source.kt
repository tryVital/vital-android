package io.tryvital.client.services.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

@JsonClass(generateAdapter = true)
data class Source(
    val type: SourceType,
    val provider: ProviderSlug,
    @Json(name = "app_id")
    val appId: String? = null
)

@JsonClass(generateAdapter = false)
data class SourceType(
    val rawValue: String
) {
    companion object {
        val Unknown = VitalAPIResource("unknown")
        val Watch = VitalAPIResource("watch")
        val Phone = VitalAPIResource("phone")
        val MultipleSources = VitalAPIResource("multiple_sources")
        val Ring = VitalAPIResource("ring")

        val jsonAdapter = object: JsonAdapter<SourceType>() {
            override fun fromJson(reader: JsonReader): SourceType?
                    = SourceType(reader.nextString())

            override fun toJson(writer: JsonWriter, value: SourceType?) {
                if (value != null) {
                    writer.value(value.rawValue)
                } else {
                    writer.nullValue()
                }
            }
        }
    }
}
