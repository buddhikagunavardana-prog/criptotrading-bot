package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Candle
import com.example.data.model.GeminiPrediction
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// --- SMA Indicator Calculation Helper ---
private fun calculateSMA(prices: List<Double>, period: Int = 14): List<Double?> {
    val sma = ArrayList<Double?>(prices.size)
    for (i in prices.indices) {
        if (i < period - 1) {
            if (i == 0) {
                sma.add(prices[0])
            } else {
                var sum = 0.0
                for (j in 0..i) {
                    sum += prices[j]
                }
                sma.add(sum / (i + 1))
            }
        } else {
            var sum = 0.0
            for (j in i - period + 1..i) {
                sum += prices[j]
            }
            sma.add(sum / period)
        }
    }
    return sma
}

// --- RSI Indicator Calculation Helper ---
private fun calculateRSI(prices: List<Double>, period: Int = 14): List<Double?> {
    val rsi = ArrayList<Double?>(prices.size)
    if (prices.size < 2) {
        return List(prices.size) { null }
    }
    
    val gains = DoubleArray(prices.size)
    val losses = DoubleArray(prices.size)
    
    for (i in 1 until prices.size) {
        val difference = prices[i] - prices[i - 1]
        if (difference > 0) {
            gains[i] = difference
            losses[i] = 0.0
        } else {
            gains[i] = 0.0
            losses[i] = -difference
        }
    }
    
    var avgGain = 0.0
    var avgLoss = 0.0
    
    for (i in 0 until prices.size) {
        if (i < period) {
            rsi.add(null)
            if (i > 0) {
                avgGain += gains[i]
                avgLoss += losses[i]
            }
            if (i == period - 1) {
                avgGain /= period
                avgLoss /= period
            }
        } else {
            avgGain = (avgGain * (period - 1) + gains[i]) / period
            avgLoss = (avgLoss * (period - 1) + losses[i]) / period
            
            if (avgLoss == 0.0) {
                rsi.add(100.0)
            } else {
                val rs = avgGain / avgLoss
                rsi.add(100.0 - (100.0 / (1.0 + rs)))
            }
        }
    }
    
    for (i in 0 until minOf(period, prices.size)) {
        if (i > 0) {
            val sumG = gains.slice(1..i).sum()
            val sumL = losses.slice(1..i).sum()
            if (sumL == 0.0) {
                rsi[i] = 100.0
            } else {
                rsi[i] = 100.0 - (100.0 / (1.0 + (sumG / sumL)))
            }
        } else {
            rsi[i] = 50.0
        }
    }
    
    return rsi
}

@Composable
private fun IndicatorPill(
    label: String,
    color: Color,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isActive) color.copy(alpha = 0.12f) else Color(0xFF1E222D))
            .border(
                width = 1.dp,
                color = if (isActive) color else Color(0xFF303642),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isActive) color else Color.Gray)
            )
            Text(
                text = label,
                color = if (isActive) Color.White else Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun InteractiveLineChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF34D399), // Emerald neon green
    prediction: GeminiPrediction? = null
) {
    if (candles.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data available", color = Color.Gray, fontSize = 12.sp)
        }
        return
    }

    // Interactive toggle controls for technical indicators
    var showSMA by remember { mutableStateOf(true) }
    var showRSI by remember { mutableStateOf(true) }

    val prices = remember(candles) { candles.map { it.close } }
    val lastPrice = prices.lastOrNull() ?: 0.0

    // Compute technical indicators dynamically
    val smaValues = remember(prices) { calculateSMA(prices, period = 14) }
    val rsiValues = remember(prices) { calculateRSI(prices, period = 14) }

    // 1. Generate future predicted trajectory if prediction is available
    val futureSteps = 6
    val futurePrices = if (prediction != null) {
        val target = prediction.targetPrice
        List(futureSteps) { k ->
            val t = k.toDouble() / (futureSteps - 1)
            val tSmooth = if (t < 0.5) 2 * t * t else -1 + (4 - 2 * t) * t
            lastPrice + (target - lastPrice) * tSmooth
        }
    } else {
        emptyList()
    }

    // 2. Compute bounds for dynamic chart scaling
    val allPrices = if (prediction != null) prices + futurePrices else prices
    val visibleSmas = if (showSMA) smaValues.filterNotNull() else emptyList()
    val allScalePrices = allPrices + visibleSmas
    val minPrice = allScalePrices.minOrNull() ?: 0.0
    val maxPrice = allScalePrices.maxOrNull() ?: 1.0
    val priceDiff = maxPrice - minPrice
    val priceRange = if (priceDiff == 0.0) 1.0 else priceDiff

    var touchX by remember { mutableStateOf<Float?>(null) }
    var isHovering by remember { mutableStateOf(false) }

    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF14171F), shape = RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF222631), shape = RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // --- Indicator Toggle Controls Row ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "INDICATORS",
                color = Color.Gray.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            
            IndicatorPill(
                label = "SMA (14)",
                color = Color(0xFFFBBF24), // Amber
                isActive = showSMA,
                onClick = { showSMA = !showSMA }
            )

            IndicatorPill(
                label = "RSI (14)",
                color = Color(0xFF8B5CF6), // Violet
                isActive = showRSI,
                onClick = { showRSI = !showRSI }
            )
        }

        // --- Synchronized Canvas Visualization Area ---
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(candles, prediction, showSMA, showRSI) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                touchX = offset.x
                                isHovering = true
                            },
                            onDrag = { change, _ ->
                                touchX = change.position.x
                                isHovering = true
                            },
                            onDragEnd = { isHovering = false },
                            onDragCancel = { isHovering = false }
                        )
                    }
                    .pointerInput(candles, prediction, showSMA, showRSI) {
                        detectTapGestures(
                            onPress = { offset ->
                                touchX = offset.x
                                isHovering = true
                                tryAwaitRelease()
                                isHovering = false
                            }
                        )
                    }
            ) {
                // Layout layout configuration
                val paddingLeft = 8.dp.toPx()
                val paddingRight = 60.dp.toPx() // Y-axis labels room
                val paddingTop = 12.dp.toPx()
                val paddingBottom = 20.dp.toPx() // Timeline labels room

                val leftPx = paddingLeft
                val rightPx = size.width - paddingRight
                val topPx = paddingTop
                val bottomPx = size.height - paddingBottom

                val chartWidth = rightPx - leftPx
                val totalChartHeight = bottomPx - topPx

                if (prices.size < 2 || chartWidth <= 0f || totalChartHeight <= 0f) return@Canvas

                // Partition drawing heights for price/indicators splits
                val priceChartHeight: Float
                val rsiChartHeight: Float
                val priceBottom: Float
                val rsiTop: Float
                val rsiBottom: Float

                if (showRSI) {
                    priceChartHeight = totalChartHeight * 0.65f
                    rsiChartHeight = totalChartHeight * 0.25f
                    val gap = totalChartHeight * 0.10f

                    priceBottom = topPx + priceChartHeight
                    rsiTop = priceBottom + gap
                    rsiBottom = rsiTop + rsiChartHeight
                } else {
                    priceChartHeight = totalChartHeight
                    rsiChartHeight = 0f
                    priceBottom = bottomPx
                    rsiTop = 0f
                    rsiBottom = 0f
                }

                val priceTop = topPx

                // Define indicator scale parameters
                val rsiMin = 0.0
                val rsiMax = 100.0
                val rsiRange = 100.0

                // Quick projection helper closures
                fun getPriceY(price: Double): Float {
                    val ratio = ((price - minPrice) / priceRange).toFloat()
                    return priceBottom - (ratio * priceChartHeight)
                }

                fun getRsiY(rsiVal: Double): Float {
                    val ratio = ((rsiVal - rsiMin) / rsiRange).toFloat()
                    return rsiBottom - (ratio * rsiChartHeight)
                }

                val historicalWidth = if (prediction != null) chartWidth * 0.78f else chartWidth
                val futureWidth = if (prediction != null) chartWidth * 0.22f else 0f
                val dividerX = leftPx + historicalWidth
                val stepXHistorical = historicalWidth / (prices.size - 1)

                fun getX(index: Int): Float {
                    return leftPx + (index * stepXHistorical)
                }

                val gridColor = Color(0xFF222631)
                val textStyle = TextStyle(
                    color = Color.Gray.copy(alpha = 0.9f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )

                // --- 1. Draw PRICE Chart horizontal gridlines & right-side Y labels ---
                val priceGridlines = 3
                for (i in 0..priceGridlines) {
                    val ratio = i.toFloat() / priceGridlines
                    val y = priceBottom - (ratio * priceChartHeight)
                    
                    drawLine(
                        color = gridColor,
                        start = Offset(leftPx, y),
                        end = Offset(rightPx, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )

                    val priceAtY = minPrice + (ratio * priceRange)
                    val priceLabel = if (priceAtY < 2.0) {
                        String.format(Locale.US, "$%.4f", priceAtY)
                    } else {
                        String.format(Locale.US, "$%,.2f", priceAtY)
                    }
                    val textLayoutResult = textMeasurer.measure(priceLabel, style = textStyle)
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(rightPx + 6.dp.toPx(), y - textLayoutResult.size.height / 2f)
                    )
                }

                // --- 2. Draw RSI Sub-chart zones, gridlines & labels ---
                if (showRSI) {
                    // Visual fill of optimal rsi band
                    val y30 = getRsiY(30.0)
                    val y70 = getRsiY(70.0)
                    drawRect(
                        color = Color(0xFF8B5CF6).copy(alpha = 0.05f),
                        topLeft = Offset(leftPx, y70),
                        size = Size(rightPx - leftPx, y30 - y70)
                    )

                    val rsiLevels = listOf(30.0, 50.0, 70.0)
                    val rsiColors = listOf(
                        Color(0xFF34D399).copy(alpha = 0.4f), // Oversold boundary green
                        Color.Gray.copy(alpha = 0.2f),
                        Color(0xFFF87171).copy(alpha = 0.4f)  // Overbought boundary red
                    )

                    rsiLevels.forEachIndexed { idx, lvl ->
                        val y = getRsiY(lvl)
                        drawLine(
                            color = rsiColors[idx],
                            start = Offset(leftPx, y),
                            end = Offset(rightPx, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (lvl != 50.0) PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) else null
                        )

                        val textLayout = textMeasurer.measure(
                            lvl.toInt().toString(),
                            style = textStyle.copy(color = rsiColors[idx].copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(rightPx + 6.dp.toPx(), y - textLayout.size.height / 2f)
                        )
                    }
                }

                // --- 3. Draw PRICE Area & Line ---
                val pathHistorical = Path()
                val fillPathHistorical = Path()

                val historicalPoints = prices.mapIndexed { index, price ->
                    val x = getX(index)
                    val y = getPriceY(price)
                    Offset(x, y)
                }

                historicalPoints.forEachIndexed { index, point ->
                    if (index == 0) {
                        pathHistorical.moveTo(point.x, point.y)
                        fillPathHistorical.moveTo(point.x, priceBottom)
                        fillPathHistorical.lineTo(point.x, point.y)
                    } else {
                        pathHistorical.lineTo(point.x, point.y)
                        fillPathHistorical.lineTo(point.x, point.y)
                    }

                    if (index == historicalPoints.size - 1) {
                        fillPathHistorical.lineTo(point.x, priceBottom)
                        fillPathHistorical.close()
                    }
                }

                // Price gradient backdrop
                drawPath(
                    path = fillPathHistorical,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.25f),
                            lineColor.copy(alpha = 0.02f),
                            Color.Transparent
                        ),
                        startY = priceTop,
                        endY = priceBottom
                    )
                )

                // Price line
                drawPath(
                    path = pathHistorical,
                    color = lineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // --- 4. Draw SMA overlay line ---
                if (showSMA) {
                    val pathSMA = Path()
                    var started = false
                    smaValues.forEachIndexed { index, smaVal ->
                        if (smaVal != null) {
                            val x = getX(index)
                            val y = getPriceY(smaVal)
                            if (!started) {
                                pathSMA.moveTo(x, y)
                                started = true
                            } else {
                                pathSMA.lineTo(x, y)
                            }
                        }
                    }
                    if (started) {
                        drawPath(
                            path = pathSMA,
                            color = Color(0xFFFBBF24), // Amber SMA
                            style = Stroke(width = 1.8f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }

                // --- 5. Draw RSI line ---
                if (showRSI) {
                    val pathRSI = Path()
                    var started = false
                    rsiValues.forEachIndexed { index, rsiVal ->
                        if (rsiVal != null) {
                            val x = getX(index)
                            val y = getRsiY(rsiVal)
                            if (!started) {
                                pathRSI.moveTo(x, y)
                                started = true
                            } else {
                                pathRSI.lineTo(x, y)
                            }
                        }
                    }
                    if (started) {
                        drawPath(
                            path = pathRSI,
                            color = Color(0xFF8B5CF6), // Purple RSI
                            style = Stroke(width = 1.8f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }

                // --- 6. Draw AI forecast projection if available ---
                val futurePoints = mutableListOf<Offset>()
                if (prediction != null && futurePrices.isNotEmpty()) {
                    val stepXFuture = futureWidth / (futureSteps - 1)
                    val predictionColor = when (prediction.signal) {
                        "BUY" -> Color(0xFF34D399)
                        "SELL" -> Color(0xFFF87171)
                        else -> Color(0xFFFBBF24)
                    }

                    val pathFuture = Path()
                    futurePrices.forEachIndexed { index, price ->
                        val x = dividerX + (index * stepXFuture)
                        val y = getPriceY(price)
                        val pt = Offset(x, y)
                        futurePoints.add(pt)

                        if (index == 0) {
                            pathFuture.moveTo(pt.x, pt.y)
                        } else {
                            pathFuture.lineTo(pt.x, pt.y)
                        }
                    }

                    drawPath(
                        path = pathFuture,
                        color = predictionColor,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )
                    )

                    // Target indicator dot
                    val targetPt = futurePoints.last()
                    drawCircle(color = predictionColor.copy(alpha = 0.25f), radius = 8.dp.toPx(), center = targetPt)
                    drawCircle(color = predictionColor, radius = 4.dp.toPx(), center = targetPt)
                    drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = targetPt)
                }

                // --- 7. Draw timeline axis labels & synchronized vertical gridlines ---
                val timelineY = if (showRSI) rsiBottom else priceBottom

                if (prediction == null) {
                    val segments = 4
                    for (i in 0..segments) {
                        val ratio = i.toFloat() / segments
                        val x = leftPx + (ratio * chartWidth)

                        drawLine(
                            color = gridColor,
                            start = Offset(x, priceTop),
                            end = Offset(x, timelineY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )

                        val timeLabel = when (i) {
                            0 -> "24h ago"
                            1 -> "18h ago"
                            2 -> "12h ago"
                            3 -> "6h ago"
                            4 -> "Now"
                            else -> ""
                        }
                        val textLayoutResult = textMeasurer.measure(timeLabel, style = textStyle)
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(x - textLayoutResult.size.width / 2f, timelineY + 4.dp.toPx())
                        )
                    }
                } else {
                    val segments = 3
                    for (i in 0..segments) {
                        val ratio = i.toFloat() / segments
                        val x = leftPx + (ratio * historicalWidth)

                        drawLine(
                            color = gridColor,
                            start = Offset(x, priceTop),
                            end = Offset(x, timelineY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )

                        val timeLabel = when (i) {
                            0 -> "24h ago"
                            1 -> "16h ago"
                            2 -> "8h ago"
                            3 -> "Now"
                            else -> ""
                        }
                        val textLayoutResult = textMeasurer.measure(timeLabel, style = textStyle)
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(x - textLayoutResult.size.width / 2f, timelineY + 4.dp.toPx())
                        )
                    }

                    // Future project lines
                    val futureSegments = 2
                    for (i in 1..futureSegments) {
                        val ratio = i.toFloat() / futureSegments
                        val x = dividerX + (ratio * futureWidth)

                        drawLine(
                            color = gridColor.copy(alpha = 0.15f),
                            start = Offset(x, priceTop),
                            end = Offset(x, timelineY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        val timeLabel = when (i) {
                            1 -> "+12h"
                            2 -> "+24h (AI)"
                            else -> ""
                        }
                        val textLayoutResult = textMeasurer.measure(
                            text = timeLabel,
                            style = textStyle.copy(
                                color = if (i == 2) Color(0xFFFBBF24) else Color.Gray,
                                fontWeight = if (i == 2) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(x - textLayoutResult.size.width / 2f, timelineY + 4.dp.toPx())
                        )
                    }
                }

                // Vertical divider at 'NOW' line if AI prediction model exists
                if (prediction != null) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.35f),
                        start = Offset(dividerX, priceTop),
                        end = Offset(dividerX, timelineY),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    val nowLabel = textMeasurer.measure("NOW", style = TextStyle(
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ))
                    drawRoundRect(
                        color = Color(0xFF1E222D),
                        topLeft = Offset(dividerX - nowLabel.size.width / 2f - 4.dp.toPx(), priceTop - 16.dp.toPx()),
                        size = Size(nowLabel.size.width.toFloat() + 8.dp.toPx(), nowLabel.size.height.toFloat() + 4.dp.toPx()),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                    drawText(
                        textLayoutResult = nowLabel,
                        topLeft = Offset(dividerX - nowLabel.size.width / 2f, priceTop - 14.dp.toPx())
                    )
                }

                // --- 8. Unified crosshair hovering and synced tooltips ---
                if (isHovering && touchX != null) {
                    val localTouchX = touchX!!.coerceIn(leftPx, rightPx)
                    
                    if (prediction == null || localTouchX <= dividerX) {
                        val relativeX = localTouchX - leftPx
                        val idx = ((relativeX / historicalWidth) * (prices.size - 1)).roundToInt().coerceIn(0, prices.size - 1)
                        
                        val pricePoint = historicalPoints[idx]
                        val hoveredPrice = prices[idx]
                        val hoveredSMA = smaValues[idx]
                        val hoveredRSI = rsiValues[idx]

                        // Vertically unified dotted tracking line across both charts!
                        drawLine(
                            color = Color.White.copy(alpha = 0.45f),
                            start = Offset(pricePoint.x, priceTop),
                            end = Offset(pricePoint.x, timelineY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        // Price crosshair line
                        drawLine(
                            color = Color.White.copy(alpha = 0.25f),
                            start = Offset(leftPx, pricePoint.y),
                            end = Offset(rightPx, pricePoint.y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        // Interactive nodes
                        drawCircle(color = lineColor, radius = 5.dp.toPx(), center = pricePoint)
                        drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = pricePoint)

                        if (showSMA && hoveredSMA != null) {
                            val smaPt = Offset(pricePoint.x, getPriceY(hoveredSMA))
                            drawCircle(color = Color(0xFFFBBF24), radius = 5.dp.toPx(), center = smaPt)
                            drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = smaPt)
                        }

                        if (showRSI && hoveredRSI != null) {
                            val rsiPt = Offset(pricePoint.x, getRsiY(hoveredRSI))
                            drawCircle(color = Color(0xFF8B5CF6), radius = 5.dp.toPx(), center = rsiPt)
                            drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = rsiPt)
                        }

                        // Sync indicators summary details inside tooltip
                        val priceText = if (hoveredPrice < 2.0) {
                            String.format(Locale.US, "Price: $%.4f", hoveredPrice)
                        } else {
                            String.format(Locale.US, "Price: $%,.2f", hoveredPrice)
                        }

                        val smaText = if (hoveredSMA != null) {
                            if (hoveredSMA < 2.0) String.format(Locale.US, "SMA: $%.4f", hoveredSMA)
                            else String.format(Locale.US, "SMA: $%,.2f", hoveredSMA)
                        } else "SMA: N/A"

                        val rsiText = if (hoveredRSI != null) {
                            String.format(Locale.US, "RSI: %.2f", hoveredRSI)
                        } else "RSI: N/A"

                        val tooltipText = buildString {
                            append(priceText)
                            if (showSMA) {
                                append("\n")
                                append(smaText)
                            }
                            if (showRSI) {
                                append("\n")
                                append(rsiText)
                            }
                        }

                        val tooltipStyle = TextStyle(
                            color = Color.White,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 11.sp
                        )
                        val tooltipLayout = textMeasurer.measure(tooltipText, style = tooltipStyle)

                        val tooltipWidth = tooltipLayout.size.width
                        val tooltipHeight = tooltipLayout.size.height

                        val tooltipX = (pricePoint.x - tooltipWidth / 2f).coerceIn(leftPx + 4.dp.toPx(), rightPx - tooltipWidth - 4.dp.toPx())
                        val tooltipY = (pricePoint.y - tooltipHeight - 16.dp.toPx()).coerceAtLeast(priceTop + 4.dp.toPx())

                        drawRoundRect(
                            color = Color(0xFF1B1D23),
                            topLeft = Offset(tooltipX - 6.dp.toPx(), tooltipY - 4.dp.toPx()),
                            size = Size(tooltipWidth + 12.dp.toPx(), tooltipHeight + 8.dp.toPx()),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                        drawRoundRect(
                            color = lineColor.copy(alpha = 0.8f),
                            topLeft = Offset(tooltipX - 6.dp.toPx(), tooltipY - 4.dp.toPx()),
                            size = Size(tooltipWidth + 12.dp.toPx(), tooltipHeight + 8.dp.toPx()),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawText(textLayoutResult = tooltipLayout, topLeft = Offset(tooltipX, tooltipY))

                    } else if (futurePoints.isNotEmpty()) {
                        val relativeX = localTouchX - dividerX
                        val idx = ((relativeX / futureWidth) * (futureSteps - 1)).roundToInt().coerceIn(0, futureSteps - 1)

                        val hoveredPoint = futurePoints[idx]
                        val hoveredPrice = futurePrices[idx]
                        
                        val predictionColor = when (prediction.signal) {
                            "BUY" -> Color(0xFF34D399)
                            "SELL" -> Color(0xFFF87171)
                            else -> Color(0xFFFBBF24)
                        }

                        drawLine(
                            color = predictionColor.copy(alpha = 0.45f),
                            start = Offset(hoveredPoint.x, priceTop),
                            end = Offset(hoveredPoint.x, timelineY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        drawCircle(color = predictionColor, radius = 5.dp.toPx(), center = hoveredPoint)
                        drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = hoveredPoint)

                        val priceLabel = if (hoveredPrice < 2.0) {
                            String.format(Locale.US, "Proj Price: $%.4f", hoveredPrice)
                        } else {
                            String.format(Locale.US, "Proj Price: $%,.2f", hoveredPrice)
                        }
                        val targetHours = (idx.toFloat() / (futureSteps - 1) * 24).roundToInt()
                        val tooltipText = "[AI Forecast]\n$priceLabel\nTime: +${targetHours}h"

                        val tooltipStyle = TextStyle(
                            color = predictionColor,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 11.sp
                        )
                        val tooltipLayout = textMeasurer.measure(tooltipText, style = tooltipStyle)

                        val tooltipWidth = tooltipLayout.size.width
                        val tooltipHeight = tooltipLayout.size.height
                        val tooltipX = (hoveredPoint.x - tooltipWidth / 2f).coerceIn(leftPx + 4.dp.toPx(), rightPx - tooltipWidth - 4.dp.toPx())
                        val tooltipY = (hoveredPoint.y - tooltipHeight - 16.dp.toPx()).coerceAtLeast(priceTop + 4.dp.toPx())

                        drawRoundRect(
                            color = Color(0xFF1B1D23),
                            topLeft = Offset(tooltipX - 6.dp.toPx(), tooltipY - 4.dp.toPx()),
                            size = Size(tooltipWidth + 12.dp.toPx(), tooltipHeight + 8.dp.toPx()),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                        drawRoundRect(
                            color = predictionColor,
                            topLeft = Offset(tooltipX - 6.dp.toPx(), tooltipY - 4.dp.toPx()),
                            size = Size(tooltipWidth + 12.dp.toPx(), tooltipHeight + 8.dp.toPx()),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawText(textLayoutResult = tooltipLayout, topLeft = Offset(tooltipX, tooltipY))
                    }
                }
            }
        }
    }
}

