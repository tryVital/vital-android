package io.tryvital.vitalsamsunghealth

import android.content.SharedPreferences
import io.tryvital.vitalhealthcore.model.VitalResource
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ChangeTimeSyncStateMigrationTests {
    private val preferences = mock<SharedPreferences>()
    private val editor = mock<SharedPreferences.Editor>()

    @Test
    fun `migration resets only resources which support change reads`() {
        whenever(
            preferences.getInt(UnSecurePrefKeys.changeTimeSyncStateMigrationVersionKey, 0)
        ).thenReturn(CHANGE_TIME_SYNC_STATE_MIGRATION_VERSION - 1)
        whenever(preferences.edit()).thenReturn(editor)

        preferences.migrateChangeTimeSyncState()

        val affectedResources = setOf(
            VitalResource.Water,
            VitalResource.BasalEnergyBurned,
            VitalResource.FloorsClimbed,
            VitalResource.Vo2Max,
            VitalResource.BloodOxygen,
            VitalResource.BloodPressure,
            VitalResource.Body,
            VitalResource.Glucose,
            VitalResource.HeartRate,
            VitalResource.Profile,
            VitalResource.Sleep,
            VitalResource.Workout,
            VitalResource.Temperature,
            VitalResource.Meal,
        )

        affectedResources.forEach { resource ->
            verify(editor).remove(UnSecurePrefKeys.syncStateKey(resource))
            verify(editor).remove(UnSecurePrefKeys.monitoringTypesKey(resource))
        }
        (VitalResource.values().toSet() - affectedResources).forEach { resource ->
            verify(editor, never()).remove(UnSecurePrefKeys.syncStateKey(resource))
            verify(editor, never()).remove(UnSecurePrefKeys.monitoringTypesKey(resource))
        }
        verify(editor).putInt(
            UnSecurePrefKeys.changeTimeSyncStateMigrationVersionKey,
            CHANGE_TIME_SYNC_STATE_MIGRATION_VERSION,
        )
        verify(editor).apply()
    }

    @Test
    fun `completed migration is not repeated`() {
        whenever(
            preferences.getInt(UnSecurePrefKeys.changeTimeSyncStateMigrationVersionKey, 0)
        ).thenReturn(CHANGE_TIME_SYNC_STATE_MIGRATION_VERSION)

        preferences.migrateChangeTimeSyncState()

        verify(preferences, never()).edit()
    }
}
