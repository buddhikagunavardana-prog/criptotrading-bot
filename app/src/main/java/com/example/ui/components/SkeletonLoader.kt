package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun shimmerBrush(
    targetValue: Float = 1000f,
    showShimmer: Boolean = true
): Brush {
    return if (showShimmer) {
        val shimmerColors = listOf(
            Color(0xFF1B212D),
            Color(0xFF2A3245),
            Color(0xFF1B212D),
        )

        val transition = rememberInfiniteTransition(label = "shimmerTransition")
        val translateAnimation by transition.animateFloat(
            initialValue = 0f,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmerAnimation"
        )

        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnimation - 500f, translateAnimation - 500f),
            end = Offset(translateAnimation, translateAnimation)
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent),
            start = Offset.Zero,
            end = Offset.Zero
        )
    }
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shapeRadius: Dp = 12.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(shapeRadius))
            .background(shimmerBrush())
    )
}

@Composable
fun ChartSkeletonLoader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1C22))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SkeletonBox(modifier = Modifier.width(140.dp).height(20.dp))
            SkeletonBox(modifier = Modifier.width(180.dp).height(24.dp))
        }
        SkeletonBox(modifier = Modifier.fillMaxWidth().height(200.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SkeletonBox(modifier = Modifier.width(100.dp).height(14.dp))
            SkeletonBox(modifier = Modifier.width(120.dp).height(14.dp))
            SkeletonBox(modifier = Modifier.width(100.dp).height(14.dp))
        }
    }
}

@Composable
fun OrderEntrySkeletonLoader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1C22))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SkeletonBox(modifier = Modifier.width(160.dp).height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonBox(modifier = Modifier.weight(1f).height(44.dp))
            SkeletonBox(modifier = Modifier.weight(1f).height(44.dp))
        }
        SkeletonBox(modifier = Modifier.fillMaxWidth().height(48.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth().height(36.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth().height(48.dp))
    }
}

@Composable
fun TickerCardsSkeletonLoader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SkeletonBox(modifier = Modifier.width(140.dp).height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                SkeletonBox(
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp)
                )
            }
        }
    }
}

@Composable
fun TableSkeletonLoader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1C22))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SkeletonBox(modifier = Modifier.width(180.dp).height(22.dp))
        repeat(3) {
            SkeletonBox(modifier = Modifier.fillMaxWidth().height(40.dp))
        }
    }
}

enum class PriceDirection { NONE, UP, DOWN }

@Composable
fun GpuPriceFlashText(
    price: Double,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default,
    formatPattern: String = "%,.2f",
    unitSuffix: String = ""
) {
    var prevPrice by remember { mutableDoubleStateOf(price) }
    var direction by remember { mutableStateOf(PriceDirection.NONE) }

    LaunchedEffect(price) {
        if (price > prevPrice && prevPrice > 0.0) {
            direction = PriceDirection.UP
            delay(450)
            direction = PriceDirection.NONE
        } else if (price < prevPrice && prevPrice > 0.0) {
            direction = PriceDirection.DOWN
            delay(450)
            direction = PriceDirection.NONE
        }
        prevPrice = price
    }

    val scale by animateFloatAsState(
        targetValue = if (direction != PriceDirection.NONE) 1.04f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "priceScale"
    )

    val flashBgColor by animateColorAsState(
        targetValue = when (direction) {
            PriceDirection.UP -> Color(0xFF34D399).copy(alpha = 0.25f)
            PriceDirection.DOWN -> Color(0xFFF87171).copy(alpha = 0.25f)
            PriceDirection.NONE -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing),
        label = "priceFlashColor"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(6.dp))
            .background(flashBgColor)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (price > 0.0) "${String.format(Locale.US, formatPattern, price)}$unitSuffix" else "Loading...",
            style = textStyle,
            color = when (direction) {
                PriceDirection.UP -> Color(0xFF34D399)
                PriceDirection.DOWN -> Color(0xFFF87171)
                PriceDirection.NONE -> textStyle.color
            }
        )
    }
}
