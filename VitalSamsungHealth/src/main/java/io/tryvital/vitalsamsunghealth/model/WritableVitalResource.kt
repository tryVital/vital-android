package io.tryvital.vitalsamsunghealth.model

sealed class WritableVitalResource(val name: String) {
    object Glucose : WritableVitalResource("glucose")
    object Water : WritableVitalResource("water")

    override fun toString(): String {
        return name
    }

    companion object {
        @Suppress("unused")
        fun values(): Array<WritableVitalResource> {
            return arrayOf(Glucose, Water)
        }

        fun valueOf(value: String): WritableVitalResource {
            return when (value) {
                "glucose" -> Glucose
                "water" -> Water
                else -> throw IllegalArgumentException("No object io.tryvital.vitalsamsunghealth.model.HealthResource.$value")
            }
        }
    }
}
