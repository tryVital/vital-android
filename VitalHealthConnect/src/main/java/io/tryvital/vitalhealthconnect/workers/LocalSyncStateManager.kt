package io.tryvital.vitalhealthconnect.workers

import UserSDKSyncStateBody
import android.content.SharedPreferences
import io.tryvital.client.VitalClient
import io.tryvital.client.createConnectedSourceIfNotExist
import io.tryvital.client.services.VitalPrivateApi
import io.tryvital.client.services.data.ManualProviderSlug
import io.tryvital.client.utils.VitalLogger
import io.tryvital.vitalhealthconnect.UnSecurePrefKeys
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.TimeZone

internal class ConnectionPaused: Throwable()

internal class LocalSyncStateManager(
    private val vitalClient: VitalClient,
    private val vitalLogger: VitalLogger,
    private val preferences: SharedPreferences,
) {

    private fun getPersistedLocalSyncState(): LocalSyncState?
        = preferences.getJson(UnSecurePrefKeys.localSyncStateKey)

    private fun setPersistedLocalSyncState(newValue: LocalSyncState) {
        preferences.edit()
            .putJson(UnSecurePrefKeys.localSyncStateKey, newValue)
            .apply()
    }

    @OptIn(VitalPrivateApi::class)
    suspend fun getLocalSyncState(): LocalSyncState {
        // If we have a LocalSyncState with valid TTL, return it.
        val state = getPersistedLocalSyncState()
        if (state != null && state.expiresAt > Instant.now()) {
            return state
        }

        return localSyncStateMutex.withLock {
            // Double check if a LocalSyncState could have already been computed by the previous
            // lock holder.
            val previousState = getPersistedLocalSyncState()
            if (previousState != null && previousState.expiresAt > Instant.now()) {
                return@withLock previousState
            }

            vitalLogger.info { "LocalSyncState: revalidating" }

            /// Make sure the user has a connected source set up
            vitalClient.createConnectedSourceIfNotExist(ManualProviderSlug.HealthConnect)

            val now = Instant.now()

            // Health Connect limits historical query to first connection date mins 30 days.
            val numberOfDaysToBackfill = minOf(preferences.getInt(UnSecurePrefKeys.numberOfDaysToBackFillKey, 30).toLong(), 30)
            val proposedStart = now.minus(numberOfDaysToBackfill, ChronoUnit.DAYS)
            val backendState = vitalClient.vitalPrivateService.healthConnectSdkSyncState(
                VitalClient.checkUserId(),
                UserSDKSyncStateBody(
                    tzinfo = TimeZone.getDefault().id,
                    requestStartDate = proposedStart,
                    requestEndDate = now,
                )
            )

            if (backendState.status != UserSDKSyncStatus.Active) {
                vitalLogger.info { "LocalSyncState: connection is paused" }
                throw ConnectionPaused()
            }

            val newState = LocalSyncState(
                // Historical start date is generally fixed once generated the first time, until signOut() reset.
                //
                // The only exception is if an ingestion start was set, in which case the most up-to-date
                // ingestion start date takes precedence.
                historicalStageAnchor = backendState.requestStartDate ?: previousState?.historicalStageAnchor ?: now,
                defaultDaysToBackfill = previousState?.defaultDaysToBackfill ?: numberOfDaysToBackfill,

                // The query upper bound (end date for historical & daily) is normally open-ended.
                // In other words, `ingestionEnd` is typically nil.
                //
                // The only exception is if an ingestion end was set, in which case the most up-to-date
                // ingestion end date dictates the query upper bound.
                ingestionEnd = backendState.requestEndDate,

                perDeviceActivityTS = backendState.perDeviceActivityTS,

                // When we should revalidate the LocalSyncState again.
                expiresAt = now.plusSeconds(backendState.expiresIn),
            )

            setPersistedLocalSyncState(newState)

            vitalLogger.info { "LocalSyncState: updated; $newState" }

            return@withLock newState
        }
    }


    companion object {
        internal val localSyncStateMutex = Mutex(false)
    }
}