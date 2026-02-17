package io.tryvital.vitalsamsunghealth

import android.content.Context
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore

class SamsungHealthClientProvider {
    fun getHealthDataStore(context: Context): HealthDataStore {
        return HealthDataService.getStore(context)
    }
}
