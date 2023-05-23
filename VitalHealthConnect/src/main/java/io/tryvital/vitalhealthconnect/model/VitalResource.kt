package io.tryvital.vitalhealthconnect.model

import androidx.health.connect.client.records.*

import kotlin.reflect.KClass

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

/**
 * VitalResource remapping.
 *
 * All individual activity timeseries resources are remapped to Activity for processing.
 * The Activity processing path is designed to work with partially granted permissions, and can
 * compute Activity Day Summary & pull individual samples adaptively in accordance to the permission
 * state.
 */
fun VitalResource.remapped(): VitalResource = when (this) {
    VitalResource.ActiveEnergyBurned, VitalResource.BasalEnergyBurned, VitalResource.Steps ->
        VitalResource.Activity

    else -> this
}

/**
 * VitalResource data dependencies on Health Connect record.
 *
 * This covers all the record types which a VitalResource will **READ**.
 */
fun VitalResource.recordTypeDependencies(): List<KClass<out Record>> = when (this) {
    VitalResource.Water -> listOf(HydrationRecord::class)
    VitalResource.ActiveEnergyBurned -> listOf(ActiveCaloriesBurnedRecord::class)
    VitalResource.Activity -> listOf(
        ActiveCaloriesBurnedRecord::class,
        BasalMetabolicRateRecord::class,
        StepsRecord::class,
        DistanceRecord::class,
        FloorsClimbedRecord::class,
    )
    VitalResource.BasalEnergyBurned -> listOf(BasalMetabolicRateRecord::class)
    VitalResource.BloodPressure -> listOf(BloodPressureRecord::class)
    VitalResource.Body -> listOf(
        BodyFatRecord::class,
        WeightRecord::class,
    )
    VitalResource.Glucose -> listOf(BloodGlucoseRecord::class)
    VitalResource.HeartRate -> listOf(HeartRateRecord::class)
    VitalResource.HeartRateVariability -> listOf(HeartRateVariabilityRmssdRecord::class)
    VitalResource.Profile -> listOf(HeightRecord::class)
    VitalResource.Sleep -> listOf(
        SleepSessionRecord::class,
        SleepStageRecord::class,
        HeartRateRecord::class,
        HeartRateVariabilityRmssdRecord::class,
        RespiratoryRateRecord::class,
        RestingHeartRateRecord::class,
        OxygenSaturationRecord::class,
    )
    VitalResource.Steps -> listOf(StepsRecord::class)
    VitalResource.Workout -> listOf(
        ExerciseSessionRecord::class,
        HeartRateRecord::class,
    )
}

/**
 * Health Connect record types whose changes should trigger sync of a given VitalResource.
 *
 * This is a subset of [recordTypeDependencies]. Some VitalResources do not need to observe all the
 * record type they read.
 *
 * For example, Sleep only needs to observe SleepSessionRecord and SleepStageRecord changes, while
 * heart rate and other related data during the session can be read on demand as we compute the API
 * payload, on the assumption that these data should have been readily available in the Health
 * Connect data store.
 */
fun VitalResource.recordTypeChangesToTriggerSync(): List<KClass<out Record>> = when (this) {
    VitalResource.Water -> listOf(HydrationRecord::class)
    VitalResource.Activity -> listOf(
        ActiveCaloriesBurnedRecord::class,
        BasalMetabolicRateRecord::class,
        StepsRecord::class,
        DistanceRecord::class,
        FloorsClimbedRecord::class,
    )
    VitalResource.BloodPressure -> listOf(BloodPressureRecord::class)
    VitalResource.Body -> listOf(BodyFatRecord::class, WeightRecord::class)
    VitalResource.Glucose -> listOf(BloodGlucoseRecord::class)
    VitalResource.HeartRate -> listOf(HeartRateRecord::class)
    VitalResource.HeartRateVariability -> listOf(HeartRateVariabilityRmssdRecord::class)
    VitalResource.Profile -> listOf(HeightRecord::class)
    VitalResource.Sleep -> listOf(SleepSessionRecord::class, SleepStageRecord::class)
    VitalResource.Workout -> listOf(ExerciseSessionRecord::class)

    VitalResource.ActiveEnergyBurned, VitalResource.BasalEnergyBurned, VitalResource.Steps ->
        throw IllegalArgumentException("Should have been remapped to Activity.")
}
