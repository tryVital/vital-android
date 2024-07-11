package io.tryvital.vitalhealthconnect.model

import android.health.connect.datatypes.units.Power
import androidx.health.connect.client.records.*

import kotlin.reflect.KClass

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
    object MenstrualCycle : VitalResource("menstrualCycle")

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
                MenstrualCycle,
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
                "menstrualCycle" -> MenstrualCycle
                else -> throw IllegalArgumentException("No object io.tryvital.vitalhealthconnect.model.HealthResource.$value")
            }
        }
    }
}

/**
 * Describes how Health Connect sample types map to a particular VitalResource.
 *
 * ### `createPermissionRequestContract(_:)` behaviour:
 * We request `required` + `optional` + `supplementary`.
 *
 * ### `hasAskedForPermission(_:)` behaviour:
 * If `required` is non-empty:
 *   - A VitalResource is "asked" if and only if all `required` sample types have been asked.
 * If `required` is empty:
 *   - A VitalResource is "asked" if at least one `optional` sample types have been asked.
 * In both cases, `supplementary` is not considered at all.
 *
 * Some sample types may appear in multiple `VitalResource`s:
 * 1. Each sample type can only be associated with ** one** VitalResource as their `required` or `optional` types.
 * 2. A sample type can optionally be marked as a `supplementary` type of any other VitalResource.
 *
 * Example:
 * - `VitalResource.heartrate` is the primary resource for `HeartRateRecord::class`.
 * - Activity, workouts and sleeps all need _supplementary_ heartrate permission for statistics, but can function without it.
 *   So they list `HeartRateRecord::class` as their `supplementary` types.
 */
internal data class RecordTypeRequirements(
    /**
     * The required set of Record types of a `VitalResource`.
     *
     * This must not change once the `VitalResource` is introduced, especially if
     * the `VitalResource` is a fully computed resource like `activity`.
     */
    val required: List<KClass<out Record>>,

    /**
     * An optional set of Record types of a `VitalResource`.
     * New types can be added or removed from this list.
     */
    val optional: List<KClass<out Record>>,

    /**
     * An "supplementary" set of Record types of a `VitalResource`.
     * New types can be added or removed from this list.
     */
    val supplementary: List<KClass<out Record>>,
) {

    fun isResourceActive(query: (KClass<out Record>) -> Boolean): Boolean {
        return if (required.isEmpty()) {
            optional.any(query)
        } else {
            required.all(query)
        }
    }

    val allRecordTypes: Set<KClass<out Record>> get()
        = required.toSet().union(optional).union(supplementary)

    companion object {
        fun single(recordType: KClass<out Record>): RecordTypeRequirements
            = RecordTypeRequirements(required = listOf(recordType), optional = emptyList(), supplementary = emptyList())
    }
}

@JvmInline
value class RemappedVitalResource(val wrapped: VitalResource) {
    override fun toString() = wrapped.toString()
}

/**
 * VitalResource remapping.
 *
 * All individual activity timeseries resources are remapped to Activity for processing.
 * The Activity processing path is designed to work with partially granted permissions, and can
 * compute Activity Day Summary & pull individual samples adaptively in accordance to the permission
 * state.
 */
internal fun VitalResource.remapped(): RemappedVitalResource = when (this) {
    VitalResource.ActiveEnergyBurned, VitalResource.BasalEnergyBurned, VitalResource.Steps ->
        RemappedVitalResource(VitalResource.Activity)

    else -> RemappedVitalResource(this)
}

/**
 * VitalResource data dependencies on Health Connect record.
 *
 * This covers all the record types which a VitalResource will **READ**.
 */
internal fun VitalResource.recordTypeDependencies(): RecordTypeRequirements = when (this) {
    VitalResource.ActiveEnergyBurned -> RecordTypeRequirements.single(ActiveCaloriesBurnedRecord::class)
    VitalResource.BasalEnergyBurned -> RecordTypeRequirements.single(BasalMetabolicRateRecord::class)
    VitalResource.Steps -> RecordTypeRequirements.single(StepsRecord::class)

    VitalResource.BloodPressure -> RecordTypeRequirements.single(BloodPressureRecord::class)
    VitalResource.Glucose -> RecordTypeRequirements.single(BloodGlucoseRecord::class)
    VitalResource.HeartRate -> RecordTypeRequirements.single(HeartRateRecord::class)
    VitalResource.HeartRateVariability -> RecordTypeRequirements.single(HeartRateVariabilityRmssdRecord::class)

    VitalResource.Water -> RecordTypeRequirements.single(HydrationRecord::class)
    VitalResource.Profile -> RecordTypeRequirements.single(HeightRecord::class)

    VitalResource.Activity -> RecordTypeRequirements(
        required = emptyList(),
        optional = listOf(
            ActiveCaloriesBurnedRecord::class,
            BasalMetabolicRateRecord::class,
            TotalCaloriesBurnedRecord::class,
            StepsRecord::class,
            DistanceRecord::class,
            FloorsClimbedRecord::class,
            Vo2MaxRecord::class,
        ),
        supplementary = emptyList(),
    )
    VitalResource.Body -> RecordTypeRequirements(
        required = emptyList(),
        optional = listOf(
            BodyFatRecord::class,
            WeightRecord::class,
        ),
        supplementary = emptyList()
    )
    VitalResource.Sleep -> RecordTypeRequirements(
        required = listOf(SleepSessionRecord::class),
        optional = emptyList(),
        supplementary = listOf(
            HeartRateRecord::class,
            HeartRateVariabilityRmssdRecord::class,
            RespiratoryRateRecord::class,
            RestingHeartRateRecord::class,
            OxygenSaturationRecord::class,
        )
    )
    VitalResource.Workout -> RecordTypeRequirements(
        required = listOf(ExerciseSessionRecord::class),
        optional = listOf(
            ElevationGainedRecord::class,
            SpeedRecord::class,
            PowerRecord::class,
        ),
        supplementary = listOf(
            HeartRateRecord::class,
        ),
    )
    VitalResource.MenstrualCycle -> RecordTypeRequirements(
        required = listOf(MenstruationPeriodRecord::class),
        optional = listOf(
            MenstruationFlowRecord::class,
            CervicalMucusRecord::class,
            OvulationTestRecord::class,
            IntermenstrualBleedingRecord::class,
            SexualActivityRecord::class,
        ),
        supplementary = emptyList(),
    )
}

/**
 * Health Connect record types whose changes should trigger sync of a given VitalResource.
 *
 * This is a subset of [recordTypeDependencies]. Some VitalResources do not need to observe all the
 * record type they read.
 *
 * For example, Sleep only needs to observe SleepSessionRecord changes, while
 * heart rate and other related data during the session can be read on demand as we compute the API
 * payload, on the assumption that these data should have been readily available in the Health
 * Connect data store.
 */
internal fun VitalResource.recordTypeChangesToTriggerSync(): List<KClass<out Record>> = when (this) {
    VitalResource.Water -> listOf(HydrationRecord::class)
    VitalResource.Activity -> listOf(
        ActiveCaloriesBurnedRecord::class,
        TotalCaloriesBurnedRecord::class,
        BasalMetabolicRateRecord::class,
        StepsRecord::class,
        DistanceRecord::class,
        FloorsClimbedRecord::class,
        Vo2MaxRecord::class,
    )
    VitalResource.BloodPressure -> listOf(BloodPressureRecord::class)
    VitalResource.Body -> listOf(BodyFatRecord::class, WeightRecord::class)
    VitalResource.Glucose -> listOf(BloodGlucoseRecord::class)
    VitalResource.HeartRate -> listOf(HeartRateRecord::class)
    VitalResource.HeartRateVariability -> listOf(HeartRateVariabilityRmssdRecord::class)
    VitalResource.Profile -> listOf(HeightRecord::class)
    VitalResource.Sleep -> listOf(SleepSessionRecord::class)
    VitalResource.Workout -> listOf(ExerciseSessionRecord::class)
    VitalResource.MenstrualCycle -> listOf(
        MenstruationPeriodRecord::class,
        MenstruationFlowRecord::class,
        CervicalMucusRecord::class,
        OvulationTestRecord::class,
        IntermenstrualBleedingRecord::class,
        SexualActivityRecord::class,
    )

    VitalResource.ActiveEnergyBurned, VitalResource.BasalEnergyBurned, VitalResource.Steps ->
        throw IllegalArgumentException("Should have been remapped to Activity.")
}
