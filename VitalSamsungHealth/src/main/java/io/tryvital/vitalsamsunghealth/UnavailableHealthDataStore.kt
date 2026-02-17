package io.tryvital.vitalsamsunghealth

import android.app.Activity
import com.samsung.android.sdk.health.data.DeviceManager
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.data.AggregatedData
import com.samsung.android.sdk.health.data.data.AssociatedDataPoints
import com.samsung.android.sdk.health.data.data.Change
import com.samsung.android.sdk.health.data.data.DataPoint
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.AggregateRequest
import com.samsung.android.sdk.health.data.request.AssociatedReadRequest
import com.samsung.android.sdk.health.data.request.ChangedDataRequest
import com.samsung.android.sdk.health.data.request.DeleteDataRequest
import com.samsung.android.sdk.health.data.request.InsertDataRequest
import com.samsung.android.sdk.health.data.request.ReadDataRequest
import com.samsung.android.sdk.health.data.request.UpdateDataRequest
import com.samsung.android.sdk.health.data.response.AsyncCompletableFuture
import com.samsung.android.sdk.health.data.response.AsyncSingleFuture
import com.samsung.android.sdk.health.data.response.DataResponse

class HealthDataStoreUnavailable: Throwable()

internal object UnavailableHealthDataStore: HealthDataStore {
    override suspend fun <T : Any> aggregateData(request: AggregateRequest<T>): DataResponse<AggregatedData<T>> {
       throw HealthDataStoreUnavailable()
    }

    override fun <T : Any> aggregateDataAsync(request: AggregateRequest<T>): AsyncSingleFuture<DataResponse<AggregatedData<T>>> {
       throw HealthDataStoreUnavailable()
    }

    override suspend fun deleteData(request: DeleteDataRequest) {
       throw HealthDataStoreUnavailable()
    }

    override fun deleteDataAsync(request: DeleteDataRequest): AsyncCompletableFuture {
       throw HealthDataStoreUnavailable()
    }

    override fun getDeviceManager(): DeviceManager {
       throw HealthDataStoreUnavailable()
    }

    override suspend fun getGrantedPermissions(permissions: Set<Permission>): Set<Permission> {
       throw HealthDataStoreUnavailable()
    }

    override fun getGrantedPermissionsAsync(permissions: Set<Permission>): AsyncSingleFuture<Set<Permission>> {
       throw HealthDataStoreUnavailable()
    }

    override suspend fun <T : DataPoint> insertData(request: InsertDataRequest<T>) {
       throw HealthDataStoreUnavailable()
    }

    override fun <T : DataPoint> insertDataAsync(request: InsertDataRequest<T>): AsyncCompletableFuture {
       throw HealthDataStoreUnavailable()
    }

    override suspend fun readAssociatedData(request: AssociatedReadRequest): DataResponse<AssociatedDataPoints> {
       throw HealthDataStoreUnavailable()
    }

    override fun readAssociatedDataAsync(request: AssociatedReadRequest): AsyncSingleFuture<DataResponse<AssociatedDataPoints>> {
       throw HealthDataStoreUnavailable()
    }

    override suspend fun <T : DataPoint> readChanges(
        request: ChangedDataRequest<T>
    ): DataResponse<Change<T>> {
       throw HealthDataStoreUnavailable()
    }

    override fun <T : DataPoint> readChangesAsync(request: ChangedDataRequest<T>): AsyncSingleFuture<DataResponse<Change<T>>> {
       throw HealthDataStoreUnavailable()
    }

    override suspend fun <T : DataPoint> readData(request: ReadDataRequest<T>): DataResponse<T> {
       throw HealthDataStoreUnavailable()
    }

    override fun <T : DataPoint> readDataAsync(request: ReadDataRequest<T>): AsyncSingleFuture<DataResponse<T>> {
       throw HealthDataStoreUnavailable()
    }

    override suspend fun requestPermissions(
        permissions: Set<Permission>,
        activity: Activity
    ): Set<Permission> {
       throw HealthDataStoreUnavailable()
    }

    override fun requestPermissionsAsync(
        permissions: Set<Permission>,
        activity: Activity
    ): AsyncSingleFuture<Set<Permission>> {
       throw HealthDataStoreUnavailable()
    }

    override suspend fun <T : DataPoint> updateData(request: UpdateDataRequest<T>) {
       throw HealthDataStoreUnavailable()
    }

    override fun <T : DataPoint> updateDataAsync(request: UpdateDataRequest<T>): AsyncCompletableFuture {
       throw HealthDataStoreUnavailable()
    }
}
