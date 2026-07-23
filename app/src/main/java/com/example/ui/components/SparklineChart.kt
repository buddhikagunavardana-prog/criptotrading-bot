package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.data.model.Candle

@Composable
fun SparklineChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0x00, 0xE6, 0x76) // Default bright neon green
) {
    if (candles.isEmpty()) {
        Box(modifier = modifier)
        return
    }

    val prices = candles.map { it.close }
    val minPrice = prices.minOrNull() ?: 0.0
    val maxPrice = prices.maxOrNull() ?: 1.0
    val priceRange = maxPrice - minPrice

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        if (prices.size < 2) return@Canvas

        val path = Path()
        val fillPath = Path()

        val stepX = width / (prices.size - 1)

        prices.forEachIndexed { index, price ->
            val ratioY = if (priceRange == 0.0) 0.5f else ((price - minPrice) / priceRange).toFloat()
            // Invert Y coordinate because Canvas 0,0 is top-left
            val x = index * stepX
            val y = height - (ratioY * (height - 12.dp.toPx())) - 6.dp.toPx()

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            if (index == prices.size - 1) {
                fillPath.lineTo(x, height)
                fillPath.close()
            }
        }

        // Draw the gradient fill underneath
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.25f),
                    Color.Transparent
                )
            )
        )

        // Draw the sparkline itself
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
