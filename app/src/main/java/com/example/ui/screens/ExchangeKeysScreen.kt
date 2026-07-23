package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.api.ExchangeKeyResponse
import com.example.ui.viewmodel.TradingViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeKeysScreen(
    viewModel: TradingViewModel,
    onDismiss: () -> Unit
) {
    val backendBaseUrl by viewModel.backendBaseUrl.collectAsState()
    val backendUsername by viewModel.backendUsername.collectAsState()
    val backendPassword by viewModel.backendPassword.collectAsState()
    val backendToken by viewModel.backendToken.collectAsState()
    val backendIsLoggedIn by viewModel.backendIsLoggedIn.collectAsState()
    val isBackendAuthenticating by viewModel.isBackendAuthenticating.collectAsState()
    val backendAuthError by viewModel.backendAuthError.collectAsState()

    val exchangeKeys by viewModel.exchangeKeys.collectAsState()
    val isFetchingExchangeKeys by viewModel.isFetchingExchangeKeys.collectAsState()
    val exchangeKeysError by viewModel.exchangeKeysError.collectAsState()
    val isSavingExchangeKey by viewModel.isSavingExchangeKey.collectAsState()
    val saveExchangeKeySuccess by viewModel.saveExchangeKeySuccess.collectAsState()

    // Form states
    var selectedExchange by remember { mutableStateOf("Binance") }
    var apiKeyInput by remember { mutableStateOf("") }
    var apiSecretInput by remember { mutableStateOf("") }
    var passphraseInput by remember { mutableStateOf("") }

    var isExchangeDropdownExpanded by remember { mutableStateOf(false) }
    var isApiSecretVisible by remember { mutableStateOf(false) }
    var isPassphraseVisible by remember { mutableStateOf(false) }

    val exchanges = listOf("Binance", "OKX", "Coinbase", "Bybit", "Kraken")

    // Automatically trigger exchange keys list fetch on startup if logged in
    LaunchedEffect(backendIsLoggedIn) {
        if (backendIsLoggedIn) {
            viewModel.fetchExchangeKeys()
        }
    }

    // Reset input fields when key is successfully saved
    LaunchedEffect(saveExchangeKeySuccess) {
        if (saveExchangeKeySuccess) {
            apiKeyInput = ""
            apiSecretInput = ""
            passphraseInput = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "Keys",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SECURE EXCHANGE KEYS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 1.sp,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF161920)
                )
            )
        },
        containerColor = Color(0xFF0E1116)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp)
        ) {
            // --- SECTION 1: Backend Server Configuration ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("backend_config_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161920)),
                    border = BorderStroke(1.dp, Color(0xFF2C303E))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BACKEND ENDPOINT CONNECTION",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Icon(
                                imageVector = if (backendIsLoggedIn) Icons.Default.Cloud else Icons.Default.CloudOff,
                                contentDescription = "Connection Status",
                                tint = if (backendIsLoggedIn) Color(0xFF34D399) else Color(0xFFF87171),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = backendBaseUrl,
                            onValueChange = { viewModel.updateBackendBaseUrl(it) },
                            label = { Text("API Backend URL", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF34D399),
                                unfocusedBorderColor = Color.DarkGray
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("backend_url_input"),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (!backendIsLoggedIn) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = backendUsername,
                                    onValueChange = { viewModel.backendUsername.value = it },
                                    label = { Text("Username", color = Color.Gray) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF34D399),
                                        unfocusedBorderColor = Color.DarkGray
                                    ),
                                    modifier = Modifier.weight(1f).testTag("backend_username_input"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = backendPassword,
                                    onValueChange = { viewModel.backendPassword.value = it },
                                    label = { Text("Password", color = Color.Gray) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF34D399),
                                        unfocusedBorderColor = Color.DarkGray
                                    ),
                                    modifier = Modifier.weight(1f).testTag("backend_password_input"),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { viewModel.loginToBackend() },
                                enabled = !isBackendAuthenticating,
                                modifier = Modifier.fillMaxWidth().testTag("connect_backend_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF34D399),
                                    disabledContainerColor = Color.DarkGray
                                )
                            ) {
                                if (isBackendAuthenticating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.Black,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Connect & Authenticate", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }

                            backendAuthError?.let { error ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = error,
                                    color = Color(0xFFF87171),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Connected as: admin",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                TextButton(
                                    onClick = { viewModel.logoutBackend() },
                                    modifier = Modifier.testTag("logout_backend_button")
                                ) {
                                    Text("Disconnect", color = Color(0xFFF87171))
                                }
                            }
                        }
                    }
                }
            }

            if (backendIsLoggedIn) {
                // --- SECTION 2: Save New Credentials Form ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("add_keys_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161920)),
                        border = BorderStroke(1.dp, Color(0xFF2C303E))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "ADD SECURE EXCHANGE CREDENTIALS",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            // Warning Banner about End-to-End Encryption
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0x1134D399)),
                                border = BorderStroke(1.dp, Color(0x3334D399)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Shield",
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "AES-256 ENCRYPTED: Credentials are encrypted on transit and in the database. They are masked before viewing.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Exchange Name Dropdown
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = selectedExchange,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Exchange", color = Color.Gray) },
                                    trailingIcon = {
                                        IconButton(onClick = { isExchangeDropdownExpanded = !isExchangeDropdownExpanded }) {
                                            Icon(
                                                imageVector = if (isExchangeDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                                contentDescription = "Dropdown Arrow",
                                                tint = Color.White
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color.DarkGray,
                                        unfocusedBorderColor = Color.DarkGray
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isExchangeDropdownExpanded = true }
                                        .testTag("exchange_dropdown")
                                )
                                DropdownMenu(
                                    expanded = isExchangeDropdownExpanded,
                                    onDismissRequest = { isExchangeDropdownExpanded = false },
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .background(Color(0xFF161920))
                                ) {
                                    exchanges.forEach { exchange ->
                                        DropdownMenuItem(
                                            text = { Text(exchange, color = Color.White) },
                                            onClick = {
                                                selectedExchange = exchange
                                                isExchangeDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // API Key Field
                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                label = { Text("API Key", color = Color.Gray) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF34D399),
                                    unfocusedBorderColor = Color.DarkGray
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
                                singleLine = true
                            )

                            // API Secret Field
                            OutlinedTextField(
                                value = apiSecretInput,
                                onValueChange = { apiSecretInput = it },
                                label = { Text("API Secret", color = Color.Gray) },
                                visualTransformation = if (isApiSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isApiSecretVisible = !isApiSecretVisible }) {
                                        Icon(
                                            imageVector = if (isApiSecretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Secret",
                                            tint = Color.Gray
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF34D399),
                                    unfocusedBorderColor = Color.DarkGray
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("api_secret_input"),
                                singleLine = true
                            )

                            // Optional Passphrase Field
                            OutlinedTextField(
                                value = passphraseInput,
                                onValueChange = { passphraseInput = it },
                                label = { Text("Passphrase (Optional)", color = Color.Gray) },
                                visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isPassphraseVisible = !isPassphraseVisible }) {
                                        Icon(
                                            imageVector = if (isPassphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle Passphrase",
                                            tint = Color.Gray
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF34D399),
                                    unfocusedBorderColor = Color.DarkGray
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("passphrase_input"),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    viewModel.saveExchangeKey(
                                        exchangeName = selectedExchange,
                                        apiKey = apiKeyInput,
                                        apiSecret = apiSecretInput,
                                        passphrase = passphraseInput.ifBlank { null }
                                    )
                                },
                                enabled = apiKeyInput.isNotBlank() && apiSecretInput.isNotBlank() && !isSavingExchangeKey,
                                modifier = Modifier.fillMaxWidth().testTag("save_exchange_key_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF34D399),
                                    disabledContainerColor = Color.DarkGray
                                )
                            ) {
                                if (isSavingExchangeKey) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.Black,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Encrypt & Save Credentials", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }

                            exchangeKeysError?.let { error ->
                                Text(
                                    text = error,
                                    color = Color(0xFFF87171),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // --- SECTION 3: Currently Registered Keys ---
                item {
                    Text(
                        text = "REGISTERED CONFIGURATIONS",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.LightGray,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (isFetchingExchangeKeys) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF34D399))
                        }
                    }
                } else if (exchangeKeys.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161920)),
                            border = BorderStroke(1.dp, Color(0xFF2C303E))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = "Empty Keys",
                                    tint = Color.DarkGray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "No exchange keys configured yet",
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Add credentials above to securely deploy bots and enable auto-trading on external exchanges.",
                                    color = Color.DarkGray,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                } else {
                    items(exchangeKeys) { keyResponse ->
                        ExchangeKeyRowItem(
                            keyResponse = keyResponse,
                            onDelete = { viewModel.deleteExchangeKey(keyResponse.id) }
                        )
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161920)),
                        border = BorderStroke(1.dp, Color(0xFF2C303E))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Authentication Required",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Connect to the API Backend server using credentials to manage and store encrypted exchange API configurations.",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExchangeKeyRowItem(
    keyResponse: ExchangeKeyResponse,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("exchange_key_item_${keyResponse.exchangeName.lowercase()}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B212D)),
        border = BorderStroke(1.dp, Color(0xFF2C303E))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x2234D399))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = keyResponse.exchangeName.uppercase(Locale.US),
                            color = Color(0xFF34D399),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Encrypted",
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Masked",
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = keyResponse.apiKeyMasked,
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_key_button_${keyResponse.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete configuration",
                    tint = Color(0xFFF87171)
                )
            }
        }
    }
}
