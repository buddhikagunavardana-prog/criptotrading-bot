#!/usr/bin/env python3
"""
=============================================================================
                  CryptoBot AI - High-Fidelity Backtesting Engine
=============================================================================
A professional backtesting module that allows running the bot's current trading
logic against historical SQLite data to compute key performance metrics before
live execution.
"""

import os
import sys
import argparse
from datetime import datetime
from typing import List, Dict, Any, Tuple, Optional
import pandas as pd
import numpy as np
from sqlalchemy import text

# Import the main bot engine and database models
try:
    from trading_bot_engine import (
        TradingBotEngine,
        SessionLocal,
        HistoricalPrice,
        engine
    )
except ImportError as e:
    print(f"Error: Could not import TradingBotEngine components: {e}")
    print("Please ensure backtest_engine.py is executed in the same folder as trading_bot_engine.py.")
    sys.exit(1)

class BacktestEngine:
    def __init__(self,
                 symbol: str = "BTC/USDT",
                 timeframe: str = "15m",
                 initial_capital: float = 10000.0,
                 limit: int = 1000,
                 force_fetch: bool = False):
        
        self.raw_symbol = symbol
        # Normalize symbol for CCXT (e.g. BTC/USDT) and SQLite (BTCUSDT)
        if "/" in symbol:
            self.symbol_ccxt = symbol
            self.symbol_db = symbol.replace("/", "")
        else:
            self.symbol_ccxt = f"{symbol[:3]}/{symbol[3:]}" if len(symbol) >= 6 else symbol
            self.symbol_db = symbol
            
        self.timeframe = timeframe
        self.initial_capital = initial_capital
        self.limit = limit
        self.force_fetch = force_fetch
        
        # Instantiate the real bot engine to use its parameters and logic
        self.bot = TradingBotEngine()
        
        # Override target symbol for testing
        self.bot.symbol = self.symbol_ccxt
        self.bot.timeframe = timeframe
        
    def ensure_historical_data(self) -> int:
        """
        Validates SQLite historical candles for backtesting. 
        Fetches fresh data via CCXT or synthetic generation if missing or forced.
        """
        db = SessionLocal()
        try:
            existing_count = db.query(HistoricalPrice).filter(
                HistoricalPrice.pair == self.symbol_db
            ).count()
            
            if existing_count < 250 or self.force_fetch:
                print(f"[*] SQLite has {existing_count} candles for {self.symbol_db}. Fetching fresh history...")
                candles = None
                
                # Try fetching via bot's exchange connection
                if self.bot.exchange:
                    try:
                        print(f"[*] Fetching historical OHLCV data from exchange via CCXT...")
                        candles = self.bot.exchange.fetch_ohlcv(
                            self.symbol_ccxt, 
                            timeframe=self.timeframe, 
                            limit=self.limit
                        )
                        print(f"[+] Successfully fetched {len(candles)} candles from exchange.")
                    except Exception as e:
                        print(f"[!] Exchange historical fetch failed: {e}. Generating high-fidelity mock data.")
                
                if not candles:
                    # Fallback: high-fidelity synthetic price walking
                    print("[*] Generating premium synthetic prices...")
                    import random
                    base_price = 65000.0 if "BTC" in self.symbol_db else (3300.0 if "ETH" in self.symbol_db else 150.0)
                    now_ts = int(datetime.utcnow().timestamp() * 1000)
                    interval_ms = 15 * 60 * 1000 # Default 15m
                    
                    if self.timeframe == "1h":
                        interval_ms = 60 * 60 * 1000
                    elif self.timeframe == "4h":
                        interval_ms = 4 * 60 * 60 * 1000
                    elif self.timeframe == "1d":
                        interval_ms = 24 * 60 * 60 * 1000
                        
                    candles = []
                    current_price = base_price
                    for i in range(self.limit):
                        ts = now_ts - (self.limit - i) * interval_ms
                        # Simulating daily trend wave with some randomness
                        trend_factor = 0.0001 * np.sin(i / 50.0)
                        change_pct = random.uniform(-0.015, 0.016) + trend_factor
                        open_p = current_price
                        close_p = current_price * (1.0 + change_pct)
                        high_p = max(open_p, close_p) * (1.0 + random.uniform(0.0, 0.005))
                        low_p = min(open_p, close_p) * (1.0 - random.uniform(0.0, 0.005))
                        volume_val = random.uniform(20.0, 500.0)
                        candles.append([ts, open_p, high_p, low_p, close_p, volume_val])
                        current_price = close_p

                # Clear old data and persist to SQLite
                db.query(HistoricalPrice).filter(HistoricalPrice.pair == self.symbol_db).delete()
                db_candles = []
                for c in candles:
                    ts_dt = datetime.utcfromtimestamp(c[0] / 1000.0)
                    db_candles.append(HistoricalPrice(
                        timestamp=ts_dt,
                        pair=self.symbol_db,
                        open=float(c[1]),
                        high=float(c[2]),
                        low=float(c[3]),
                        close=float(c[4]),
                        volume=float(c[5])
                    ))
                db.add_all(db_candles)
                db.commit()
                print(f"[+] SQLite database successfully synchronized. {len(db_candles)} records saved.")
                return len(db_candles)
            else:
                print(f"[+] Loaded {existing_count} existing candles from SQLite.")
                return existing_count
        except Exception as e:
            print(f"[!] Error ensuring historical data: {e}")
            db.rollback()
            return 0
        finally:
            db.close()

    def run_backtest(self) -> Dict[str, Any]:
        """
        Executes a professional chronological backtest of the bot's current trading logic.
        """
        db = SessionLocal()
        try:
            # Load from SQLite database
            db_rows = db.query(HistoricalPrice).filter(
                HistoricalPrice.pair == self.symbol_db
            ).order_by(HistoricalPrice.timestamp.asc()).all()
            
            if not db_rows:
                return {"error": "No historical price records found in SQLite database."}
                
            # Build DataFrame
            df_data = {
                'timestamp': [row.timestamp for row in db_rows],
                'open': [row.open for row in db_rows],
                'high': [row.high for row in db_rows],
                'low': [row.low for row in db_rows],
                'close': [row.close for row in db_rows],
                'volume': [row.volume for row in db_rows]
            }
            df = pd.DataFrame(df_data)
            
            if len(df) < 205:
                return {"error": f"Backtest requires at least 205 candles to compute indicators. Current length: {len(df)}."}
                
            print(f"[*] Commencing Backtest simulation across {len(df)} historical candles...")
            
            # Simulation State
            balance = self.initial_capital
            position = 0.0
            entry_price = 0.0
            stop_loss = 0.0
            take_profit = 0.0
            
            trades = []
            equity_history = []
            
            # Start loop from index 201 so first df.iloc[:i+1] has 202 candles (enough for SMA 200 & indicators)
            start_idx = 201
            
            for i in range(start_idx, len(df)):
                sub_df = df.iloc[:i+1] # No lookahead bias!
                current_row = df.iloc[i]
                current_price = current_row['close']
                current_high = current_row['high']
                current_low = current_row['low']
                current_open = current_row['open']
                timestamp = current_row['timestamp']
                
                # 1. Compute indicators dynamically matching the bot's configuration
                sub_df_indicators = self.bot.calculate_indicators(sub_df)
                
                # 2. Compute Signals
                action, metrics = self.bot.calculate_signals(sub_df_indicators)
                
                # Track Current Equity value
                current_equity = balance + (position * current_price)
                equity_history.append(current_equity)
                
                # 3. Position and Risk Evaluation
                if position > 0.0:
                    # Check Stop-Loss Breach using Low price of candle
                    if current_low <= stop_loss:
                        sell_price = min(stop_loss, current_open)
                        pnl = (sell_price - entry_price) * position
                        pnl_pct = ((sell_price - entry_price) / entry_price) * 100.0
                        balance = position * sell_price
                        
                        trades.append({
                            "type": "STOP_LOSS",
                            "entry_price": entry_price,
                            "exit_price": sell_price,
                            "amount": position,
                            "timestamp": timestamp,
                            "pnl": pnl,
                            "pnl_pct": pnl_pct,
                            "reason": "Stop Loss triggered"
                        })
                        position = 0.0
                        entry_price = 0.0
                        
                    # Check Take-Profit target achieved using High price of candle
                    elif current_high >= take_profit:
                        sell_price = max(take_profit, current_open)
                        pnl = (sell_price - entry_price) * position
                        pnl_pct = ((sell_price - entry_price) / entry_price) * 100.0
                        balance = position * sell_price
                        
                        trades.append({
                            "type": "TAKE_PROFIT",
                            "entry_price": entry_price,
                            "exit_price": sell_price,
                            "amount": position,
                            "timestamp": timestamp,
                            "pnl": pnl,
                            "pnl_pct": pnl_pct,
                            "reason": "Take Profit achieved"
                        })
                        position = 0.0
                        entry_price = 0.0
                        
                    # Check strategy liquidation signal
                    elif action == "SELL":
                        sell_price = current_price
                        pnl = (sell_price - entry_price) * position
                        pnl_pct = ((sell_price - entry_price) / entry_price) * 100.0
                        balance = position * sell_price
                        
                        trades.append({
                            "type": "SELL",
                            "entry_price": entry_price,
                            "exit_price": sell_price,
                            "amount": position,
                            "timestamp": timestamp,
                            "pnl": pnl,
                            "pnl_pct": pnl_pct,
                            "reason": "Proactive sell signal"
                        })
                        position = 0.0
                        entry_price = 0.0
                        
                else:
                    # Check entry signal
                    if action == "BUY":
                        # Compute SL and TP thresholds exactly as the bot
                        stop_loss = current_price * (1 - self.bot.stop_loss_pct)
                        take_profit = current_price * (1 + self.bot.take_profit_pct)
                        
                        # Apply risk sizing matching the bot's formulas
                        risk_usd = balance * self.bot.risk_per_trade_pct
                        risk_per_asset = abs(current_price - stop_loss)
                        
                        if risk_per_asset > 0:
                            target_size = risk_usd / risk_per_asset
                        else:
                            target_size = balance / current_price
                            
                        # Ensure it doesn't exceed total balance
                        max_possible_size = balance / current_price
                        if target_size > max_possible_size:
                            target_size = max_possible_size
                            
                        if target_size > 0:
                            position = target_size
                            entry_price = current_price
                            balance -= (position * current_price)
                            
                            trades.append({
                                "type": "BUY",
                                "entry_price": current_price,
                                "exit_price": 0.0,
                                "amount": target_size,
                                "timestamp": timestamp,
                                "pnl": 0.0,
                                "pnl_pct": 0.0,
                                "reason": "Golden cross & RSI entry"
                            })
            
            # Force close any open position at the end of backtest to capture full portfolio evaluation
            if position > 0.0:
                final_row = df.iloc[-1]
                sell_price = final_row['close']
                pnl = (sell_price - entry_price) * position
                pnl_pct = ((sell_price - entry_price) / entry_price) * 100.0
                balance += position * sell_price
                
                trades.append({
                    "type": "SELL_FORCE",
                    "entry_price": entry_price,
                    "exit_price": sell_price,
                    "amount": position,
                    "timestamp": final_row['timestamp'],
                    "pnl": pnl,
                    "pnl_pct": pnl_pct,
                    "reason": "Force End-of-Backtest Liquidation"
                })
                
                equity_history.append(balance)
                position = 0.0
                
            # Compile Performance Metrics
            sell_trades = [t for t in trades if t["type"] in ("SELL", "SELL_FORCE", "STOP_LOSS", "TAKE_PROFIT")]
            total_trades = len(sell_trades)
            wins = sum(1 for t in sell_trades if t["pnl"] > 0)
            losses = total_trades - wins
            win_rate = (wins / total_trades) * 100.0 if total_trades > 0 else 0.0
            
            total_net_profit = balance - self.initial_capital
            total_net_profit_pct = (total_net_profit / self.initial_capital) * 100.0
            
            # Calculate Profit Factor
            gross_profits = sum(t["pnl"] for t in sell_trades if t["pnl"] > 0)
            gross_losses = sum(abs(t["pnl"]) for t in sell_trades if t["pnl"] < 0)
            profit_factor = gross_profits / gross_losses if gross_losses > 0 else (float('inf') if gross_profits > 0 else 1.0)
            
            # Calculate Max Drawdown from the dynamic equity curve
            max_drawdown = 0.0
            peak = self.initial_capital
            for eq in equity_history:
                if eq > peak:
                    peak = eq
                drawdown = ((peak - eq) / peak) * 100.0 if peak > 0 else 0.0
                if drawdown > max_drawdown:
                    max_drawdown = drawdown
                    
            return {
                "symbol": self.symbol_ccxt,
                "timeframe": self.timeframe,
                "initial_capital": self.initial_capital,
                "final_capital": balance,
                "net_profit": total_net_profit,
                "net_profit_pct": total_net_profit_pct,
                "total_trades": total_trades,
                "wins": wins,
                "losses": losses,
                "win_rate": win_rate,
                "max_drawdown": max_drawdown,
                "profit_factor": profit_factor,
                "trades_list": sell_trades,
                "all_events": trades,
                "sentiment_score": self.bot.cached_sentiment_score,
                "sentiment_justification": self.bot.cached_sentiment_justification,
                "buy_rsi_threshold": 30.0 + (self.bot.cached_sentiment_score * 10.0),
                "sell_rsi_threshold": 70.0 + (self.bot.cached_sentiment_score * 10.0)
            }
            
        except Exception as e:
            import traceback
            traceback.print_exc()
            return {"error": f"Exception occurred during backtest loop: {e}"}
        finally:
            db.close()

    def print_report(self, report: Dict[str, Any]):
        """
        Prints a gorgeous console ASCII report of the backtest.
        """
        if "error" in report:
            print(f"\n[!] Backtest Error: {report['error']}\n")
            return
            
        print("\n" + "="*58)
        print("         CryptoBot AI - Technical Backtest Report        ")
        print("="*58)
        print(f" Target Symbol:     {report['symbol']:<25}")
        print(f" Timeframe:         {report['timeframe']:<25}")
        print(f" Backtest Start:    {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        if "sentiment_score" in report:
            print(f" AI Market Sentiment: {report['sentiment_score']:+.2f} ({report['sentiment_justification']})")
            print(f" RSI Buy/Sell Bounds: < {report['buy_rsi_threshold']:.1f} / > {report['sell_rsi_threshold']:.1f}")
        print("-"*58)
        print(f" Starting Balance:  ${report['initial_capital']:,.2f} USDT")
        print(f" Ending Balance:    ${report['final_capital']:,.2f} USDT")
        
        profit_color = "+" if report['net_profit'] >= 0 else "-"
        print(f" Total Net Profit:  {profit_color}${abs(report['net_profit']):,.2f} USDT ({report['net_profit_pct']:+.2f}%)")
        print("-"*58)
        print(f" Completed Cycles:  {report['total_trades']:<10}")
        print(f" Winning Trades:    {report['wins']:<10} ({report['win_rate']:.2f}% Win Rate)")
        print(f" Losing Trades:     {report['losses']:<10}")
        
        pf_str = f"{report['profit_factor']:.2f}" if report['profit_factor'] != float('inf') else "∞"
        print(f" Profit Factor:     {pf_str:<10}")
        print(f" Max Drawdown:      {report['max_drawdown']:.2f}%")
        print("="*58)
        
        trades_list = report.get("trades_list", [])
        if trades_list:
            print("\nCompleted Trades Log:")
            print("-"*102)
            print(f" {'Timestamp':<19} | {'Exit Type':<11} | {'Entry Px':<10} | {'Exit Px':<10} | {'Amount':<9} | {'PnL (USDT)':<12} | {'Pct':<7}")
            print("-"*102)
            for t in trades_list:
                ts_str = t["timestamp"].strftime('%Y-%m-%d %H:%M:%S') if isinstance(t["timestamp"], datetime) else str(t["timestamp"])
                pnl_sign = "+" if t["pnl"] >= 0 else "-"
                print(f" {ts_str:<19} | {t['type']:<11} | ${t['entry_price']:<9,.2f} | ${t['exit_price']:<9,.2f} | {t['amount']:<9.4f} | {pnl_sign}${abs(t['pnl']):<10,.2f} | {t['pnl_pct']:+.2f}%")
            print("-"*102 + "\n")
        else:
            print("\n[*] No completed trade cycles recorded during the simulation period.\n")


def main():
    parser = argparse.ArgumentParser(description="CryptoBot AI Backtesting Tool")
    parser.add_argument("--symbol", type=str, default="BTC/USDT", help="Trading Pair (default: BTC/USDT)")
    parser.add_argument("--timeframe", type=str, default="15m", help="Candle timeframe e.g. 15m, 1h, 4h, 1d (default: 15m)")
    parser.add_argument("--capital", type=float, default=10000.0, help="Initial mock USDT capital (default: 10000.0)")
    parser.add_argument("--limit", type=int, default=1000, help="Number of candles to test (default: 1000)")
    parser.add_argument("--force-fetch", action="store_true", help="Force refresh SQLite candles from CCXT")
    
    args = parser.parse_args()
    
    # Initialize Engine
    engine_inst = BacktestEngine(
        symbol=args.symbol,
        timeframe=args.timeframe,
        initial_capital=args.capital,
        limit=args.limit,
        force_fetch=args.force_fetch
    )
    
    # Sync SQLite candle cache
    engine_inst.ensure_historical_data()
    
    # Execute chronological backtest
    results = engine_inst.run_backtest()
    
    # Print report
    engine_inst.print_report(results)

if __name__ == "__main__":
    main()
