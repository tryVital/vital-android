package io.tryvital.vitalhealthconnect

import io.tryvital.vitalhealthconnect.workers.WorkNames
import io.tryvital.vitalhealthcore.model.RemappedVitalResource
import io.tryvital.vitalhealthcore.model.VitalResource
import org.junit.Assert
import org.junit.Test

class WorkNamesTests {
    @Test
    fun `resource sync starter work name is namespaced`() {
        Assert.assertEquals("HC.ResourceSyncStarter", WorkNames.resourceSyncStarter)
    }

    @Test
    fun `resource sync worker work name is namespaced`() {
        val resource = RemappedVitalResource(VitalResource.Steps)

        Assert.assertEquals("HC.ResourceSyncWorker.steps", WorkNames.resourceSyncWorker(resource))
    }
}
