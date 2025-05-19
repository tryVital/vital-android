package io.tryvital.client

import io.tryvital.client.utils.InstantJsonAdapter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant

class InstantJsonAdapterTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `Test Instant adapter`() = runTest {
        assertEquals(
            InstantJsonAdapter.fromJson("\"2024-11-22T11:22:33Z\""),
            Instant.parse("2024-11-22T11:22:33Z")
        )
        assertEquals(
            InstantJsonAdapter.fromJson("\"2024-11-22T11:22:33+00:00\""),
            Instant.parse("2024-11-22T11:22:33Z")
        )
    }
}