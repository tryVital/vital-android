package io.tryvital.vitalsamsunghealth.model

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

    @Deprecated(
        message = "Use SamsungHealthUnavailable instead.",
        replaceWith = ReplaceWith("PermissionOutcome.SamsungHealthUnavailable")
    )
    object HealthConnectUnavailable: PermissionOutcome {
        override fun toString() = "healthDataUnavailable"
    }

    companion object {
        val SamsungHealthUnavailable: PermissionOutcome
            @Suppress("DEPRECATION")
            get() = HealthConnectUnavailable
    }
}

