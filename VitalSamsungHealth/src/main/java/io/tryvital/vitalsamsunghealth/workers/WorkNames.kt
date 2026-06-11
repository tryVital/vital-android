package io.tryvital.vitalsamsunghealth.workers

import io.tryvital.vitalhealthcore.model.RemappedVitalResource

internal object WorkNames {
    const val resourceSyncStarter = "SH.ResourceSyncStarter"

    fun resourceSyncWorker(resource: RemappedVitalResource): String =
        "SH.ResourceSyncWorker.${resource}"
}
