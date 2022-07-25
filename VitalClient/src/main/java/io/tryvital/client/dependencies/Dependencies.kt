package io.tryvital.client.dependencies

import android.content.Context
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.tryvital.client.Environment
import io.tryvital.client.Region
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.*

class Dependencies(
    context: Context,
    region: Region,
    environment: Environment
) {

    private val httpClient: OkHttpClient by lazy {
        createHttpClient(context)
    }
    private val moshi: Moshi by lazy {
        createMoshi()
    }

    val retrofit: Retrofit by lazy {
        createRetrofit(resolveUrl(region, environment), httpClient, moshi)
    }

    companion object {
        fun createHttpClient(context: Context? = null): OkHttpClient {
            val cacheSizeInMB: Long = 2 * 1024 * 1024

            val loggingInterceptor = HttpLoggingInterceptor()
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
            return OkHttpClient.Builder()
                .apply {
                    context?.let {
                        val cache = Cache(it.cacheDir, cacheSizeInMB)
                        cache(cache)
                    }
                }
                .addInterceptor(loggingInterceptor)
                .build()
        }

        fun createRetrofit(
            baseUrl: String,
            okHttpClient: OkHttpClient,
            moshi: Moshi
        ): Retrofit =
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .addCallAdapterFactory(CoroutineCallAdapterFactory())
                .client(okHttpClient)
                .build()

        fun createMoshi(): Moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .add(Date::class.java, Rfc3339DateJsonAdapter().nullSafe())
            .build()

        private fun resolveUrl(region: Region, environment: Environment): String {
            val urls = mapOf(
                Region.EU to mapOf(
                    Environment.production to "https://api.eu.tryvital.io",
                    Environment.dev to "https://api.dev.eu.tryvital.io",
                    Environment.sandbox to "https://api.sandbox.eu.tryvital.io"
                ),
                Region.US to mapOf(
                    Environment.production to "https://api.tryvital.io",
                    Environment.dev to "https://api.dev.tryvital.io",
                    Environment.sandbox to "https://api.sandbox.tryvital.io"
                )
            )
            return "${urls[region]!![environment]!!}/v2";
        }
    }
}