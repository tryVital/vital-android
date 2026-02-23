package io.tryvital.vitalhealthconnect

import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import io.tryvital.vitalhealthconnect.model.RecordTypeRequirements
import io.tryvital.vitalhealthconnect.model.recordTypeDependencies
import io.tryvital.vitalhealthconnect.model.remapped
import io.tryvital.vitalhealthcore.model.VitalResource
import org.junit.Assert
import org.junit.Test
import kotlin.reflect.KClass

class VitalResourceTests {
    @Test
    fun `RecordTypeRequirements singleObjectType`() {
        val requirements = RecordTypeRequirements (
            required = listOf(StepsRecord::class),
            optional = emptyList(),
            supplementary = emptyList(),
        )

        // required[0] has been asked
        Assert.assertTrue(requirements.isResourceActive { true })

        // required[0] has not been asked
        Assert.assertFalse(requirements.isResourceActive { false })
    }

    @Test
    fun `RecordTypeRequirements multipleRequiredObjectTypes`() {
        val requirements = RecordTypeRequirements (
            required = listOf(
                StepsRecord::class,
                ActiveCaloriesBurnedRecord::class,
                SpeedRecord::class,
            ),
            optional = emptyList(),
            supplementary = emptyList()
        )

        // All asked
        Assert.assertTrue(requirements.isResourceActive { true })

        // All asked, except for ActiveCaloriesBurnedRecord::class
        Assert.assertFalse(requirements.isResourceActive { type -> type != ActiveCaloriesBurnedRecord::class })

        // All have not been asked
        Assert.assertFalse(requirements.isResourceActive { false })
    }

    @Test
    fun `RecordTypeRequirements SomeRequiredSomeOptionalTypes`() {
        val requirements = RecordTypeRequirements (
            required = listOf(
                StepsRecord::class,
                SpeedRecord::class,
            ),
            optional = listOf(
                ActiveCaloriesBurnedRecord::class,
            ),
            supplementary = emptyList()
        )

        // All asked
        Assert.assertTrue(requirements.isResourceActive { true })

        // Only SpeedRecord::class has been asked
        Assert.assertFalse(requirements.isResourceActive { type -> type == SpeedRecord::class })

        // All except ActiveCaloriesBurnedRecord::class have been asked
        Assert.assertTrue(requirements.isResourceActive { type -> type != ActiveCaloriesBurnedRecord::class })

        // All have not been asked
        Assert.assertFalse(requirements.isResourceActive { false })
    }

    @Test
    fun `RecordTypeRequirements noRequiredMultipleOptionalTypes`() {
        val requirements = RecordTypeRequirements (
            required = emptyList(),
            optional = listOf(
                StepsRecord::class,
                ActiveCaloriesBurnedRecord::class,
                SpeedRecord::class,
            ),
            supplementary = emptyList()
        )

        // All asked
        Assert.assertTrue(requirements.isResourceActive { true })

        // Only ActiveCaloriesBurnedRecord::class has been asked
        Assert.assertTrue(requirements.isResourceActive { type -> type == ActiveCaloriesBurnedRecord::class })

        // All have not been asked
        Assert.assertFalse(requirements.isResourceActive { false })
    }

    @Test
    fun `RecordTypeRequirements empty`() {
        val requirements = RecordTypeRequirements (
            required = emptyList(),
            optional = emptyList(),
            supplementary = emptyList()
        )

        Assert.assertFalse(requirements.isResourceActive { true })
        Assert.assertFalse(requirements.isResourceActive { false })
    }


    @Test
    fun `RecordTypeRequirements does_not_overlap`() {
        val claimedObjectTypes = mutableSetOf<KClass<out Record>>()
        val remappedResources = VitalResource.values().mapTo(mutableSetOf()) { it.remapped() }

        for (resource in remappedResources) {
            val requirements = resource.wrapped.recordTypeDependencies()

            val isNonoverlapping = requirements.required.intersect(claimedObjectTypes).isEmpty()

            Assert.assertTrue(
                "$resource overlaps with another VitalResource.",
                isNonoverlapping
            )

            // NOTE = A VitalResource can declare `requirements.supplementary` that overlaps with other
            //       resource.
            claimedObjectTypes.addAll(requirements.required)
        }
    }
}