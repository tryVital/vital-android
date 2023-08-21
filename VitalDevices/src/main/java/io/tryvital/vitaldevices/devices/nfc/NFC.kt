@file:OptIn(ExperimentalUnsignedTypes::class)

package io.tryvital.vitaldevices.devices.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.FLAG_READER_NFC_V
import android.nfc.Tag
import android.nfc.tech.NfcV
import kotlinx.coroutines.CancellableContinuation
import java.nio.ByteBuffer
import kotlin.coroutines.resumeWithException

internal class NFC(
    private val continuation: CancellableContinuation<Pair<Sensor, List<Glucose>>>
) {
    private var session: Pair<Activity, NfcAdapter>? = null

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
        var systemInfo: NfcVSystemInfo? = null

        nfcV.connect()
        nfcV.use { nfcV ->
            var patchInfo: UByteArray = UByteArray(0)
            val retries = 5
            var requestedRetry = 0
            var failedToScan = false

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
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                NfcTransportError("failed reading SystemInfo")
                            )
                        }
                        close()
                        return
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
        }
    }
}

internal fun NfcV.systemInfo(flags: RequestFlag): NfcVSystemInfo {
    val apdu = runCommand(flags, 0x2B)
    apdu.throwIfNotOk()
    return NfcVSystemInfo.parse(apdu.data)
}

internal fun NfcV.customCommand(flags: RequestFlag, code: Int, requestData: ByteArray = ByteArray(0)): UByteArray {
    check(code in 0xA0..0xDF)
    val apdu = runCommand(flags, code, requestData)
    apdu.throwIfNotOk()
    return apdu.data
}

internal fun NfcV.runCommand(flags: RequestFlag, code: Int, requestData: ByteArray = ByteArray(0)): NfcResponseVAPDU {
    // NfcV has it backwards
    val tagUid = tag.id.reversedArray()
    check(tagUid.size == 8) { "NfcV tags must have 8-byte UID" }

    val command = ByteBuffer.allocate(maxTransceiveLength).apply {
        put(RequestFlag.Address.union(flags).rawValue.toByte())  // Flags byte
        put(code.toByte())  // Command code byte
        put(tagUid[1])      // Manufacturer Code; 2nd byte of Tag UID
        put(tagUid)         // Tag UID
        put(requestData)
    }

    val rawResponse = transceive(command.array()).toUByteArray()
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

class NfcTransportError(message: String): Throwable(message)
class NfcResponseError(status: UByte, status2: UByte): Throwable("NFC Response error: %x %x".format(status, status2))

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
            val buffer = ByteBuffer.wrap(bytes.toByteArray())
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