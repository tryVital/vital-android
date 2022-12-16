package io.tryvital.client.dependencies

import android.content.Context
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.tryvital.client.BuildConfig
import io.tryvital.client.Environment
import io.tryvital.client.Region
import io.tryvital.client.utils.ApiKeyInterceptor
import io.tryvital.client.utils.GzipRequestInterceptor
import io.tryvital.client.utils.VitalLogger
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.lang.reflect.Type
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class Dependencies(
    context: Context,
    region: Region,
    environment: Environment,
    apiKey: String
) {

    private val httpClient: OkHttpClient by lazy {
        createHttpClient(context, apiKey)
    }
    private val moshi: Moshi by lazy {
        createMoshi()
    }

    val vitalLogger: VitalLogger by lazy {
        VitalLogger.create()
    }

    val retrofit: Retrofit by lazy {
        createRetrofit(resolveUrl(region, environment), httpClient, moshi)
    }


    companion object {
        internal fun createHttpClient(context: Context? = null, apiKey: String): OkHttpClient {
            val cacheSizeInMB: Long = 2 * 1024 * 1024

            val loggingInterceptor = HttpLoggingInterceptor()
            if (BuildConfig.DEBUG) {
                loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
            } else {
                loggingInterceptor.level = HttpLoggingInterceptor.Level.NONE
            }

            return OkHttpClient.Builder()
                .apply {
                    context?.let {
                        val cache = Cache(it.cacheDir, cacheSizeInMB)
                        cache(cache)
                    }
                }
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(GzipRequestInterceptor())
                .addNetworkInterceptor(ApiKeyInterceptor(apiKey))
                .addInterceptor(loggingInterceptor)
                .build()
        }

        internal fun createRetrofit(
            baseUrl: String,
            okHttpClient: OkHttpClient,
            moshi: Moshi
        ): Retrofit =
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .addConverterFactory(QueryConverterFactory.create())
                .addCallAdapterFactory(CoroutineCallAdapterFactory())
                .client(okHttpClient)
                .build()

        internal fun createMoshi(): Moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .add(Date::class.java, Rfc3339DateJsonAdapter().nullSafe())
            .build()

        internal fun resolveUrl(region: Region, environment: Environment): String {
            val urls = mapOf(
                Region.EU to mapOf(
                    Environment.Production to "https://api.eu.tryvital.io",
                    Environment.Dev to "https://api.dev.eu.tryvital.io",
                    Environment.Sandbox to "https://api.sandbox.eu.tryvital.io"
                ),
                Region.US to mapOf(
                    Environment.Production to "https://api.tryvital.io",
                    Environment.Dev to "https://api.dev.tryvital.io",
                    Environment.Sandbox to "https://api.sandbox.tryvital.io"
                )
            )
            return "${urls[region]!![environment]!!}/v2/"
        }
    }

    class QueryConverterFactory : Converter.Factory() {
        override fun stringConverter(
            type: Type,
            annotations: Array<out Annotation>,
            retrofit: Retrofit
        ): Converter<*, String>? {
            return if (type === Date::class.java) {
                DateQueryConverter.INSTANCE
            } else null
        }

        private class DateQueryConverter : Converter<Date, String> {
            override fun convert(date: Date): String {
                return DF.get()?.format(date) ?: "Error"
            }

            companion object {
                val INSTANCE = DateQueryConverter()
                private val DF: ThreadLocal<DateFormat> = object : ThreadLocal<DateFormat>() {
                    public override fun initialValue(): DateFormat {
                        return SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                    }
                }
            }
        }

        companion object {
            fun create(): QueryConverterFactory {
                return QueryConverterFactory()
            }
        }
    }
}