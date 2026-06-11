package io.tryvital.vitalhealthconnect.workers

import io.tryvital.vitalhealthcore.model.RemappedVitalResource

internal object WorkNames {
    const val resourceSyncStarter = "HC.ResourceSyncStarter"

    fun resourceSyncWorker(resource: RemappedVitalResource): String =
        "HC.ResourceSyncWorker.${resource}"
}
