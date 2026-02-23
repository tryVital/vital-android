package io.tryvital.vitalsamsunghealth.healthconnect.client.records

import io.tryvital.vitalsamsunghealth.healthconnect.client.records.metadata.Metadata
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.BloodGlucose
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.Energy
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.Length
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.Mass
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.Percentage
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.Power
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.Pressure
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.Temperature
import io.tryvital.vitalsamsunghealth.healthconnect.client.units.Volume
import java.time.Instant
import java.time.ZoneOffset

open class Record

class ActiveCaloriesBurnedRecord(
    val startTime: Instant,
    val startZoneOffset: ZoneOffset,
    val endTime: Instant,
    val endZoneOffset: ZoneOffset,
    val energy: Energy,
    val metadata: Metadata,
) : Record()

class BasalMetabolicRateRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val basalMetabolicRate: Power,
    val metadata: Metadata,
) : Record()

class BloodGlucoseRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val metadata: Metadata,
    val level: BloodGlucose,
    val specimenSource: Int,
    val mealType: Int,
    val relationToMeal: Int,
) : Record() {
    companion object {
        const val RELATION_TO_MEAL_UNKNOWN = 0
        const val SPECIMEN_SOURCE_UNKNOWN = 0
    }
}

class BloodPressureRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val metadata: Metadata,
    val systolic: Pressure,
    val diastolic: Pressure,
    val bodyPosition: Int,
    val measurementLocation: Int,
) : Record() {
    companion object {
        const val BODY_POSITION_UNKNOWN = 0
        const val MEASUREMENT_LOCATION_UNKNOWN = 0
    }
}

class BodyFatRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val percentage: Percentage,
    val metadata: Metadata,
) : Record()

class BodyTemperatureRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val metadata: Metadata,
    val temperature: Temperature,
    val measurementLocation: Int,
) : Record()

class CervicalMucusRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val appearance: Int,
    val metadata: Metadata,
) : Record() {
    companion object {
        const val APPEARANCE_CREAMY = 1
        const val APPEARANCE_DRY = 2
        const val APPEARANCE_STICKY = 3
        const val APPEARANCE_EGG_WHITE = 4
        const val APPEARANCE_WATERY = 5
    }
}

class DistanceRecord(
    val startTime: Instant,
    val startZoneOffset: ZoneOffset,
    val endTime: Instant,
    val endZoneOffset: ZoneOffset,
    val distance: Length,
    val metadata: Metadata,
) : Record()

class ElevationGainedRecord : Record()
class SpeedRecord : Record()
class PowerRecord : Record()

class ExerciseSessionRecord(
    val startTime: Instant,
    val startZoneOffset: ZoneOffset,
    val endTime: Instant,
    val endZoneOffset: ZoneOffset,
    val metadata: Metadata,
    val exerciseType: Int,
) : Record() {
    companion object {
        const val EXERCISE_TYPE_OTHER_WORKOUT = 0
        val EXERCISE_TYPE_INT_TO_STRING_MAP: Map<Int, String> = mapOf(
            EXERCISE_TYPE_OTHER_WORKOUT to "workout"
        )
        val EXERCISE_TYPE_STRING_TO_INT_MAP: Map<String, Int> =
            EXERCISE_TYPE_INT_TO_STRING_MAP.entries.associate { (k, v) -> v to k }
    }
}

class FloorsClimbedRecord(
    val startTime: Instant,
    val startZoneOffset: ZoneOffset,
    val endTime: Instant,
    val endZoneOffset: ZoneOffset,
    val floors: Double,
    val metadata: Metadata,
) : Record()

class HeartRateRecord(
    val startTime: Instant,
    val startZoneOffset: ZoneOffset,
    val endTime: Instant,
    val endZoneOffset: ZoneOffset,
    val samples: List<Sample>,
    val metadata: Metadata,
) : Record() {
    class Sample(
        val time: Instant,
        val beatsPerMinute: Long,
    )
}

class HeartRateVariabilityRmssdRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val heartRateVariabilityMillis: Double,
    val metadata: Metadata,
) : Record()

class HeightRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val height: Length,
    val metadata: Metadata,
) : Record()

class HydrationRecord(
    val startTime: Instant,
    val startZoneOffset: ZoneOffset,
    val endTime: Instant,
    val endZoneOffset: ZoneOffset,
    val volume: Volume,
    val metadata: Metadata,
) : Record()

class IntermenstrualBleedingRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val metadata: Metadata,
) : Record()

class MenstruationFlowRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val flow: Int,
    val metadata: Metadata,
) : Record() {
    companion object {
        const val FLOW_UNKNOWN = 0
        const val FLOW_LIGHT = 1
        const val FLOW_MEDIUM = 2
        const val FLOW_HEAVY = 3
    }
}

class MenstruationPeriodRecord(
    val startTime: Instant,
    val startZoneOffset: ZoneOffset?,
    val endTime: Instant,
    val endZoneOffset: ZoneOffset?,
    val metadata: Metadata,
) : Record()

class NutritionRecord(
    val startTime: Instant,
    val startZoneOffset: ZoneOffset,
    val endTime: Instant,
    val endZoneOffset: ZoneOffset,
    val metadata: Metadata,
    val biotin: Mass? = null,
    val caffeine: Mass? = null,
    val calcium: Mass? = null,
    val energy: Energy? = null,
    val energyFromFat: Energy? = null,
    val chloride: Mass? = null,
    val cholesterol: Mass? = null,
    val chromium: Mass? = null,
    val copper: Mass? = null,
    val dietaryFiber: Mass? = null,
    val folate: Mass? = null,
    val folicAcid: Mass? = null,
    val iodine: Mass? = null,
    val iron: Mass? = null,
    val magnesium: Mass? = null,
    val manganese: Mass? = null,
    val molybdenum: Mass? = null,
    val monounsaturatedFat: Mass? = null,
    val niacin: Mass? = null,
    val pantothenicAcid: Mass? = null,
    val phosphorus: Mass? = null,
    val polyunsaturatedFat: Mass? = null,
    val potassium: Mass? = null,
    val protein: Mass? = null,
    val riboflavin: Mass? = null,
    val saturatedFat: Mass? = null,
    val selenium: Mass? = null,
    val sodium: Mass? = null,
    val sugar: Mass? = null,
    val thiamin: Mass? = null,
    val totalCarbohydrate: Mass? = null,
    val totalFat: Mass? = null,
    val transFat: Mass? = null,
    val unsaturatedFat: Mass? = null,
    val vitaminA: Mass? = null,
    val vitaminB12: Mass? = null,
    val vitaminB6: Mass? = null,
    val vitaminC: Mass? = null,
    val vitaminD: Mass? = null,
    val vitaminE: Mass? = null,
    val vitaminK: Mass? = null,
    val zinc: Mass? = null,
    val name: String? = null,
    val mealType: Int = 0,
) : Record()

class OvulationTestRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val result: Int,
    val metadata: Metadata,
) : Record() {
    companion object {
        const val RESULT_INCONCLUSIVE = 0
        const val RESULT_NEGATIVE = 1
        const val RESULT_POSITIVE = 2
        const val RESULT_HIGH = 3
    }
}

class OxygenSaturationRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val percentage: Percentage,
    val metadata: Metadata,
) : Record()

class RespiratoryRateRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val rate: Double,
    val metadata: Metadata,
) : Record()

class RestingHeartRateRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val beatsPerMinute: Long,
    val metadata: Metadata,
) : Record()

class SexualActivityRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val protectionUsed: Int,
    val metadata: Metadata,
) : Record() {
    companion object {
        const val PROTECTION_USED_UNKNOWN = 0
        const val PROTECTION_USED_PROTECTED = 1
        const val PROTECTION_USED_UNPROTECTED = 2
    }
}

class SleepSessionRecord(
    val startTime: Instant,
    val startZoneOffset: ZoneOffset,
    val endTime: Instant,
    val endZoneOffset: ZoneOffset,
    val metadata: Metadata,
    val title: String? = null,
    val notes: String? = null,
    val stages: List<Stage> = emptyList(),
) : Record() {
    class Stage(
        val startTime: Instant,
        val endTime: Instant,
        val stage: Int,
    )

    companion object {
        const val STAGE_TYPE_UNKNOWN = 0
        const val STAGE_TYPE_AWAKE = 1
        const val STAGE_TYPE_SLEEPING = 2
        const val STAGE_TYPE_OUT_OF_BED = 3
        const val STAGE_TYPE_LIGHT = 4
        const val STAGE_TYPE_DEEP = 5
        const val STAGE_TYPE_REM = 6
    }
}

class StepsRecord(
    val startTime: Instant,
    val startZoneOffset: ZoneOffset,
    val endTime: Instant,
    val endZoneOffset: ZoneOffset,
    val count: Long,
    val metadata: Metadata,
) : Record()

class TotalCaloriesBurnedRecord : Record()

class Vo2MaxRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val metadata: Metadata,
    val vo2MillilitersPerMinuteKilogram: Double,
    val measurementMethod: Int,
) : Record() {
    companion object {
        const val MEASUREMENT_METHOD_OTHER = 0
    }
}

class WeightRecord(
    val time: Instant,
    val zoneOffset: ZoneOffset,
    val weight: Mass,
    val metadata: Metadata,
) : Record()
