@file:OptIn(ExperimentalUnsignedTypes::class)

package io.tryvital.vitaldevices.devices.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.FLAG_READER_NFC_V
import android.nfc.Tag
import android.nfc.tech.NfcV
import kotlinx.coroutines.CancellableContinuation
import java.nio.ByteBuffer
import java.time.Instant
import kotlin.coroutines.resumeWithException

@Suppress("unused")
internal class NFC(
    private val continuation: CancellableContinuation<Pair<Sensor, List<Glucose>>>
) {
    private var session: Pair<Activity, NfcAdapter>? = null

    @Suppress("unused")
    fun startSession(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity.applicationContext)
        adapter.enableReaderMode(
            activity,
            { tag -> onDiscoveredTag(tag) },
            // NfvC = iso15693
            FLAG_READER_NFC_V,
            null,
        )

        this.session = Pair(activity, adapter)
    }

    fun close() {
        val session = session
        if (session != null) {
            session.second.disableReaderMode(session.first)
            this.session = null
        }
    }

    private fun onDiscoveredTag(tag: Tag) {
        val nfcV = NfcV.get(tag)

        nfcV.connect()
        nfcV.use { nfcV ->
            var patchInfo = UByteArray(0)
            val retries = 5
            var requestedRetry = 0
            var failedToScan: Boolean
            var systemInfo: NfcVSystemInfo? = null

            do {
                failedToScan = false

                try {
                    patchInfo = nfcV.customCommand(RequestFlag.HighDataRate, 0xA1)
                } catch (e: Throwable) {
                    failedToScan = true
                }

                try {
                    systemInfo = nfcV.systemInfo(RequestFlag.HighDataRate)
                } catch (e: Throwable) {
                    if (requestedRetry > retries) {
                        return fail(NfcTransportError("failed reading SystemInfo", e))
                    }
                    failedToScan = true
                    requestedRetry += 1
                }

                try {
                    patchInfo = nfcV.customCommand(RequestFlag.HighDataRate, 0xA1)
                } catch (e: Throwable) {
                    if (requestedRetry > retries && systemInfo != null) {
                        requestedRetry = 0
                    } else {
                        if (!failedToScan) {
                            failedToScan = true
                            requestedRetry += 1
                        }
                    }
                }

            } while (failedToScan && requestedRetry > 0)

            check(systemInfo != null)

            if (patchInfo.isEmpty()) {
                return fail(NfcTransportError("failed reading PatchInfo"))
            }

            when (val sensorType = SensorType.parseFromPatchInfo(patchInfo)) {
                SensorType.libre3, SensorType.libre2, SensorType.libreProH ->
                    return fail(NfcUnsupportedSensorError(sensorType))
                else -> {}
            }

            val sensor = Sensor(
                patchInfo = patchInfo,
                // NFC uses Big Endian, while ARM/x86 is Little Endian.
                uid = tag.id.asUByteArray().reversedArray(),
            )

            val blocks = 22u + 24u    // (32 * 6 / 8)

            try {
                val (_, data) = nfcV.read(start = 0, blocks = blocks, blockSize = systemInfo.blockSize)
                val lastReadingDate = Instant.now()

                sensor.lastReadingDate = lastReadingDate
                sensor.setFRAM(data)
                close()

            } catch (e: Throwable) {
                return fail(NfcTransportError("failed reading blocks", e))
            }

            val uniqueValues = (sensor.factoryTrend + sensor.factoryHistory).associateBy { it.date }.values
            val ordered = uniqueValues.sortedByDescending { it.date }

            if (continuation.isActive) {
                continuation.resumeWith(Result.success(Pair(sensor, ordered)))
            }
            close()
        }
    }

    private fun fail(exception: Throwable) {
        if (continuation.isActive) {
            continuation.resumeWithException(exception)
        }
        close()
    }
}

internal fun NfcV.read(start: Int, blocks: UInt, blockSize: UByte, requesting: UInt = 3u, retries: UInt = 5u): Pair<Int, UByteArray> {
    val buffer = ByteBuffer.allocate((blocks * blockSize).toInt())

    var remaining = blocks
    var requested = requesting
    var retry = 0u

    while (remaining > 0u && retry <= retries) {

        val blockToRead = (start + buffer.position() / 8).toUInt()

        try {
            val dataArray = readMultipleBlocks(
                RequestFlag.HighDataRate,
                blockToRead until blockToRead + requested
            )

            buffer.put(dataArray.asByteArray())
            remaining -= requested

            if (remaining != 0u && remaining < requested) {
                requested = remaining
            }
        } catch (e: Throwable) {
            retry += 1u
            if (retry <= retries) {
                Thread.sleep(250)
            } else {
                throw NfcTransportError("failed to read data blocks", e)
            }
        }
    }

    return Pair(start, buffer.toUByteArray())
}

internal fun NfcV.systemInfo(flags: RequestFlag): NfcVSystemInfo {
    val apdu = runCommand(flags, 0x2B)
    apdu.throwIfNotOk()
    return NfcVSystemInfo.parse(apdu.data)
}

internal fun NfcV.customCommand(flags: RequestFlag, code: Int, requestData: UByteArray = UByteArray(0)): UByteArray {
    check(code in 0xA0..0xDF)
    val apdu = runCommand(flags, code, requestData)
    apdu.throwIfNotOk()
    return apdu.data
}

internal fun NfcV.readMultipleBlocks(flags: RequestFlag, range: UIntRange): UByteArray {
    val apdu = runCommand(
        flags,
        0x23,
         ByteBuffer.allocate(4).run {
             putShort(range.first.toShort())                // First block number
             putShort((range.last - range.first).toShort()) // Number of blocks
             toUByteArray()
         }
    )
    apdu.throwIfNotOk()
    return apdu.data
}

internal fun NfcV.runCommand(flags: RequestFlag, code: Int, requestData: UByteArray = UByteArray(0)): NfcResponseVAPDU {
    // NFC uses Big Endian, while ARM/x86 is Little Endian.
    val tagUid = tag.id.reversedArray()
    check(tagUid.size == 8) { "NfcV tags must have 8-byte UID" }

    val command = ByteBuffer.allocate(maxTransceiveLength).apply {
        put(RequestFlag.Address.union(flags).rawValue.toByte())  // Flags byte
        put(code.toByte())  // Command code byte
        put(tagUid[1])      // Manufacturer Code; 2nd byte of Tag UID
        put(tagUid)         // Tag UID
        put(requestData.asByteArray())
    }

    val rawResponse = transceive(command.toUByteArray().asByteArray()).asUByteArray()
    return NfcResponseVAPDU.parse(rawResponse)
}

@JvmInline
value class RequestFlag(val rawValue: UInt) {
    companion object {
        val HighDataRate = RequestFlag(1u shl 1)
        val Address = RequestFlag(1u shl 5)
    }

    fun union(other: RequestFlag) = RequestFlag(rawValue.or(other.rawValue))
}

@Suppress("MemberVisibilityCanBePrivate", "CanBeParameter")
class NfcUnsupportedSensorError(val sensorType: SensorType): Throwable("Unsupported sensor: $sensorType")
class NfcTransportError(message: String, cause: Throwable? = null): Throwable(message, cause)
class NfcResponseError(status: UByte, status2: UByte): Throwable("NFC Response error: %x %x".format(status, status2))

@Suppress("unused")
class NfcResponseVAPDU(
    val status: UByte,
    val status2: UByte,
    val data: UByteArray,
) {
    fun throwIfNotOk() {
        if (status != 0.toUByte()) {
            throw NfcResponseError(status, status2)
        }
    }

    companion object {
        fun parse(bytes: UByteArray): NfcResponseVAPDU {
            check(bytes.size >= 2) { "NfcV Response APDU must be at least 2 bytes large" }
            return NfcResponseVAPDU(
                status = bytes[0],
                status2 = bytes[0],
                data = bytes.sliceArray(2 until bytes.size)
            )
        }
    }
}

@Suppress("unused")
class NfcVSystemInfo(
    val uid: ByteArray,
    val dsfId: UByte,
    val applicationFamilyId: UByte,
    val blockSize: UByte,
    val totalBlocks: UByte,
    val icReference: UByte,
) {
    companion object {
        fun parse(bytes: UByteArray): NfcVSystemInfo {
            val buffer = ByteBuffer.wrap(bytes.asByteArray())
            return NfcVSystemInfo(
                uid = ByteArray(8).apply { buffer.get(this, 0, 8) },
                dsfId = buffer.get().toUByte(),
                applicationFamilyId = buffer.get().toUByte(),
                blockSize = buffer.get().toUByte(),
                totalBlocks = buffer.get().toUByte(),
                icReference = buffer.get().toUByte(),
            )
        }
    }
}

internal fun ByteBuffer.toUByteArray(): UByteArray {
    return UByteArray(position()).also { array ->
        rewind()
        get(array.asByteArray())
    }
}