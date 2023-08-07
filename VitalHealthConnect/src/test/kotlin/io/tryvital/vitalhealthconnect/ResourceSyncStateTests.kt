package io.tryvital.vitalhealthconnect

import com.squareup.moshi.JsonAdapter
import io.tryvital.vitalhealthconnect.ext.toDate
import io.tryvital.vitalhealthconnect.workers.ResourceSyncState
import io.tryvital.vitalhealthconnect.workers.moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import java.time.Instant


@OptIn(ExperimentalCoroutinesApi::class)
class ResourceSyncStateTests {
    private val historicalStub = ResourceSyncState.Historical(
        start = Instant.parse("2023-01-23T12:34:56Z").toDate(),
        end = Instant.parse("2023-01-25T23:54:32Z").toDate(),
    )
    private val historicalJsonStub = """{"type":"historical","start":"2023-01-23T12:34:56.000Z","end":"2023-01-25T23:54:32.000Z"}"""
    private val incrementalStub = ResourceSyncState.Incremental(
        changesToken = "this-is-not-a-token",
        lastSync = Instant.parse("2023-01-25T23:54:32Z").toDate()
    )
    private val incrementalJsonStub = """{"type":"incremental","changesToken":"this-is-not-a-token","lastSync":"2023-01-25T23:54:32.000Z"}"""
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
