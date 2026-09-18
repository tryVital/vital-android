package io.tryvital.vitalsamsunghealth

import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.request.DataType
import io.tryvital.vitalsamsunghealth.records.HealthConnectRecordProcessor
import io.tryvital.vitalsamsunghealth.records.RecordAggregator
import io.tryvital.vitalsamsunghealth.records.RecordReader
import io.tryvital.vitalsamsunghealth.records.mealGroupingKey
import io.tryvital.vitalsamsunghealth.records.mealTypeToInt
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

class RecordProcessorNutritionTests {
    private val reader = mock<RecordReader>()
    private val processor = HealthConnectRecordProcessor(reader, mock<RecordAggregator>())

    @Test
    fun `nutrition values preserve Samsung units`() = runTest {
        val point = nutritionPoint(
            start = Instant.parse("2026-09-18T08:00:00Z"),
            mealType = DataType.NutritionType.MealType.BREAKFAST,
            calories = 420.5f,
            title = "Breakfast",
        ) {
            addFieldData(DataType.NutritionType.CALCIUM, 12.5f)
            addFieldData(DataType.NutritionType.CARBOHYDRATE, 31.5f)
            addFieldData(DataType.NutritionType.CHOLESTEROL, 18.5f)
            addFieldData(DataType.NutritionType.DIETARY_FIBER, 4.5f)
            addFieldData(DataType.NutritionType.IRON, 2.5f)
            addFieldData(DataType.NutritionType.MONOSATURATED_FAT, 3.5f)
            addFieldData(DataType.NutritionType.POLYSATURATED_FAT, 1.5f)
            addFieldData(DataType.NutritionType.POTASSIUM, 210.5f)
            addFieldData(DataType.NutritionType.PROTEIN, 22.5f)
            addFieldData(DataType.NutritionType.SATURATED_FAT, 5.5f)
            addFieldData(DataType.NutritionType.SODIUM, 310.5f)
            addFieldData(DataType.NutritionType.SUGAR, 7.5f)
            addFieldData(DataType.NutritionType.TOTAL_FAT, 9.5f)
            addFieldData(DataType.NutritionType.TRANS_FAT, 0.5f)
            addFieldData(DataType.NutritionType.VITAMIN_A, 95.5f)
            addFieldData(DataType.NutritionType.VITAMIN_C, 8.5f)
        }
        whenever(reader.readNutritionRecords(any(), any())).thenReturn(listOf(point))

        val record = processor.processMeals(null, TimeZone.getTimeZone("UTC"))
            .meals.single().healthConnect!!.nutritionRecords.single()

        assertEquals(420.5, record.energy!!, 0.0)
        assertEquals(12.5, record.calcium!!, 0.0)
        assertEquals(31.5, record.totalCarbohydrate!!, 0.0)
        assertEquals(18.5, record.cholesterol!!, 0.0)
        assertEquals(4.5, record.dietaryFiber!!, 0.0)
        assertEquals(2.5, record.iron!!, 0.0)
        assertEquals(3.5, record.monounsaturatedFat!!, 0.0)
        assertEquals(1.5, record.polyunsaturatedFat!!, 0.0)
        assertEquals(210.5, record.potassium!!, 0.0)
        assertEquals(22.5, record.protein!!, 0.0)
        assertEquals(5.5, record.saturatedFat!!, 0.0)
        assertEquals(310.5, record.sodium!!, 0.0)
        assertEquals(7.5, record.sugar!!, 0.0)
        assertEquals(9.5, record.totalFat!!, 0.0)
        assertEquals(0.5, record.transFat!!, 0.0)
        assertEquals(95.5, record.vitaminA!!, 0.0)
        assertEquals(8.5, record.vitaminC!!, 0.0)
        assertEquals("Breakfast", record.name)
        assertEquals(1, record.mealType)
    }

    @Test
    fun `nutrition records group by source meal type and local date`() = runTest {
        val records = listOf(
            nutritionPoint(Instant.parse("2026-09-18T08:00:00Z"), DataType.NutritionType.MealType.BREAKFAST),
            nutritionPoint(Instant.parse("2026-09-18T08:30:00Z"), DataType.NutritionType.MealType.BREAKFAST),
            nutritionPoint(Instant.parse("2026-09-18T12:00:00Z"), DataType.NutritionType.MealType.LUNCH),
            nutritionPoint(Instant.parse("2026-09-19T08:00:00Z"), DataType.NutritionType.MealType.BREAKFAST),
        )
        whenever(reader.readNutritionRecords(any(), any())).thenReturn(records)

        val meals = processor.processMeals(null, TimeZone.getTimeZone("UTC")).meals

        assertEquals(3, meals.size)
        assertEquals(
            listOf(1, 1, 2),
            meals.map { it.healthConnect!!.nutritionRecords.size }.sorted(),
        )

        assertNotEquals(
            mealGroupingKey(
                "source-a",
                1,
                Instant.parse("2026-09-18T08:00:00Z"),
                ZoneOffset.UTC,
                ZoneId.of("UTC"),
            ),
            mealGroupingKey(
                "source-b",
                1,
                Instant.parse("2026-09-18T08:00:00Z"),
                ZoneOffset.UTC,
                ZoneId.of("UTC"),
            ),
        )
    }

    @Test
    fun `all Samsung meal types map to backend meal types`() {
        assertEquals(0, mealTypeToInt(null))
        assertEquals(0, mealTypeToInt(DataType.NutritionType.MealType.UNDEFINED))
        assertEquals(1, mealTypeToInt(DataType.NutritionType.MealType.BREAKFAST))
        assertEquals(2, mealTypeToInt(DataType.NutritionType.MealType.LUNCH))
        assertEquals(3, mealTypeToInt(DataType.NutritionType.MealType.DINNER))
        assertEquals(4, mealTypeToInt(DataType.NutritionType.MealType.MORNING_SNACK))
        assertEquals(4, mealTypeToInt(DataType.NutritionType.MealType.AFTERNOON_SNACK))
        assertEquals(4, mealTypeToInt(DataType.NutritionType.MealType.EVENING_SNACK))
    }

    private fun nutritionPoint(
        start: Instant,
        mealType: DataType.NutritionType.MealType,
        calories: Float = 0f,
        title: String = "Meal",
        fields: HealthDataPoint.Builder.() -> Unit = {},
    ): HealthDataPoint {
        return HealthDataPoint.builder()
            .setDeviceId("test-device")
            .setStartTime(start, ZoneOffset.UTC)
            .setEndTime(start.plusSeconds(60), ZoneOffset.UTC)
            .addFieldData(DataType.NutritionType.MEAL_TYPE, mealType)
            .addFieldData(DataType.NutritionType.CALORIES, calories)
            .addFieldData(DataType.NutritionType.TITLE, title)
            .apply(fields)
            .build()
    }
}
