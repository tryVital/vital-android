@file:OptIn(ExperimentalUnsignedTypes::class)

package io.tryvital.vitaldevices.devices.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.FLAG_READER_NFC_V
import android.nfc.Tag
import android.nfc.tech.NfcV
import io.tryvital.client.utils.VitalLogger
import kotlinx.coroutines.CancellableContinuation
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import kotlin.coroutines.resumeWithException

private val logger = VitalLogger.getOrCreate()

@Suppress("unused")
internal class NFC(
    private val continuation: CancellableContinuation<Pair<Sensor, List<Glucose>>>
) {
    private var session: Pair<Activity, NfcAdapter>? = null

    @Suppress("unused")
    fun startSession(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity.applicationContext)
        if (adapter == null) {
            if (continuation.isActive) {
                continuation.resumeWithException(NfcUnsupportedError())
            }
            return
        }

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
        logger.info { "discovered NfcV tag: ${tag.id.asUByteArray().toHex()}" }

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
                    logger.info { "requesting patch info [0]" }
                    patchInfo = nfcV.customCommand(RequestFlag.HighDataRate, 0xA1)
                    logger.info { "received patch info [0]: ${patchInfo.toHex()}" }
                } catch (e: Throwable) {
                    logger.exception(e) { "failed to get patch info [0]" }
                    failedToScan = true
                }

                try {
                    logger.info { "requesting system info [1]" }
                    systemInfo = nfcV.systemInfo(RequestFlag.HighDataRate)
                    logger.info { "received system info [1]: $systemInfo" }
                } catch (e: Throwable) {
                    logger.exception(e) { "failed to get system info [1]" }

                    if (requestedRetry > retries) {
                        return fail(NfcTransportError("failed reading SystemInfo", e))
                    }
                    failedToScan = true
                    requestedRetry += 1
                }

                try {
                    logger.info { "requesting patch info [2]" }
                    patchInfo = nfcV.customCommand(RequestFlag.HighDataRate, 0xA1)
                    logger.info { "received patch info [2]: ${patchInfo.toHex()}" }
                } catch (e: Throwable) {
                    logger.exception(e) { "failed to get patch info [2]" }

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
                logger.info { "reading $blocks blocks of size ${systemInfo.blockSize} at offset 0" }

                val (_, data) = nfcV.read(start = 0, blocks = blocks, blockSize = systemInfo.blockSize)
                logger.info { "received data (${data.size} bytes): ${data.toHex()}" }

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
        exception.printStackTrace()

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
        logger.info { "bufpos: ${buffer.position()} read: $blockToRead <= x < ${blockToRead + requested}" }

        try {
            val dataArray = readMultipleBlocks(
                RequestFlag.HighDataRate,
                blockToRead until (blockToRead + requested)
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
    return NfcVSystemInfo.parse(apdu.original.sliceArray(2 until apdu.original.size), apdu.original[1])
}

internal fun NfcV.customCommand(flags: RequestFlag, code: Int, requestData: UByteArray = UByteArray(0)): UByteArray {
    check(code in 0xA0..0xDF)
    val apdu = runCommand(flags, code, requestData)
    apdu.throwIfNotOk()
    return apdu.original.sliceArray(2 until apdu.original.size)
}

internal fun NfcV.readMultipleBlocks(flags: RequestFlag, range: UIntRange): UByteArray {
    val apdu = runCommand(
        flags.union(RequestFlag.Address),
        0x23,
        ByteBuffer.allocate(4).run {
            put(range.first.toByte())                     // First block number
            put((range.last - range.first).toByte()) // Number of blocks
            toUByteArray()
        },
        manufacturerCode = false,
    )
    apdu.throwIfNotOk()
    return apdu.original.sliceArray(1 until apdu.original.size)
}

internal fun NfcV.runCommand(flags: RequestFlag, code: Int, requestData: UByteArray = UByteArray(0), manufacturerCode: Boolean = true): NfcResponseVAPDU {
    val tagUid = tag.id
    check(tagUid.size == 8) { "NfcV tags must have 8-byte UID" }

    val command = ByteBuffer.allocate(maxTransceiveLength).apply {
        put(flags.rawValue.toByte())  // Flags byte
        put(code.toByte())  // Command code byte
        if (manufacturerCode) {
            put(tagUid[6])      // Manufacturer Code; 2nd last byte of 8-byte tag.id
        }
        if (flags.contains(RequestFlag.Address)) {
            put(tagUid)         // Tag UID
        }
        put(requestData.asByteArray())
    }

    val commandBytes = command.toUByteArray()
    logger.info { "sending command: ${commandBytes.toHex()}" }

    val rawResponse = transceive(commandBytes.asByteArray()).asUByteArray()
    logger.info { "received VAPDU: ${rawResponse.toHex()}" }

    return NfcResponseVAPDU.parse(rawResponse)
}

@JvmInline
value class RequestFlag(val rawValue: UInt) {
    companion object {
        val HighDataRate = RequestFlag(1u shl 1)
        val Address = RequestFlag(1u shl 5)
    }

    fun union(other: RequestFlag) = RequestFlag(rawValue.or(other.rawValue))
    fun contains(other: RequestFlag) = rawValue.and(other.rawValue) != 0u
}

@Suppress("MemberVisibilityCanBePrivate", "CanBeParameter")
class NfcUnsupportedSensorError(val sensorType: SensorType): Throwable("Unsupported sensor: $sensorType")
class NfcTransportError(message: String, cause: Throwable? = null): Throwable(message, cause)
class NfcResponseError(status: UByte, payload: String): Throwable("NFC Response error: %x %s".format(status, payload))
class NfcUnsupportedError: Throwable("NFC is not supported by this device")

@Suppress("unused")
class NfcResponseVAPDU(
    val status: UByte,
    val original: UByteArray,
) {
    fun throwIfNotOk() {
        if (status != 0.toUByte()) {
            logger.info { "NFC Response error: status = $status; payload = ${original.toHex()}" }
            throw NfcResponseError(status, original.toHex())
        }
    }

    companion object {
        fun parse(bytes: UByteArray): NfcResponseVAPDU {
            check(bytes.size >= 2) { "NfcV Response APDU must be at least 2 bytes large" }
            return NfcResponseVAPDU(
                status = bytes[0],
                original = bytes,
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
        fun parse(bytes: UByteArray, infoFlag: UByte): NfcVSystemInfo {
            check(infoFlag == 0x04u.toUByte()) { "support only information flag = 0x04 at this time" }
            return NfcVSystemInfo(
                uid = bytes.sliceArray(0 until 8).asByteArray(),
                dsfId = 0u, // buffer.get().toUByte(),
                applicationFamilyId = 0u, //buffer.get().toUByte(),
                totalBlocks = bytes[8],
                blockSize = (1 shl bytes[9].countOneBits()).toUByte(),
                icReference = 0u, //buffer.get().toUByte(),
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

fun UByteArray.toHex(): String = joinToString(separator = " ") { it.toString(16).padStart(2, '0') }