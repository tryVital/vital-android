package io.tryvital.vitaldevices

import kotlinx.coroutines.CancellationException

class BluetoothError(message: String): Throwable(message = message)

internal object NoMoreSamplesException: CancellationException()
