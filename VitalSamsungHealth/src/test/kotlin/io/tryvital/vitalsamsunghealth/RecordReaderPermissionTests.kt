package io.tryvital.vitalsamsunghealth

import android.content.ContextWrapper
import io.tryvital.vitalsamsunghealth.records.HealthConnectRecordReader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecordReaderPermissionTests {
    @Test
    fun `record reads use the permission snapshot`() = runTest {
        val reader = HealthConnectRecordReader(
            context = ContextWrapper(null),
            samsungHealthClientProvider = SamsungHealthClientProvider(),
            grantedPermissions = { emptySet() },
        )

        val records = reader.readHydration(
            startTime = Instant.parse("2026-09-20T00:00:00Z"),
            endTime = Instant.parse("2026-09-21T00:00:00Z"),
        )

        assertTrue(records.isEmpty())
    }
}
