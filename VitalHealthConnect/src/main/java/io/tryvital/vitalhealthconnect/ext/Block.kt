package io.tryvital.vitalhealthconnect.ext

suspend fun returnZeroIfException(block: suspend () -> Long): Long {
    return try {
        block()
    } catch (exception: Exception) {
        0
    }
}

suspend fun <T> returnEmptyIfException(block: suspend () -> List<T>): List<T> {
    return try {
        block()
    } catch (exception: Exception) {
        emptyList()
    }
}
