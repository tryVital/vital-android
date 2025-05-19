package io.tryvital.client.utils

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.time.Instant

object InstantJsonAdapter: JsonAdapter<Instant>() {
    override fun fromJson(reader: JsonReader): Instant? {
        if (reader.peek() == JsonReader.Token.NULL) {
            return reader.nextNull()
        }

        val rawValue = reader.nextString()
        return Instant.parse(rawValue.replace("+00:00", "Z"))
    }

    override fun toJson(writer: JsonWriter, value: Instant?) {
        if (value != null) {
            writer.value(value.toString())
        } else {
            writer.nullValue()
        }
    }

}
