package io.tryvital.vitaldevices

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * [chunked] buffers a maximum of [maxSize] elements, preferring to emit early rather than wait if less than
 * [maxSize]
 *
 * If [checkIntervalMillis] is specified, chunkedNaturally suspends [checkIntervalMillis] to allow the buffer to fill.
 */
fun <T> Flow<T>.chunked(maxSize: Int, checkIntervalMillis: Long = 0): Flow<List<T>> {
    val buffer = Channel<T>(maxSize)
    return channelFlow {
        coroutineScope {
            launch {
                this@chunked.collect {
                    // `send` will suspend if [maxSize] elements are currently in buffer
                    buffer.send(it)
                }
                buffer.close()
            }
            launch {
                @Suppress("OPT_IN_USAGE")
                while (!buffer.isClosedForReceive) {
                    val chunk = getChunk(buffer, maxSize)
                    this@channelFlow.send(chunk)
                    delay(checkIntervalMillis)
                }
            }
        }
    }
}

private suspend fun <T> getChunk(channel: Channel<T>, maxChunkSize: Int): List<T> {
    // suspend until there's an element in the buffer
    val received = channel.receive()
    // start a chunk
    val chunk = mutableListOf(received)
    // no more than chunk size will be retrieved
    while (chunk.size < maxChunkSize) {
        val polled = channel.tryReceive().getOrNull()

        @Suppress("FoldInitializerAndIfToElvis")
        if (polled == null) {
            // then we've reached the end of the elements currently buffered.
            return chunk
        }
        chunk.add(polled)
    }
    return chunk
}