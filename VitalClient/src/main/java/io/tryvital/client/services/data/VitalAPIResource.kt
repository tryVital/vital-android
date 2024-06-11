@file:Suppress("unused")

package io.tryvital.client.services.data

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

@JsonClass(generateAdapter = false)
data class VitalAPIResource(
    val rawValue: String
): Comparable<VitalAPIResource> {

    override fun compareTo(other: VitalAPIResource): Int = rawValue.compareTo(other.rawValue)

    companion object {
        val Activity = VitalAPIResource("activity")
        val Sleep = VitalAPIResource("sleep")
        val Workouts = VitalAPIResource("workouts")
        val Body = VitalAPIResource("body")

        val jsonAdapter = object: JsonAdapter<VitalAPIResource>() {
            override fun fromJson(reader: JsonReader): VitalAPIResource?
                = VitalAPIResource(reader.nextString())

            override fun toJson(writer: JsonWriter, value: VitalAPIResource?) {
                if (value != null) {
                    writer.value(value.rawValue)
                } else {
                    writer.nullValue()
                }
            }
        }
    }
}
