package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class TokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String
)

@JsonClass(generateAdapter = true)
data class ExchangeKeyCreate(
    @Json(name = "exchange_name") val exchangeName: String,
    @Json(name = "api_key") val apiKey: String,
    @Json(name = "api_secret") val apiSecret: String,
    @Json(name = "passphrase") val passphrase: String? = null
)

@JsonClass(generateAdapter = true)
data class ExchangeKeyResponse(
    @Json(name = "id") val id: Int,
    @Json(name = "username") val username: String,
    @Json(name = "exchange_name") val exchangeName: String,
    @Json(name = "api_key_masked") val apiKeyMasked: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String
)

interface BackendService {
    @FormUrlEncoded
    @POST("token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): TokenResponse

    @POST("exchange/keys")
    suspend fun saveExchangeKey(
        @Header("Authorization") authHeader: String,
        @Body config: ExchangeKeyCreate
    ): ExchangeKeyResponse

    @GET("exchange/keys")
    suspend fun listExchangeKeys(
        @Header("Authorization") authHeader: String
    ): List<ExchangeKeyResponse>

    @DELETE("exchange/keys/{key_id}")
    suspend fun deleteExchangeKey(
        @Header("Authorization") authHeader: String,
        @Path("key_id") keyId: Int
    ): Response<ResponseBody>

    @POST("ai/multiplier")
    suspend fun updateAiMultiplier(
        @Body payload: Map<String, Double>
    ): Map<String, Any>

    @GET("ai/multiplier")
    suspend fun getAiMultiplier(): Map<String, Any>
}
