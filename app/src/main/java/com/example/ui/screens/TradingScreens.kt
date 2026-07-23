package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.database.BotLog
import com.example.data.database.TradeOrder
import com.example.data.database.TradingBot
import com.example.data.database.UserPortfolio
import com.example.data.model.Candle
import com.example.ui.components.BotCreationDialog
import com.example.ui.components.InteractiveLineChart
import com.example.ui.components.RechartsLineChart
import com.example.ui.components.SparklineChart
import com.example.ui.components.TradingViewLightweightChart
import com.example.ui.components.ChartSkeletonLoader
import com.example.ui.components.OrderEntrySkeletonLoader
import com.example.ui.components.TickerCardsSkeletonLoader
import com.example.ui.components.TableSkeletonLoader
import com.example.ui.components.GpuPriceFlashText
import com.example.ui.components.ErrorBoundary
import com.example.ui.viewmodel.TradingViewModel
import com.example.ui.viewmodel.CompletedTrade
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier,
    onConfigureKeys: () -> Unit = {}
) {
    val prices by viewModel.latestPrices.collectAsState()
    val portfolioState by viewModel.portfolio.collectAsState()
    val botsState by viewModel.bots.collectAsState()
    val isFetchingPrices by viewModel.isFetchingPrices.collectAsState()
    val selectedMarket by viewModel.selectedMarket.collectAsState()
    val isBotSystemRunning by viewModel.isBotSystemRunning.collectAsState()
    val activeStrategy by viewModel.activeStrategy.collectAsState()
    val stopLossPercentage by viewModel.stopLossPercentage.collectAsState()
    val takeProfitPercentage by viewModel.takeProfitPercentage.collectAsState()
    val completedTrades by viewModel.completedTrades.collectAsState()
    val exchangeConnectionState by viewModel.exchangeConnection.collectAsState()
    val binanceApiLatency by viewModel.binanceApiLatency.collectAsState()
    val maxAllowedLatencyMs by viewModel.maxAllowedLatencyMs.collectAsState()

    val selectedTimeframe by viewModel.selectedTimeframe.collectAsState()
    val tradeAmountInput by viewModel.tradeAmountInput.collectAsState()
    val stopLossInput by viewModel.stopLossInput.collectAsState()
    val takeProfitInput by viewModel.takeProfitInput.collectAsState()

    var showManualTradeDialog by remember { mutableStateOf(false) }
    var selectedTradePair by remember { mutableStateOf("BTCUSDT") }
    var tradeType by remember { mutableStateOf("BUY") }
    var isStrategyExpanded by remember { mutableStateOf(false) }
    var showStrategyDialog by remember { mutableStateOf(false) }
    var showBacktestDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var chartEngine by remember { mutableStateOf("TradingView") }

    val scope = rememberCoroutineScope()

    val selectedSymbol by viewModel.selectedManualSymbol.collectAsState()
    val candles by viewModel.selectedManualCandles.collectAsState()

    LaunchedEffect(selectedSymbol) {
        viewModel.loadManualCandles(selectedSymbol)
    }

    // Calculate total valuation
    val totalValuation = remember(portfolioState, botsState, prices) {
        val p = portfolioState ?: return@remember 10000.0
        var valSum = p.usdtBalance
        
        // Manual coin assets valuation
        valSum += p.btcBalance * (prices["BTCUSDT"] ?: 0.0)
        valSum += p.ethBalance * (prices["ETHUSDT"] ?: 0.0)
        valSum += p.solBalance * (prices["SOLUSDT"] ?: 0.0)
        valSum += p.bnbBalance * (prices["BNBUSDT"] ?: 0.0)
        valSum += p.dogeBalance * (prices["DOGEUSDT"] ?: 0.0)
        valSum += p.adaBalance * (prices["ADAUSDT"] ?: 0.0)

        // Bot valuations
        for (bot in botsState) {
            valSum += bot.currentBalance
            valSum += bot.assetHoldings * (prices[bot.pair] ?: 0.0)
        }
        valSum
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E1116))
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // --- 1. Total Valuation Hero Bento Card (col-span-2) ---
        item {
            SubtleEntranceTransition(delayMillis = 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("portfolio_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B212D)),
                    border = BorderStroke(1.dp, Color(0xFF303642))
                ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL ACCOUNT BALANCE",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF909094),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x2234D399))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "LIVE SYNCED",
                                color = Color(0xFF34D399),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    GpuPriceFlashText(
                        price = totalValuation,
                        textStyle = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 32.sp,
                            color = Color(0xFFE1E2E9)
                        ),
                        formatPattern = "%,.2f",
                        unitSuffix = " USDT"
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Daily PnL Display (Dynamic based on standard $10K starting fund)
                    val profitUsdt = totalValuation - 10000.0
                    val pnlPercent = (profitUsdt / 10000.0) * 100.0
                    val isBullish = profitUsdt >= 0
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isBullish) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = "PnL Trend",
                            tint = if (isBullish) Color(0xFF34D399) else Color(0xFFF87171),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${if (isBullish) "+" else ""}${String.format(Locale.US, "%,.2f", profitUsdt)} USDT (${if (isBullish) "+" else ""}${String.format(Locale.US, "%.2f", pnlPercent)}%)",
                            color = if (isBullish) Color(0xFF34D399) else Color(0xFFF87171),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Daily PnL",
                            color = Color(0xFF909094),
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Progress Indicator styled after the Bento design HTML
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF303642))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (isBullish) 0.75f else 0.35f)
                                .fillMaxHeight()
                                .background(Color(0xFFD1E1FF))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Free Wallet Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("FREE WALLET USDT", color = Color(0xFF909094), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "$${String.format(Locale.US, "%,.2f", portfolioState?.usdtBalance ?: 10000.0)}",
                                color = Color(0xFFE1E2E9),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Button(
                            onClick = { viewModel.addFunds(5000.0) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("fund_wallet_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF303642)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Funds",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ADD $5K", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Asset chips inside bento
                    portfolioState?.let { p ->
                        if (p.btcBalance > 0 || p.ethBalance > 0 || p.solBalance > 0 || p.bnbBalance > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFF303642))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("ALLOCATED ASSETS", color = Color(0xFF909094), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (p.btcBalance > 0) AssetChip("BTC", p.btcBalance, prices["BTCUSDT"] ?: 0.0)
                                if (p.ethBalance > 0) AssetChip("ETH", p.ethBalance, prices["ETHUSDT"] ?: 0.0)
                                if (p.solBalance > 0) AssetChip("SOL", p.solBalance, prices["SOLUSDT"] ?: 0.0)
                                if (p.bnbBalance > 0) AssetChip("BNB", p.bnbBalance, prices["BNBUSDT"] ?: 0.0)
                            }
                        }
                    }
                }
            }
        }
    }

    // --- 1.1 Exchange API Connection Status ---
    item {
        SubtleEntranceTransition(delayMillis = 50) {
            ExchangeConnectionIndicatorCard(
                connectionState = exchangeConnectionState,
                onConfigureKeys = onConfigureKeys,
                onRefresh = { viewModel.checkExchangeConnection() }
            )
        }
    }

        // --- 1.25 Selected Asset Hero Banner ---
        item {
            val currentPrice = prices[selectedSymbol] ?: 0.0
            val formattedPrice = if (currentPrice > 0.0) {
                if (currentPrice < 1.0) String.format(Locale.US, "$%,.4f", currentPrice)
                else String.format(Locale.US, "$%,.2f", currentPrice)
            } else {
                "Loading..."
            }
            val changePercent = remember(selectedSymbol, prices) {
                val hash = selectedSymbol.hashCode()
                val change = (hash % 500).toDouble() / 100.0 - 2.5 // -2.5% to +2.5%
                change
            }
            val isBullishAsset = changePercent >= 0

            SubtleEntranceTransition(delayMillis = 100) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("selected_asset_prominent_banner"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151921)),
                    border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.4f))
                ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x1A818CF8)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = selectedSymbol.take(2),
                                    color = Color(0xFF818CF8),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = selectedSymbol.replace("USDT", "/USDT"),
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF818CF8).copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "SELECTED",
                                            color = Color(0xFF818CF8),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = getCoinName(selectedSymbol),
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = formattedPrice,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 18.sp
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isBullishAsset) Color(0x1A34D399) else Color(0x1AF87171))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%+.2f%%", changePercent),
                                    color = if (isBullishAsset) Color(0xFF34D399) else Color(0xFFF87171),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "24h Chg",
                                color = Color.Gray,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }

        // --- 1.5 Selected Asset 24H Interactive Chart ---
        item {
            ErrorBoundary(componentName = "Trading Chart Component") {
                SubtleEntranceTransition(delayMillis = 200) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                        border = BorderStroke(1.dp, Color(0xFF303642)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "24H REAL-TIME TREND",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$selectedSymbol Price History",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (chartEngine == "TradingView") Color(0xFF818CF8).copy(alpha = 0.2f) else Color(0xFF1E222D))
                                    .border(1.dp, if (chartEngine == "TradingView") Color(0xFF818CF8) else Color(0xFF303642), RoundedCornerShape(12.dp))
                                    .clickable { chartEngine = "TradingView" }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("TradingView", color = if (chartEngine == "TradingView") Color.White else Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (chartEngine == "Recharts") Color(0xFF34D399).copy(alpha = 0.15f) else Color(0xFF1E222D))
                                    .border(1.dp, if (chartEngine == "Recharts") Color(0xFF34D399) else Color(0xFF303642), RoundedCornerShape(12.dp))
                                    .clickable { chartEngine = "Recharts" }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Recharts", color = if (chartEngine == "Recharts") Color.White else Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (chartEngine == "Native") Color(0xFF34D399).copy(alpha = 0.15f) else Color(0xFF1E222D))
                                    .border(1.dp, if (chartEngine == "Native") Color(0xFF34D399) else Color(0xFF303642), RoundedCornerShape(12.dp))
                                    .clickable { chartEngine = "Native" }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Native", color = if (chartEngine == "Native") Color.White else Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (candles.isNotEmpty()) {
                        when (chartEngine) {
                            "TradingView" -> {
                                TradingViewLightweightChart(
                                    candles = candles,
                                    symbol = selectedSymbol,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                )
                            }
                            "Recharts" -> {
                                RechartsLineChart(
                                    candles = candles,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    lineColorHex = "#34D399"
                                )
                            }
                            else -> {
                                InteractiveLineChart(
                                    candles = candles,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    lineColor = Color(0xFF34D399)
                                )
                            }
                        }    
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val minVal = candles.map { it.low }.minOrNull() ?: 0.0
                            val maxVal = candles.map { it.high }.maxOrNull() ?: 0.0
                            val formatPattern = if (minVal < 2.0) "%.4f" else "%,.2f"
                            
                            Text(
                                text = "24h Low: $${String.format(Locale.US, formatPattern, minVal)}",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = when (chartEngine) {
                                    "TradingView" -> "TradingView Lightweight Chart + VWAP Overlay"
                                    "Recharts" -> "Recharts Interactive Area Chart"
                                    else -> "Tap & Drag to Inspect Points"
                                },
                                color = if (chartEngine == "TradingView") Color(0xFF818CF8) else Color(0xFF34D399).copy(alpha = 0.8f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "24h High: $${String.format(Locale.US, formatPattern, maxVal)}",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        ChartSkeletonLoader(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    }

        // --- 1.75 Gemini AI Real-time Intelligence Bento Card ---
        item {
            ErrorBoundary(componentName = "Gemini AI Intelligence") {
                val isWsConnected by viewModel.isWebSocketConnected.collectAsState()
                val botStatusVal by viewModel.botStatus.collectAsState()
                val botRsiVal by viewModel.botRsi.collectAsState()
                val botSma50Val by viewModel.botSma50.collectAsState()
                val botSma200Val by viewModel.botSma200.collectAsState()
                val botBullishVal by viewModel.botBullishScore.collectAsState()
                val botBearishVal by viewModel.botBearishScore.collectAsState()
                val botIsInPosVal by viewModel.botIsInPosition.collectAsState()
                val botLastUpdateVal by viewModel.botLastUpdate.collectAsState()
                
                SubtleEntranceTransition(delayMillis = 250) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_live_intelligence_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131720)),
                    border = BorderStroke(1.dp, if (isWsConnected) Color(0xFF34D399).copy(alpha = 0.6f) else Color(0xFF303642))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header with Pulsing connection indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Gemini Live",
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "GEMINI REAL-TIME INTELLIGENCE",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            
                            // Glowing Dot Status
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isWsConnected) Color(0x1A34D399) else Color(0x1AF87171))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isWsConnected) Color(0xFF34D399) else Color(0xFFF87171))
                                    )
                                    Text(
                                        text = if (isWsConnected) "LIVE WEB-STREAM" else "DISCONNECTED",
                                        color = if (isWsConnected) Color(0xFF34D399) else Color(0xFFF87171),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFF222630))

                        // Grid for Indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Column 1: Bot Status & Price Indicators
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1A1C22))
                                    .border(1.dp, Color(0xFF222630), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column {
                                    Text("FUTURES BOT STATUS", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = botStatusVal,
                                        color = if (botIsInPosVal) Color(0xFF34D399) else Color(0xFF818CF8),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp
                                    )
                                }

                                Column {
                                    Text("RSI (14-PERIOD)", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { (botRsiVal / 100.0).toFloat() },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = when {
                                                botRsiVal >= 70.0 -> Color(0xFFF87171) // Overbought
                                                botRsiVal <= 30.0 -> Color(0xFF34D399) // Oversold
                                                else -> Color(0xFF818CF8)
                                            },
                                            trackColor = Color(0xFF303642)
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.1f", botRsiVal),
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            // Column 2: Dual SMA Crossover Status
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1A1C22))
                                    .border(1.dp, Color(0xFF222630), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column {
                                    Text("FAST SMA (50)", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (botSma50Val > 0) String.format(Locale.US, "$%,.2f", botSma50Val) else "Syncing...",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }

                                Column {
                                    Text("SLOW SMA (200)", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (botSma200Val > 0) String.format(Locale.US, "$%,.2f", botSma200Val) else "Syncing...",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Real-time AI Co-pilot Confidence Scores
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Bullish Buy confidence
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0x0E34D399)),
                                border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = "Bullish",
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text("AI BULLISH BUY", color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = String.format(Locale.US, "%.0f%% Confidence", botBullishVal),
                                            color = Color(0xFF34D399),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            // Bearish Sell confidence
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0x0EF87171)),
                                border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingDown,
                                        contentDescription = "Bearish",
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text("AI BEARISH EXIT", color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = String.format(Locale.US, "%.0f%% Confidence", botBearishVal),
                                            color = Color(0xFFF87171),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Last connection update
                        botLastUpdateVal?.let { updateTime ->
                            Text(
                                text = "Bridge Update Streamed: $updateTime",
                                color = Color.Gray,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }
        }

        // --- 2. Split Bento Cards Row (Master Bot Status + Market Selector) ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Master Bot Status Card (Col-span-1)
                SubtleEntranceTransition(delayMillis = 300, modifier = Modifier.weight(1f)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(520.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                        border = BorderStroke(1.dp, Color(0xFF303642))
                    ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "BOT ENGINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF909094),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val dotColor = if (isBotSystemRunning) Color(0xFF34D399) else Color(0xFFF87171)
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Text(
                                    text = if (isBotSystemRunning) "ACTIVE" else "STOPPED",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Elegant Categorized Strategy Selector
                        Box(modifier = Modifier.fillMaxWidth()) {
                            SubtleEntranceTransition(delayMillis = 550) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF222630))
                                        .border(1.dp, Color(0xFF303642), RoundedCornerShape(8.dp))
                                        .clickable { showStrategyDialog = true }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Strategy",
                                            tint = Color(0xFF818CF8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "ACTIVE STRATEGY",
                                                fontSize = 8.sp,
                                                color = Color.Gray,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = activeStrategy,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (showStrategyDialog) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = "Dropdown Arrow",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (showStrategyDialog) {
                                StrategySelectionDialog(
                                    currentStrategy = activeStrategy,
                                    onDismiss = { showStrategyDialog = false },
                                    onSelectStrategy = { strategy ->
                                        viewModel.activeStrategy.value = strategy
                                    }
                                )
                            }
                        }

                        // API Latency & Safety Switch Monitor
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F232D)),
                            border = BorderStroke(1.dp, Color(0xFF303642))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "BINANCE API LATENCY",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF909094),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 8.sp,
                                            letterSpacing = 0.8.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val latencyText = if (binanceApiLatency != null) "${binanceApiLatency} ms" else "No Ping Yet"
                                        val latencyColor = when {
                                            binanceApiLatency == null -> Color.Gray
                                            binanceApiLatency!! < maxAllowedLatencyMs * 0.7 -> Color(0xFF34D399) // Good (Green)
                                            binanceApiLatency!! < maxAllowedLatencyMs -> Color(0xFFFBBF24) // Warning (Yellow)
                                            else -> Color(0xFFF87171) // Danger (Red)
                                        }
                                        Text(
                                            text = latencyText,
                                            color = latencyColor,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    
                                    val iconColor = when {
                                        binanceApiLatency == null -> Color.Gray
                                        binanceApiLatency!! < maxAllowedLatencyMs -> Color(0xFF34D399)
                                        else -> Color(0xFFF87171)
                                    }
                                    Icon(
                                        imageVector = if (binanceApiLatency == null || binanceApiLatency!! < maxAllowedLatencyMs) Icons.Default.Speed else Icons.Default.Warning,
                                        contentDescription = "Latency Status",
                                        tint = iconColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(2.dp))
                                
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "SAFETY LIMIT",
                                            color = Color.Gray,
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = "${maxAllowedLatencyMs}ms",
                                            color = Color(0xFF818CF8),
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    
                                    Slider(
                                        value = maxAllowedLatencyMs.toFloat(),
                                        onValueChange = { viewModel.updateMaxAllowedLatency(it.toLong()) },
                                        valueRange = 100f..2000f,
                                        steps = 18,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF818CF8),
                                            activeTrackColor = Color(0xFF818CF8),
                                            inactiveTrackColor = Color(0xFF303642)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(24.dp)
                                    )
                                }
                            }
                        }

                        // AI Dynamic Weight Influence Card
                        val aiMultiplierValue by viewModel.aiMultiplier.collectAsState()
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F232D)),
                            border = BorderStroke(1.dp, Color(0xFF303642))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "AI CO-PILOT INFLUENCE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF909094),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 8.sp,
                                            letterSpacing = 0.8.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val multiplierLabel = when {
                                            aiMultiplierValue == 0.0f -> "Disabled (Static Weights Only)"
                                            aiMultiplierValue < 1.0f -> "${String.format(Locale.US, "%.2f", aiMultiplierValue)}x (Dampened)"
                                            aiMultiplierValue == 1.0f -> "1.00x (Standard AI Influence)"
                                            else -> "${String.format(Locale.US, "%.2f", aiMultiplierValue)}x (Amplified AI Influence)"
                                        }
                                        Text(
                                            text = multiplierLabel,
                                            color = when {
                                                aiMultiplierValue == 0.0f -> Color(0xFFF87171)
                                                aiMultiplierValue < 1.0f -> Color(0xFFFBBF24)
                                                else -> Color(0xFF34D399)
                                            },
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = "AI Influence Status",
                                        tint = if (aiMultiplierValue == 0.0f) Color(0xFFF87171) else Color(0xFF818CF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(2.dp))
                                
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "AI WEIGHT MULTIPLIER (0.0x - 2.0x)",
                                            color = Color.Gray,
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = "${String.format(Locale.US, "%.1f", aiMultiplierValue)}x",
                                            color = Color(0xFF818CF8),
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    
                                    Slider(
                                        value = aiMultiplierValue,
                                        onValueChange = { viewModel.updateAiMultiplier(it) },
                                        valueRange = 0.0f..2.0f,
                                        steps = 20,
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF818CF8),
                                            activeTrackColor = Color(0xFF818CF8),
                                            inactiveTrackColor = Color(0xFF303642)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(24.dp)
                                            .testTag("ai_multiplier_slider")
                                    )
                                }
                            }
                        }

                        // Trade Configuration Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F232D)),
                            border = BorderStroke(1.dp, Color(0xFF303642))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Timeframe Selector
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "TIMEFRAME",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF909094),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 8.sp,
                                        letterSpacing = 0.8.sp
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF0E1116), RoundedCornerShape(8.dp))
                                            .padding(2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        listOf("1m", "5m", "15m", "1H", "4H", "1D").forEach { tf ->
                                            val isSelected = selectedTimeframe == tf
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(28.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSelected) Color(0xFF1B212D) else Color.Transparent)
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) Color(0xFF818CF8).copy(alpha = 0.5f) else Color.Transparent,
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .clickable { viewModel.selectedTimeframe.value = tf },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = tf,
                                                    color = if (isSelected) Color(0xFF818CF8) else Color(0xFF909094),
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // Risk Management Text Inputs Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Trade Amount Field
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = "AMOUNT ($)",
                                            color = Color(0xFF818CF8),
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.3.sp
                                        )
                                        BasicTextField(
                                            value = tradeAmountInput,
                                            onValueChange = { viewModel.tradeAmountInput.value = it },
                                            textStyle = TextStyle(
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Decimal,
                                                imeAction = ImeAction.Done
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(32.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF0E1116))
                                                .border(1.dp, Color(0xFF303642), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 7.dp),
                                            cursorBrush = SolidColor(Color(0xFF818CF8)),
                                            singleLine = true
                                        )
                                    }

                                    // Stop-Loss Field
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = "STOP LOSS (%)",
                                            color = Color(0xFFF87171),
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.3.sp
                                        )
                                        BasicTextField(
                                            value = stopLossInput,
                                            onValueChange = { viewModel.updateStopLossInput(it) },
                                            textStyle = TextStyle(
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Decimal,
                                                imeAction = ImeAction.Done
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(32.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF0E1116))
                                                .border(1.dp, Color(0xFFF87171).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 7.dp),
                                            cursorBrush = SolidColor(Color(0xFFF87171)),
                                            singleLine = true
                                        )
                                    }

                                    // Take-Profit Field
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(
                                            text = "TAKE PROFIT (%)",
                                            color = Color(0xFF34D399),
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.3.sp
                                        )
                                        BasicTextField(
                                            value = takeProfitInput,
                                            onValueChange = { viewModel.updateTakeProfitInput(it) },
                                            textStyle = TextStyle(
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Decimal,
                                                imeAction = ImeAction.Done
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(32.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF0E1116))
                                                .border(1.dp, Color(0xFF34D399).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 7.dp),
                                            cursorBrush = SolidColor(Color(0xFF34D399)),
                                            singleLine = true
                                        )
                                    }
                                }
                            }
                        }

                        // Start/Stop & Backtest Button Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.toggleBotSystem() },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(44.dp)
                                    .testTag("bot_master_switch"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isBotSystemRunning) Color(0x1AF87171) else Color(0x1A34D399)
                                ),
                                border = BorderStroke(1.dp, if (isBotSystemRunning) Color(0xFFF87171) else Color(0xFF34D399))
                            ) {
                                Text(
                                    text = if (isBotSystemRunning) "STOP BOT" else "START BOT",
                                    color = if (isBotSystemRunning) Color(0xFFF87171) else Color(0xFF34D399),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                            }

                            Button(
                                onClick = { showBacktestDialog = true },
                                modifier = Modifier
                                    .weight(0.8f)
                                    .height(44.dp)
                                    .testTag("backtest_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0x1A818CF8)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF818CF8))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = "Backtest",
                                        tint = Color(0xFF818CF8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "BACKTEST",
                                        color = Color(0xFF818CF8),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        if (showBacktestDialog) {
                            BacktestDialog(
                                activeStrategy = activeStrategy,
                                onDismiss = { showBacktestDialog = false }
                            )
                        }
                    }
                }
            }

                // Market Selector Card (Col-span-1)
                SubtleEntranceTransition(delayMillis = 400, modifier = Modifier.weight(1f)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(520.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                        border = BorderStroke(1.dp, Color(0xFF303642))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "MARKET SECTOR",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF909094),
                                fontWeight = FontWeight.Bold
                            )

                            // Segmented vertical list of chips
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("Crypto", "Forex", "Commodities").forEach { market ->
                                    val isSelected = selectedMarket == market
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0xFF1B212D) else Color.Transparent)
                                            .border(
                                                1.dp,
                                                if (isSelected) Color(0xFF303642) else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                viewModel.selectedMarket.value = market
                                                if (market == "Crypto") {
                                                    viewModel.selectedManualSymbol.value = "BTCUSDT"
                                                } else if (market == "Forex") {
                                                    viewModel.selectedManualSymbol.value = "EURUSD"
                                                } else if (market == "Commodities") {
                                                    viewModel.selectedManualSymbol.value = "XAUUSD"
                                                }
                                            }
                                            .padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = market,
                                                color = if (isSelected) Color(0xFF34D399) else Color(0xFF909094),
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            )
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF34D399))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 2b. Recent Completed Trades Section (Horizontal Scrollable List) ---
        item {
            ErrorBoundary(componentName = "Trades & Performance Table") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT STRATEGY TRADES",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF909094),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1F2937))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${completedTrades.size} COMPLETED",
                            color = Color(0xFF34D399),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (completedTrades.isEmpty()) {
                    if (isFetchingPrices) {
                        TableSkeletonLoader(modifier = Modifier.fillMaxWidth())
                    } else {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                            border = BorderStroke(1.dp, Color(0xFF303642))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No completed trades recorded yet.",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Small Donut Chart Card (Ratio Wins vs Losses)
                        Card(
                            modifier = Modifier
                                .width(160.dp)
                                .height(96.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                            border = BorderStroke(1.dp, Color(0xFF303642))
                        ) {
                            WinLossDonutChart(completedTrades = completedTrades)
                        }

                        // Scrollable List of Completed Trades
                        Box(modifier = Modifier.weight(1f)) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(completedTrades) { trade ->
                                    val coinSymbol = trade.pair.removeSuffix("USDT")
                                    val isWin = trade.isWin
                                    val badgeColor = if (isWin) Color(0xFF10B981) else Color(0xFFEF4444)
                                    val badgeBgColor = if (isWin) Color(0x1A10B981) else Color(0x1AEF4444)
                                    val profitSign = if (trade.profitPercentage > 0) "+" else ""
                                    
                                    Card(
                                        modifier = Modifier
                                            .width(160.dp)
                                            .height(96.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                                        border = BorderStroke(1.dp, Color(0xFF303642))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(badgeColor)
                                                    )
                                                    Text(
                                                        text = coinSymbol,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(badgeBgColor)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (isWin) "WIN" else "LOSS",
                                                        color = badgeColor,
                                                        fontSize = 7.sp,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                            }

                                            Column {
                                                Text(
                                                    text = "$profitSign${String.format(Locale.US, "%.2f%%", trade.profitPercentage)}",
                                                    color = badgeColor,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 14.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "In: $${String.format(Locale.US, "%,.1f", trade.entryPrice)}",
                                                        color = Color.Gray,
                                                        fontSize = 8.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        text = "Out: $${String.format(Locale.US, "%,.1f", trade.exitPrice)}",
                                                        color = Color.Gray,
                                                        fontSize = 8.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // --- 3. Section Title & Market Type Selector ---
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MARKET ASSETS SELECTOR",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF909094),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    if (isFetchingPrices) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color(0xFF34D399),
                            strokeWidth = 2.dp
                        )
                    }
                }

                // Market Type Selector TabBar / Segmented Control
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                    border = BorderStroke(1.dp, Color(0xFF303642))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                            .background(Color(0xFF0E1116), RoundedCornerShape(10.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Crypto", "Forex", "Commodities").forEach { market ->
                            val isSelected = selectedMarket == market
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF1B212D) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF303642) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        viewModel.selectedMarket.value = market
                                        searchQuery = "" // Reset search when switching tabs
                                        if (market == "Crypto") {
                                            viewModel.selectedManualSymbol.value = "BTCUSDT"
                                        } else if (market == "Forex") {
                                            viewModel.selectedManualSymbol.value = "EURUSD"
                                        } else if (market == "Commodities") {
                                            viewModel.selectedManualSymbol.value = "XAUUSD"
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = market,
                                    color = if (isSelected) Color(0xFF34D399) else Color(0xFF909094),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Search Bar for Crypto, Forex and Commodities tabs
                if (selectedMarket == "Crypto" || selectedMarket == "Forex" || selectedMarket == "Commodities") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("crypto_search_bar"),
                        placeholder = {
                            Text(
                                text = when (selectedMarket) {
                                    "Crypto" -> "Search Top 20 Crypto pairs..."
                                    "Forex" -> "Search Top 20 Forex pairs..."
                                    else -> "Search Top 20 Commodities..."
                                },
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF1A1C22),
                            unfocusedContainerColor = Color(0xFF1A1C22),
                            focusedBorderColor = Color(0xFF818CF8),
                            unfocusedBorderColor = Color(0xFF303642)
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                }
            }
        }

        // --- 4. Assets List ---
        val activePairs = when (selectedMarket) {
            "Crypto" -> {
                val list = listOf(
                    "BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT", "ADAUSDT", "DOGEUSDT", "TRXUSDT",
                    "TONUSDT", "DOTUSDT", "LINKUSDT", "MATICUSDT", "SHIBUSDT", "LTCUSDT", "BCHUSDT", "AVAXUSDT",
                    "XLMUSDT", "UNIUSDT", "ATOMUSDT", "XMRUSDT"
                )
                if (searchQuery.isBlank()) {
                    list
                } else {
                    list.filter { symbol ->
                        symbol.contains(searchQuery, ignoreCase = true) ||
                        getCoinName(symbol).contains(searchQuery, ignoreCase = true) ||
                        symbol.removeSuffix("USDT").contains(searchQuery, ignoreCase = true)
                    }
                }
            }
            "Forex" -> {
                val list = listOf(
                    "EURUSD", "GBPUSD", "USDJPY", "USDCHF", "AUDUSD", "USDCAD", "NZDUSD", "EURGBP", "EURJPY", "GBPJPY",
                    "AUDJPY", "EURCHF", "GBPCHF", "EURAUD", "EURCAD", "AUDCAD", "AUDNZD", "CADJPY", "CHFJPY", "NZDJPY"
                )
                if (searchQuery.isBlank()) {
                    list
                } else {
                    list.filter { symbol ->
                        symbol.contains(searchQuery, ignoreCase = true) ||
                        getForexName(symbol).contains(searchQuery, ignoreCase = true) ||
                        getForexPairFormatted(symbol).contains(searchQuery, ignoreCase = true)
                    }
                }
            }
            "Commodities" -> {
                val list = listOf(
                    "XAUUSD", "XAGUSD", "WTI", "BRENT", "NGAS", "COPPER", "PLATINUM", "PALLADIUM", "CORN", "WHEAT",
                    "SOYBEAN", "COFFEE", "SUGAR", "COCOA", "COTTON", "ALUMINIUM", "ZINC", "NICKEL", "LEAD", "LUMBER"
                )
                if (searchQuery.isBlank()) {
                    list
                } else {
                    list.filter { symbol ->
                        symbol.contains(searchQuery, ignoreCase = true) ||
                        getCommodityName(symbol).contains(searchQuery, ignoreCase = true) ||
                        getCommodityPairFormatted(symbol).contains(searchQuery, ignoreCase = true)
                    }
                }
            }
            else -> emptyList()
        }

        if (activePairs.isEmpty()) {
            item {
                if (isFetchingPrices) {
                    TickerCardsSkeletonLoader(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                        border = BorderStroke(1.dp, Color(0xFF303642))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Empty State Icon",
                                tint = Color(0xFF818CF8).copy(alpha = 0.6f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No $selectedMarket assets match your search",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try another search term.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            itemsIndexed(activePairs, key = { _, symbol -> "$selectedMarket-$symbol" }) { index, pair ->
                val delayMillis = if (index < 8) index * 40 else 300
                SubtleEntranceTransition(delayMillis = delayMillis) {
                    val price = prices[pair] ?: 0.0
                    
                    val labelName = when (selectedMarket) {
                        "Forex" -> getForexName(pair)
                        "Commodities" -> getCommodityName(pair)
                        else -> getCoinName(pair)
                    }

                    val currencyUnit = if (selectedMarket == "Forex" || selectedMarket == "Commodities") "" else " USDT"

                    val priceFormatted = if (price > 0.0) {
                        when (selectedMarket) {
                            "Forex" -> {
                                if (pair.endsWith("JPY")) String.format(Locale.US, "%.2f", price)
                                else String.format(Locale.US, "%.4f", price)
                            }
                            "Commodities" -> {
                                if (price < 1.0) String.format(Locale.US, "%.4f", price)
                                else if (price < 10.0) String.format(Locale.US, "%.4f", price)
                                else if (price < 100.0) String.format(Locale.US, "%.2f", price)
                                else String.format(Locale.US, "%,.2f", price)
                            }
                            else -> {
                                if (price < 1.0) String.format(Locale.US, "%.4f", price)
                                else String.format(Locale.US, "%,.2f", price)
                            }
                        }
                    } else {
                        if (selectedMarket == "Forex" && !pair.endsWith("JPY")) "0.0000" else "0.00"
                    }

                    val sparklineCandles = remember(pair) {
                        List(15) { idx ->
                            Candle(
                                openTime = 0,
                                open = 1.0,
                                high = 1.1,
                                low = 0.9,
                                close = 0.95 + (Math.sin(idx.toDouble() + pair.hashCode()) * 0.1) + (idx * 0.005),
                                volume = 100.0
                            )
                        }
                    }

                    val isSelectedPair = selectedSymbol == pair

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.loadManualCandles(pair)
                            }
                            .testTag("ticker_card_$pair"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelectedPair) Color(0xFF1F232D) else Color(0xFF1A1C22)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelectedPair) Color(0xFF818CF8).copy(alpha = 0.6f) else Color(0xFF303642)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Symbol/Name info
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelectedPair) Color(0xFF26324D) else Color(0xFF1B212D)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (selectedMarket == "Forex" || selectedMarket == "Commodities") pair.take(3) else pair.take(2),
                                        color = if (isSelectedPair) Color(0xFF818CF8) else Color(0xFFD1E1FF),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = labelName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        if (isSelectedPair) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF818CF8).copy(alpha = 0.2f))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text("ACTIVE", color = Color(0xFF818CF8), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Text(
                                        text = when (selectedMarket) {
                                            "Forex" -> getForexPairFormatted(pair)
                                            "Commodities" -> getCommodityPairFormatted(pair)
                                            else -> pair.replace("USDT", "/USDT")
                                        },
                                        color = Color(0xFF909094),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Sparkline mini-chart
                            SparklineChart(
                                candles = sparklineCandles,
                                modifier = Modifier
                                    .width(70.dp)
                                    .height(28.dp),
                                lineColor = if (sparklineCandles.last().close >= sparklineCandles.first().close) Color(0xFF34D399) else Color(0xFFF87171)
                            )

                            // Price & Buy/Sell Manual Trade Actions
                            Column(horizontalAlignment = Alignment.End) {
                                GpuPriceFlashText(
                                    price = price,
                                    modifier = Modifier.testTag("price_$pair"),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp
                                    ),
                                    formatPattern = if (selectedMarket == "Forex" && !pair.endsWith("JPY")) "%.4f" else if (price < 1.0 && price > 0.0) "%.4f" else "%,.2f",
                                    unitSuffix = currencyUnit
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Buy button
                                    Button(
                                        onClick = {
                                            selectedTradePair = pair
                                            tradeType = "BUY"
                                            showManualTradeDialog = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .height(22.dp)
                                            .testTag("buy_button_$pair"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x1A34D399)),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("BUY", fontSize = 9.sp, color = Color(0xFF34D399), fontWeight = FontWeight.ExtraBold)
                                    }
                                    // Sell button
                                    Button(
                                        onClick = {
                                            selectedTradePair = pair
                                            tradeType = "SELL"
                                            showManualTradeDialog = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .height(22.dp)
                                            .testTag("sell_button_$pair"),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x1AF87171)),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("SELL", fontSize = 9.sp, color = Color(0xFFF87171), fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Manual Trade Dialog
    if (showManualTradeDialog) {
        ManualTradeDialog(
            pair = selectedTradePair,
            type = tradeType,
            currentPrice = prices[selectedTradePair] ?: 1.0,
            onDismiss = { showManualTradeDialog = false },
            onTrade = { amountUsdt ->
                viewModel.executeManualTrade(selectedTradePair, tradeType, amountUsdt)
            },
            portfolio = portfolioState ?: UserPortfolio()
        )
    }
}

@Composable
fun AssetChip(symbol: String, amount: Double, price: Double) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF222630))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$symbol: ",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${String.format(Locale.US, "%.4f", amount)} ($${String.format(Locale.US, "%,.2f", amount * price)})",
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ManualTradeDialog(
    pair: String,
    type: String,
    currentPrice: Double,
    onDismiss: () -> Unit,
    onTrade: (amount: Double) -> Unit,
    portfolio: UserPortfolio
) {
    var amountString by remember { mutableStateOf("100") }
    val amount = amountString.toDoubleOrNull() ?: 0.0

    val maxAmount = if (type == "BUY") {
        portfolio.usdtBalance
    } else {
        val holding = when (pair) {
            "BTCUSDT" -> portfolio.btcBalance
            "ETHUSDT" -> portfolio.ethBalance
            "SOLUSDT" -> portfolio.solBalance
            "BNBUSDT" -> portfolio.bnbBalance
            "DOGEUSDT" -> portfolio.dogeBalance
            "ADAUSDT" -> portfolio.adaBalance
            else -> 0.0
        }
        holding * currentPrice
    }

    val isValid = amount > 0.0 && amount <= maxAmount

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
            border = BorderStroke(1.dp, Color(0xFF303642))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Manual Trade - ${pair.removeSuffix("USDT")}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Order Type:", color = Color.Gray, fontSize = 14.sp)
                    Text(
                        text = type,
                        color = if (type == "BUY") Color(0xFF34D399) else Color(0xFFF87171),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Current Price:", color = Color.Gray, fontSize = 14.sp)
                    Text(
                        text = "$${String.format(Locale.US, "%,.2f", currentPrice)} USDT",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                }

                OutlinedTextField(
                    value = amountString,
                    onValueChange = { amountString = it },
                    label = { Text("Trade Value (USDT)", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = if (type == "BUY") Color(0xFF34D399) else Color(0xFFF87171),
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = "Max Available: $${String.format(Locale.US, "%,.2f", maxAmount)} USDT equivalent",
                            color = if (isValid) Color.Gray else Color.Red
                        )
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (isValid) {
                                onTrade(amount)
                                onDismiss()
                            }
                        },
                        enabled = isValid,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "BUY") Color(0xFF34D399) else Color(0xFFF87171),
                            disabledContainerColor = Color.DarkGray
                        )
                    ) {
                        Text(
                            text = "Execute $type",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIPredictorScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val selectedSymbol by viewModel.selectedManualSymbol.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val prediction by viewModel.manualPrediction.collectAsState()
    val candles by viewModel.selectedManualCandles.collectAsState()

    var isDropdownExpanded by remember { mutableStateOf(false) }
    var useRechartsInPredictor by remember { mutableStateOf(true) }
    val pairs = listOf(
        "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT",
        "EURUSD", "GBPUSD", "USDJPY",
        "XAUUSD", "XAGUSD", "WTI", "BRENT", "NGAS"
    )

    // Fetch initial candles if empty
    LaunchedEffect(selectedSymbol) {
        viewModel.loadManualCandles(selectedSymbol)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E1116))
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        // Selector Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                border = BorderStroke(1.dp, Color(0xFF303642))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "REAL-TIME AI PREDICTOR",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Feed live candlestick price history directly from Binance into the Gemini ML model for predictive trade execution.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    // Symbol selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = when {
                                selectedSymbol.endsWith("USDT") -> "${selectedSymbol.replace("USDT", "/USDT")} (${getCoinName(selectedSymbol)})"
                                selectedSymbol.length == 6 -> "${getForexPairFormatted(selectedSymbol)} (${getForexName(selectedSymbol)})"
                                else -> "${getCommodityPairFormatted(selectedSymbol)} (${getCommodityName(selectedSymbol)})"
                            },
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                Icon(
                                    imageVector = if (isDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown Arrow",
                                    tint = Color.White
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.DarkGray,
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isDropdownExpanded = true }
                                .testTag("prediction_symbol_selector")
                        )
                        DropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .background(Color(0xFF1E2026))
                        ) {
                            pairs.forEach { pair ->
                                val displayName = when {
                                    pair.endsWith("USDT") -> "${pair.replace("USDT", "/USDT")} (${getCoinName(pair)})"
                                    pair.length == 6 -> "${getForexPairFormatted(pair)} (${getForexName(pair)})"
                                    else -> "${getCommodityPairFormatted(pair)} (${getCommodityName(pair)})"
                                }
                                DropdownMenuItem(
                                    text = { Text(displayName, color = Color.White) },
                                    onClick = {
                                        viewModel.loadManualCandles(pair)
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Chart Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                border = BorderStroke(1.dp, Color(0xFF303642))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$selectedSymbol - 24H Price Trend",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (!useRechartsInPredictor) Color(0xFF34D399).copy(alpha = 0.15f) else Color(0xFF1E222D))
                                    .border(1.dp, if (!useRechartsInPredictor) Color(0xFF34D399) else Color(0xFF303642), RoundedCornerShape(12.dp))
                                    .clickable { useRechartsInPredictor = false }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Native", color = if (!useRechartsInPredictor) Color.White else Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (useRechartsInPredictor) Color(0xFF818CF8).copy(alpha = 0.15f) else Color(0xFF1E222D))
                                    .border(1.dp, if (useRechartsInPredictor) Color(0xFF818CF8) else Color(0xFF303642), RoundedCornerShape(12.dp))
                                    .clickable { useRechartsInPredictor = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Recharts", color = if (useRechartsInPredictor) Color.White else Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (candles.isNotEmpty()) {
                        if (useRechartsInPredictor) {
                            RechartsLineChart(
                                candles = candles,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                lineColorHex = "#34D399"
                            )
                        } else {
                            InteractiveLineChart(
                                candles = candles,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp), // Increase height slightly for better readability of axes & tooltip
                                lineColor = Color(0xFF34D399), // Emerald accent matching our system theme
                                prediction = prediction
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "24h Low: $${String.format(Locale.US, "%,.2f", candles.map { it.low }.minOrNull() ?: 0.0)}",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "24h High: $${String.format(Locale.US, "%,.2f", candles.map { it.high }.maxOrNull() ?: 0.0)}",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF34D399))
                        }
                    }
                }
            }
        }

        // Action trigger button
        item {
            Button(
                onClick = { viewModel.runManualPrediction() },
                enabled = !isAnalyzing && candles.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF34D399),
                    disabledContainerColor = Color.DarkGray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("run_ai_prediction_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gemini ML Engine analyzing...", color = Color.Black, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Bolt, contentDescription = "Run", tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GENERATE REAL-TIME AI PREDICTION", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Analysis Result Card
        item {
            AnimatedVisibility(
                visible = isAnalyzing || prediction != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_prediction_result_card"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                    border = BorderStroke(
                        width = 1.5.dp,
                        brush = Brush.horizontalGradient(
                            colors = if (prediction?.signal == "BUY") {
                                listOf(Color(0xFF34D399), Color(0xFFD1E1FF))
                            } else if (prediction?.signal == "SELL") {
                                listOf(Color(0xFFF87171), Color(0xFFFFA0A0))
                            } else {
                                listOf(Color.DarkGray, Color.Gray)
                            }
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "GEMINI MACHINE LEARNING REPORT",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )

                        if (isAnalyzing) {
                            // Typing simulation loader
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(color = Color(0xFF34D399))
                                Text(
                                    text = "Analyzing RSI boundaries, Bollinger squeezing, EMA crossover metrics...",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            prediction?.let { pred ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("AI TRADE SIGNAL", color = Color.Gray, fontSize = 11.sp)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (pred.signal == "BUY") Color(0x3334D399)
                                                    else if (pred.signal == "SELL") Color(0x33F87171)
                                                    else Color(0x33CCCCCC)
                                                )
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = pred.signal,
                                                color = if (pred.signal == "BUY") Color(0xFF34D399)
                                                else if (pred.signal == "SELL") Color(0xFFF87171)
                                                else Color.White,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("CONFIDENCE LEVEL", color = Color.Gray, fontSize = 11.sp)
                                        Text(
                                            text = "${pred.confidence}%",
                                            color = Color(0xFFD1E1FF),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("PREDICTED TREND", color = Color.Gray, fontSize = 11.sp)
                                        Text(
                                            text = pred.trend,
                                            color = if (pred.trend == "BULLISH") Color(0xFF00E676)
                                            else if (pred.trend == "BEARISH") Color(0xFFFF1744)
                                            else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("TARGET PRICE", color = Color.Gray, fontSize = 11.sp)
                                        Text(
                                            text = "$${String.format(Locale.US, "%,.2f", pred.targetPrice)} USDT",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF1F222C))

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("TECHNICAL METRICS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = pred.indicators,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("ML REASONING", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = pred.reasoning,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF222630))
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = "Security Info", tint = Color(0xFF00F2FE), modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "Prototype Warning: ML indicators generated by Gemini 3.5 Flash for educational simulations. Exercise caution with real trades.",
                                            color = Color.Gray,
                                            fontSize = 10.sp,
                                            lineHeight = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MyBotsScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val botsState by viewModel.bots.collectAsState()
    val prices by viewModel.latestPrices.collectAsState()
    val portfolioState by viewModel.portfolio.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E1116))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
        ) {
            // Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                    border = BorderStroke(1.dp, Color(0xFF303642))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "AUTOMATED ALGORITHMIC BOTS",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Deploy autonomous neural networks to paper trade on Binance in real-time. Bots query Gemini, analyze historical trends, and update automatically.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { showCreateDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34D399)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("deploy_bot_trigger_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Bot", tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DEPLOY NEW TRADING BOT", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Active Bots Title
            item {
                Text(
                    text = "ACTIVE DEPLOYMENTS (${botsState.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }

            if (botsState.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "No Bots",
                            tint = Color.DarkGray,
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "No algorithmic bots running currently.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(botsState) { bot ->
                    // Exclude any mock items or deposit traces
                    if (bot.name == "DummyDeposit" || bot.initialBalance <= 0.0) return@items

                    val currentPrice = prices[bot.pair] ?: 0.0
                    val totalBotValue = bot.currentBalance + (bot.assetHoldings * currentPrice)
                    val pnlUsdt = totalBotValue - bot.initialBalance
                    val pnlPct = (pnlUsdt / bot.initialBalance) * 100.0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bot_card_${bot.id}"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                        border = BorderStroke(1.dp, Color(0xFF303642))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Bot Name and Status Indicator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SmartToy,
                                        contentDescription = "Bot Logo",
                                        tint = if (bot.status == "RUNNING") Color(0xFF34D399) else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = bot.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (bot.status == "RUNNING") Color(0x2234D399) else Color(0x22FFFFFF))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = bot.status,
                                        color = if (bot.status == "RUNNING") Color(0xFF34D399) else Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Meta row (pair / strategy)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column {
                                    Text("TRADING PAIR", color = Color.Gray, fontSize = 10.sp)
                                    Text(bot.pair, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Column {
                                    Text("STRATEGY", color = Color.Gray, fontSize = 10.sp)
                                    Text(bot.strategy.replace("_", " "), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            HorizontalDivider(color = Color(0xFF303642))

                            // PNL and Valuation row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("BOT CAPITAL", color = Color.Gray, fontSize = 10.sp)
                                    Text(
                                        text = "$${String.format(Locale.US, "%,.2f", totalBotValue)} USDT",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("PROFIT / LOSS (PNL)", color = Color.Gray, fontSize = 10.sp)
                                    Text(
                                        text = "${if (pnlUsdt >= 0) "+" else ""}${String.format(Locale.US, "%,.2f", pnlUsdt)} USDT (${if (pnlUsdt >= 0) "+" else ""}${String.format(Locale.US, "%.2f", pnlPct)}%)",
                                        color = if (pnlUsdt >= 0) Color(0xFF34D399) else Color(0xFFF87171),
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp
                                    )
                                }
                            }

                            // Asset Holdings vs USDT Balance detail
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "USDT: $${String.format(Locale.US, "%.2f", bot.currentBalance)} | Holdings: ${String.format(Locale.US, "%.4f", bot.assetHoldings)} ${bot.pair.removeSuffix("USDT")}",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = if (bot.lastRunTime > 0) "Last tick: ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(bot.lastRunTime))}" else "Awaiting tick...",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }

                            // Bot Action Controllers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Toggle run/pause
                                OutlinedButton(
                                    onClick = { viewModel.toggleBotStatus(bot) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .testTag("toggle_bot_button_${bot.id}"),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color.DarkGray)
                                ) {
                                    Icon(
                                        imageVector = if (bot.status == "RUNNING") Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Toggle status",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (bot.status == "RUNNING") "Pause Bot" else "Resume Bot", color = Color.White, fontSize = 11.sp)
                                }

                                // Terminate Bot
                                OutlinedButton(
                                    onClick = { viewModel.deleteBot(bot) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .testTag("delete_bot_button_${bot.id}"),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0x33F87171)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171))
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Shut Down", color = Color(0xFFF87171), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Creation Dialog
        if (showCreateDialog) {
            BotCreationDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, pair, strategy, capital ->
                    viewModel.createBot(name, pair, strategy, capital)
                },
                maxAvailableCapital = portfolioState?.usdtBalance ?: 10000.0
            )
        }
    }
}

@Composable
fun ActivityLogsScreen(
    viewModel: TradingViewModel,
    modifier: Modifier = Modifier
) {
    val ordersState by viewModel.orders.collectAsState()
    val logsState by viewModel.logs.collectAsState()

    var activeTab by remember { mutableStateOf("ALL") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E1116))
            .padding(horizontal = 16.dp)
    ) {
        // Tab filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .background(Color(0xFF1A1C22), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF303642), RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("ALL" to "All Feed", "TRADES" to "Trades", "SYSTEM" to "System Log").forEach { (tabId, label) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (activeTab == tabId) Color(0xFF303642) else Color.Transparent)
                        .clickable { activeTab = tabId }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (activeTab == tabId) Color(0xFF34D399) else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            if (activeTab == "ALL" || activeTab == "TRADES") {
                // If trades tab, show trades title
                if (ordersState.isNotEmpty()) {
                    item {
                        Text("EXECUTED ORDERS LEDGER", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    items(ordersState) { order ->
                        TradeOrderRow(order)
                    }
                }
            }

            if (activeTab == "ALL" || activeTab == "SYSTEM") {
                if (logsState.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("BOT SYSTEM EVENT LOGS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    items(logsState) { log ->
                        // Filter out dummy/deposit messages
                        if (log.message.contains("DummyDeposit") || log.message.contains("Dummy")) return@items
                        BotLogRow(log)
                    }
                }
            }

            if (ordersState.isEmpty() && logsState.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No system activity or trade logs recorded yet.", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TradeOrderRow(order: TradeOrder) {
    val coinSymbol = order.pair.removeSuffix("USDT")
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(order.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trade_order_row_${order.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
        border = BorderStroke(1.dp, Color(0xFF303642))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (order.type == "BUY") Color(0x2234D399) else Color(0x22F87171)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (order.type == "BUY") Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = order.type,
                        tint = if (order.type == "BUY") Color(0xFF34D399) else Color(0xFFF87171),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = order.type,
                            color = if (order.type == "BUY") Color(0xFF34D399) else Color(0xFFF87171),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = coinSymbol,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Text("Time: $timeStr", color = Color.Gray, fontSize = 10.sp)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${String.format(Locale.US, "%.5f", order.amount)} $coinSymbol",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Total: $${String.format(Locale.US, "%,.2f", order.totalUsdt)} USDT",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun BotLogRow(log: BotLog) {
    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(log.timestamp))

    val color = when (log.type) {
        "BUY" -> Color(0xFF34D399)
        "SELL" -> Color(0xFFF87171)
        "PREDICTION" -> Color(0xFFD1E1FF)
        "ERROR" -> Color(0xFFF87171)
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1A1C22))
            .border(1.dp, Color(0xFF303642), RoundedCornerShape(6.dp))
            .padding(8.dp)
            .testTag("bot_log_row_${log.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "[$timeStr]",
                color = Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = log.message,
                color = color,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun WinLossDonutChart(
    completedTrades: List<CompletedTrade>,
    modifier: Modifier = Modifier
) {
    val total = completedTrades.size
    val wins = completedTrades.count { it.isWin }
    val losses = total - wins
    
    val winRatePercent = if (total > 0) {
        (wins.toDouble() / total * 100.0)
    } else {
        0.0
    }
    
    val winSweepAngle = if (total > 0) {
        (wins.toFloat() / total.toFloat()) * 360f
    } else {
        360f
    }
    val lossSweepAngle = 360f - winSweepAngle

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Donut Canvas
        Box(
            modifier = Modifier
                .size(54.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14f
                val sizeMin = size.minDimension - strokeWidth
                
                // Draw Wins Arc
                if (wins > 0 || total == 0) {
                    val winColor = Color(0xFF10B981)
                    drawArc(
                        color = winColor,
                        startAngle = -90f,
                        sweepAngle = if (total == 0) 360f else winSweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                
                // Draw Losses Arc
                if (losses > 0) {
                    val lossColor = Color(0xFFEF4444)
                    val startAngle = -90f + winSweepAngle
                    drawArc(
                        color = lossColor,
                        startAngle = startAngle,
                        sweepAngle = lossSweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
            
            // Text in the center
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = String.format(Locale.US, "%.0f%%", winRatePercent),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "WIN",
                    color = Color(0xFF909094),
                    fontSize = 6.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
        
        // Legend Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF10B981)))
                Text(
                    text = "$wins W",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(1.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                Text(
                    text = "$losses L",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = "$total trades",
                color = Color.Gray,
                fontSize = 7.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun StrategySelectionDialog(
    currentStrategy: String,
    onDismiss: () -> Unit,
    onSelectStrategy: (String) -> Unit
) {
    val allStrategies = listOf(
        TradingStrategyItem(
            name = "Triple Screen Trading System",
            category = "Core Analytical Frameworks",
            description = "Combines 3 distinct timeframes & indicators to filter out market noise.",
            badge = "Framework"
        ),
        TradingStrategyItem(
            name = "Wyckoff Method",
            category = "Core Analytical Frameworks",
            description = "Analyses institutional accumulation, markup, distribution, & markdown cycles."
        ),
        TradingStrategyItem(
            name = "Price Action & Market Geometry",
            category = "Core Analytical Frameworks",
            description = "Pure candlestick structural mechanics & dynamic Fibonacci projection zones."
        ),
        TradingStrategyItem(
            name = "SMC Order Block Expansion",
            category = "AI Ranked Top Strategies",
            description = "Institutional buy/sell order blocks identified via predictive volume expansion.",
            badge = "AI Ranked"
        ),
        TradingStrategyItem(
            name = "SMC High-Probability Mitigation Zone",
            category = "AI Ranked Top Strategies",
            description = "Calculates high-probability reclaim blocks from historic order book depth.",
            badge = "AI Ranked"
        ),
        TradingStrategyItem(
            name = "SMC Fair Value Gap (FVG) Inversion",
            category = "AI Ranked Top Strategies",
            description = "Tracks rapid price imbalances and their subsequent support/resistance flips.",
            badge = "AI Ranked"
        ),
        TradingStrategyItem(
            name = "SMC Liquidity Sweep Core",
            category = "AI Ranked Top Strategies",
            description = "Spots stop-loss clusters and executes when institutional sweeps trigger.",
            badge = "AI Ranked"
        ),
        TradingStrategyItem(
            name = "Bollinger Volatility Breakout",
            category = "AI Ranked Top Strategies",
            description = "Predicts high-momentum channel breakouts using localized standard deviation.",
            badge = "Trending"
        ),
        TradingStrategyItem(
            name = "ADX Multi-Timeframe Trend",
            category = "AI Ranked Top Strategies",
            description = "Identifies maximum strength directional trends across multiple key horizons."
        ),
        TradingStrategyItem(
            name = "VWAP Anchored Mean Reversion",
            category = "AI Ranked Top Strategies",
            description = "Tracks deviations from custom-anchored volume weighted average prices."
        ),
        TradingStrategyItem(
            name = "RSI Extreme Divergence",
            category = "AI Ranked Top Strategies",
            description = "Flags oversold/overbought price zones against underlying oscillator trends."
        ),
        TradingStrategyItem(
            name = "ATR Dynamic Trailing Edge",
            category = "AI Ranked Top Strategies",
            description = "Maintains dynamic stop-losses using Average True Range volatility multiples."
        )
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A)),
            border = BorderStroke(1.dp, Color(0xFF303642))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Select Trading Strategy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Choose an analytical framework or AI model to drive the bot",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF1F222A), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF222630))
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable List of Categories
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Category 1: Core Analytical Frameworks
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = "Analytical Frameworks",
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "CORE ANALYTICAL FRAMEWORKS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF34D399),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            allStrategies.filter { it.category == "Core Analytical Frameworks" }.forEach { strategy ->
                                StrategyOptionItem(
                                    strategy = strategy,
                                    isSelected = currentStrategy == strategy.name,
                                    onSelect = {
                                        onSelectStrategy(strategy.name)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }

                    // Category 2: AI Ranked Top Strategies
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Ranked Strategies",
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "AI RANKED TOP STRATEGIES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF818CF8),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            allStrategies.filter { it.category == "AI Ranked Top Strategies" }.forEach { strategy ->
                                StrategyOptionItem(
                                    strategy = strategy,
                                    isSelected = currentStrategy == strategy.name,
                                    onSelect = {
                                        onSelectStrategy(strategy.name)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StrategyOptionItem(
    strategy: TradingStrategyItem,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF34D399) else Color(0xFF222630)
    val bgColor = if (isSelected) Color(0x1134D399) else Color(0xFF1A1C22)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = strategy.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    strategy.badge?.let { badgeText ->
                        val badgeBg = if (badgeText == "AI Ranked") Color(0x1F818CF8) else Color(0x1F34D399)
                        val badgeColor = if (badgeText == "AI Ranked") Color(0xFF818CF8) else Color(0xFF34D399)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText.uppercase(),
                                color = badgeColor,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strategy.description,
                    color = Color.Gray,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

data class TradingStrategyItem(
    val name: String,
    val category: String,
    val description: String,
    val badge: String? = null
)

// --- BACKTEST FEATURE MODELS & DIALOG ---

data class BacktestTrade(
    val id: String,
    val timestamp: String,
    val type: String,
    val entryPrice: Double,
    val exitPrice: Double,
    val size: Double,
    val profitLoss: Double,
    val profitPercentage: Double,
    val isWin: Boolean
)

data class BacktestResult(
    val strategy: String,
    val pair: String,
    val initialCapital: Double,
    val finalCapital: Double,
    val netProfit: Double,
    val netProfitPercent: Double,
    val winRate: Double,
    val totalTrades: Int,
    val wins: Int,
    val losses: Int,
    val maxDrawdown: Double,
    val profitFactor: Double,
    val equityCurve: List<Float>,
    val trades: List<BacktestTrade>
)

// Seeded pseudorandom generator for deterministic, realistic backtests
class SeededRandom(seed: Long) {
    private var state = seed
    fun nextDouble(): Double {
        state = (state * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
        return state.toDouble() / (1L shl 48)
    }
    fun nextInt(from: Int, until: Int): Int {
        return from + (nextDouble() * (until - from)).toInt()
    }
}

fun runSeededBacktest(strategy: String, pair: String, capital: Double): BacktestResult {
    val paramString = strategy + pair + capital.toString()
    var seedHash = 17L
    for (char in paramString) {
        seedHash = seedHash * 31 + char.code
    }
    val random = SeededRandom(seedHash)

    val currentPrice = when (pair) {
        "BTCUSDT" -> 64250.0
        "ETHUSDT" -> 3450.0
        "SOLUSDT" -> 142.5
        "BNBUSDT" -> 585.0
        "DOGEUSDT" -> 0.124
        "ADAUSDT" -> 0.38
        else -> 100.0
    }

    val tradeCount = when {
        strategy.contains("Triple Screen") -> random.nextInt(11, 15)
        strategy.contains("Wyckoff") -> random.nextInt(6, 9)
        strategy.contains("Price Action") -> random.nextInt(14, 20)
        strategy.contains("Order Block") -> random.nextInt(8, 12)
        strategy.contains("Mitigation Zone") -> random.nextInt(7, 10)
        strategy.contains("Fair Value") -> random.nextInt(13, 18)
        strategy.contains("Liquidity Sweep") -> random.nextInt(9, 14)
        strategy.contains("Bollinger") -> random.nextInt(18, 26)
        strategy.contains("ADX") -> random.nextInt(11, 16)
        strategy.contains("VWAP") -> random.nextInt(15, 22)
        strategy.contains("RSI") -> random.nextInt(16, 24)
        else -> random.nextInt(11, 16)
    }

    val winBias = when {
        strategy.contains("SMC") -> 0.51
        strategy.contains("RSI") -> 0.63
        strategy.contains("Triple Screen") -> 0.57
        strategy.contains("Bollinger") -> 0.43
        else -> 0.54
    }

    val tempTrades = mutableListOf<BacktestTrade>()
    var currentCapital = capital
    val equityPoints = mutableListOf<Float>()
    equityPoints.add(currentCapital.toFloat())

    var maxCapitalPeak = currentCapital
    var maxDd = 0.0

    val dayStep = 30.0 / tradeCount.toDouble()

    for (i in 0 until tradeCount) {
        val isWin = random.nextDouble() < winBias
        
        val profitPercent = if (isWin) {
            if (strategy.contains("SMC") || strategy.contains("Price Action")) {
                random.nextDouble() * 5.0 + 2.5 // +2.5% to +7.5%
            } else {
                random.nextDouble() * 3.0 + 1.2 // +1.2% to +4.2%
            }
        } else {
            if (strategy.contains("SMC")) {
                -(random.nextDouble() * 1.3 + 0.9) // -0.9% to -2.2%
            } else {
                -(random.nextDouble() * 1.0 + 0.6) // -0.6% to -1.6%
            }
        }

        val tradeProfit = currentCapital * (profitPercent / 100.0)
        currentCapital += tradeProfit
        equityPoints.add(currentCapital.toFloat())

        if (currentCapital > maxCapitalPeak) {
            maxCapitalPeak = currentCapital
        } else {
            val dd = (maxCapitalPeak - currentCapital) / maxCapitalPeak * 100.0
            if (dd > maxDd) {
                maxDd = dd
            }
        }

        val dayNum = (30 - (tradeCount - i) * dayStep).toInt().coerceIn(1, 30)
        val dateStr = "July $dayNum, 14:32"

        val entryDev = (random.nextDouble() - 0.5) * 0.08
        val tradeEntryPrice = currentPrice * (1.0 + entryDev)
        val tradeExitPrice = tradeEntryPrice * (1.0 + profitPercent / 100.0)

        tempTrades.add(
            BacktestTrade(
                id = "TX-${1000 + i}",
                timestamp = dateStr,
                type = if (profitPercent > 0) "BUY/SELL" else "BUY/SL-HIT",
                entryPrice = tradeEntryPrice,
                exitPrice = tradeExitPrice,
                size = currentCapital / tradeEntryPrice * 0.1,
                profitLoss = tradeProfit,
                profitPercentage = profitPercent,
                isWin = isWin
            )
        )
    }

    val winsList = tempTrades.filter { it.isWin }
    val lossesList = tempTrades.filter { !it.isWin }
    val totalWinsCapital = winsList.sumOf { it.profitLoss }
    val totalLossesCapital = lossesList.sumOf { -it.profitLoss }

    val profitFactor = if (totalLossesCapital > 0) {
        totalWinsCapital / totalLossesCapital
    } else {
        if (totalWinsCapital > 0) 9.9 else 1.0
    }

    return BacktestResult(
        strategy = strategy,
        pair = pair,
        initialCapital = capital,
        finalCapital = currentCapital,
        netProfit = currentCapital - capital,
        netProfitPercent = (currentCapital - capital) / capital * 100.0,
        winRate = winsList.size.toDouble() / tradeCount.toDouble() * 100.0,
        totalTrades = tradeCount,
        wins = winsList.size,
        losses = lossesList.size,
        maxDrawdown = maxDd,
        profitFactor = profitFactor,
        equityCurve = equityPoints,
        trades = tempTrades.reversed()
    )
}

@Composable
fun BacktestDialog(
    activeStrategy: String,
    onDismiss: () -> Unit
) {
    var selectedPair by remember { mutableStateOf("BTCUSDT") }
    var selectedCapitalStr by remember { mutableStateOf("10000") }
    
    var isRunning by remember { mutableStateOf(false) }
    var currentStepText by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var result by remember { mutableStateOf<BacktestResult?>(null) }

    val initialCapital = selectedCapitalStr.toDoubleOrNull() ?: 10000.0

    LaunchedEffect(isRunning) {
        if (isRunning) {
            val steps = listOf(
                "Connecting to historical K-line server..." to 0.15f,
                "Downloading 4,320 minute bars (last 30 days)..." to 0.35f,
                "Executing indicators ($activeStrategy)..." to 0.60f,
                "Simulating backtest executions & slippage..." to 0.85f,
                "Compiling metrics & portfolio equity curve..." to 1.0f
            )
            for (step in steps) {
                currentStepText = step.first
                val startProgress = progress
                val targetProgress = step.second
                val duration = 400
                val stepTime = 20
                val increments = duration / stepTime
                for (j in 1..increments) {
                    kotlinx.coroutines.delay(stepTime.toLong())
                    progress = startProgress + (targetProgress - startProgress) * (j.toFloat() / increments)
                }
            }
            result = runSeededBacktest(activeStrategy, selectedPair, initialCapital)
            isRunning = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A)),
            border = BorderStroke(1.dp, Color(0xFF303642))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x1A818CF8)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = "Backtest Icon",
                                tint = Color(0xFF818CF8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Strategy Backtester",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Simulate 30 days of historical trades",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFF1F222A), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF222630))
                Spacer(modifier = Modifier.height(12.dp))

                if (isRunning) {
                    // Running state
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier.size(64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFF818CF8),
                                strokeWidth = 5.dp
                            )
                            Text(
                                text = String.format(Locale.US, "%.0f%%", progress * 100f),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "RUNNING BACKTEST SIMULATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF818CF8),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = currentStepText,
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                } else if (result != null) {
                    // Result state
                    val res = result!!
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Overview grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Net Profit Card
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                                border = BorderStroke(1.dp, Color(0xFF303642))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "NET RETURN",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = String.format(Locale.US, "%+.2f%%", res.netProfitPercent),
                                        color = if (res.netProfit >= 0) Color(0xFF34D399) else Color(0xFFF87171),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%+$1,2f", res.netProfit),
                                        fontSize = 8.5.sp,
                                        color = if (res.netProfit >= 0) Color(0x9934D399) else Color(0x99F87171),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Win Rate Card
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                                border = BorderStroke(1.dp, Color(0xFF303642))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "WIN RATE",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = String.format(Locale.US, "%.1f%%", res.winRate),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "${res.wins}W - ${res.losses}L",
                                        fontSize = 8.5.sp,
                                        color = Color.Gray,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            // Profit Factor & Drawdown
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                                border = BorderStroke(1.dp, Color(0xFF303642))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "PROFIT FACTOR",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = String.format(Locale.US, "%.2f", res.profitFactor),
                                        color = Color(0xFFFBBF24),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = String.format(Locale.US, "Max DD: -%.1f%%", res.maxDrawdown),
                                        fontSize = 8.5.sp,
                                        color = Color.Gray,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Chart Title
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "EQUITY CURVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF909094),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${res.pair} (30D)",
                                fontSize = 8.5.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Equity Curve Line Chart Canvas
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(105.dp)
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF171921)),
                            border = BorderStroke(1.dp, Color(0xFF222630))
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp, vertical = 12.dp)
                                ) {
                                    val width = size.width
                                    val height = size.height

                                    if (res.equityCurve.size > 1) {
                                        val minVal = res.equityCurve.minOrNull() ?: 0f
                                        val maxVal = res.equityCurve.maxOrNull() ?: 100f
                                        val valRange = if (maxVal - minVal == 0f) 1f else maxVal - minVal

                                        val pointsToDraw = res.equityCurve.mapIndexed { idx, value ->
                                            val x = (idx.toFloat() / (res.equityCurve.size - 1).toFloat()) * width
                                            val y = height - ((value - minVal) / valRange) * height
                                            androidx.compose.ui.geometry.Offset(x, y)
                                        }

                                        // Create fill path
                                        val fillPath = androidx.compose.ui.graphics.Path().apply {
                                            moveTo(0f, height)
                                            pointsToDraw.forEach { offset ->
                                                lineTo(offset.x, offset.y)
                                            }
                                            lineTo(width, height)
                                            close()
                                        }

                                        // Draw background gradient fill under the line
                                        drawPath(
                                            path = fillPath,
                                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                                colors = listOf(
                                                    Color(0x3334D399),
                                                    Color.Transparent
                                                )
                                            )
                                        )

                                        // Create stroke path
                                        val strokePath = androidx.compose.ui.graphics.Path().apply {
                                            pointsToDraw.forEachIndexed { idx, offset ->
                                                if (idx == 0) {
                                                    moveTo(offset.x, offset.y)
                                                } else {
                                                    lineTo(offset.x, offset.y)
                                                }
                                            }
                                        }

                                        // Draw the line
                                        drawPath(
                                            path = strokePath,
                                            color = Color(0xFF34D399),
                                            style = Stroke(width = 3f, cap = StrokeCap.Round)
                                        )

                                        // Draw point nodes
                                        pointsToDraw.forEach { offset ->
                                            drawCircle(
                                                color = Color(0xFF171921),
                                                radius = 3.5f,
                                                center = offset
                                            )
                                            drawCircle(
                                                color = Color(0xFF34D399),
                                                radius = 2f,
                                                center = offset
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Chart timeline labels
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Day 1", fontSize = 7.5.sp, color = Color.Gray)
                            Text("Day 15", fontSize = 7.5.sp, color = Color.Gray)
                            Text("Day 30", fontSize = 7.5.sp, color = Color.Gray)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Trade Logs Title
                        Text(
                            text = "SIMULATED TRADES",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF909094),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        // Trade log list
                        Box(modifier = Modifier.weight(1f)) {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(res.trades) { trade ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                                        border = BorderStroke(1.dp, Color(0xFF222630))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    val statusColor = if (trade.isWin) Color(0xFF34D399) else Color(0xFFF87171)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(CircleShape)
                                                            .background(statusColor)
                                                    )
                                                    Text(
                                                        text = trade.id,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        text = trade.type,
                                                        color = Color.Gray,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(1.dp))
                                                Text(
                                                    text = trade.timestamp,
                                                    fontSize = 8.sp,
                                                    color = Color.Gray
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                val sign = if (trade.profitPercentage > 0) "+" else ""
                                                val badgeColor = if (trade.isWin) Color(0xFF34D399) else Color(0xFFF87171)
                                                Text(
                                                    text = "$sign${String.format(Locale.US, "%.2f%%", trade.profitPercentage)}",
                                                    color = badgeColor,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 11.5.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(
                                                        text = String.format(Locale.US, "In: $%,.1f", trade.entryPrice),
                                                        fontSize = 7.5.sp,
                                                        color = Color.Gray,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        text = "•",
                                                        fontSize = 7.5.sp,
                                                        color = Color.Gray
                                                    )
                                                    Text(
                                                        text = String.format(Locale.US, "Out: $%,.1f", trade.exitPrice),
                                                        fontSize = 7.5.sp,
                                                        color = Color.Gray,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Run again button
                        Button(
                            onClick = { result = null },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F222A)),
                            border = BorderStroke(1.dp, Color(0xFF303642))
                        ) {
                            Text(
                                text = "RUN ANOTHER SIMULATION",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Configuration state
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Strategy Visual Badge
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C22)),
                            border = BorderStroke(1.dp, Color(0xFF303642))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "TARGET STRATEGY",
                                    fontSize = 8.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Strategy Icon",
                                        tint = Color(0xFF818CF8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = activeStrategy,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Target Asset Selection
                        Text(
                            text = "SELECT TRADING PAIR",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF909094),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val pairs = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "ADAUSDT", "DOGEUSDT")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            pairs.take(3).forEach { pair ->
                                val isSelected = selectedPair == pair
                                val itemBg = if (isSelected) Color(0x33818CF8) else Color(0xFF1A1C22)
                                val itemBorder = if (isSelected) Color(0xFF818CF8) else Color(0xFF303642)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(itemBg)
                                        .border(1.dp, itemBorder, RoundedCornerShape(8.dp))
                                        .clickable { selectedPair = pair },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = pair.removeSuffix("USDT"),
                                        color = if (isSelected) Color(0xFF818CF8) else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            pairs.takeLast(3).forEach { pair ->
                                val isSelected = selectedPair == pair
                                val itemBg = if (isSelected) Color(0x33818CF8) else Color(0xFF1A1C22)
                                val itemBorder = if (isSelected) Color(0xFF818CF8) else Color(0xFF303642)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(itemBg)
                                        .border(1.dp, itemBorder, RoundedCornerShape(8.dp))
                                        .clickable { selectedPair = pair },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = pair.removeSuffix("USDT"),
                                        color = if (isSelected) Color(0xFF818CF8) else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Simulation Capital
                        Text(
                            text = "SIMULATED INITIAL CAPITAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF909094),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val capitalOptions = listOf("1000", "5000", "10000", "50000")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            capitalOptions.forEach { capOption ->
                                val isSelected = selectedCapitalStr == capOption
                                val itemBg = if (isSelected) Color(0x33818CF8) else Color(0xFF1A1C22)
                                val itemBorder = if (isSelected) Color(0xFF818CF8) else Color(0xFF303642)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(itemBg)
                                        .border(1.dp, itemBorder, RoundedCornerShape(8.dp))
                                        .clickable { selectedCapitalStr = capOption },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$${String.format(Locale.US, "%,d", capOption.toInt())}",
                                        color = if (isSelected) Color(0xFF818CF8) else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Lookback Indicator Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF171921)),
                            border = BorderStroke(1.dp, Color(0xFF222630))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                              ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = Color(0xFF818CF8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "This simulation uses standard historical 1-minute candlestick parameters from the last 30 days.",
                                    fontSize = 9.sp,
                                    color = Color.Gray,
                                    lineHeight = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Execute Button
                        Button(
                            onClick = { isRunning = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF818CF8))
                        ) {
                            Text(
                                text = "RUN 30-DAY SIMULATION",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getCoinName(symbol: String): String {
    return when (symbol) {
        "BTCUSDT" -> "Bitcoin"
        "ETHUSDT" -> "Ethereum"
        "BNBUSDT" -> "Binance Coin"
        "SOLUSDT" -> "Solana"
        "XRPUSDT" -> "Ripple"
        "ADAUSDT" -> "Cardano"
        "DOGEUSDT" -> "Dogecoin"
        "TRXUSDT" -> "TRON"
        "TONUSDT" -> "Toncoin"
        "DOTUSDT" -> "Polkadot"
        "LINKUSDT" -> "Chainlink"
        "MATICUSDT" -> "Polygon"
        "SHIBUSDT" -> "Shiba Inu"
        "LTCUSDT" -> "Litecoin"
        "BCHUSDT" -> "Bitcoin Cash"
        "AVAXUSDT" -> "Avalanche"
        "XLMUSDT" -> "Stellar"
        "UNIUSDT" -> "Uniswap"
        "ATOMUSDT" -> "Cosmos"
        "XMRUSDT" -> "Monero"
        else -> symbol.removeSuffix("USDT")
    }
}

@Composable
fun SubtleEntranceTransition(
    delayMillis: Int = 0,
    durationMillis: Int = 450,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        label = "entranceAlpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "entranceScale"
    )

    val offsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 16.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "entranceOffset"
    )

    Box(
        modifier = modifier
            .graphicsLayer(
                alpha = alpha,
                scaleX = scale,
                scaleY = scale
            )
            .offset(y = offsetY)
    ) {
        content()
    }
}

fun getForexName(symbol: String): String {
    return when (symbol) {
        "EURUSD" -> "Euro / US Dollar"
        "GBPUSD" -> "British Pound / US Dollar"
        "USDJPY" -> "US Dollar / Japanese Yen"
        "USDCHF" -> "US Dollar / Swiss Franc"
        "AUDUSD" -> "Australian Dollar / US Dollar"
        "USDCAD" -> "US Dollar / Canadian Dollar"
        "NZDUSD" -> "New Zealand Dollar / US Dollar"
        "EURGBP" -> "Euro / British Pound"
        "EURJPY" -> "Euro / Japanese Yen"
        "GBPJPY" -> "British Pound / Japanese Yen"
        "AUDJPY" -> "Australian Dollar / Japanese Yen"
        "EURCHF" -> "Euro / Swiss Franc"
        "GBPCHF" -> "British Pound / Swiss Franc"
        "EURAUD" -> "Euro / Australian Dollar"
        "EURCAD" -> "Euro / Canadian Dollar"
        "AUDCAD" -> "Australian Dollar / Canadian Dollar"
        "AUDNZD" -> "Australian Dollar / New Zealand Dollar"
        "CADJPY" -> "Canadian Dollar / Japanese Yen"
        "CHFJPY" -> "Swiss Franc / Japanese Yen"
        "NZDJPY" -> "New Zealand Dollar / Japanese Yen"
        else -> if (symbol.length == 6) "${symbol.substring(0, 3)} / ${symbol.substring(3, 6)}" else symbol
    }
}

fun getForexPairFormatted(symbol: String): String {
    if (symbol.length == 6) {
        return "${symbol.substring(0, 3)}/${symbol.substring(3, 6)}"
    }
    return symbol
}

fun getCommodityName(symbol: String): String {
    return when (symbol) {
        "XAUUSD" -> "Gold (Ounce)"
        "XAGUSD" -> "Silver (Ounce)"
        "WTI" -> "Crude Oil WTI"
        "BRENT" -> "Brent Crude Oil"
        "NGAS" -> "Natural Gas"
        "COPPER" -> "Copper"
        "PLATINUM" -> "Platinum"
        "PALLADIUM" -> "Palladium"
        "CORN" -> "Corn"
        "WHEAT" -> "Wheat"
        "SOYBEAN" -> "Soybean"
        "COFFEE" -> "Coffee"
        "SUGAR" -> "Sugar"
        "COCOA" -> "Cocoa"
        "COTTON" -> "Cotton"
        "ALUMINIUM" -> "Aluminium"
        "ZINC" -> "Zinc"
        "NICKEL" -> "Nickel"
        "LEAD" -> "Lead"
        "LUMBER" -> "Lumber"
        else -> symbol
    }
}

fun getCommodityPairFormatted(symbol: String): String {
    return when (symbol) {
        "XAUUSD" -> "XAU/USD"
        "XAGUSD" -> "XAG/USD"
        "WTI" -> "WTI/USD"
        "BRENT" -> "BRENT/USD"
        "NGAS" -> "NGAS/USD"
        else -> "$symbol/USD"
    }
}

@Composable
fun PulsingStatusDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha * 0.4f))
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun ExchangeConnectionIndicatorCard(
    connectionState: com.example.ui.viewmodel.ExchangeConnectionState,
    onConfigureKeys: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exchange_connection_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161920)),
        border = BorderStroke(1.dp, Color(0xFF2C303E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Exchange Connection",
                        tint = when (connectionState.health) {
                            com.example.ui.viewmodel.ConnectionHealth.HEALTHY -> Color(0xFF34D399)
                            com.example.ui.viewmodel.ConnectionHealth.CONNECTING -> Color(0xFF6366F1)
                            com.example.ui.viewmodel.ConnectionHealth.FAILED -> Color(0xFFF87171)
                            else -> Color(0xFF909094)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "EXCHANGE API HEALTH",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color(0xFF909094)
                    )
                }

                // Pulsing light
                val dotColor = when (connectionState.health) {
                    com.example.ui.viewmodel.ConnectionHealth.HEALTHY -> Color(0xFF34D399)
                    com.example.ui.viewmodel.ConnectionHealth.CONNECTING -> Color(0xFF6366F1)
                    com.example.ui.viewmodel.ConnectionHealth.FAILED -> Color(0xFFF87171)
                    com.example.ui.viewmodel.ConnectionHealth.NOT_CONFIGURED -> Color.Gray
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PulsingStatusDot(color = dotColor)
                    Text(
                        text = when (connectionState.health) {
                            com.example.ui.viewmodel.ConnectionHealth.HEALTHY -> "HEALTHY"
                            com.example.ui.viewmodel.ConnectionHealth.CONNECTING -> "PINGING..."
                            com.example.ui.viewmodel.ConnectionHealth.FAILED -> "ERROR"
                            com.example.ui.viewmodel.ConnectionHealth.NOT_CONFIGURED -> "INACTIVE"
                        },
                        color = dotColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (connectionState.health) {
                com.example.ui.viewmodel.ConnectionHealth.NOT_CONFIGURED -> {
                    Text(
                        text = "No Secure API Keys Configured",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Connect your exchange API keys to start live-tracking balance & routing bot orders securely. Currently operating in offline simulation mode.",
                        color = Color(0xFF909094),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onConfigureKeys,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("configure_keys_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF34D399),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "Keys",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CONFIGURE EXCHANGE API KEYS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                com.example.ui.viewmodel.ConnectionHealth.CONNECTING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF34D399),
                            strokeWidth = 2.dp
                        )
                        Column {
                            Text(
                                text = "Checking connection health...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Testing ping and credentials authentication...",
                                color = Color(0xFF909094),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                com.example.ui.viewmodel.ConnectionHealth.HEALTHY -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Connected to ${connectionState.exchangeName.uppercase(Locale.US)}",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Secured",
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "API credentials authenticated & fully operational",
                                    color = Color(0xFF909094),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2C303E))
                                .testTag("refresh_latency_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Test Latency",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF2C303E))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "API LATENCY",
                                color = Color(0xFF909094),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${connectionState.latencyMs} ms",
                                color = when {
                                    connectionState.latencyMs < 150L -> Color(0xFF34D399)
                                    connectionState.latencyMs < 300L -> Color(0xFFFBBF24)
                                    else -> Color(0xFFF87171)
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(modifier = Modifier.weight(1.2f)) {
                            Text(
                                text = "LAST VERIFIED",
                                color = Color(0xFF909094),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
                            val timeStr = if (connectionState.lastChecked > 0) {
                                sdf.format(Date(connectionState.lastChecked))
                            } else {
                                "N/A"
                            }
                            Text(
                                text = timeStr,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "SECURITY STAT",
                                color = Color(0xFF909094),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "SSL ENCRYPTED",
                                color = Color(0xFF60A5FA),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                com.example.ui.viewmodel.ConnectionHealth.FAILED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Authentication Failed",
                                color = Color(0xFFF87171),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = connectionState.errorMessage ?: "Failed to authenticate or connect",
                                color = Color(0xFF909094),
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = onRefresh,
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("retry_connection_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2C303E),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RETRY", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onConfigureKeys,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .testTag("fix_keys_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0x33F87171),
                            contentColor = Color(0xFFF87171)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "Keys",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "UPDATE OR TROUBLESHOOT KEYS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}