package io.tryvital.client.utils

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.time.LocalDate

object LocalDateJsonAdapter: JsonAdapter<LocalDate>() {
    override fun fromJson(reader: JsonReader): LocalDate? {
        if (reader.peek() == JsonReader.Token.NULL) {
            return reader.nextNull()
        }

        val dateString = reader.nextString()
        return LocalDate.parse(dateString)
    }

    override fun toJson(writer: JsonWriter, value: LocalDate?) {
        if (value != null) {
            writer.value(value.toString())
        } else {
            writer.nullValue()
        }
    }
}