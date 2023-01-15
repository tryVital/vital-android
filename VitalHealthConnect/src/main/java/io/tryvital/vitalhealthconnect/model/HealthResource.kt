package io.tryvital.vitalhealthconnect.model

val healthResources = setOf(
    HealthResource.Profile,
    HealthResource.Body,
    HealthResource.Workout,
    HealthResource.Activity,
    HealthResource.Sleep,
    HealthResource.Glucose,
    HealthResource.BloodPressure,
    HealthResource.HeartRate,
    HealthResource.Steps,
    HealthResource.ActiveEnergyBurned,
    HealthResource.BasalEnergyBurned,
    HealthResource.Water,
)

sealed class HealthResource(val name: String) {
    object Profile : HealthResource("profile")
    object Body : HealthResource("body")
    object Workout : HealthResource("workout")
    object Activity : HealthResource("activity")
    object Sleep : HealthResource("sleep")
    object Glucose : HealthResource("glucose")
    object BloodPressure : HealthResource("bloodPressure")
    object HeartRate : HealthResource("heartRate")
    object Steps : HealthResource("steps")
    object ActiveEnergyBurned : HealthResource("activeEnergyBurned")
    object BasalEnergyBurned : HealthResource("basalEnergyBurned")
    object Water : HealthResource("water")

    override fun toString(): String {
        return name
    }

    companion object {
        @Suppress("unused")
        fun values(): Array<HealthResource> {
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
                Water
            )
        }

        fun valueOf(value: String): HealthResource {
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
                else -> throw IllegalArgumentException("No object io.tryvital.vitalhealthconnect.model.HealthResource.$value")
            }
        }
    }
}
