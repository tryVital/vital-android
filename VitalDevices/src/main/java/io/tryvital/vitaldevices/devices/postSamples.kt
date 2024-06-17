@file:OptIn(VitalPrivateApi::class, DelicateCoroutinesApi::class)

package io.tryvital.vitaldevices.devices

import android.content.Context
import io.tryvital.client.VitalClient
import io.tryvital.client.createConnectedSourceIfNotExist
import io.tryvital.client.services.VitalPrivateApi
import io.tryvital.client.services.data.LocalBloodPressureSample
import io.tryvital.client.services.data.DataStage
import io.tryvital.client.services.data.ManualProviderSlug
import io.tryvital.client.services.data.LocalQuantitySample
import io.tryvital.client.services.data.TimeseriesPayload
import io.tryvital.client.utils.VitalLogger
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.TimeZone

internal fun postGlucoseSamples(context: Context, provider: ManualProviderSlug, samples: List<LocalQuantitySample>) {
    if (samples.isEmpty()) {
        return
    }

    GlobalScope.launch {
        try {
            postGlucoseSamplesImpl(context, provider, samples)
            VitalLogger.getOrCreate().logI("[Device] posted ${samples.count()} glucose for $provider")

        } catch (e: Throwable) {
            VitalLogger.getOrCreate().logE("[Device] failed to post ${samples.count()} glucose for $provider", e)
        }
    }
}

internal fun postBloodPressureSamples(context: Context, provider: ManualProviderSlug, samples: List<LocalBloodPressureSample>) {
    if (samples.isEmpty()) {
        return
    }

    GlobalScope.launch {
        try {
            postBloodPressureSamplesImpl(context, provider, samples)
            VitalLogger.getOrCreate().logI("[Device] posted ${samples.count()} BP for $provider")

        } catch (e: Throwable) {
            VitalLogger.getOrCreate().logE("[Device] failed to post ${samples.count()} BP for $provider", e)
        }
    }
}

private suspend fun postGlucoseSamplesImpl(context: Context, provider: ManualProviderSlug, samples: List<LocalQuantitySample>) {
    val client = VitalClient.getOrCreate(context)
    if (VitalClient.Status.SignedIn !in VitalClient.status) {
        return
    }

    client.createConnectedSourceIfNotExist(provider)
    client.vitalPrivateService.timeseriesPost(
        userId = VitalClient.checkUserId(),
        resource = "glucose",
        payload = TimeseriesPayload(
            stage = DataStage.Daily,
            data = samples,
            startDate = null,
            endDate = null,
            timeZoneId = TimeZone.getDefault().id,
            provider = provider,
        )
    )
}

private suspend fun postBloodPressureSamplesImpl(context: Context, provider: ManualProviderSlug, samples: List<LocalBloodPressureSample>) {
    val client = VitalClient.getOrCreate(context)
    if (VitalClient.Status.SignedIn !in VitalClient.status) {
        return
    }

    client.createConnectedSourceIfNotExist(provider)
    client.vitalPrivateService.bloodPressureTimeseriesPost(
        userId = VitalClient.checkUserId(),
        payload = TimeseriesPayload(
            stage = DataStage.Daily,
            data = samples,
            startDate = null,
            endDate = null,
            timeZoneId = TimeZone.getDefault().id,
            provider = provider,
        )
    )
}