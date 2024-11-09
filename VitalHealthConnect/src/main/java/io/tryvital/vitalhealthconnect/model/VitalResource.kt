package io.tryvital.vitalhealthconnect.model

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

    object Meal: VitalResource("meal")

    override fun toString(): String {
        return name
    }

    val priority: Int get() = when (this) {
        Activity -> 10
        Body -> 11
        MenstrualCycle -> 12
        Profile -> 13
        Sleep -> 20
        BloodPressure -> 21
        Glucose -> 22
        HeartRateVariability -> 23
        Vo2Max -> 24
        Water -> 25
        RespiratoryRate -> 26
        Temperature -> 27
        BloodOxygen -> 28
        Meal -> 29
        Workout -> 31
        Steps -> 51
        DistanceWalkingRunning -> 52
        FloorsClimbed -> 53
        HeartRate -> 91
        ActiveEnergyBurned -> 92
        BasalEnergyBurned -> 93
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
                FloorsClimbed,
                DistanceWalkingRunning,
                Vo2Max,
                Water,
                HeartRateVariability,
                MenstrualCycle,
                RespiratoryRate,
                Temperature,
                BloodOxygen,
                Meal,
            )
        }

        fun valueOf(value: String) = values().first { it.name == value }
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
 *
 * In both cases, `supplementary` is not considered at all.
 *
 * A rule of thumb is that `supplementary` should be used over `optional` when the VitalResource
 * should be considered as INACTIVE iff:
 * 1. ONLY one or more supplementary Record types are granted
 * 2. NONE of the optional Record types are granted.
 * 3. The resource has no required Record types.
 *
 * Some Record types may appear in multiple `VitalResource`s:
 * 1. Each Record type can only be associated with **one** VitalResource as their `required` types.
 * 2. A Record type can be present an `optional` or `supplementary` type as many VitalResources as needed.
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
internal fun VitalResource.remapped(): RemappedVitalResource = RemappedVitalResource(this)

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
    VitalResource.BloodOxygen -> RecordTypeRequirements.single(OxygenSaturationRecord::class)
    VitalResource.Glucose -> RecordTypeRequirements.single(BloodGlucoseRecord::class)
    VitalResource.HeartRate -> RecordTypeRequirements.single(HeartRateRecord::class)
    VitalResource.HeartRateVariability -> RecordTypeRequirements.single(HeartRateVariabilityRmssdRecord::class)

    VitalResource.Water -> RecordTypeRequirements.single(HydrationRecord::class)
    VitalResource.Profile -> RecordTypeRequirements.single(HeightRecord::class)

    VitalResource.Meal -> RecordTypeRequirements.single(NutritionRecord::class)

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
        supplementary = listOf(
        ),
    )
    VitalResource.ActiveEnergyBurned -> RecordTypeRequirements.single(ActiveCaloriesBurnedRecord::class)
    VitalResource.BasalEnergyBurned -> RecordTypeRequirements.single(BasalMetabolicRateRecord::class)
    VitalResource.DistanceWalkingRunning -> RecordTypeRequirements.single(DistanceRecord::class)
    VitalResource.FloorsClimbed -> RecordTypeRequirements.single(FloorsClimbedRecord::class)
    VitalResource.Steps -> RecordTypeRequirements.single(StepsRecord::class)
    VitalResource.Vo2Max -> RecordTypeRequirements.single(Vo2MaxRecord::class)

    VitalResource.RespiratoryRate -> RecordTypeRequirements.single(RespiratoryRateRecord::class)
    VitalResource.Temperature -> RecordTypeRequirements.single(BodyTemperatureRecord::class)

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
    VitalResource.Activity -> listOf()
    VitalResource.Meal -> listOf()
    VitalResource.ActiveEnergyBurned -> listOf(ActiveCaloriesBurnedRecord::class)
    VitalResource.BasalEnergyBurned -> listOf(BasalMetabolicRateRecord::class)
    VitalResource.DistanceWalkingRunning -> listOf(DistanceRecord::class)
    VitalResource.FloorsClimbed -> listOf(FloorsClimbedRecord::class)
    VitalResource.Steps -> listOf(StepsRecord::class)
    VitalResource.Vo2Max -> listOf(Vo2MaxRecord::class)
    VitalResource.BloodOxygen -> listOf(OxygenSaturationRecord::class)
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
    VitalResource.RespiratoryRate -> listOf(RespiratoryRateRecord::class)
    VitalResource.Temperature -> listOf(BodyTemperatureRecord::class)
}
