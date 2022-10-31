package io.tryvital.client.healthconnect

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import io.tryvital.client.dependencies.HealthConnectClientProvider
import io.tryvital.client.services.LinkService
import io.tryvital.client.services.SummaryService
import io.tryvital.client.services.data.CreateLinkRequest
import io.tryvital.client.services.data.ManualProviderRequest
import io.tryvital.client.services.data.SummaryTimeframe
import java.time.Instant
import java.util.*

private const val minSupportedSDK = Build.VERSION_CODES.P
private const val providerId = "health_connect"

class HealthConnectManager private constructor(
    private val healthConnectClientProvider: HealthConnectClientProvider,
    private val summaryService: SummaryService,
    private val linkService: LinkService,
    private val recordProcessor: RecordProcessor,
) {
    private var userId: String? = null

    val requiredPermissions =
        setOf(
            HealthPermission.createReadPermission(ExerciseSessionRecord::class),
            HealthPermission.createReadPermission(DistanceRecord::class),
            HealthPermission.createReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.createReadPermission(HeartRateRecord::class),
            HealthPermission.createReadPermission(RespiratoryRateRecord::class),
        )

    fun isAvailable(context: Context): HealthConnectAvailability {
        return when {
            Build.VERSION.SDK_INT < minSupportedSDK -> HealthConnectAvailability.NotSupportedSDK
            HealthConnectClient.isAvailable(context) -> HealthConnectAvailability.Installed
            else -> HealthConnectAvailability.NotInstalled
        }
    }

    suspend fun hasAllPermissions(context: Context): Boolean {
        return requiredPermissions == healthConnectClientProvider.getHealthConnectClient(context)
            .permissionController.getGrantedPermissions(requiredPermissions)
    }

    fun setUserId(userId: String) {
        this.userId = userId
    }

    suspend fun linkUserHealthConnectProvider(callbackURI: String) {
        if (userId == null) {
            throw IllegalStateException("You need to call setUserId before you can read the health data")
        }

        val token = linkService
            .createLink(
                CreateLinkRequest(
                    userId!!,
                    providerId,
                    callbackURI
                )
            )

        linkService.manualProvider(
            provider = providerId,
            linkToken = token.linkToken!!,
            ManualProviderRequest(
                userId = userId!!,
                providerId = ""
            )
        )
    }

    suspend fun readAndUploadHealthData(
        startTime: Instant,
        endTime: Instant,
    ) {
        if (userId == null) {
            throw IllegalStateException("You need to call setUserId before you can read the health data")
        }


        summaryService.addWorkout(
            userId!!, SummaryTimeframe(
                stage = "daily", //Not used
                provider = providerId,
                startDate = Date.from(startTime),
                endDate = Date.from(endTime),
                timeZone = "0",
                data = recordProcessor.processWorkouts(startTime, endTime, Build.DEVICE),
            )
        )

        summaryService.addProfile(
            userId!!, SummaryTimeframe(
                stage = "daily", //Not used
                provider = providerId,
                startDate = Date.from(startTime),
                endDate = Date.from(endTime),
                timeZone = "0",
                data = recordProcessor.processProfile(startTime, endTime)
            )
        )
    }

    companion object {
        fun create(
            healthConnectClientProvider: HealthConnectClientProvider,
            summaryService: SummaryService,
            linkService: LinkService,
            recordProcessor: RecordProcessor
        ): HealthConnectManager =
            HealthConnectManager(
                healthConnectClientProvider,
                summaryService,
                linkService,
                recordProcessor
            )
    }
}
