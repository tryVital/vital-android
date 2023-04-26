package io.tryvital.vitalhealthconnect.model

sealed interface PermissionOutcome {
    object Success: PermissionOutcome
    class Failure(cause: Throwable?): PermissionOutcome
    object HealthConnectUnavailable: PermissionOutcome
}