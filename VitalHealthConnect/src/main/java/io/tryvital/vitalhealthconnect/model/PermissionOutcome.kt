package io.tryvital.vitalhealthconnect.model

sealed interface PermissionOutcome {
    object Success: PermissionOutcome {
        override fun toString() = "success"
    }
    sealed class Failure(val cause: Throwable?): PermissionOutcome
    object Cancelled: Failure(null) {
        override fun toString() = "cancelled"
    }
    class UnknownError(cause: Throwable): Failure(cause) {
        override fun toString() = "unknownError(${cause.toString()})"
    }
    object NotPrompted: Failure(null) {
        override fun toString() = "notPrompted"
    }
    object HealthConnectUnavailable: PermissionOutcome {
        override fun toString() = "healthDataUnavailable"
    }
}