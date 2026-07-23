package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TradingDao {
    // --- Bots ---
    @Query("SELECT * FROM trading_bots ORDER BY createdAt DESC")
    fun getAllBots(): Flow<List<TradingBot>>

    @Query("SELECT * FROM trading_bots WHERE status = 'RUNNING'")
    suspend fun getActiveBots(): List<TradingBot>

    @Query("SELECT * FROM trading_bots WHERE id = :id")
    suspend fun getBotById(id: Int): TradingBot?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBot(bot: TradingBot): Long

    @Update
    suspend fun updateBot(bot: TradingBot)

    @Delete
    suspend fun deleteBot(bot: TradingBot)

    // --- Trade Orders ---
    @Query("SELECT * FROM trade_orders ORDER BY timestamp DESC")
    fun getAllOrders(): Flow<List<TradeOrder>>

    @Query("SELECT * FROM trade_orders WHERE botId = :botId ORDER BY timestamp DESC")
    fun getOrdersForBot(botId: Int): Flow<List<TradeOrder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: TradeOrder): Long

    // --- Bot Logs ---
    @Query("SELECT * FROM bot_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllLogs(): Flow<List<BotLog>>

    @Query("SELECT * FROM bot_logs WHERE botId = :botId ORDER BY timestamp DESC LIMIT 100")
    fun getLogsForBot(botId: Int): Flow<List<BotLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: BotLog)

    // --- Portfolio ---
    @Query("SELECT * FROM user_portfolio WHERE id = 1")
    fun getPortfolioFlow(): Flow<UserPortfolio?>

    @Query("SELECT * FROM user_portfolio WHERE id = 1")
    suspend fun getPortfolio(): UserPortfolio?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortfolio(portfolio: UserPortfolio)

    @Update
    suspend fun updatePortfolio(portfolio: UserPortfolio)
}
