package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trading_bots")
data class TradingBot(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val pair: String, // e.g. "BTCUSDT"
    val strategy: String, // "ML_TREND_FOLLOWER", "GRID", "DCA"
    val status: String, // "RUNNING", "PAUSED"
    val initialBalance: Double, // in USDT
    val currentBalance: Double, // in USDT
    val assetHoldings: Double, // amount of asset (e.g. BTC)
    val createdAt: Long = System.currentTimeMillis(),
    val lastRunTime: Long = 0L
)

@Entity(tableName = "trade_orders")
data class TradeOrder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val botId: Int, // 0 if manual trade
    val pair: String,
    val type: String, // "BUY", "SELL"
    val price: Double,
    val amount: Double,
    val totalUsdt: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val prediction: String? = null // "BULLISH", "BEARISH", "NEUTRAL"
)

@Entity(tableName = "bot_logs")
data class BotLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val botId: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val type: String // "INFO", "BUY", "SELL", "ERROR", "PREDICTION"
)

@Entity(tableName = "user_portfolio")
data class UserPortfolio(
    @PrimaryKey val id: Int = 1,
    val usdtBalance: Double = 10000.0,
    val btcBalance: Double = 0.0,
    val ethBalance: Double = 0.0,
    val solBalance: Double = 0.0,
    val bnbBalance: Double = 0.0,
    val dogeBalance: Double = 0.0,
    val adaBalance: Double = 0.0
)
