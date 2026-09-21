package io.tryvital.vitalhealthconnect

import androidx.health.connect.client.records.metadata.Device
import io.tryvital.client.services.data.SourceType
import io.tryvital.vitalhealthconnect.model.sourceType
import io.tryvital.vitalhealthconnect.model.toMetadataMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceTypeInferenceTests {
    @Test
    fun `recognized Health Connect devices map to a definitive source type`() {
        assertEquals(SourceType.Watch, Device(Device.TYPE_WATCH).sourceType)
        assertEquals(SourceType.Phone, Device(Device.TYPE_PHONE).sourceType)
        assertEquals(SourceType.Scale, Device(Device.TYPE_SCALE).sourceType)
    }

    @Test
    fun `indeterminate Health Connect devices defer inference to the backend`() {
        assertNull(Device(Device.TYPE_UNKNOWN).sourceType)
        assertNull(Device(Device.TYPE_HEAD_MOUNTED).sourceType)
        assertNull(Device(Device.TYPE_SMART_DISPLAY).sourceType)
    }

    @Test
    fun `indeterminate device metadata omits source type but preserves identifying fields`() {
        val metadata = Device(
            type = Device.TYPE_UNKNOWN,
            manufacturer = "Example",
            model = "Tracker",
        ).toMetadataMap()

        assertEquals(
            mapOf(
                "_DMA" to "Example",
                "_DMO" to "Tracker",
            ),
            metadata,
        )
    }
}
