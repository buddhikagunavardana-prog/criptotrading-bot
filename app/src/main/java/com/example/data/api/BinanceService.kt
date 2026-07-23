package com.example.data.api

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class TickerPrice(
    val symbol: String,
    val price: String
)

interface BinanceService {
    @GET("api/v3/ticker/price")
    suspend fun getPrices(): List<TickerPrice>

    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "1h",
        @Query("limit") limit: Int = 24
    ): List<List<Any>>
}
