package io.tryvital.vitalsamsunghealth

import android.content.Context
import android.util.Log
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore

class SamsungHealthClientProvider {
    fun getHealthDataStore(context: Context): HealthDataStore {
        return try {
            HealthDataService.getStore(context)
        } catch (e: Throwable) {
            Log.i("????", "shealth getStore error")
            throw Throwable("????!!!!!", cause = e)
            return UnavailableHealthDataStore
        }
    }
}
