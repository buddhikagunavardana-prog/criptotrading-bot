package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class ToastType {
    SUCCESS,
    ERROR,
    INFO,
    ORDER_SENT,
    STOP_LOSS
}

data class ToastMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val type: ToastType = ToastType.INFO,
    val durationMs: Long = 3500L
)

@Composable
fun ToastNotificationOverlay(
    toast: ToastMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = toast != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        if (toast != null) {
            LaunchedEffect(toast.id) {
                delay(toast.durationMs)
                onDismiss()
            }

            val (bgColor, borderColor, icon, iconColor) = when (toast.type) {
                ToastType.SUCCESS -> Quadruple(
                    Color(0xFF132A22),
                    Color(0xFF34D399),
                    Icons.Default.CheckCircle,
                    Color(0xFF34D399)
                )
                ToastType.ERROR -> Quadruple(
                    Color(0xFF2D1619),
                    Color(0xFFF87171),
                    Icons.Default.Error,
                    Color(0xFFF87171)
                )
                ToastType.ORDER_SENT -> Quadruple(
                    Color(0xFF262010),
                    Color(0xFFFBBF24),
                    Icons.Default.Send,
                    Color(0xFFFBBF24)
                )
                ToastType.STOP_LOSS -> Quadruple(
                    Color(0xFF2C1C13),
                    Color(0xFFFB923C),
                    Icons.Default.Shield,
                    Color(0xFFFB923C)
                )
                ToastType.INFO -> Quadruple(
                    Color(0xFF191D2A),
                    Color(0xFF818CF8),
                    Icons.Default.Info,
                    Color(0xFF818CF8)
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("toast_notification_${toast.type.name.lowercase()}"),
                shape = RoundedCornerShape(14.dp),
                color = bgColor,
                border = BorderStroke(1.dp, borderColor),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(iconColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = toast.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = toast.message,
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Toast",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

val LocalComponentErrorHandler = staticCompositionLocalOf<((String) -> Unit)?> { null }

@Composable
fun ErrorBoundary(
    componentName: String,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    CompositionLocalProvider(
        LocalComponentErrorHandler provides { err ->
            hasError = true
            errorMessage = err
        }
    ) {
        if (hasError) {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(4.dp)
                    .testTag("error_boundary_$componentName"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF221618)),
                border = BorderStroke(1.dp, Color(0xFFF87171).copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "Error Boundary Warning",
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "$componentName Fault Isolated",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = errorMessage ?: "Component state fault isolated. Rest of dashboard is operational.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Button(
                        onClick = {
                            hasError = false
                            errorMessage = null
                            onReset?.invoke()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF303642),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reload $componentName", fontSize = 11.sp)
                    }
                }
            }
        } else {
            Box(modifier = modifier) {
                content()
            }
        }
    }
}

@Composable
fun NetworkStatusIndicator(
    isWebSocketConnected: Boolean,
    latencyMs: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val isHealthy = isWebSocketConnected && latencyMs in 1..400
    val isWarning = isWebSocketConnected && latencyMs > 400
    
    val dotColor = when {
        isHealthy -> Color(0xFF34D399)
        isWarning -> Color(0xFFFBBF24)
        else -> Color(0xFFF87171)
    }

    val statusText = when {
        isHealthy -> "LIVE ${latencyMs}ms"
        isWarning -> "SLOW ${latencyMs}ms"
        else -> "OFFLINE"
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulseDot")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .testTag("network_status_indicator"),
        color = Color(0xFF1B212D),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(if (isHealthy) pulseAlpha else 1.0f)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            Text(
                text = statusText,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
