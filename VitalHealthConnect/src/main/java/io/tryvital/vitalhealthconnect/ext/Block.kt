package io.tryvital.vitalhealthconnect.ext

import io.tryvital.vitalhealthcore.ext.returnEmptyIfException as coreReturnEmptyIfException

suspend fun <T> returnEmptyIfException(block: suspend () -> List<T>): List<T> =
    coreReturnEmptyIfException(block)
