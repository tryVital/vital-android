package io.tryvital.vitalsamsunghealth

import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import io.tryvital.vitalsamsunghealth.model.SamsungRecordType

internal fun recordTypeToSamsungDataType(recordType: SamsungRecordType): DataType? = when (recordType) {
    SamsungRecordType.ActiveCaloriesBurned -> DataTypes.ACTIVITY_SUMMARY
    SamsungRecordType.BasalMetabolicRate -> DataTypes.BODY_COMPOSITION
    SamsungRecordType.TotalCaloriesBurned -> DataTypes.ACTIVITY_SUMMARY
    SamsungRecordType.Steps -> DataTypes.STEPS
    SamsungRecordType.Distance -> DataTypes.ACTIVITY_SUMMARY
    SamsungRecordType.FloorsClimbed -> DataTypes.FLOORS_CLIMBED
    SamsungRecordType.Vo2Max -> DataTypes.EXERCISE

    SamsungRecordType.BloodPressure -> DataTypes.BLOOD_PRESSURE
    SamsungRecordType.OxygenSaturation -> DataTypes.BLOOD_OXYGEN
    SamsungRecordType.BloodGlucose -> DataTypes.BLOOD_GLUCOSE
    SamsungRecordType.HeartRate,
    SamsungRecordType.RestingHeartRate -> DataTypes.HEART_RATE

    SamsungRecordType.Hydration -> DataTypes.WATER_INTAKE
    SamsungRecordType.Height,
    SamsungRecordType.Weight,
    SamsungRecordType.BodyFat -> DataTypes.BODY_COMPOSITION

    SamsungRecordType.BodyTemperature -> DataTypes.BODY_TEMPERATURE

    SamsungRecordType.SleepSession -> DataTypes.SLEEP
    SamsungRecordType.ExerciseSession -> DataTypes.EXERCISE
    SamsungRecordType.Nutrition -> DataTypes.NUTRITION

    SamsungRecordType.HeartRateVariabilityRmssd,
    SamsungRecordType.RespiratoryRate,
    SamsungRecordType.MenstruationPeriod,
    SamsungRecordType.MenstruationFlow,
    SamsungRecordType.CervicalMucus,
    SamsungRecordType.OvulationTest,
    SamsungRecordType.IntermenstrualBleeding,
    SamsungRecordType.SexualActivity,
    SamsungRecordType.ElevationGained,
    SamsungRecordType.Speed,
    SamsungRecordType.Power -> null
}

internal fun writableResourceToSamsungDataType(resource: io.tryvital.vitalhealthcore.model.WritableVitalResource): DataType = when (resource) {
    io.tryvital.vitalhealthcore.model.WritableVitalResource.Water -> DataTypes.WATER_INTAKE
    io.tryvital.vitalhealthcore.model.WritableVitalResource.Glucose -> DataTypes.BLOOD_GLUCOSE
}

internal fun permissionKey(dataType: DataType, accessType: AccessType): String {
    return "${accessType.name.lowercase()}:${dataType.name}"
}

internal fun permissionForRecordType(
    recordType: SamsungRecordType,
    accessType: AccessType,
): Permission? {
    val dataType = recordTypeToSamsungDataType(recordType) ?: return null
    return Permission.of(dataType, accessType)
}
