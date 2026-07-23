package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object BackendClient {
    private var baseUrl = "http://10.0.2.2:8000/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private var retrofit = createRetrofitInstance(baseUrl)
    private var serviceInstance = retrofit.create(BackendService::class.java)

    val service: BackendService
        get() = serviceInstance

    fun getBaseUrl(): String = baseUrl

    fun updateBaseUrl(newUrl: String) {
        val formattedUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        if (baseUrl != formattedUrl) {
            baseUrl = formattedUrl
            retrofit = createRetrofitInstance(baseUrl)
            serviceInstance = retrofit.create(BackendService::class.java)
        }
    }

    private fun createRetrofitInstance(url: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(url)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
}
