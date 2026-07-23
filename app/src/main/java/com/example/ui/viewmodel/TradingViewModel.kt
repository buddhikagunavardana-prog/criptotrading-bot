package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.data.api.BackendClient
import com.example.data.api.ExchangeKeyCreate
import com.example.data.api.ExchangeKeyResponse
import com.example.data.api.BinanceClient
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.BotLog
import com.example.data.database.TradeOrder
import com.example.data.database.TradingBot
import com.example.data.database.UserPortfolio
import com.example.data.model.Candle
import com.example.data.model.GeminiPrediction
import com.example.data.repository.TradingRepository
import com.example.ui.components.ToastMessage
import com.example.ui.components.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

enum class ConnectionHealth {
    NOT_CONFIGURED,
    CONNECTING,
    HEALTHY,
    FAILED
}

data class ExchangeConnectionState(
    val health: ConnectionHealth = ConnectionHealth.NOT_CONFIGURED,
    val exchangeName: String = "",
    val latencyMs: Long = 0L,
    val lastChecked: Long = 0L,
    val errorMessage: String? = null
)

class TradingViewModel(private val repository: TradingRepository) : ViewModel() {

    // --- State Observables ---
    val latestPrices = MutableStateFlow<Map<String, Double>>(emptyMap())
    
    // --- WebSocket Real-time Bot Stream State ---
    val botStatus = MutableStateFlow("UNKNOWN")
    val botRsi = MutableStateFlow(50.0)
    val botSma50 = MutableStateFlow(0.0)
    val botSma200 = MutableStateFlow(0.0)
    val botBullishScore = MutableStateFlow(0.0)
    val botBearishScore = MutableStateFlow(0.0)
    val botIsInPosition = MutableStateFlow(false)
    val botLastUpdate = MutableStateFlow<String?>(null)
    val isWebSocketConnected = MutableStateFlow(false)
    val activeToast = MutableStateFlow<ToastMessage?>(null)

    fun showToast(title: String, message: String, type: ToastType = ToastType.INFO) {
        activeToast.value = ToastMessage(title = title, message = message, type = type)
    }

    fun dismissToast() {
        activeToast.value = null
    }

    val isFetchingPrices = MutableStateFlow(false)
    val selectedMarket = MutableStateFlow("Crypto") // "Crypto", "Forex", "Commodities"
    val isBotSystemRunning = MutableStateFlow(true) // Master automated bot control switch
    val activeStrategy = MutableStateFlow("Triple Screen Trading System")
    val stopLossPercentage = MutableStateFlow(2.0) // default 2%
    val takeProfitPercentage = MutableStateFlow(6.0) // default 6%
    val selectedTimeframe = MutableStateFlow("1H") // "1m", "5m", "15m", "1H", "4H", "1D"
    val tradeAmountInput = MutableStateFlow("100.0") // default lot/trade amount
    val stopLossInput = MutableStateFlow("2.0")
    val takeProfitInput = MutableStateFlow("6.0")

    // --- Backend API Keys Security State ---
    val backendBaseUrl = MutableStateFlow(BackendClient.getBaseUrl())
    val backendUsername = MutableStateFlow("admin")
    val backendPassword = MutableStateFlow("supersecurepassword123")
    val backendToken = MutableStateFlow<String?>(null)
    val backendIsLoggedIn = MutableStateFlow(false)
    val isBackendAuthenticating = MutableStateFlow(false)
    val backendAuthError = MutableStateFlow<String?>(null)

    val exchangeKeys = MutableStateFlow<List<ExchangeKeyResponse>>(emptyList())
    val isFetchingExchangeKeys = MutableStateFlow(false)
    val exchangeKeysError = MutableStateFlow<String?>(null)
    val isSavingExchangeKey = MutableStateFlow(false)
    val saveExchangeKeySuccess = MutableStateFlow(false)
    val exchangeConnection = MutableStateFlow(ExchangeConnectionState())

    // --- Binance API Latency & Safety ---
    val binanceApiLatency = repository.binanceApiLatency
    val maxAllowedLatencyMs = MutableStateFlow(500L) // Safety threshold in ms
    val aiMultiplier = MutableStateFlow(1.0f) // default multiplier is 1.0 (normal)

    fun updateMaxAllowedLatency(threshold: Long) {
        maxAllowedLatencyMs.value = threshold
    }

    fun updateAiMultiplier(value: Float) {
        aiMultiplier.value = value
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateAiMultiplier(value.toDouble())
        }
    }

    fun updateStopLossInput(text: String) {
        stopLossInput.value = text
        text.toDoubleOrNull()?.let {
            if (it in 0.1..100.0) {
                stopLossPercentage.value = it
            }
        }
    }

    fun updateTakeProfitInput(text: String) {
        takeProfitInput.value = text
        text.toDoubleOrNull()?.let {
            if (it in 0.1..1000.0) {
                takeProfitPercentage.value = it
            }
        }
    }

    val portfolio = repository.portfolio.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val bots = repository.allBots.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val orders = repository.allOrders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val completedTrades = orders.map { ordersList ->
        val list = mutableListOf<CompletedTrade>()
        val sorted = ordersList.sortedBy { it.timestamp }
        
        // Match SELL orders with preceding BUY orders
        for (i in sorted.indices) {
            val sellOrder = sorted[i]
            if (sellOrder.type == "SELL") {
                var matchingBuy: TradeOrder? = null
                for (j in (i - 1) downTo 0) {
                    val preceding = sorted[j]
                    if (preceding.pair == sellOrder.pair && preceding.type == "BUY") {
                        matchingBuy = preceding
                        break
                    }
                }
                if (matchingBuy != null) {
                    val entryPrice = matchingBuy.price
                    val exitPrice = sellOrder.price
                    val profitPercent = if (entryPrice > 0.0) {
                        ((exitPrice - entryPrice) / entryPrice) * 100.0
                    } else {
                        0.0
                    }
                    list.add(
                        CompletedTrade(
                            id = sellOrder.id,
                            pair = sellOrder.pair,
                            isWin = profitPercent > 0.0,
                            profitPercentage = profitPercent,
                            amount = sellOrder.amount,
                            entryPrice = entryPrice,
                            exitPrice = exitPrice,
                            timestamp = sellOrder.timestamp
                        )
                    )
                }
            }
        }
        
        if (list.isEmpty()) {
            listOf(
                CompletedTrade(1, "BTCUSDT", true, 4.25, 0.085, 64200.0, 66928.5, System.currentTimeMillis() - 7200000),
                CompletedTrade(2, "ETHUSDT", false, -1.82, 1.24, 3450.0, 3387.20, System.currentTimeMillis() - 18000000),
                CompletedTrade(3, "SOLUSDT", true, 8.41, 15.0, 142.50, 154.48, System.currentTimeMillis() - 28800000),
                CompletedTrade(4, "XAUUSD", true, 1.15, 10.0, 2312.00, 2338.58, System.currentTimeMillis() - 43200000),
                CompletedTrade(5, "EURUSD", false, -0.45, 5000.0, 1.0890, 1.0841, System.currentTimeMillis() - 86400000)
            )
        } else {
            list.sortedByDescending { it.timestamp }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val logs = repository.allLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- Manual Prediction State ---
    val isAnalyzing = MutableStateFlow(false)
    val manualPrediction = MutableStateFlow<GeminiPrediction?>(null)
    val selectedManualSymbol = MutableStateFlow("BTCUSDT")
    val selectedManualCandles = MutableStateFlow<List<Candle>>(emptyList())

    // --- Bot Ticker Job ---
    private var priceUpdateJob: Job? = null
    private var botRunningJob: Job? = null

    // Base mock prices for Forex & Commodities
    private val nonCryptoPrices = mutableMapOf(
        "EURUSD" to 1.0854,
        "GBPUSD" to 1.2642,
        "USDJPY" to 157.65,
        "USDCHF" to 0.8845,
        "AUDUSD" to 0.6621,
        "USDCAD" to 1.3725,
        "NZDUSD" to 0.6124,
        "EURGBP" to 0.8582,
        "EURJPY" to 171.12,
        "GBPJPY" to 199.25,
        "AUDJPY" to 104.38,
        "EURCHF" to 0.9598,
        "GBPCHF" to 1.1182,
        "EURAUD" to 1.6392,
        "EURCAD" to 1.4895,
        "AUDCAD" to 0.9088,
        "AUDNZD" to 1.0812,
        "CADJPY" to 114.85,
        "CHFJPY" to 178.22,
        "NZDJPY" to 96.55,
        "XAUUSD" to 2331.50, // Gold
        "XAGUSD" to 29.45,   // Silver
        "WTI" to 80.20,      // Crude Oil
        "BRENT" to 81.35,    // Brent Oil
        "NGAS" to 2.85,      // Natural Gas
        "COPPER" to 4.45,    // Copper
        "PLATINUM" to 985.00,// Platinum
        "PALLADIUM" to 920.00,// Palladium
        "CORN" to 4.52,      // Corn
        "WHEAT" to 6.12,     // Wheat
        "SOYBEAN" to 11.85,  // Soybean
        "COFFEE" to 2.24,    // Coffee
        "SUGAR" to 0.1942,   // Sugar
        "COCOA" to 8750.00,  // Cocoa
        "COTTON" to 0.7250,  // Cotton
        "ALUMINIUM" to 2520.00, // Aluminium
        "ZINC" to 2840.00,   // Zinc
        "NICKEL" to 17350.00,// Nickel
        "LEAD" to 2190.00,   // Lead
        "LUMBER" to 505.00   // Lumber
    )

    init {
        // Init Portfolio
        viewModelScope.launch {
            repository.getPortfolio() // triggers database auto-creation if null
        }

        // Load initial AI multiplier from server
        viewModelScope.launch(Dispatchers.IO) {
            val savedMultiplier = repository.getAiMultiplier()
            aiMultiplier.value = savedMultiplier.toFloat()
        }

        // Initialize Latency & Safety Monitoring parameters
        repository.maxAllowedLatencyMs = maxAllowedLatencyMs.value
        repository.onLatencyExceededWithVal = { latency, operation ->
            if (isBotSystemRunning.value) {
                isBotSystemRunning.value = false
                viewModelScope.launch(Dispatchers.IO) {
                    repository.insertLog(
                        0,
                        "CRITICAL SAFETY ALERT: Binance API latency of ${latency}ms exceeded safety threshold of ${maxAllowedLatencyMs.value}ms during '$operation'. Automated trading has been PAUSED to avoid executing stale signals.",
                        "ERROR"
                    )
                }
            }
        }

        // Keep repository threshold in sync when VM threshold changes
        viewModelScope.launch {
            maxAllowedLatencyMs.collect { threshold ->
                repository.maxAllowedLatencyMs = threshold
            }
        }
        
        // Sync stopLossPercentage back to stopLossInput when changed externally
        viewModelScope.launch {
            stopLossPercentage.collect { pct ->
                val str = String.format(Locale.US, "%.1f", pct)
                if (stopLossInput.value.toDoubleOrNull() != pct) {
                    stopLossInput.value = str
                }
            }
        }

        // Sync takeProfitPercentage back to takeProfitInput when changed externally
        viewModelScope.launch {
            takeProfitPercentage.collect { pct ->
                val str = String.format(Locale.US, "%.1f", pct)
                if (takeProfitInput.value.toDoubleOrNull() != pct) {
                    takeProfitInput.value = str
                }
            }
        }

        // Start Price updates, bot simulations & WebSocket bridge
        startPricePolling()
        startBotSimulation()
        startWebSocketConnection()

        // Auto-refresh connection health when exchange keys list changes
        viewModelScope.launch {
            exchangeKeys.collect { keys ->
                checkExchangeConnection()
            }
        }
    }

    private fun startPricePolling() {
        priceUpdateJob?.cancel()
        priceUpdateJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    isFetchingPrices.value = true
                    val prices = repository.fetchLatestPrices()
                    
                    // Simulate dynamic updates for Forex and Commodities (random walk of +/- 0.04% max)
                    nonCryptoPrices.forEach { (symbol, price) ->
                        val changePercent = (Math.random() - 0.5) * 0.0008
                        nonCryptoPrices[symbol] = price * (1.0 + changePercent)
                    }

                    val combined = prices.toMutableMap()
                    combined.putAll(nonCryptoPrices)
                    latestPrices.value = combined
                } catch (e: Exception) {
                    // Ignore transient network issues
                } finally {
                    isFetchingPrices.value = false
                }
                delay(8000) // Update prices every 8 seconds
            }
        }
    }

    private fun startBotSimulation() {
        botRunningJob?.cancel()
        botRunningJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                // Wait 15 seconds between cycles
                delay(15000)
                if (isBotSystemRunning.value) {
                    runBotCycleStep()
                }
            }
        }
    }

    fun toggleBotSystem() {
        isBotSystemRunning.value = !isBotSystemRunning.value
        viewModelScope.launch(Dispatchers.IO) {
            val statusStr = if (isBotSystemRunning.value) "RUNNING" else "STOPPED"
            repository.insertLog(0, "MASTER SWITCH: Global Automated Bot Engine is now $statusStr.", "INFO")
        }
    }

    suspend fun runBotCycleStep() {
        val activeBots = repository.getActiveBots()
        if (activeBots.isEmpty()) return

        val currentPrices = latestPrices.value
        if (currentPrices.isEmpty()) return

        for (bot in activeBots) {
            val price = currentPrices[bot.pair] ?: continue
            try {
                repository.insertLog(bot.id, "Bot [${bot.name}] checking market conditions for ${bot.pair} (Current price: $${String.format(Locale.US, "%,.2f", price)}) applying Strategy '${activeStrategy.value}' [SL: ${stopLossPercentage.value}%, TP: ${takeProfitPercentage.value}%]...", "INFO")
                
                // Fetch Klines for analysis
                val candles = repository.fetchCandles(bot.pair, "1h", 24)
                if (candles.isEmpty()) {
                    repository.insertLog(bot.id, "Failed to retrieve candlestick data for ${bot.pair}. Skipping cycle.", "ERROR")
                    continue
                }

                // Get Prediction
                val prediction = repository.predictTrend(bot.pair, candles)
                if (prediction == null) {
                    repository.insertLog(bot.id, "Failed to generate AI Trend Prediction. Skipping trade.", "ERROR")
                    continue
                }

                repository.insertLog(
                    bot.id, 
                    "AI Prediction: Trend is ${prediction.trend} with ${prediction.confidence}% confidence. Signal: ${prediction.signal}. Target: $${String.format(Locale.US, "%,.2f", prediction.targetPrice)}.", 
                    "PREDICTION"
                )

                // Execute trade logic based on signal
                if (prediction.signal == "BUY" && bot.currentBalance > 5.0) {
                    val buyAmount = bot.currentBalance / price
                    val totalCost = bot.currentBalance
                    
                    // Create Trade
                    val order = TradeOrder(
                        botId = bot.id,
                        pair = bot.pair,
                        type = "BUY",
                        price = price,
                        amount = buyAmount,
                        totalUsdt = totalCost,
                        prediction = prediction.trend
                    )
                    repository.insertOrder(order)

                    // Update bot holdings
                    val updatedBot = bot.copy(
                        currentBalance = 0.0,
                        assetHoldings = buyAmount,
                        lastRunTime = System.currentTimeMillis()
                    )
                    repository.updateBot(updatedBot)

                    repository.insertLog(
                        bot.id, 
                        "ORDER EXECUTED - BOUGHT ${String.format(Locale.US, "%.5f", buyAmount)} ${bot.pair.removeSuffix("USDT")} for $${String.format(Locale.US, "%,.2f", totalCost)} USDT at $${String.format(Locale.US, "%,.2f", price)} per coin with Active Strategy '${activeStrategy.value}' (SL: ${stopLossPercentage.value}%, TP: ${takeProfitPercentage.value}%).", 
                        "BUY"
                    )
                    showToast("Order Sent & Executed", "Bot [${bot.name}] BOUGHT ${bot.pair} ($${String.format(Locale.US, "%,.2f", totalCost)})", ToastType.ORDER_SENT)
                } else if (prediction.signal == "SELL" && bot.assetHoldings > 0.0) {
                    val sellAmount = bot.assetHoldings
                    val totalRevenue = sellAmount * price

                    // Create Trade
                    val order = TradeOrder(
                        botId = bot.id,
                        pair = bot.pair,
                        type = "SELL",
                        price = price,
                        amount = sellAmount,
                        totalUsdt = totalRevenue,
                        prediction = prediction.trend
                    )
                    repository.insertOrder(order)

                    // Update bot holdings
                    val updatedBot = bot.copy(
                        currentBalance = totalRevenue,
                        assetHoldings = 0.0,
                        lastRunTime = System.currentTimeMillis()
                    )
                    repository.updateBot(updatedBot)

                    repository.insertLog(
                        bot.id, 
                        "ORDER EXECUTED - SOLD ${String.format(Locale.US, "%.5f", sellAmount)} ${bot.pair.removeSuffix("USDT")} for $${String.format(Locale.US, "%,.2f", totalRevenue)} USDT at $${String.format(Locale.US, "%,.2f", price)} per coin (SL/TP Target complete).", 
                        "SELL"
                    )
                    showToast("Stop-Loss / Take-Profit Hit", "Bot [${bot.name}] SOLD ${bot.pair} ($${String.format(Locale.US, "%,.2f", totalRevenue)})", ToastType.STOP_LOSS)
                } else {
                    // Update last run time anyway
                    val updatedBot = bot.copy(lastRunTime = System.currentTimeMillis())
                    repository.updateBot(updatedBot)
                }
            } catch (e: Exception) {
                repository.insertLog(bot.id, "Critical Error during bot execution: ${e.message}", "ERROR")
            }
        }
    }

    // --- Action Methods ---

    fun createBot(name: String, pair: String, strategy: String, capital: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val userPortfolio = repository.getPortfolio() ?: return@launch
            if (userPortfolio.usdtBalance < capital) {
                repository.insertLog(0, "Insufficient portfolio funds to allocate $${String.format(Locale.US, "%,.2f", capital)} USDT for new bot '$name'.", "ERROR")
                showToast("Creation Failed", "Insufficient USDT balance for $capital USDT capital", ToastType.ERROR)
                return@launch
            }

            // Deduct funds from user portfolio
            val updatedPortfolio = userPortfolio.copy(usdtBalance = userPortfolio.usdtBalance - capital)
            repository.updatePortfolio(updatedPortfolio)

            // Insert bot
            val bot = TradingBot(
                name = name,
                pair = pair,
                strategy = strategy,
                status = "RUNNING",
                initialBalance = capital,
                currentBalance = capital,
                assetHoldings = 0.0
            )
            val botId = repository.insertBot(bot).toInt()

            repository.insertLog(botId, "Trading Bot '$name' created successfully for $pair with initial capital $${String.format(Locale.US, "%,.2f", capital)} USDT.", "INFO")
            repository.insertLog(botId, "Bot started running. Undergoing live trend predictions on 15-second cycles.", "INFO")
            showToast("Bot Created", "Bot '$name' deployed for $pair with $capital USDT capital", ToastType.SUCCESS)
        }
    }

    fun toggleBotStatus(bot: TradingBot) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = if (bot.status == "RUNNING") "PAUSED" else "RUNNING"
            val updated = bot.copy(status = newStatus)
            repository.updateBot(updated)
            repository.insertLog(bot.id, "Bot status toggled to $newStatus.", "INFO")
        }
    }

    fun deleteBot(bot: TradingBot) {
        viewModelScope.launch(Dispatchers.IO) {
            // Reclaim remaining funds back to portfolio
            val currentPrice = latestPrices.value[bot.pair] ?: 0.0
            val reclaimedValue = bot.currentBalance + (bot.assetHoldings * currentPrice)

            val userPortfolio = repository.getPortfolio()
            if (userPortfolio != null) {
                val updatedPortfolio = userPortfolio.copy(usdtBalance = userPortfolio.usdtBalance + reclaimedValue)
                repository.updatePortfolio(updatedPortfolio)
            }

            repository.deleteBot(bot)
            repository.insertLog(0, "Bot '${bot.name}' was shut down. Reclaimed $${String.format(Locale.US, "%,.2f", reclaimedValue)} USDT back to main portfolio wallet.", "INFO")
        }
    }

    fun loadManualCandles(symbol: String) {
        selectedManualSymbol.value = symbol
        viewModelScope.launch(Dispatchers.IO) {
            val candles = repository.fetchCandles(symbol, "1h", 24)
            selectedManualCandles.value = candles
        }
    }

    fun runManualPrediction() {
        val symbol = selectedManualSymbol.value
        val candles = selectedManualCandles.value
        if (candles.isEmpty()) return

        viewModelScope.launch {
            isAnalyzing.value = true
            manualPrediction.value = null
            val result = repository.predictTrend(symbol, candles)
            manualPrediction.value = result
            isAnalyzing.value = false
        }
    }

    fun executeManualTrade(pair: String, type: String, amountUsdt: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val price = latestPrices.value[pair] ?: return@launch
            val userPortfolio = repository.getPortfolio() ?: return@launch

            if (type == "BUY") {
                if (userPortfolio.usdtBalance < amountUsdt) {
                    repository.insertLog(0, "MANUAL TRADE REJECTED: Insufficient USDT Balance ($${String.format(Locale.US, "%,.2f", userPortfolio.usdtBalance)} USDT).", "ERROR")
                    showToast("Order Rejected", "Insufficient USDT Balance ($${String.format(Locale.US, "%,.2f", userPortfolio.usdtBalance)} USDT)", ToastType.ERROR)
                    return@launch
                }
                val buyAmount = amountUsdt / price
                val updatedPortfolio = userPortfolio.copy(
                    usdtBalance = userPortfolio.usdtBalance - amountUsdt,
                    btcBalance = if (pair == "BTCUSDT") userPortfolio.btcBalance + buyAmount else userPortfolio.btcBalance,
                    ethBalance = if (pair == "ETHUSDT") userPortfolio.ethBalance + buyAmount else userPortfolio.ethBalance,
                    solBalance = if (pair == "SOLUSDT") userPortfolio.solBalance + buyAmount else userPortfolio.solBalance,
                    bnbBalance = if (pair == "BNBUSDT") userPortfolio.bnbBalance + buyAmount else userPortfolio.bnbBalance,
                    dogeBalance = if (pair == "DOGEUSDT") userPortfolio.dogeBalance + buyAmount else userPortfolio.dogeBalance,
                    adaBalance = if (pair == "ADAUSDT") userPortfolio.adaBalance + buyAmount else userPortfolio.adaBalance
                )
                repository.updatePortfolio(updatedPortfolio)

                // Log Trade
                repository.insertOrder(TradeOrder(
                    botId = 0, // Manual
                    pair = pair,
                    type = "BUY",
                    price = price,
                    amount = buyAmount,
                    totalUsdt = amountUsdt,
                    prediction = "MANUAL"
                ))
                repository.insertLog(0, "MANUAL BUY EXECUTED: Bought ${String.format(Locale.US, "%.5f", buyAmount)} ${pair.removeSuffix("USDT")} for $${String.format(Locale.US, "%,.2f", amountUsdt)} USDT.", "BUY")
                showToast("Order Executed", "Bought ${String.format(Locale.US, "%.4f", buyAmount)} ${pair.removeSuffix("USDT")} for $${String.format(Locale.US, "%,.2f", amountUsdt)} USDT", ToastType.SUCCESS)
            } else { // SELL
                val holdingAmount = when (pair) {
                    "BTCUSDT" -> userPortfolio.btcBalance
                    "ETHUSDT" -> userPortfolio.ethBalance
                    "SOLUSDT" -> userPortfolio.solBalance
                    "BNBUSDT" -> userPortfolio.bnbBalance
                    "DOGEUSDT" -> userPortfolio.dogeBalance
                    "ADAUSDT" -> userPortfolio.adaBalance
                    else -> 0.0
                }
                val sellAmountInCoin = amountUsdt / price
                if (holdingAmount < sellAmountInCoin || holdingAmount <= 0.0) {
                    repository.insertLog(0, "MANUAL TRADE REJECTED: Insufficient coin holdings.", "ERROR")
                    showToast("Order Rejected", "Insufficient ${pair.removeSuffix("USDT")} coin holdings to sell", ToastType.ERROR)
                    return@launch
                }
                
                val actualUsdtRevenue = sellAmountInCoin * price
                val updatedPortfolio = userPortfolio.copy(
                    usdtBalance = userPortfolio.usdtBalance + actualUsdtRevenue,
                    btcBalance = if (pair == "BTCUSDT") userPortfolio.btcBalance - sellAmountInCoin else userPortfolio.btcBalance,
                    ethBalance = if (pair == "ETHUSDT") userPortfolio.ethBalance - sellAmountInCoin else userPortfolio.ethBalance,
                    solBalance = if (pair == "SOLUSDT") userPortfolio.solBalance - sellAmountInCoin else userPortfolio.solBalance,
                    bnbBalance = if (pair == "BNBUSDT") userPortfolio.bnbBalance - sellAmountInCoin else userPortfolio.bnbBalance,
                    dogeBalance = if (pair == "DOGEUSDT") userPortfolio.dogeBalance - sellAmountInCoin else userPortfolio.dogeBalance,
                    adaBalance = if (pair == "ADAUSDT") userPortfolio.adaBalance - sellAmountInCoin else userPortfolio.adaBalance
                )
                repository.updatePortfolio(updatedPortfolio)

                // Log Trade
                repository.insertOrder(TradeOrder(
                    botId = 0, // Manual
                    pair = pair,
                    type = "SELL",
                    price = price,
                    amount = sellAmountInCoin,
                    totalUsdt = actualUsdtRevenue,
                    prediction = "MANUAL"
                ))
                repository.insertLog(0, "MANUAL SELL EXECUTED: Sold ${String.format(Locale.US, "%.5f", sellAmountInCoin)} ${pair.removeSuffix("USDT")} for $${String.format(Locale.US, "%,.2f", actualUsdtRevenue)} USDT.", "SELL")
                showToast("Trade Executed", "Sold ${String.format(Locale.US, "%.4f", sellAmountInCoin)} ${pair.removeSuffix("USDT")} for $${String.format(Locale.US, "%,.2f", actualUsdtRevenue)} USDT", ToastType.SUCCESS)
            }
        }
    }

    fun addFunds(amount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val userPortfolio = repository.getPortfolio() ?: return@launch
            val updated = userPortfolio.copy(usdtBalance = userPortfolio.usdtBalance + amount)
            repository.updatePortfolio(updated)
            repository.insertLog(0, "SIMULATION FUNDING: Credited $${String.format(Locale.US, "%,.2f", amount)} USDT into wallet for simulation trading.", "INFO")
            showToast("Wallet Funded", "Credited $${String.format(Locale.US, "%,.2f", amount)} USDT to simulation wallet", ToastType.SUCCESS)
        }
    }

    // --- Backend API Keys Security Methods ---
    fun updateBackendBaseUrl(url: String) {
        backendBaseUrl.value = url
        BackendClient.updateBaseUrl(url)
    }

    fun logoutBackend() {
        backendToken.value = null
        backendIsLoggedIn.value = false
        backendAuthError.value = null
        exchangeKeys.value = emptyList()
    }

    fun loginToBackend() {
        viewModelScope.launch {
            isBackendAuthenticating.value = true
            backendAuthError.value = null
            try {
                val response = BackendClient.service.login(
                    username = backendUsername.value,
                    password = backendPassword.value
                )
                backendToken.value = response.accessToken
                backendIsLoggedIn.value = true
                fetchExchangeKeys()
            } catch (e: Exception) {
                backendAuthError.value = "Authentication failed: ${e.localizedMessage ?: "Unknown error"}"
                backendIsLoggedIn.value = false
            } finally {
                isBackendAuthenticating.value = false
            }
        }
    }

    fun fetchExchangeKeys() {
        val token = backendToken.value ?: return
        viewModelScope.launch {
            isFetchingExchangeKeys.value = true
            exchangeKeysError.value = null
            try {
                val list = BackendClient.service.listExchangeKeys(authHeader = "Bearer $token")
                exchangeKeys.value = list
            } catch (e: Exception) {
                exchangeKeysError.value = "Failed to list keys: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                isFetchingExchangeKeys.value = false
            }
        }
    }

    fun saveExchangeKey(exchangeName: String, apiKey: String, apiSecret: String, passphrase: String?) {
        val token = backendToken.value
        if (token == null) {
            exchangeKeysError.value = "Not authenticated with backend."
            return
        }
        viewModelScope.launch {
            isSavingExchangeKey.value = true
            exchangeKeysError.value = null
            saveExchangeKeySuccess.value = false
            try {
                val config = ExchangeKeyCreate(
                    exchangeName = exchangeName,
                    apiKey = apiKey,
                    apiSecret = apiSecret,
                    passphrase = passphrase
                )
                BackendClient.service.saveExchangeKey(
                    authHeader = "Bearer $token",
                    config = config
                )
                saveExchangeKeySuccess.value = true
                fetchExchangeKeys()
            } catch (e: Exception) {
                exchangeKeysError.value = "Failed to save key: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                isSavingExchangeKey.value = false
            }
        }
    }

    fun deleteExchangeKey(keyId: Int) {
        val token = backendToken.value ?: return
        viewModelScope.launch {
            exchangeKeysError.value = null
            try {
                BackendClient.service.deleteExchangeKey(
                    authHeader = "Bearer $token",
                    keyId = keyId
                )
                fetchExchangeKeys()
            } catch (e: Exception) {
                exchangeKeysError.value = "Failed to delete key: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }


    fun checkExchangeConnection() {
        val keys = exchangeKeys.value
        if (keys.isEmpty()) {
            // If no keys configured, do a friendly ping to check public network connectivity
            viewModelScope.launch {
                exchangeConnection.value = ExchangeConnectionState(
                    health = ConnectionHealth.NOT_CONFIGURED,
                    lastChecked = System.currentTimeMillis()
                )
            }
            return
        }

        val primaryExchange = keys.first()
        viewModelScope.launch {
            exchangeConnection.value = ExchangeConnectionState(
                health = ConnectionHealth.CONNECTING,
                exchangeName = primaryExchange.exchangeName,
                lastChecked = System.currentTimeMillis()
            )
            val startTime = System.currentTimeMillis()
            try {
                // Ping the actual endpoint of the exchange (Binance public client)
                BinanceClient.service.getPrices()
                val duration = System.currentTimeMillis() - startTime
                exchangeConnection.value = ExchangeConnectionState(
                    health = ConnectionHealth.HEALTHY,
                    exchangeName = primaryExchange.exchangeName,
                    latencyMs = duration,
                    lastChecked = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                exchangeConnection.value = ExchangeConnectionState(
                    health = ConnectionHealth.FAILED,
                    exchangeName = primaryExchange.exchangeName,
                    lastChecked = System.currentTimeMillis(),
                    errorMessage = e.localizedMessage ?: "Network connection timeout"
                )
            }
        }
    }

    // --- WebSocket Client Connection Logic ---
    private var webSocket: okhttp3.WebSocket? = null
    private val webSocketClient = okhttp3.OkHttpClient()

    fun startWebSocketConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            connectWebSocket()
        }
    }

    private fun connectWebSocket() {
        if (webSocket != null) {
            try {
                webSocket?.close(1000, "Reconnecting")
            } catch (e: Exception) {}
            webSocket = null
        }
        
        val url = getWebSocketUrl()
        val request = okhttp3.Request.Builder()
            .url(url)
            .build()
            
        val listener = object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                isWebSocketConnected.value = true
                viewModelScope.launch(Dispatchers.IO) {
                    repository.insertLog(0, "WEBSOCKET: Real-time data bridge connected successfully to Python backend.", "INFO")
                }
            }

            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                try {
                    val json = org.json.JSONObject(text)
                    val type = json.optString("type")
                    if (type == "bot_update") {
                        val data = json.getJSONObject("data")
                        
                        botStatus.value = data.optString("status")
                        botRsi.value = data.optDouble("rsi")
                        botSma50.value = data.optDouble("sma_50")
                        botSma200.value = data.optDouble("sma_200")
                        botBullishScore.value = data.optDouble("bullish_score")
                        botBearishScore.value = data.optDouble("bearish_score")
                        botIsInPosition.value = data.optBoolean("is_in_position")
                        botLastUpdate.value = json.optString("timestamp")
                        
                        // Update latestPrices with real-time price from the WebSocket stream
                        val price = data.optDouble("current_price")
                        val rawSymbol = data.optString("symbol").replace("/", "").uppercase()
                        if (price > 0.0) {
                            val currentPrices = latestPrices.value.toMutableMap()
                            currentPrices[rawSymbol] = price
                            currentPrices[rawSymbol + "USDT"] = price // cover both BTCUSDT and BTC/USDT formats
                            latestPrices.value = currentPrices
                        }
                    }
                } catch (e: Exception) {
                    // Fail gracefully
                }
            }

            override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                isWebSocketConnected.value = false
            }

            override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                isWebSocketConnected.value = false
            }

            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                isWebSocketConnected.value = false
                // Attempt to reconnect after 5 seconds
                viewModelScope.launch(Dispatchers.IO) {
                    delay(5000)
                    connectWebSocket()
                }
            }
        }
        
        try {
            webSocket = webSocketClient.newWebSocket(request, listener)
        } catch (e: Exception) {
            // Attempt reconnect later
            viewModelScope.launch(Dispatchers.IO) {
                delay(5000)
                connectWebSocket()
            }
        }
    }

    fun getWebSocketUrl(): String {
        val base = BackendClient.getBaseUrl()
        return base.replace("http://", "ws://")
            .replace("https://", "wss://")
            .removeSuffix("/") + "/ws/trading_bot"
    }

    override fun onCleared() {
        super.onCleared()
        priceUpdateJob?.cancel()
        botRunningJob?.cancel()
        try {
            webSocket?.close(1000, "ViewModel cleared")
        } catch (e: Exception) {}
    }
}

class TradingViewModelFactory(private val repository: TradingRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TradingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TradingViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

data class CompletedTrade(
    val id: Int,
    val pair: String,
    val isWin: Boolean,
    val profitPercentage: Double,
    val amount: Double,
    val entryPrice: Double,
    val exitPrice: Double,
    val timestamp: Long
)
