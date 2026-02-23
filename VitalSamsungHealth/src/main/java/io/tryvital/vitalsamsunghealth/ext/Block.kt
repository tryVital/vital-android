package io.tryvital.vitalsamsunghealth.ext

suspend fun <T> returnEmptyIfException(block: suspend () -> List<T>): List<T> {
    return try {
        block()
    } catch (exception: SecurityException) {
        // No permission to access
        emptyList()
    }
}
