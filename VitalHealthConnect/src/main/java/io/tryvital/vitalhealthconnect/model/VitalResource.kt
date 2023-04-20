package io.tryvital.vitalhealthconnect.model

val healthResources = setOf(
    VitalResource.Profile,
    VitalResource.Body,
    VitalResource.Workout,
    VitalResource.Activity,
    VitalResource.Sleep,
    VitalResource.Glucose,
    VitalResource.BloodPressure,
    VitalResource.HeartRate,
    VitalResource.Steps,
    VitalResource.ActiveEnergyBurned,
    VitalResource.BasalEnergyBurned,
    VitalResource.Water,
)

sealed class VitalResource(val name: String) {
    object Profile : VitalResource("profile")
    object Body : VitalResource("body")
    object Workout : VitalResource("workout")
    object Activity : VitalResource("activity")
    object Sleep : VitalResource("sleep")
    object Glucose : VitalResource("glucose")
    object BloodPressure : VitalResource("bloodPressure")
    object HeartRate : VitalResource("heartRate")
    object Steps : VitalResource("steps")
    object ActiveEnergyBurned : VitalResource("activeEnergyBurned")
    object BasalEnergyBurned : VitalResource("basalEnergyBurned")
    object Water : VitalResource("water")
    object HeartRateVariability : VitalResource("heartRateVariability")

    override fun toString(): String {
        return name
    }

    companion object {
        @Suppress("unused")
        fun values(): Array<VitalResource> {
            return arrayOf(
                Profile,
                Body,
                Workout,
                Activity,
                Sleep,
                Glucose,
                BloodPressure,
                HeartRate,
                Steps,
                ActiveEnergyBurned,
                BasalEnergyBurned,
                Water,
                HeartRateVariability,
            )
        }

        fun valueOf(value: String): VitalResource {
            return when (value) {
                "profile" -> Profile
                "body" -> Body
                "workout" -> Workout
                "activity" -> Activity
                "sleep" -> Sleep
                "glucose" -> Glucose
                "bloodPressure" -> BloodPressure
                "heartRate" -> HeartRate
                "steps" -> Steps
                "activeEnergyBurned" -> ActiveEnergyBurned
                "basalEnergyBurned" -> BasalEnergyBurned
                "water" -> Water
                "heartRateVariability" -> HeartRateVariability
                else -> throw IllegalArgumentException("No object io.tryvital.vitalhealthconnect.model.HealthResource.$value")
            }
        }
    }
}
