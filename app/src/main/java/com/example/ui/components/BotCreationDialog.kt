package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun BotCreationDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, pair: String, strategy: String, capital: Double) -> Unit,
    maxAvailableCapital: Double
) {
    var name by remember { mutableStateOf("Quant Bot Alpha") }
    var selectedPair by remember { mutableStateOf("BTCUSDT") }
    var selectedStrategy by remember { mutableStateOf("ML_TREND_FOLLOWER") }
    var capitalString by remember { mutableStateOf("1000") }
    var isPairDropdownExpanded by remember { mutableStateOf(false) }
    var isStrategyDropdownExpanded by remember { mutableStateOf(false) }

    val pairs = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "DOGEUSDT", "ADAUSDT")
    val strategies = listOf(
        "ML_TREND_FOLLOWER" to "AI Trend Predictor",
        "DCA" to "Dollar-Cost Averaging (DCA)",
        "GRID" to "AI Grid Scalper"
    )

    val capital = capitalString.toDoubleOrNull() ?: 0.0
    val isCapitalValid = capital > 0.0 && capital <= maxAvailableCapital

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("bot_creation_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1C22)
            ),
            border = BorderStroke(1.dp, Color(0xFF303642))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Launch New AI Trading Bot",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )

                // Bot Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Bot Name", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF34D399),
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("bot_name_input"),
                    singleLine = true
                )

                // Coin Pair Selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedPair,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Crypto Pair", color = Color.Gray) },
                        trailingIcon = {
                            Icon(
                                imageVector = if (isPairDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
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
                            .clickable { isPairDropdownExpanded = true }
                            .testTag("pair_selector")
                    )
                    DropdownMenu(
                        expanded = isPairDropdownExpanded,
                        onDismissRequest = { isPairDropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(Color(0xFF1A1C22))
                    ) {
                        pairs.forEach { pair ->
                            DropdownMenuItem(
                                text = { Text(pair, color = Color.White) },
                                onClick = {
                                    selectedPair = pair
                                    isPairDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Strategy Selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    val strategyLabel = strategies.firstOrNull { it.first == selectedStrategy }?.second ?: selectedStrategy
                    OutlinedTextField(
                        value = strategyLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Trading Strategy", color = Color.Gray) },
                        trailingIcon = {
                            Icon(
                                imageVector = if (isStrategyDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
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
                            .clickable { isStrategyDropdownExpanded = true }
                            .testTag("strategy_selector")
                    )
                    DropdownMenu(
                        expanded = isStrategyDropdownExpanded,
                        onDismissRequest = { isStrategyDropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(Color(0xFF1A1C22))
                    ) {
                        strategies.forEach { (strategyId, label) ->
                            DropdownMenuItem(
                                text = { Text(label, color = Color.White) },
                                onClick = {
                                    selectedStrategy = strategyId
                                    isStrategyDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Capital Allocation
                OutlinedTextField(
                    value = capitalString,
                    onValueChange = { capitalString = it },
                    label = { Text("Capital Allocation (USDT)", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF34D399),
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("capital_input"),
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = "Available Capital: $${String.format("%.2f", maxAvailableCapital)} USDT",
                            color = if (isCapitalValid) Color.Gray else Color.Red
                        )
                    }
                )

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (isCapitalValid && name.isNotBlank()) {
                                onCreate(name, selectedPair, selectedStrategy, capital)
                                onDismiss()
                            }
                        },
                        enabled = isCapitalValid && name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF34D399),
                            disabledContainerColor = Color.DarkGray
                        ),
                        modifier = Modifier.testTag("confirm_create_bot_button")
                    ) {
                        Text("Deploy Bot", color = Color.Black)
                    }
                }
            }
        }
    }
}
