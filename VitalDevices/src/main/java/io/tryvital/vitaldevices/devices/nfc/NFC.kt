package io.tryvital.vitaldevices.devices.nfc

import kotlinx.coroutines.CancellableContinuation

internal class NFC(
    val readingMessage: String,
    val errorMessage: String,
    val completionMessage: String,
    val continuation: CancellableContinuation<Pair<Sensor, List<Glucose>>>
) {
    fun startSession() {

    }
}