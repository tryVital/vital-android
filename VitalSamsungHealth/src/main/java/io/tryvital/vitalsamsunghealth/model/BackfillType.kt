package io.tryvital.vitalsamsunghealth.model
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
internal enum class BackfillType {
    @Json(name = "workouts")                    WORKOUTS,
    @Json(name = "activity")                    ACTIVITY,
    @Json(name = "sleep")                       SLEEP,
    @Json(name = "body")                        BODY,
    @Json(name = "workout_stream")              WORKOUT_STREAM,
    @Json(name = "sleep_stream")                SLEEP_STREAM,
    @Json(name = "profile")                     PROFILE,
    @Json(name = "blood_pressure")              BLOOD_PRESSURE,
    @Json(name = "blood_oxygen")                BLOOD_OXYGEN,
    @Json(name = "glucose")                     GLUCOSE,
    @Json(name = "heartrate")                   HEARTRATE,
    @Json(name = "heartrate_variability")       HEARTRATE_VARIABILITY,
    @Json(name = "weight")                      WEIGHT,
    @Json(name = "fat")                         FAT,
    @Json(name = "meal")                        MEAL,
    @Json(name = "water")                       WATER,
    @Json(name = "caffeine")                    CAFFEINE,
    @Json(name = "mindfulness_minutes")         MINDFULNESS_MINUTES,
    @Json(name = "calories_active")             CALORIES_ACTIVE,
    @Json(name = "calories_basal")              CALORIES_BASAL,
    @Json(name = "distance")                    DISTANCE,
    @Json(name = "floors_climbed")              FLOORS_CLIMBED,
    @Json(name = "steps")                       STEPS,
    @Json(name = "respiratory_rate")            RESPIRATORY_RATE,
    @Json(name = "vo2_max")                     VO2_MAX,
    @Json(name = "stress")                      STRESS,
    @Json(name = "electrocardiogram")           ELECTROCARDIOGRAM,
    @Json(name = "temperature")                 TEMPERATURE,
    @Json(name = "menstrual_cycle")             MENSTRUAL_CYCLE,
    @Json(name = "heart_rate_alert")            HEART_RATE_ALERT,
    @Json(name = "afib_burden")                 AFIB_BURDEN,
    @Json(name = "stand_hour")                  STAND_HOUR,
    @Json(name = "stand_duration")              STAND_DURATION,
    @Json(name = "sleep_apnea_alert")           SLEEP_APNEA_ALERT,
    @Json(name = "sleep_breathing_disturbance") SLEEP_BREATHING_DISTURBANCE,
    @Json(name = "wheelchair_push")             WHEELCHAIR_PUSH,
    @Json(name = "forced_expiratory_volume_1")  FORCED_EXPIRATORY_VOLUME_1,
    @Json(name = "forced_vital_capacity")       FORCED_VITAL_CAPACITY,
    @Json(name = "peak_expiratory_flow_rate")   PEAK_EXPIRATORY_FLOW_RATE,
    @Json(name = "inhaler_usage")               INHALER_USAGE,
    @Json(name = "fall")                        FALL,
    @Json(name = "uv_exposure")                 UV_EXPOSURE,
    @Json(name = "daylight_exposure")           DAYLIGHT_EXPOSURE,
    @Json(name = "handwashing")                 HANDWASHING,
    @Json(name = "basal_body_temperature")      BASAL_BODY_TEMPERATURE,
    @Json(name = "heart_rate_recovery_one_minute") HEART_RATE_RECOVERY_ONE_MINUTE;
}

internal val VitalResource.backfillType get() = when (this) {
    VitalResource.ActiveEnergyBurned -> BackfillType.CALORIES_ACTIVE
    VitalResource.Activity -> BackfillType.ACTIVITY
    VitalResource.BasalEnergyBurned -> BackfillType.CALORIES_BASAL
    VitalResource.BloodOxygen -> BackfillType.BLOOD_OXYGEN
    VitalResource.BloodPressure -> BackfillType.BLOOD_PRESSURE
    VitalResource.Body -> BackfillType.BODY
    VitalResource.DistanceWalkingRunning -> BackfillType.DISTANCE
    VitalResource.FloorsClimbed -> BackfillType.FLOORS_CLIMBED
    VitalResource.Glucose -> BackfillType.GLUCOSE
    VitalResource.HeartRate -> BackfillType.HEARTRATE
    VitalResource.HeartRateVariability -> BackfillType.HEARTRATE_VARIABILITY
    VitalResource.Meal -> BackfillType.MEAL
    VitalResource.MenstrualCycle -> BackfillType.MENSTRUAL_CYCLE
    VitalResource.Profile -> BackfillType.PROFILE
    VitalResource.RespiratoryRate -> BackfillType.RESPIRATORY_RATE
    VitalResource.Sleep -> BackfillType.SLEEP
    VitalResource.Steps -> BackfillType.STEPS
    VitalResource.Temperature -> BackfillType.TEMPERATURE
    VitalResource.Vo2Max -> BackfillType.VO2_MAX
    VitalResource.Water -> BackfillType.WATER
    VitalResource.Workout -> BackfillType.WORKOUTS
}