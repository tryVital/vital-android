package io.tryvital.client.services

import io.tryvital.client.services.data.*
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*
import java.util.*

interface TestkitService {

    @POST("testkit/orders")
    suspend fun createOrder(
        @Body request: CreateOrderRequest,
        @Header("skip-address-validation") skipAddressValidation: Boolean = false,
    ): OrderResponse

    @GET("testkit/")
    suspend fun getAllTestkits(): TestkitsResponse

    @GET("testkit/orders/{order_id}")
    @Deprecated("For backwards compatibility, use getOrder")
    suspend fun getOrderStatus(@Path("order_id")  orderId:String): OrderData

    @GET("testkit/orders/{order_id}")
    suspend fun getOrder(@Path("order_id")  orderId:String): OrderData

    @GET("testkit/orders")
    suspend fun getAllOrders(
        @Query("start_date") startDate: Date,
        @Query("end_date") endDate: Date?,
        @Query("status") status: List<String>?,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 50,
    ): OrdersResponse

    @POST("testkit/orders/{order_id}/cancel")
    suspend fun cancelOrder(@Path("order_id") orderId: String): OrderResponse

    companion object {
        fun create(retrofit: Retrofit): TestkitService {
            return retrofit.create(TestkitService::class.java)
        }
    }
}