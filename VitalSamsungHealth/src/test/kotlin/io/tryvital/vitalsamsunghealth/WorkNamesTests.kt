package io.tryvital.vitalsamsunghealth

import io.tryvital.vitalhealthcore.model.RemappedVitalResource
import io.tryvital.vitalhealthcore.model.VitalResource
import io.tryvital.vitalsamsunghealth.workers.WorkNames
import org.junit.Assert
import org.junit.Test

class WorkNamesTests {
    @Test
    fun `resource sync starter work name is namespaced`() {
        Assert.assertEquals("SH.ResourceSyncStarter", WorkNames.resourceSyncStarter)
    }

    @Test
    fun `resource sync worker work name is namespaced`() {
        val resource = RemappedVitalResource(VitalResource.Steps)

        Assert.assertEquals("SH.ResourceSyncWorker.steps", WorkNames.resourceSyncWorker(resource))
    }
}
