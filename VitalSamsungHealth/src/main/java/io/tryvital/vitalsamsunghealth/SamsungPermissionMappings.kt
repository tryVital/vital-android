package io.tryvital.vitalsamsunghealth

import io.tryvital.vitalsamsunghealth.healthconnect.client.records.ActiveCaloriesBurnedRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.BasalMetabolicRateRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.BloodGlucoseRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.BloodPressureRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.BodyFatRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.BodyTemperatureRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.CervicalMucusRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.DistanceRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.ElevationGainedRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.ExerciseSessionRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.FloorsClimbedRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.HeartRateRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.HeartRateVariabilityRmssdRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.HeightRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.HydrationRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.IntermenstrualBleedingRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.MenstruationFlowRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.MenstruationPeriodRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.NutritionRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.OvulationTestRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.OxygenSaturationRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.PowerRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.Record
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.RespiratoryRateRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.RestingHeartRateRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.SexualActivityRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.SleepSessionRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.SpeedRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.StepsRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.TotalCaloriesBurnedRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.Vo2MaxRecord
import io.tryvital.vitalsamsunghealth.healthconnect.client.records.WeightRecord
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import kotlin.reflect.KClass

internal fun recordTypeToSamsungDataType(recordType: KClass<out Record>): DataType? = when (recordType) {
    ActiveCaloriesBurnedRecord::class -> DataTypes.ACTIVITY_SUMMARY
    BasalMetabolicRateRecord::class -> DataTypes.BODY_COMPOSITION
    TotalCaloriesBurnedRecord::class -> DataTypes.ACTIVITY_SUMMARY
    StepsRecord::class -> DataTypes.STEPS
    DistanceRecord::class -> DataTypes.ACTIVITY_SUMMARY
    FloorsClimbedRecord::class -> DataTypes.FLOORS_CLIMBED
    Vo2MaxRecord::class -> DataTypes.EXERCISE

    BloodPressureRecord::class -> DataTypes.BLOOD_PRESSURE
    OxygenSaturationRecord::class -> DataTypes.BLOOD_OXYGEN
    BloodGlucoseRecord::class -> DataTypes.BLOOD_GLUCOSE
    HeartRateRecord::class -> DataTypes.HEART_RATE
    RestingHeartRateRecord::class -> DataTypes.HEART_RATE

    HydrationRecord::class -> DataTypes.WATER_INTAKE
    HeightRecord::class -> DataTypes.BODY_COMPOSITION
    WeightRecord::class -> DataTypes.BODY_COMPOSITION
    BodyFatRecord::class -> DataTypes.BODY_COMPOSITION

    BodyTemperatureRecord::class -> DataTypes.BODY_TEMPERATURE

    SleepSessionRecord::class -> DataTypes.SLEEP
    ExerciseSessionRecord::class -> DataTypes.EXERCISE
    NutritionRecord::class -> DataTypes.NUTRITION

    HeartRateVariabilityRmssdRecord::class,
    RespiratoryRateRecord::class,
    MenstruationPeriodRecord::class,
    MenstruationFlowRecord::class,
    CervicalMucusRecord::class,
    OvulationTestRecord::class,
    IntermenstrualBleedingRecord::class,
    SexualActivityRecord::class,
    ElevationGainedRecord::class,
    SpeedRecord::class,
    PowerRecord::class -> null
    else -> null
}

internal fun writableResourceToSamsungDataType(resource: io.tryvital.vitalsamsunghealth.model.WritableVitalResource): DataType = when (resource) {
    io.tryvital.vitalsamsunghealth.model.WritableVitalResource.Water -> DataTypes.WATER_INTAKE
    io.tryvital.vitalsamsunghealth.model.WritableVitalResource.Glucose -> DataTypes.BLOOD_GLUCOSE
}

internal fun permissionKey(dataType: DataType, accessType: AccessType): String {
    return "${accessType.name.lowercase()}:${dataType.name}"
}

internal fun permissionForRecordType(
    recordType: KClass<out Record>,
    accessType: AccessType,
): Permission? {
    val dataType = recordTypeToSamsungDataType(recordType) ?: return null
    return Permission.of(dataType, accessType)
}
