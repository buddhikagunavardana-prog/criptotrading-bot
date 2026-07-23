package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.Candle
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RechartsLineChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
    lineColorHex: String = "#34D399"
) {
    val dataJson = remember(candles) {
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("[")
        candles.forEachIndexed { index, candle ->
            val formattedTime = SimpleDateFormat("HH:mm", Locale.US).format(Date(candle.openTime))
            jsonBuilder.append("{")
            jsonBuilder.append("\"time\":\"").append(formattedTime).append("\",")
            jsonBuilder.append("\"price\":").append(candle.close).append(",")
            jsonBuilder.append("\"high\":").append(candle.high).append(",")
            jsonBuilder.append("\"low\":").append(candle.low).append(",")
            jsonBuilder.append("\"volume\":").append(candle.volume)
            jsonBuilder.append("}")
            if (index < candles.size - 1) {
                jsonBuilder.append(",")
            }
        }
        jsonBuilder.append("]")
        jsonBuilder.toString()
    }

    val htmlTemplate = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
          <style>
            body {
              background-color: #14171F;
              margin: 0;
              padding: 0;
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
              color: #FFFFFF;
              overflow: hidden;
            }
            #chart-container {
              width: 100vw;
              height: 100vh;
              display: flex;
              justify-content: center;
              align-items: center;
            }
          </style>
          <!-- Load React & ReactDOM -->
          <script src="https://unpkg.com/react@18/umd/react.production.min.js" crossorigin></script>
          <script src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js" crossorigin></script>
          <!-- Load Prop-Types (required by Recharts) -->
          <script src="https://unpkg.com/prop-types@15.8.1/prop-types.min.js" crossorigin></script>
          <!-- Load Recharts -->
          <script src="https://unpkg.com/recharts@2.12.7/umd/Recharts.js"></script>
          <!-- Load Babel for JSX compiling in browser -->
          <script src="https://unpkg.com/@babel/standalone/babel.min.js"></script>
        </head>
        <body>
          <div id="chart-container"></div>
        
          <script type="text/babel">
            const { ResponsiveContainer, AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip } = Recharts;
        
            const data = $dataJson;
        
            function CustomTooltip({ active, payload }) {
              if (active && payload && payload.length) {
                const item = payload[0].payload;
                return (
                  <div style={{
                    backgroundColor: '#1B1D23',
                    border: '1px solid $lineColorHex',
                    padding: '8px 12px',
                    borderRadius: '6px',
                    fontSize: '11px',
                    fontFamily: 'monospace',
                    boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.5)'
                  }}>
                    <div style={{ color: '#9CA3AF', marginBottom: '4px', fontWeight: 'bold' }}>{item.time}</div>
                    <div style={{ color: '$lineColorHex', fontWeight: 'bold' }}>
                      Price: ${'$'}{Number(payload[0].value).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 4 })}
                    </div>
                    <div style={{ color: '#818CF8' }}>
                      Vol: {Number(item.volume).toLocaleString(undefined, { maximumFractionDigits: 0 })}
                    </div>
                  </div>
                );
              }
              return null;
            }
        
            function App() {
              return (
                <div style={{ width: '100%', height: '100%', padding: '4px', boxSizing: 'border-box' }}>
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={data} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                      <defs>
                        <linearGradient id="colorPrice" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor="$lineColorHex" stopOpacity={0.4}/>
                          <stop offset="95%" stopColor="$lineColorHex" stopOpacity={0.0}/>
                        </linearGradient>
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" stroke="#222631" />
                      <XAxis 
                        dataKey="time" 
                        stroke="#6B7280" 
                        fontSize={9} 
                        tickLine={false}
                      />
                      <YAxis 
                        domain={['auto', 'auto']} 
                        stroke="#6B7280" 
                        fontSize={9} 
                        tickLine={false}
                        tickFormatter={(val) => '$' + Number(val).toLocaleString(undefined, { maximumFractionDigits: val < 2 ? 4 : 1 })}
                      />
                      <Tooltip content={<CustomTooltip />} />
                      <Area 
                        type="monotone" 
                        dataKey="price" 
                        stroke="$lineColorHex" 
                        strokeWidth={2}
                        fillOpacity={1} 
                        fill="url(#colorPrice)" 
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                </div>
              );
            }
        
            const root = ReactDOM.createRoot(document.getElementById('chart-container'));
            root.render(<App />);
          </script>
        </body>
        </html>
    """.trimIndent()

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
                webViewClient = WebViewClient()
                setBackgroundColor(android.graphics.Color.parseColor("#14171F"))
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://localhost",
                htmlTemplate,
                "text/html",
                "UTF-8",
                null
            )
        },
        modifier = modifier
    )
}
