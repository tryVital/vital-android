package io.tryvital.client.utils

import io.tryvital.client.VitalClient
import okhttp3.*
import okio.Buffer
import okio.BufferedSink
import okio.GzipSink
import okio.buffer

internal class VitalRequestInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var builder = request.newBuilder()
            .header("x-vital-android-sdk-version", VitalClient.sdkVersion)

        // Only these requests under these specific paths should be gzipped:
        // 1. POST https://example.com/v2/summary/:resource/:user_id
        // 1. POST https://example.com/v2/timeseries/:user_id/:resource
        val apiVersion = request.url.pathSegments.getOrNull(0)
        val root = request.url.pathSegments.getOrNull(1)
        val body = request.body
        if (
            body != null &&
            request.method == "POST" &&
            apiVersion == "v2" &&
            (root == "timeseries" || root == "summary")
        ) {
            builder = builder
                .header("Content-Encoding", "gzip")
                .method(request.method, gzip(body))
        }

        return chain.proceed(builder.build())
    }

    private fun gzip(requestBody: RequestBody): RequestBody {
        val buffer = Buffer()

        val gzipSink = GzipSink(buffer).buffer()
        requestBody.writeTo(gzipSink)
        gzipSink.close()

        return object : RequestBody() {
            override fun contentType(): MediaType? {
                return requestBody.contentType()
            }

            override fun contentLength(): Long {
                return buffer.size
            }

            override fun writeTo(sink: BufferedSink) {
                sink.write(buffer.snapshot())
            }
        }
    }
}