package io.tryvital.vitalsamsunghealth

import com.squareup.moshi.JsonAdapter
import io.tryvital.vitalsamsunghealth.workers.ResourceSyncState
import io.tryvital.vitalsamsunghealth.workers.moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import java.time.Instant


@OptIn(ExperimentalCoroutinesApi::class)
class ResourceSyncStateTests {
    private val historicalStub = ResourceSyncState.Historical(
        start = Instant.parse("2023-01-23T12:34:56Z"),
        end = Instant.parse("2023-01-25T23:54:32Z"),
    )
    private val historicalJsonStub = """{"type":"historical","start":"2023-01-23T12:34:56Z","end":"2023-01-25T23:54:32Z"}"""
    private val incrementalStub = ResourceSyncState.Incremental(
        changesToken = "this-is-not-a-token",
        lastSync = Instant.parse("2023-01-25T23:54:32Z")
    )
    private val incrementalJsonStub = """{"type":"incremental","changesToken":"this-is-not-a-token","lastSync":"2023-01-25T23:54:32Z"}"""
    private val adapter: JsonAdapter<ResourceSyncState> = moshi.adapter(ResourceSyncState::class.java)

    @Test
    fun `Serialize Historical State`() = runTest {
        Assert.assertEquals(historicalJsonStub, adapter.toJson(historicalStub))
    }
    @Test
    fun `Serialize Incremental State`() = runTest {
        Assert.assertEquals(incrementalJsonStub, adapter.toJson(incrementalStub))
    }

    @Test
    fun `Deserialize Historical State`() = runTest {
        Assert.assertEquals(historicalStub, adapter.fromJson(historicalJsonStub))
    }
    @Test
    fun `Deserialize Incremental State`() = runTest {
        Assert.assertEquals(incrementalStub, adapter.fromJson(incrementalJsonStub))
    }
}
