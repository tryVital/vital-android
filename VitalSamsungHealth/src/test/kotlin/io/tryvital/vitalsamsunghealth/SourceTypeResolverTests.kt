package io.tryvital.vitalsamsunghealth

import com.samsung.android.sdk.health.data.device.AccessoryType
import com.samsung.android.sdk.health.data.device.DeviceGroup
import io.tryvital.client.services.data.SourceType
import io.tryvital.vitalsamsunghealth.records.sourceTypeForDeviceType
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceTypeResolverTests {
    @Test
    fun `Samsung device groups map to source types`() {
        assertEquals(SourceType.Phone, sourceTypeForDeviceType(DeviceGroup.MOBILE))
        assertEquals(SourceType.Watch, sourceTypeForDeviceType(DeviceGroup.WATCH))
        assertEquals(SourceType.Watch, sourceTypeForDeviceType(DeviceGroup.BAND))
        assertEquals(SourceType.Ring, sourceTypeForDeviceType(DeviceGroup.RING))
        assertEquals(null, sourceTypeForDeviceType(DeviceGroup.OTHER))
    }

    @Test
    fun `Samsung accessories map to source types`() {
        assertEquals(SourceType.Scale, sourceTypeForDeviceType(AccessoryType.WEIGHT_SCALE))
        assertEquals(SourceType.ChestStrap, sourceTypeForDeviceType(AccessoryType.HEART_RATE_MONITOR))
        assertEquals(SourceType.Cuff, sourceTypeForDeviceType(AccessoryType.BLOOD_PRESSURE_MONITOR))
        assertEquals(SourceType.Fingerprick, sourceTypeForDeviceType(AccessoryType.BLOOD_GLUCOSE_METER))
        assertEquals(null, sourceTypeForDeviceType(AccessoryType.UNKNOWN))
    }
}
