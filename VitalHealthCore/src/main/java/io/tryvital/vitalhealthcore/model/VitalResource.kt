package io.tryvital.vitalhealthcore.model

sealed class VitalResource(val name: String) {
    object Profile : VitalResource("profile")
    object Body : VitalResource("body")
    object Workout : VitalResource("workout")
    object Activity : VitalResource("activity")
    object Sleep : VitalResource("sleep")
    object Glucose : VitalResource("glucose")
    object BloodPressure : VitalResource("bloodPressure")
    object BloodOxygen : VitalResource("bloodOxygen")
    object HeartRate : VitalResource("heartRate")
    object Water : VitalResource("water")
    object HeartRateVariability : VitalResource("heartRateVariability")
    object MenstrualCycle : VitalResource("menstrualCycle")

    object Steps : VitalResource("steps")
    object ActiveEnergyBurned : VitalResource("activeEnergyBurned")
    object BasalEnergyBurned : VitalResource("basalEnergyBurned")
    object FloorsClimbed : VitalResource("floorsClimbed")
    object DistanceWalkingRunning : VitalResource("distanceWalkingRunning")
    object Vo2Max : VitalResource("vo2Max")

    object RespiratoryRate : VitalResource("respiratoryRate")
    object Temperature : VitalResource("temperature")

    object Meal : VitalResource("meal")

    override fun toString(): String = name

    val priority: Int get() = vitalResourcePriority(name)

    companion object {
        @Suppress("unused")
        fun values(): Array<VitalResource> = vitalResourceOrder().map(::valueOf).toTypedArray()

        fun valueOf(value: String) = when (value) {
            Profile.name -> Profile
            Body.name -> Body
            Workout.name -> Workout
            Activity.name -> Activity
            Sleep.name -> Sleep
            Glucose.name -> Glucose
            BloodPressure.name -> BloodPressure
            BloodOxygen.name -> BloodOxygen
            HeartRate.name -> HeartRate
            Water.name -> Water
            HeartRateVariability.name -> HeartRateVariability
            MenstrualCycle.name -> MenstrualCycle
            Steps.name -> Steps
            ActiveEnergyBurned.name -> ActiveEnergyBurned
            BasalEnergyBurned.name -> BasalEnergyBurned
            FloorsClimbed.name -> FloorsClimbed
            DistanceWalkingRunning.name -> DistanceWalkingRunning
            Vo2Max.name -> Vo2Max
            RespiratoryRate.name -> RespiratoryRate
            Temperature.name -> Temperature
            Meal.name -> Meal
            else -> throw IllegalArgumentException("No object io.tryvital.vitalhealthcore.model.VitalResource.$value")
        }
    }
}

@JvmInline
value class RemappedVitalResource(val wrapped: VitalResource) {
    override fun toString() = wrapped.toString()
}

fun VitalResource.remapped(): RemappedVitalResource = RemappedVitalResource(this)
