package io.tryvital.client.services.data

import android.health.connect.datatypes.units.Mass
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import io.tryvital.client.utils.AlwaysSerializeNulls
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@AlwaysSerializeNulls
@JsonClass(generateAdapter = true)
data class HealthConnectRecordCollection(
    @Json(name = "source_bundle")
    val sourceBundle: String,

    @Json(name = "nutrition_records")
    val nutritionRecords: List<NutritionRecord>
)

@JsonClass(generateAdapter = true)
data class NutritionRecord(
    @Json(name = "start_time")
    val startTime: Instant,
    @Json(name = "start_zone_offset")
    val startZoneOffset: String?,
    @Json(name = "end_time")
    val endTime: Instant,
    @Json(name = "end_zone_offset")
    val endZoneOffset: String?,
    val biotin: Double?,
    val caffeine: Double?,
    val calcium: Double?,
    val energy: Double?,
    @Json(name = "energy_from_fat")
    val energyFromFat: Double?,
    val chloride: Double?,
    val cholesterol: Double?,
    val chromium: Double?,
    val copper: Double?,
    @Json(name = "dietary_fiber")
    val dietaryFiber: Double?,
    val folate: Double?,
    @Json(name = "folic_acid")
    val folicAcid: Double?,
    val iodine: Double?,
    val iron: Double?,
    val magnesium: Double?,
    val manganese: Double?,
    val molybdenum: Double?,
    @Json(name = "monounsaturated_fat")
    val monounsaturatedFat: Double?,
    val niacin: Double?,
    @Json(name = "pantothenic_acid")
    val pantothenicAcid: Double?,
    val phosphorus: Double?,
    @Json(name = "polyunsaturated_fat")
    val polyunsaturatedFat: Double?,
    val potassium: Double?,
    val protein: Double?,
    val riboflavin: Double?,
    @Json(name = "saturated_fat")
    val saturatedFat: Double?,
    val selenium: Double?,
    val sodium: Double?,
    val sugar: Double?,
    val thiamin: Double?,
    @Json(name = "total_carbohydrate")
    val totalCarbohydrate: Double?,
    @Json(name = "total_fat")
    val totalFat: Double?,
    @Json(name = "trans_fat")
    val transFat: Double?,
    @Json(name = "unsaturated_fat")
    val unsaturatedFat: Double?,
    @Json(name = "vitamin_a")
    val vitaminA: Double?,
    @Json(name = "vitamin_b12")
    val vitaminB12: Double?,
    @Json(name = "vitamin_b6")
    val vitaminB6: Double?,
    @Json(name = "vitamin_c")
    val vitaminC: Double?,
    @Json(name = "vitamin_d")
    val vitaminD: Double?,
    @Json(name = "vitamin_e")
    val vitaminE: Double?,
    @Json(name = "vitamin_k")
    val vitaminK: Double?,
    val zinc: Double?,
    val name: String?,
    @Json(name = "meal_type")
    val mealType: Int,
    val metadata: Map<String, Map<String, String>>
)
