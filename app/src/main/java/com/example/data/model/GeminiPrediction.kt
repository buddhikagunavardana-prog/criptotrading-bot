package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiPrediction(
    @Json(name = "signal") val signal: String, // "BUY", "SELL", "HOLD"
    @Json(name = "trend") val trend: String, // "BULLISH", "BEARISH", "NEUTRAL"
    @Json(name = "target_price") val targetPrice: Double,
    @Json(name = "confidence") val confidence: Int, // e.g. 85
    @Json(name = "indicators") val indicators: String,
    @Json(name = "reasoning") val reasoning: String
)
