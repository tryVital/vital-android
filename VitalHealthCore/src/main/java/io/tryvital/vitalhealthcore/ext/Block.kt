package io.tryvital.vitalhealthcore.ext

suspend fun <T> returnEmptyIfException(block: suspend () -> List<T>): List<T> {
    return try {
        block()
    } catch (exception: SecurityException) {
        emptyList()
    }
}
