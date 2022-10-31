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
    private val recordProcessor: RecordProcessor,
    private val summaryService: SummaryService,
    private val linkService: LinkService,
) {
    private var userId: String? = null

    val requiredPermissions =
        setOf(
            HealthPermission.createReadPermission(ExerciseSessionRecord::class),
            HealthPermission.createReadPermission(DistanceRecord::class),
            HealthPermission.createReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.createReadPermission(HeartRateRecord::class),
            HealthPermission.createReadPermission(RespiratoryRateRecord::class),
            HealthPermission.createReadPermission(HeightRecord::class),
            HealthPermission.createReadPermission(BodyFatRecord::class),
            HealthPermission.createReadPermission(WeightRecord::class),
            HealthPermission.createReadPermission(SleepSessionRecord::class),
            HealthPermission.createReadPermission(OxygenSaturationRecord::class),
            HealthPermission.createReadPermission(HeartRateVariabilitySdnnRecord::class),
            HealthPermission.createReadPermission(RestingHeartRateRecord::class),
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
                providerId = providerId
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

        val currentDevice = Build.MODEL
        val startDate = Date.from(startTime)
        val endDate = Date.from(endTime)
        val stage = "daily"
        val timeZoneInSecond = "0"

        summaryService.addWorkout(
            userId!!, SummaryTimeframe(
                stage = stage,
                provider = providerId,
                startDate = startDate,
                endDate = endDate,
                timeZoneInSecond = timeZoneInSecond,
                data = recordProcessor.processWorkouts(startTime, endTime, currentDevice),
            )
        )

        summaryService.addProfile(
            userId!!, SummaryTimeframe(
                stage = stage,
                provider = providerId,
                startDate = startDate,
                endDate = endDate,
                timeZoneInSecond = timeZoneInSecond,
                data = recordProcessor.processProfile(startTime, endTime)
            )
        )

        summaryService.addBody(
            userId!!, SummaryTimeframe(
                stage = stage,
                provider = providerId,
                startDate = startDate,
                endDate = endDate,
                timeZoneInSecond = timeZoneInSecond,
                data = recordProcessor.processBody(startTime, endTime, currentDevice)
            )
        )

        summaryService.addSleep(
            userId!!, SummaryTimeframe(
                stage = stage,
                provider = providerId,
                startDate = startDate,
                endDate = endDate,
                timeZoneInSecond = timeZoneInSecond,
                data = recordProcessor.processSleep(startTime, endTime, currentDevice)
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
                recordProcessor,
                summaryService,
                linkService,
            )
    }
}
