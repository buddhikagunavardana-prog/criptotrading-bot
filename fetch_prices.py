#!/usr/bin/env python3
"""
Binance Real-Time Price Fetcher using CCXT
This script connects to Binance via CCXT, fetches real-time prices for top crypto pairs,
and demonstrates how to retrieve order book and OHLCV (candlestick) data for trading bots.
"""

import time
import sys
from datetime import datetime

try:
    import ccxt
except ImportError:
    print("Error: The 'ccxt' library is not installed.")
    print("Please install it using: pip install ccxt")
    sys.exit(1)

def get_realtime_prices(exchange, symbols):
    """
    Fetches real-time ticker data for the specified symbols.
    """
    print(f"\n--- Fetching Real-Time Tickers ({datetime.now().strftime('%Y-%m-%d %H:%M:%S')}) ---")
    try:
        # Fetch multiple tickers at once to save API requests and avoid rate limits
        tickers = exchange.fetch_tickers(symbols)
        for symbol in symbols:
            if symbol in tickers:
                ticker = tickers[symbol]
                last_price = ticker['last']
                bid = ticker['bid']
                ask = ticker['ask']
                volume = ticker['baseVolume']
                change_24h = ticker['percentage']
                
                print(f"[{symbol}] Price: ${last_price:,.2f} | Bid: ${bid:,.2f} | Ask: ${ask:,.2f} | 24h Vol: {volume:,.2f} | 24h Change: {change_24h:+.2f}%")
            else:
                print(f"[{symbol}] Ticker not found.")
    except ccxt.NetworkError as e:
        print(f"Network error fetching tickers: {e}")
    except ccxt.ExchangeError as e:
        print(f"Exchange error fetching tickers: {e}")
    except Exception as e:
        print(f"An unexpected error occurred: {e}")

def get_order_book(exchange, symbol, limit=5):
    """
    Retrieves the current order book (depth) for a given symbol.
    Useful for assessing market depth, liquidity, and slippage.
    """
    print(f"\n--- Order Book Depth for {symbol} (Limit: {limit}) ---")
    try:
        order_book = exchange.fetch_order_book(symbol, limit)
        bids = order_book['bids']
        asks = order_book['asks']
        
        print("  Bids (Buy Orders)      |   Asks (Sell Orders)")
        print("  Price       Amount     |   Price       Amount")
        print("  ---------------------------------------------")
        for i in range(min(limit, len(bids), len(asks))):
            bid_price, bid_amt = bids[i]
            ask_price, ask_amt = asks[i]
            print(f"  ${bid_price:<10,.2f} {bid_amt:<10.4f} |  ${ask_price:<10,.2f} {ask_amt:<10.4f}")
    except Exception as e:
        print(f"Error fetching order book: {e}")

def get_ohlcv(exchange, symbol, timeframe='1h', limit=5):
    """
    Retrieves historical OHLCV (Open, High, Low, Close, Volume) candlestick data.
    Essential for technical analysis indicator calculations (RSI, MACD, etc.).
    """
    print(f"\n--- Recent Candles for {symbol} ({timeframe} timeframe) ---")
    try:
        # fetch_ohlcv returns list of list: [timestamp, open, high, low, close, volume]
        candles = exchange.fetch_ohlcv(symbol, timeframe, limit=limit)
        print("  Timestamp            | Open       | High       | Low        | Close      | Volume")
        print("  ---------------------------------------------------------------------------------")
        for candle in candles:
            dt = datetime.fromtimestamp(candle[0] / 1000.0).strftime('%Y-%m-%d %H:%M:%S')
            open_p, high_p, low_p, close_p, vol = candle[1], candle[2], candle[3], candle[4], candle[5]
            print(f"  {dt}  | ${open_p:<10,.2f} | ${high_p:<10,.2f} | ${low_p:<10,.2f} | ${close_p:<10,.2f} | {vol:<10.2f}")
    except Exception as e:
        print(f"Error fetching OHLCV candles: {e}")

def main():
    # Initialize the exchange client
    # enableRateLimit is highly recommended to manage request speeds automatically
    exchange = ccxt.binance({
        'enableRateLimit': True,
        'options': {
            'defaultType': 'spot', # 'spot', 'future', 'delivery'
        }
    })

    # Trading pairs to monitor
    symbols = ['BTC/USDT', 'ETH/USDT', 'SOL/USDT', 'BNB/USDT']

    print("==========================================================")
    print("      Binance Real-Time CCXT Trading Bot Interface        ")
    print("==========================================================")
    
    # 1. Fetch Order Book
    get_order_book(exchange, 'BTC/USDT', limit=5)
    
    # 2. Fetch OHLCV Candlesticks
    get_ohlcv(exchange, 'BTC/USDT', timeframe='15m', limit=5)

    # 3. Enter continuous tracking loop for real-time ticker prices
    print("\nStarting continuous real-time price feed tracker (Press Ctrl+C to stop)...")
    try:
        while True:
            get_realtime_prices(exchange, symbols)
            # Fetch prices every 5 seconds
            time.sleep(5)
    except KeyboardInterrupt:
        print("\nTracker stopped. Exiting gracefully.")
        sys.exit(0)

if __name__ == "__main__":
    main()
