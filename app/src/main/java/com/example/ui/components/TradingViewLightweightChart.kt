package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.Candle
import java.util.Locale

data class VwapPoint(val timeSec: Long, val value: Double)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TradingViewLightweightChart(
    candles: List<Candle>,
    symbol: String = "BTCUSDT",
    modifier: Modifier = Modifier
) {
    // Format candle objects into JSON for TradingView Lightweight Charts
    val candlesJson = remember(candles) {
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("[")
        candles.forEachIndexed { index, candle ->
            val timeSec = if (candle.openTime > 2000000000000L) candle.openTime / 1000L else if (candle.openTime > 2000000000L) candle.openTime / 1000L else candle.openTime
            jsonBuilder.append("{")
            jsonBuilder.append("\"time\":").append(timeSec).append(",")
            jsonBuilder.append("\"open\":").append(candle.open).append(",")
            jsonBuilder.append("\"high\":").append(candle.high).append(",")
            jsonBuilder.append("\"low\":").append(candle.low).append(",")
            jsonBuilder.append("\"close\":").append(candle.close).append(",")
            jsonBuilder.append("\"volume\":").append(candle.volume)
            jsonBuilder.append("}")
            if (index < candles.size - 1) {
                jsonBuilder.append(",")
            }
        }
        jsonBuilder.append("]")
        jsonBuilder.toString()
    }

    val htmlTemplate = remember(symbol) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            html, body {
              width: 100%;
              height: 100%;
              margin: 0;
              padding: 0;
              background-color: #14171F;
              overflow: hidden;
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            }
            #chart-container {
              width: 100%;
              height: 100%;
              position: relative;
            }
            #loading-indicator {
              position: absolute;
              top: 50%;
              left: 50%;
              transform: translate(-50%, -50%);
              color: #9CA3AF;
              font-size: 11px;
              font-family: monospace;
              z-index: 10;
            }
            .legend {
              position: absolute;
              top: 8px;
              left: 12px;
              z-index: 5;
              font-family: monospace;
              font-size: 11px;
              color: #E5E7EB;
              background: rgba(20, 23, 31, 0.85);
              padding: 4px 8px;
              border-radius: 4px;
              border: 1px solid #303642;
              pointer-events: none;
              display: flex;
              align-items: center;
              gap: 12px;
              transition: background-color 300ms ease, transform 300ms ease;
              will-change: transform, opacity, background-color;
            }
            .vwap-legend {
              color: #818CF8;
              font-weight: bold;
            }
            @keyframes priceFlashGreen {
              0% { background-color: rgba(52, 211, 153, 0.35); transform: scale(1.02) translateZ(0); }
              100% { background-color: rgba(20, 23, 31, 0.85); transform: scale(1) translateZ(0); }
            }
            @keyframes priceFlashRed {
              0% { background-color: rgba(248, 113, 113, 0.35); transform: scale(1.02) translateZ(0); }
              100% { background-color: rgba(20, 23, 31, 0.85); transform: scale(1) translateZ(0); }
            }
            .flash-up {
              animation: priceFlashGreen 400ms ease-out;
            }
            .flash-down {
              animation: priceFlashRed 400ms ease-out;
            }
          </style>
          <!-- Asynchronous script loading to prevent blocking the main thread -->
          <script src="https://unpkg.com/lightweight-charts@4.2.0/dist/lightweight-charts.standalone.production.js" async></script>
        </head>
        <body>
          <div id="chart-container">
            <div id="loading-indicator">Loading Lightweight Chart...</div>
            <div class="legend" id="legend">
              <span id="leg-symbol" style="font-weight: bold; color: #34D399;">$symbol</span>
              <span id="leg-vwap" class="vwap-legend">VWAP: --</span>
            </div>
          </div>

          <script>
            let chart, candlestickSeries, vwapSeries;
            let pendingCandleUpdate = null;
            let pendingVwapUpdate = null;
            let updateTimer = null;
            let isLoaded = false;

            function calculateVWAP(candles) {
              let cumTPV = 0;
              let cumVol = 0;
              return candles.map(c => {
                const tp = (c.high + c.low + c.close) / 3;
                const vol = c.volume > 0 ? c.volume : 1;
                cumTPV += tp * vol;
                cumVol += vol;
                return {
                  time: c.time,
                  value: cumVol > 0 ? (cumTPV / cumVol) : tp
                };
              });
            }

            function initChart() {
              if (typeof LightweightCharts === 'undefined') {
                setTimeout(initChart, 50);
                return;
              }

              const loader = document.getElementById('loading-indicator');
              if (loader) loader.style.display = 'none';

              const container = document.getElementById('chart-container');

              chart = LightweightCharts.createChart(container, {
                width: container.clientWidth || 300,
                height: container.clientHeight || 180,
                layout: {
                  background: { type: 'solid', color: '#14171F' },
                  textColor: '#9CA3AF',
                  fontSize: 10,
                },
                grid: {
                  vertLines: { color: '#1F2430' },
                  horzLines: { color: '#1F2430' },
                },
                crosshair: {
                  mode: LightweightCharts.CrosshairMode.Normal,
                },
                rightPriceScale: {
                  borderColor: '#303642',
                  scaleMargins: {
                    top: 0.1,
                    bottom: 0.1,
                  },
                },
                timeScale: {
                  borderColor: '#303642',
                  timeVisible: true,
                  secondsVisible: false,
                },
              });

              candlestickSeries = chart.addCandlestickSeries({
                upColor: '#34D399',
                downColor: '#F87171',
                borderUpColor: '#34D399',
                borderDownColor: '#F87171',
                wickUpColor: '#34D399',
                wickDownColor: '#F87171',
              });

              vwapSeries = chart.addLineSeries({
                color: '#818CF8',
                lineWidth: 2,
                title: 'VWAP',
                priceLineVisible: false,
              });

              // Responsive observer to fit container precisely
              const resizeObserver = new ResizeObserver(entries => {
                if (entries && entries.length > 0) {
                  const { width, height } = entries[0].contentRect;
                  if (width > 0 && height > 0) {
                    chart.applyOptions({ width, height });
                  }
                }
              });
              resizeObserver.observe(container);

              isLoaded = true;

              // Load initial data
              if (window.pendingHistoricalData) {
                setHistoricalData(window.pendingHistoricalData);
              }

              // Connect WebSocket for live price updates
              connectWebSocket("$symbol");
            }

            function setHistoricalData(candles) {
              if (!isLoaded || !candlestickSeries) {
                window.pendingHistoricalData = candles;
                return;
              }
              const formattedCandles = candles.map(c => ({
                time: c.time,
                open: c.open,
                high: c.high,
                low: c.low,
                close: c.close
              }));
              candlestickSeries.setData(formattedCandles);

              const vwapData = calculateVWAP(candles);
              vwapSeries.setData(vwapData);

              if (vwapData.length > 0) {
                const lastVwap = vwapData[vwapData.length - 1].value;
                document.getElementById('leg-vwap').innerText = 'VWAP: $' + lastVwap.toFixed(2);
              }

              chart.timeScale().fitContent();
            }

            let lastPrice = 0;
            function triggerPriceFlash(currentPrice) {
              const legend = document.getElementById('legend');
              if (!legend || !currentPrice) return;
              if (lastPrice > 0 && currentPrice !== lastPrice) {
                legend.classList.remove('flash-up', 'flash-down');
                void legend.offsetWidth;
                if (currentPrice > lastPrice) {
                  legend.classList.add('flash-up');
                } else {
                  legend.classList.add('flash-down');
                }
              }
              lastPrice = currentPrice;
            }

            // Real-time dynamic candle update without redrawing full history
            function updateLatestCandle(c, vwapVal) {
              if (!isLoaded || !candlestickSeries) return;

              triggerPriceFlash(c.close);

              pendingCandleUpdate = {
                time: c.time,
                open: c.open,
                high: c.high,
                low: c.low,
                close: c.close
              };
              pendingVwapUpdate = {
                time: c.time,
                value: vwapVal
              };

              // Limit updates to ~5 FPS (200ms debounce/throttle)
              if (!updateTimer) {
                updateTimer = setTimeout(() => {
                  if (pendingCandleUpdate) {
                    candlestickSeries.update(pendingCandleUpdate);
                    vwapSeries.update(pendingVwapUpdate);
                    document.getElementById('leg-vwap').innerText = 'VWAP: $' + pendingVwapUpdate.value.toFixed(2);
                    pendingCandleUpdate = null;
                    pendingVwapUpdate = null;
                  }
                  updateTimer = null;
                }, 200);
              }
            }

            let socket = null;
            function connectWebSocket(pair) {
              if (socket) {
                try { socket.close(); } catch(e) {}
              }
              const cleanPair = pair.toLowerCase().replace('/', '').replace('usdt', 'usdt');
              const wsUrl = `wss://stream.binance.com:9443/ws/` + cleanPair + `@kline_1m`;
              try {
                socket = new WebSocket(wsUrl);
                socket.onmessage = function(event) {
                  const msg = JSON.parse(event.data);
                  if (msg.e === 'kline') {
                    const k = msg.k;
                    const candle = {
                      time: Math.floor(k.t / 1000),
                      open: parseFloat(k.o),
                      high: parseFloat(k.h),
                      low: parseFloat(k.l),
                      close: parseFloat(k.c),
                      volume: parseFloat(k.v)
                    };
                    const tp = (candle.high + candle.low + candle.close) / 3;
                    updateLatestCandle(candle, tp);
                  }
                };
              } catch(e) {
                console.log('WS Connection error:', e);
              }
            }

            window.onload = initChart;
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // When candles dataset updates, update historical data via Javascript bridge without re-instantiating WebView
    LaunchedEffect(candlesJson, webViewRef) {
        webViewRef?.evaluateJavascript("setHistoricalData($candlesJson);", null)
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript("setHistoricalData($candlesJson);", null)
                    }
                }
                setBackgroundColor(android.graphics.Color.parseColor("#14171F"))
                loadDataWithBaseURL(
                    "https://localhost",
                    htmlTemplate,
                    "text/html",
                    "UTF-8",
                    null
                )
                webViewRef = this
            }
        },
        update = { webView ->
            webViewRef = webView
        },
        modifier = modifier
    )
}
