package io.tryvital.client.utils

import okhttp3.*
import okio.Buffer
import okio.BufferedSink
import okio.GzipSink
import okio.buffer


internal class GzipRequestInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest: Request = chain.request()
        if (originalRequest.body == null || originalRequest.header("Content-Encoding") != null || originalRequest.method != "POST") {
            return chain.proceed(originalRequest)
        }

        // Only these requests under these specific paths should be gzipped:
        // 1. POST https://example.com/v2/summary/:resource/:user_id
        // 1. POST https://example.com/v2/timeseries/:user_id/:resource
        val apiVersion = originalRequest.url.pathSegments.getOrNull(0)
        val root = originalRequest.url.pathSegments.getOrNull(1)
        if (apiVersion != "v2" || !(root == "timeseries" || root == "summary")) {
            return chain.proceed(originalRequest)
        }

        val compressedRequest: Request = originalRequest.newBuilder()
            .header("Content-Encoding", "gzip")
            .method(originalRequest.method, gzip(originalRequest.body!!))
            .build()

        return chain.proceed(compressedRequest)
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