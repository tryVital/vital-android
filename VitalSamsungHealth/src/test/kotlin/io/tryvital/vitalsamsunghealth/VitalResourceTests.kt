package io.tryvital.vitalsamsunghealth

import com.samsung.android.sdk.health.data.request.DataTypes
import io.tryvital.vitalhealthcore.model.VitalResource
import io.tryvital.vitalhealthcore.model.remapped
import io.tryvital.vitalsamsunghealth.model.RecordTypeRequirements
import io.tryvital.vitalsamsunghealth.model.SamsungRecordType
import io.tryvital.vitalsamsunghealth.model.dataTypeChangesToTriggerSync
import io.tryvital.vitalsamsunghealth.model.recordTypeChangesToTriggerSync
import io.tryvital.vitalsamsunghealth.model.recordTypeDependencies
import io.tryvital.vitalsamsunghealth.model.supportedBySamsungDataApi
import org.junit.Assert
import org.junit.Test

class VitalResourceTests {
    @Test
    fun `RecordTypeRequirements singleObjectType`() {
        val requirements = RecordTypeRequirements(
            required = listOf(SamsungRecordType.Steps),
            optional = emptyList(),
            supplementary = emptyList(),
        )

        Assert.assertTrue(requirements.isResourceActive { true })
        Assert.assertFalse(requirements.isResourceActive { false })
    }

    @Test
    fun `RecordTypeRequirements multipleRequiredObjectTypes`() {
        val requirements = RecordTypeRequirements(
            required = listOf(
                SamsungRecordType.Steps,
                SamsungRecordType.ActiveCaloriesBurned,
                SamsungRecordType.Speed,
            ),
            optional = emptyList(),
            supplementary = emptyList(),
        )

        Assert.assertTrue(requirements.isResourceActive { true })
        Assert.assertFalse(requirements.isResourceActive { type -> type != SamsungRecordType.ActiveCaloriesBurned })
        Assert.assertFalse(requirements.isResourceActive { false })
    }

    @Test
    fun `RecordTypeRequirements SomeRequiredSomeOptionalTypes`() {
        val requirements = RecordTypeRequirements(
            required = listOf(SamsungRecordType.Steps, SamsungRecordType.Speed),
            optional = listOf(SamsungRecordType.ActiveCaloriesBurned),
            supplementary = emptyList(),
        )

        Assert.assertTrue(requirements.isResourceActive { true })
        Assert.assertFalse(requirements.isResourceActive { type -> type == SamsungRecordType.Speed })
        Assert.assertTrue(requirements.isResourceActive { type -> type != SamsungRecordType.ActiveCaloriesBurned })
        Assert.assertFalse(requirements.isResourceActive { false })
    }

    @Test
    fun `RecordTypeRequirements noRequiredMultipleOptionalTypes`() {
        val requirements = RecordTypeRequirements(
            required = emptyList(),
            optional = listOf(
                SamsungRecordType.Steps,
                SamsungRecordType.ActiveCaloriesBurned,
                SamsungRecordType.Speed,
            ),
            supplementary = emptyList(),
        )

        Assert.assertTrue(requirements.isResourceActive { true })
        Assert.assertTrue(requirements.isResourceActive { type -> type == SamsungRecordType.ActiveCaloriesBurned })
        Assert.assertFalse(requirements.isResourceActive { false })
    }

    @Test
    fun `RecordTypeRequirements empty`() {
        val requirements = RecordTypeRequirements(
            required = emptyList(),
            optional = emptyList(),
            supplementary = emptyList(),
        )

        Assert.assertFalse(requirements.isResourceActive { true })
        Assert.assertFalse(requirements.isResourceActive { false })
    }

    @Test
    fun `RecordTypeRequirements does_not_overlap`() {
        val claimedObjectTypes = mutableSetOf<SamsungRecordType>()
        val remappedResources = VitalResource.values().mapTo(mutableSetOf()) { it.remapped() }

        for (resource in remappedResources) {
            val requirements = resource.wrapped.recordTypeDependencies()
            val isNonoverlapping = requirements.required.intersect(claimedObjectTypes).isEmpty()

            Assert.assertTrue("$resource overlaps with another VitalResource.", isNonoverlapping)
            claimedObjectTypes.addAll(requirements.required)
        }
    }

    @Test
    fun `Sleep includes skin temperature as supplementary data`() {
        Assert.assertTrue(
            SamsungRecordType.SkinTemperature in VitalResource.Sleep.recordTypeDependencies().supplementary
        )
    }

    @Test
    fun `Meal is supported and observes nutrition changes`() {
        Assert.assertTrue(VitalResource.Meal.supportedBySamsungDataApi())
        Assert.assertEquals(
            listOf(DataTypes.NUTRITION),
            VitalResource.Meal.dataTypeChangesToTriggerSync(),
        )
        Assert.assertEquals(
            listOf(SamsungRecordType.Nutrition),
            VitalResource.Meal.recordTypeChangesToTriggerSync(),
        )
    }
}
