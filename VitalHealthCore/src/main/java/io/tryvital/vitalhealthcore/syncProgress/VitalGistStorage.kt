package io.tryvital.vitalhealthcore.syncProgress

import android.content.Context
import androidx.core.util.AtomicFile
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import io.tryvital.client.utils.InstantJsonAdapter
import io.tryvital.client.utils.VitalLogger
import java.io.File
import java.io.FileNotFoundException
import java.time.Instant

@OptIn(ExperimentalStdlibApi::class)
class VitalGistStorage(private val applicationDataDir: File) {
    inline fun <reified Value> get(key: VitalGistStorageKey<Value>): Value? {
        try {
            val file = atomicFile(key)
            return moshi.adapter<Value>().fromJson(file.readFully().decodeToString())
        } catch (exc: FileNotFoundException) {
            return null
        } catch (exc: Throwable) {
            VitalLogger.getOrCreate().exception(exc) { "VitalGistStorage: failed to load ${key.key}" }
            return null
        }
    }

    inline fun <reified Value> set(newValue: Value?, key: VitalGistStorageKey<Value>) {
        val file = atomicFile(key)
        val json = moshi.adapter<Value>().toJson(newValue)

        val out = file.startWrite()
        try {
            out.write(json.encodeToByteArray())
            out.flush()
            file.finishWrite(out)
        } catch (t: Throwable) {
            file.failWrite(out)
            throw t
        }
    }

    @PublishedApi
    internal fun atomicFile(key: VitalGistStorageKey<*>) = AtomicFile(
        File(applicationDataDir, "vital_mobile_sdk/${key.key}.json")
    )

    companion object {
        private var shared: VitalGistStorage? = null

        fun getOrCreate(context: Context) = synchronized(this) {
            when (val shared = this.shared) {
                null -> {
                    this.shared = VitalGistStorage(context.applicationContext.dataDir)
                    this.shared!!
                }
                else -> shared
            }
        }

        val moshi: Moshi by lazy {
            Moshi.Builder()
                .add(Instant::class.java, InstantJsonAdapter)
                .add(SyncProgress.SyncStatus::class.java, IntEnumAdapter(SyncProgress.SyncStatus.Companion::of))
                .add(SyncProgress.SystemEventType::class.java, IntEnumAdapter(SyncProgress.SystemEventType.Companion::of))
                .add(SyncProgress.SyncContextTag::class.java, IntEnumAdapter(SyncProgress.SyncContextTag.Companion::of))
                .build()
        }
    }
}

interface RawIntRepresentable {
    val rawValue: Int
}

inline fun <reified Enum: RawIntRepresentable> IntEnumAdapter(
    crossinline create: (Int) -> Enum
): JsonAdapter<Enum> = object: JsonAdapter<Enum>() {
    override fun fromJson(reader: JsonReader): Enum = create(reader.nextInt())

    override fun toJson(writer: JsonWriter, value: Enum?) {
        if (value != null) writer.value(value.rawValue) else writer.nullValue()
    }
}

open class VitalGistStorageKey<Value>(
    val key: String
)
