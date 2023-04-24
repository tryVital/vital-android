package io.tryvital.vitalhealthconnect.model

sealed class WritableVitalResource(val name: String) {
    object Glucose : WritableVitalResource("glucose")
    object Water : WritableVitalResource("water")
}
