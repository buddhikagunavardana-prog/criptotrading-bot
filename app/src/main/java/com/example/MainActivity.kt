package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.AppDatabase
import com.example.data.repository.TradingRepository
import com.example.ui.screens.ActivityLogsScreen
import com.example.ui.screens.AIPredictorScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.MyBotsScreen
import com.example.ui.screens.ExchangeKeysScreen
import com.example.ui.components.NetworkStatusIndicator
import com.example.ui.components.ToastNotificationOverlay
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TradingViewModel
import com.example.ui.viewmodel.TradingViewModelFactory

class MainActivity : ComponentActivity() {

    private val db by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { TradingRepository(db.tradingDao()) }
    
    private val viewModel: TradingViewModel by viewModels {
        TradingViewModelFactory(repository)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var selectedTab by remember { mutableStateOf(0) }
                var showExchangeKeysDialog by remember { mutableStateOf(false) }

                val isWsConnected by viewModel.isWebSocketConnected.collectAsState()
                val latency by viewModel.binanceApiLatency.collectAsState()
                val activeToast by viewModel.activeToast.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SmartToy,
                                        contentDescription = "Bot",
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "AI TRADING BOT",
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.5.sp,
                                        fontSize = 18.sp,
                                        color = Color(0xFFE1E2E9)
                                    )
                                }
                            },
                            actions = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    NetworkStatusIndicator(
                                        isWebSocketConnected = isWsConnected,
                                        latencyMs = latency ?: 0L,
                                        onClick = { showExchangeKeysDialog = true }
                                    )
                                    IconButton(
                                        onClick = { showExchangeKeysDialog = true },
                                        modifier = Modifier.testTag("open_exchange_keys_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VpnKey,
                                            contentDescription = "Exchange Keys Manager",
                                            tint = Color(0xFF34D399)
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = Color(0xFF0E1116)
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = Color(0xFF1A1C22),
                            tonalElevation = 8.dp,
                            modifier = Modifier.testTag("bottom_nav_bar")
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                                label = { Text("Dashboard", fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Black,
                                    selectedTextColor = Color(0xFF34D399),
                                    indicatorColor = Color(0xFF34D399),
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray
                                ),
                                modifier = Modifier.testTag("nav_dashboard")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.Psychology, contentDescription = "AI Predictor") },
                                label = { Text("AI Predictor", fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Black,
                                    selectedTextColor = Color(0xFF34D399),
                                    indicatorColor = Color(0xFF34D399),
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray
                                ),
                                modifier = Modifier.testTag("nav_ai")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.SmartToy, contentDescription = "My Bots") },
                                label = { Text("My Bots", fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Black,
                                    selectedTextColor = Color(0xFF34D399),
                                    indicatorColor = Color(0xFF34D399),
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray
                                ),
                                modifier = Modifier.testTag("nav_bots")
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Logs") },
                                label = { Text("History", fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Black,
                                    selectedTextColor = Color(0xFF34D399),
                                    indicatorColor = Color(0xFF34D399),
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray
                                ),
                                modifier = Modifier.testTag("nav_logs")
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(Color(0xFF0E1116))
                    ) {
                        when (selectedTab) {
                            0 -> DashboardScreen(viewModel, onConfigureKeys = { showExchangeKeysDialog = true })
                            1 -> AIPredictorScreen(viewModel)
                            2 -> MyBotsScreen(viewModel)
                            3 -> ActivityLogsScreen(viewModel)
                        }

                        ToastNotificationOverlay(
                            toast = activeToast,
                            onDismiss = { viewModel.dismissToast() },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                        )
                    }
                }

                if (showExchangeKeysDialog) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showExchangeKeysDialog = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        ExchangeKeysScreen(
                            viewModel = viewModel,
                            onDismiss = { showExchangeKeysDialog = false }
                        )
                    }
                }
            }
        }
    }
}
