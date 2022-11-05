package io.tryvital.vitalhealthconnect

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import io.tryvital.client.VitalClient
import io.tryvital.client.services.data.CreateLinkRequest
import io.tryvital.client.services.data.ManualProviderRequest
import io.tryvital.client.services.data.SummaryPayload
import java.time.Instant
import java.util.*

private const val minSupportedSDK = Build.VERSION_CODES.P
private const val providerId = "health_connect"

class VitalHealthConnectManager private constructor(
    private val healthConnectClientProvider: HealthConnectClientProvider,
    private val recordProcessor: RecordProcessor,
    private val vitalClient: VitalClient,
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
            HealthPermission.createReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.createReadPermission(BasalMetabolicRateRecord::class),
            HealthPermission.createReadPermission(StepsRecord::class),
            HealthPermission.createReadPermission(DistanceRecord::class),
            HealthPermission.createReadPermission(FloorsClimbedRecord::class),
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

        val token = vitalClient.linkService
            .createLink(
                CreateLinkRequest(
                    userId!!,
                    providerId,
                    callbackURI
                )
            )

        vitalClient.linkService.manualProvider(
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
        Log.e("asd","asd2")
        if (userId == null) {
            throw IllegalStateException("You need to call setUserId before you can read the health data")
        }

        vitalClient.vitalLogger.enabled = true

        val currentDevice = Build.MODEL
        val startDate = Date.from(startTime)
        val endDate = Date.from(endTime)
        val stage = "daily"
        val hostTimeZone = TimeZone.getDefault()
        val timeZoneId = hostTimeZone.id

        vitalClient.summaryService.addWorkouts(
            userId!!, SummaryPayload(
                stage = stage,
                provider = providerId,
                startDate = startDate,
                endDate = endDate,
                timeZoneId = timeZoneId,
                data = recordProcessor.processWorkouts(startTime, endTime, currentDevice),
            )
        )

        vitalClient.summaryService.addActivities(
            userId!!, SummaryPayload(
                stage = stage,
                provider = providerId,
                startDate = startDate,
                endDate = endDate,
                timeZoneId = timeZoneId,
                data = recordProcessor.processActivities(
                    startTime,
                    endTime,
                    currentDevice,
                    hostTimeZone
                ),
            )
        )

        vitalClient.summaryService.addProfile(
            userId!!, SummaryPayload(
                stage = stage,
                provider = providerId,
                startDate = startDate,
                endDate = endDate,
                timeZoneId = timeZoneId,
                data = recordProcessor.processProfile(startTime, endTime)
            )
        )

        vitalClient.summaryService.addBody(
            userId!!, SummaryPayload(
                stage = stage,
                provider = providerId,
                startDate = startDate,
                endDate = endDate,
                timeZoneId = timeZoneId,
                data = recordProcessor.processBody(startTime, endTime, currentDevice)
            )
        )

        vitalClient.summaryService.addSleeps(
            userId!!, SummaryPayload(
                stage = stage,
                provider = providerId,
                startDate = startDate,
                endDate = endDate,
                timeZoneId = timeZoneId,
                data = recordProcessor.processSleep(startTime, endTime, currentDevice)
            )
        )
    }

    companion object {
        fun create(
            context: Context,
            vitalClient: VitalClient
        ): VitalHealthConnectManager {
            val healthConnectClientProvider = HealthConnectClientProvider()

            return VitalHealthConnectManager(
                healthConnectClientProvider,
                HealthConnectRecordProcessor(
                    HealthConnectRecordReader(context, healthConnectClientProvider),
                    HealthConnectRecordAggregator(context, healthConnectClientProvider),
                    vitalClient
                ),
                vitalClient,
            )
        }
    }
}

