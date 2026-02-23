package io.tryvital.vitalsamsunghealth.ext

import io.tryvital.vitalhealthcore.ext.returnEmptyIfException as coreReturnEmptyIfException

suspend fun <T> returnEmptyIfException(block: suspend () -> List<T>): List<T> =
    coreReturnEmptyIfException(block)
