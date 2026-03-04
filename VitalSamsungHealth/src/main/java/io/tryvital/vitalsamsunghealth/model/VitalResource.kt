package io.tryvital.vitalsamsunghealth.model

import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import io.tryvital.vitalhealthcore.model.VitalResource
import io.tryvital.vitalhealthcore.model.remapped as coreRemapped

internal data class RecordTypeRequirements(
    val required: List<SamsungRecordType>,
    val optional: List<SamsungRecordType>,
    val supplementary: List<SamsungRecordType>,
) {
    fun isResourceActive(query: (SamsungRecordType) -> Boolean): Boolean {
        return if (required.isEmpty()) optional.any(query) else required.all(query)
    }

    val allRecordTypes: Set<SamsungRecordType>
        get() = required.toSet().union(optional).union(supplementary)

    companion object {
        fun single(recordType: SamsungRecordType): RecordTypeRequirements {
            return RecordTypeRequirements(required = listOf(recordType), optional = emptyList(), supplementary = emptyList())
        }
    }
}

internal fun VitalResource.remapped(): io.tryvital.vitalhealthcore.model.RemappedVitalResource = coreRemapped()

internal fun VitalResource.recordTypeDependencies(): RecordTypeRequirements = when (this) {
    VitalResource.ActiveEnergyBurned -> RecordTypeRequirements.single(SamsungRecordType.ActiveCaloriesBurned)
    VitalResource.BasalEnergyBurned -> RecordTypeRequirements.single(SamsungRecordType.BasalMetabolicRate)
    VitalResource.DistanceWalkingRunning -> RecordTypeRequirements.single(SamsungRecordType.Distance)
    VitalResource.FloorsClimbed -> RecordTypeRequirements.single(SamsungRecordType.FloorsClimbed)
    VitalResource.Steps -> RecordTypeRequirements.single(SamsungRecordType.Steps)
    VitalResource.Vo2Max -> RecordTypeRequirements.single(SamsungRecordType.Vo2Max)

    VitalResource.BloodPressure -> RecordTypeRequirements.single(SamsungRecordType.BloodPressure)
    VitalResource.BloodOxygen -> RecordTypeRequirements.single(SamsungRecordType.OxygenSaturation)
    VitalResource.Glucose -> RecordTypeRequirements.single(SamsungRecordType.BloodGlucose)
    VitalResource.HeartRate -> RecordTypeRequirements.single(SamsungRecordType.HeartRate)
    VitalResource.HeartRateVariability -> RecordTypeRequirements.single(SamsungRecordType.HeartRateVariabilityRmssd)

    VitalResource.Water -> RecordTypeRequirements.single(SamsungRecordType.Hydration)
    VitalResource.Profile -> RecordTypeRequirements.single(SamsungRecordType.Height)
    VitalResource.Meal -> RecordTypeRequirements.single(SamsungRecordType.Nutrition)

    VitalResource.Activity -> RecordTypeRequirements(
        required = emptyList(),
        optional = listOf(
            SamsungRecordType.ActiveCaloriesBurned,
            SamsungRecordType.BasalMetabolicRate,
            SamsungRecordType.TotalCaloriesBurned,
            SamsungRecordType.Steps,
            SamsungRecordType.Distance,
            SamsungRecordType.FloorsClimbed,
            SamsungRecordType.Vo2Max,
        ),
        supplementary = emptyList(),
    )

    VitalResource.RespiratoryRate -> RecordTypeRequirements.single(SamsungRecordType.RespiratoryRate)
    VitalResource.Temperature -> RecordTypeRequirements.single(SamsungRecordType.BodyTemperature)

    VitalResource.Body -> RecordTypeRequirements(
        required = emptyList(),
        optional = listOf(SamsungRecordType.BodyFat, SamsungRecordType.Weight),
        supplementary = emptyList(),
    )

    VitalResource.Sleep -> RecordTypeRequirements(
        required = listOf(SamsungRecordType.SleepSession),
        optional = emptyList(),
        supplementary = listOf(
            SamsungRecordType.HeartRate,
            SamsungRecordType.HeartRateVariabilityRmssd,
            SamsungRecordType.RespiratoryRate,
            SamsungRecordType.OxygenSaturation,
        ),
    )

    VitalResource.Workout -> RecordTypeRequirements(
        required = listOf(SamsungRecordType.ExerciseSession),
        optional = listOf(
            SamsungRecordType.ElevationGained,
            SamsungRecordType.Speed,
            SamsungRecordType.Power,
        ),
        supplementary = listOf(SamsungRecordType.HeartRate),
    )

    VitalResource.MenstrualCycle -> RecordTypeRequirements(
        required = listOf(SamsungRecordType.MenstruationPeriod),
        optional = listOf(
            SamsungRecordType.MenstruationFlow,
            SamsungRecordType.CervicalMucus,
            SamsungRecordType.OvulationTest,
            SamsungRecordType.IntermenstrualBleeding,
            SamsungRecordType.SexualActivity,
        ),
        supplementary = emptyList(),
    )
}

internal fun VitalResource.supportedBySamsungDataApi(): Boolean = when (this) {
    VitalResource.HeartRateVariability -> false
    VitalResource.MenstrualCycle -> false
    VitalResource.RespiratoryRate -> false
    VitalResource.Meal -> false
    else -> true
}

internal fun VitalResource.recordTypeChangesToTriggerSync(): List<SamsungRecordType> = when (this) {
    VitalResource.Water -> listOf(SamsungRecordType.Hydration)
    VitalResource.Activity -> emptyList()
    VitalResource.Meal -> emptyList()
    VitalResource.ActiveEnergyBurned -> listOf(SamsungRecordType.ActiveCaloriesBurned)
    VitalResource.BasalEnergyBurned -> listOf(SamsungRecordType.BasalMetabolicRate)
    VitalResource.DistanceWalkingRunning -> listOf(SamsungRecordType.Distance)
    VitalResource.FloorsClimbed -> listOf(SamsungRecordType.FloorsClimbed)
    VitalResource.Steps -> listOf(SamsungRecordType.Steps)
    VitalResource.Vo2Max -> listOf(SamsungRecordType.Vo2Max)
    VitalResource.BloodOxygen -> listOf(SamsungRecordType.OxygenSaturation)
    VitalResource.BloodPressure -> listOf(SamsungRecordType.BloodPressure)
    VitalResource.Body -> listOf(SamsungRecordType.BodyFat, SamsungRecordType.Weight)
    VitalResource.Glucose -> listOf(SamsungRecordType.BloodGlucose)
    VitalResource.HeartRate -> listOf(SamsungRecordType.HeartRate)
    VitalResource.HeartRateVariability -> listOf(SamsungRecordType.HeartRateVariabilityRmssd)
    VitalResource.Profile -> listOf(SamsungRecordType.Height)
    VitalResource.Sleep -> listOf(SamsungRecordType.SleepSession)
    VitalResource.Workout -> listOf(SamsungRecordType.ExerciseSession)
    VitalResource.MenstrualCycle -> listOf(
        SamsungRecordType.MenstruationPeriod,
        SamsungRecordType.MenstruationFlow,
        SamsungRecordType.CervicalMucus,
        SamsungRecordType.OvulationTest,
        SamsungRecordType.IntermenstrualBleeding,
        SamsungRecordType.SexualActivity,
    )
    VitalResource.RespiratoryRate -> listOf(SamsungRecordType.RespiratoryRate)
    VitalResource.Temperature -> listOf(SamsungRecordType.BodyTemperature)
}

internal fun VitalResource.dataTypeChangesToTriggerSync(): List<DataType> = when (this) {
    VitalResource.Water -> listOf(DataTypes.WATER_INTAKE)
    VitalResource.ActiveEnergyBurned -> emptyList()
    VitalResource.BasalEnergyBurned -> listOf(DataTypes.BODY_COMPOSITION)
    VitalResource.DistanceWalkingRunning -> emptyList()
    VitalResource.FloorsClimbed -> listOf(DataTypes.FLOORS_CLIMBED)
    VitalResource.Steps -> emptyList()
    VitalResource.Vo2Max -> listOf(DataTypes.EXERCISE)
    VitalResource.BloodOxygen -> listOf(DataTypes.BLOOD_OXYGEN)
    VitalResource.BloodPressure -> listOf(DataTypes.BLOOD_PRESSURE)
    VitalResource.Body -> listOf(DataTypes.BODY_COMPOSITION)
    VitalResource.Glucose -> listOf(DataTypes.BLOOD_GLUCOSE)
    VitalResource.HeartRate -> listOf(DataTypes.HEART_RATE)
    VitalResource.HeartRateVariability -> emptyList()
    VitalResource.Profile -> listOf(DataTypes.BODY_COMPOSITION)
    VitalResource.Sleep -> listOf(DataTypes.SLEEP)
    VitalResource.Workout -> listOf(DataTypes.EXERCISE)
    VitalResource.MenstrualCycle -> emptyList()
    VitalResource.RespiratoryRate -> emptyList()
    VitalResource.Temperature -> listOf(DataTypes.BODY_TEMPERATURE)
    VitalResource.Activity -> emptyList()
    VitalResource.Meal -> emptyList()
}
