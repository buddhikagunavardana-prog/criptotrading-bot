"""
Backtester Script for VWAP + ATR AI Strategy
Runs the VWAPATRAIEngine strategy against historical OHLCV data fetched via CCXT
to calculate potential PnL, Win Rate, and Risk Metrics without live trading.
"""

import os
import sys
import argparse
import logging
import random
import math
from datetime import datetime, timedelta
from typing import Dict, Any, List, Optional

# Ensure project root is in python path
current_dir = os.path.dirname(os.path.abspath(__file__))
if current_dir not in sys.path:
    sys.path.insert(0, current_dir)

# Handle numpy import with minimal fallback
try:
    import numpy as np
except ImportError:
    class MockNumPyRandom:
        @staticmethod
        def seed(s):
            random.seed(s)
        @staticmethod
        def normal(loc=0.0, scale=1.0, size=1):
            if isinstance(size, int):
                return [random.normalvariate(loc, scale) for _ in range(size)]
            return random.normalvariate(loc, scale)
        @staticmethod
        def uniform(low=0.0, high=1.0, size=1):
            if isinstance(size, int):
                return [random.uniform(low, high) for _ in range(size)]
            return random.uniform(low, high)

    class MockNumPy:
        random = MockNumPyRandom()
        @staticmethod
        def exp(x):
            if isinstance(x, list):
                return [math.exp(v) for v in x]
            return math.exp(x)
        @staticmethod
        def cumsum(x):
            res = []
            acc = 0.0
            for v in x:
                acc += v
                res.append(acc)
            return res
        @staticmethod
        def abs(x):
            if isinstance(x, list):
                return [abs(v) for v in x]
            return abs(x)
        nan = float('nan')
    np = MockNumPy()

# Handle pandas import with minimal fallback
try:
    import pandas as pd
except ImportError:
    class MockSeries(list):
        def __add__(self, other):
            if isinstance(other, (int, float)):
                return MockSeries([x + other for x in self])
            return MockSeries([a + b for a, b in zip(self, other)])
        def __radd__(self, other):
            return self.__add__(other)
        def __sub__(self, other):
            if isinstance(other, (int, float)):
                return MockSeries([x - other for x in self])
            return MockSeries([a - b for a, b in zip(self, other)])
        def __rsub__(self, other):
            if isinstance(other, (int, float)):
                return MockSeries([other - x for x in self])
            return MockSeries([b - a for a, b in zip(self, other)])
        def __mul__(self, other):
            if isinstance(other, (int, float)):
                return MockSeries([x * other for x in self])
            return MockSeries([a * b for a, b in zip(self, other)])
        def __rmul__(self, other):
            return self.__mul__(other)
        def __truediv__(self, other):
            if isinstance(other, (int, float)):
                return MockSeries([x / other for x in self])
            return MockSeries([a / b if b != 0 else float('nan') for a, b in zip(self, other)])
        def __rtruediv__(self, other):
            if isinstance(other, (int, float)):
                return MockSeries([other / x if x != 0 else float('nan') for x in self])
            return MockSeries([b / a if a != 0 else float('nan') for a, b in zip(self, other)])
        def shift(self, periods=1):
            res = [float('nan')] * len(self)
            for i in range(periods, len(self)):
                res[i] = self[i - periods]
            return MockSeries(res)
        def abs(self):
            return MockSeries([abs(v) if v == v else float('nan') for v in self])
        def cumsum(self):
            acc = 0.0
            res = []
            for v in self:
                acc += v
                res.append(acc)
            return MockSeries(res)
        def replace(self, to_replace, value):
            return MockSeries([value if v == to_replace else v for v in self])
        def ffill(self):
            last = 0.0
            res = []
            for v in self:
                if v == v and not math.isnan(v):
                    last = v
                res.append(last)
            return MockSeries(res)
        def bfill(self):
            last = 0.0
            res = list(self)
            for i in range(len(res)-1, -1, -1):
                if res[i] == res[i] and not math.isnan(res[i]):
                    last = res[i]
                else:
                    res[i] = last
            return MockSeries(res)
        def max(self, axis=0):
            return max(self)
        def ewm(self, alpha=1.0/14, min_periods=14, adjust=False):
            class EWM:
                def __init__(self, data, a):
                    self.data = data
                    self.a = a
                def mean(self):
                    res = []
                    val = 0.0
                    for idx, x in enumerate(self.data):
                        if idx == 0 or math.isnan(x):
                            val = x if not math.isnan(x) else 0.0
                        else:
                            val = self.a * x + (1 - self.a) * val
                        res.append(val)
                    return MockSeries(res)
            return EWM(self, alpha)

    class MockDataFrame:
        def __init__(self, data=None, columns=None):
            if isinstance(data, dict):
                self.data = data
                self.columns = list(data.keys())
                first_key = list(data.keys())[0] if data else None
                self._len = len(data[first_key]) if first_key else 0
            elif isinstance(data, list):
                self.data = {}
                cols = columns or ["timestamp", "open", "high", "low", "close", "volume"]
                for idx, col in enumerate(cols):
                    self.data[col] = [row[idx] for row in data]
                self.columns = cols
                self._len = len(data)
            else:
                self.data = {}
                self.columns = []
                self._len = 0

        @property
        def empty(self):
            return self._len == 0

        def __len__(self):
            return self._len

        def __getitem__(self, item):
            if isinstance(item, str):
                return MockSeries(self.data.get(item, []))
            return self

        def __setitem__(self, key, value):
            self.data[key] = list(value)
            if key not in self.columns:
                self.columns.append(key)

        @property
        def iloc(self):
            df_self = self
            class IlocIndexer:
                def __getitem__(self, idx):
                    class Row:
                        def __init__(self, df, i):
                            self.d = {col: df.data[col][i] for col in df.columns}
                        def __getitem__(self, item):
                            return self.d[item]
                    if isinstance(idx, int):
                        if idx < 0:
                            idx = len(df_self) + idx
                        return Row(df_self, idx)
                    return df_self
            return IlocIndexer()

        def copy(self):
            new_data = {k: list(v) for k, v in self.data.items()}
            return MockDataFrame(data=new_data)

    class MockPandas:
        DataFrame = MockDataFrame
        Series = MockSeries
        @staticmethod
        def concat(objs, axis=1):
            class Concatenated:
                def __init__(self, series_list):
                    self.series_list = series_list
                def max(self, axis=1):
                    length = len(self.series_list[0]) if self.series_list else 0
                    res = []
                    for i in range(length):
                        row_vals = [s[i] for s in self.series_list if i < len(s) and not math.isnan(s[i])]
                        res.append(max(row_vals) if row_vals else 0.0)
                    return MockSeries(res)
            return Concatenated(objs)
        @staticmethod
        def to_datetime(arg, unit="ms"):
            if isinstance(arg, list):
                return [datetime.fromtimestamp(ts/1000.0) if isinstance(ts, (int, float)) else ts for ts in arg]
            return arg
        @staticmethod
        def date_range(end=None, periods=100, freq="15min"):
            end_dt = end or datetime.now()
            return [end_dt - timedelta(minutes=15 * (periods - i)) for i in range(periods)]
        class Timestamp:
            def __init__(self, arg=None):
                pass
            @staticmethod
            def now():
                return datetime.now()

    pd = MockPandas()

# Mock CCXT if not present in path
try:
    import ccxt
except ImportError:
    from types import ModuleType
    mock_ccxt = ModuleType("ccxt")
    sys.modules["ccxt"] = mock_ccxt

from ai_trading_bot_backend.services.indicator_service import IndicatorService
from ai_trading_bot_backend.services.ai_scoring_engine import AIScoringEngine
from ai_trading_bot_backend.services.risk_manager import RiskManagerService
from ai_trading_bot_backend.exchange_handlers.ccxt_handler import CCXTHandler

# Configure logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("VWAPATRBacktester")


class VWAPATRBacktester:
    """
    Backtester for the VWAP + ATR AI Strategy.
    Simulates position entries, exit targets (SL/TP), and PnL metrics on historical candles.
    """

    def __init__(
        self,
        symbol: str = "BTC/USDT",
        timeframe: str = "15m",
        limit: int = 300,
        initial_balance: float = 10000.0,
        trade_allocation_pct: float = 10.0,
        leverage: int = 10,
        atr_multiplier: float = 2.0,
        min_ai_score: float = 30.0
    ):
        self.symbol = symbol
        self.timeframe = timeframe
        self.limit = limit
        self.initial_balance = initial_balance
        self.trade_allocation_pct = trade_allocation_pct
        self.leverage = leverage
        self.atr_multiplier = atr_multiplier
        self.min_ai_score = min_ai_score

        self.ccxt_handler = CCXTHandler()
        self.ai_scoring_engine = AIScoringEngine(strong_buy_threshold=min_ai_score)
        self.risk_manager = RiskManagerService()

    def fetch_historical_ohlcv(self) -> pd.DataFrame:
        """
        Fetches historical OHLCV data via CCXT.
        Falls back to generating realistic synthetic data if live exchange calls fail.
        """
        formatted_symbol = self.ccxt_handler.format_symbol(self.symbol)
        df = pd.DataFrame()

        try:
            if hasattr(self.ccxt_handler, "exchange") and self.ccxt_handler.exchange is not None:
                logger.info(f"Fetching {self.limit} historical '{self.timeframe}' candles for {formatted_symbol} via CCXT...")
                ohlcv = self.ccxt_handler.exchange.fetch_ohlcv(
                    symbol=formatted_symbol,
                    timeframe=self.timeframe,
                    limit=self.limit
                )
                if ohlcv and len(ohlcv) > 0:
                    df = pd.DataFrame(ohlcv, columns=["timestamp", "open", "high", "low", "close", "volume"])
                    df["timestamp"] = pd.to_datetime(df["timestamp"], unit="ms")
                    logger.info(f"Successfully loaded {len(df)} candles from CCXT.")
        except Exception as e:
            logger.warning(f"Failed to fetch live OHLCV from CCXT: {e}. Generating synthetic historical data.")

        if df.empty:
            df = self._generate_synthetic_ohlcv(num_bars=self.limit)

        return df

    def _generate_synthetic_ohlcv(self, num_bars: int = 300, start_price: float = 65000.0) -> pd.DataFrame:
        """
        Generates realistic synthetic OHLCV data with trend regimes and volatility.
        """
        logger.info(f"Generating synthetic {num_bars} bars of historical OHLCV data...")
        random.seed(42)

        price_series = []
        current_p = start_price
        for _ in range(num_bars):
            ret = random.normalvariate(0.0002, 0.003)
            current_p = current_p * math.exp(ret)
            price_series.append(current_p)

        highs = [p * (1 + abs(random.normalvariate(0.001, 0.002))) for p in price_series]
        lows = [p * (1 - abs(random.normalvariate(0.001, 0.002))) for p in price_series]
        opens = [p * (1 + random.normalvariate(0, 0.001)) for p in price_series]
        closes = list(price_series)
        volumes = [random.uniform(100, 1500) for _ in range(num_bars)]

        dates = pd.date_range(end=pd.Timestamp.now(), periods=num_bars, freq=self.timeframe)
        return pd.DataFrame({
            "timestamp": dates,
            "open": opens,
            "high": highs,
            "low": lows,
            "close": closes,
            "volume": volumes
        })

    def run_backtest(self) -> Dict[str, Any]:
        """
        Executes historical backtest simulation over OHLCV bars.

        Returns:
            Dict[str, Any]: Comprehensive performance metrics and trade logs.
        """
        df = self.fetch_historical_ohlcv()
        if df.empty or len(df) < 20:
            logger.error("Insufficient historical data for backtesting.")
            return {}

        # 1. Add Indicators (VWAP, ATR)
        df = IndicatorService.add_all_indicators(df, atr_period=14)

        current_balance = self.initial_balance
        peak_balance = self.initial_balance
        max_drawdown_usd = 0.0
        max_drawdown_pct = 0.0

        active_position = None
        trade_log = []

        # Warm up period (skip first 20 bars for stable VWAP/ATR)
        warmup_bars = 20

        for i in range(warmup_bars, len(df)):
            row = df.iloc[i]
            timestamp = row["timestamp"]
            open_p = float(row["open"])
            high_p = float(row["high"])
            low_p = float(row["low"])
            close_p = float(row["close"])
            vwap_v = float(row["vwap"])
            atr_v = float(row["atr"])

            # 2. Check if active position hits SL or TP during current bar
            if active_position is not None:
                side = active_position["side"]
                entry_p = active_position["entry_price"]
                sl_p = active_position["stop_loss"]
                tp_p = active_position["take_profit"]
                margin_usd = active_position["margin_usd"]
                qty = active_position["quantity"]

                exit_triggered = False
                exit_price = close_p
                exit_reason = "END_OF_DATA"

                if side == "BUY":
                    if low_p <= sl_p and high_p >= tp_p:
                        # Both hit in same bar -> conservative assumption: SL hit first
                        exit_triggered = True
                        exit_price = sl_p
                        exit_reason = "STOP_LOSS"
                    elif low_p <= sl_p:
                        exit_triggered = True
                        exit_price = sl_p
                        exit_reason = "STOP_LOSS"
                    elif high_p >= tp_p:
                        exit_triggered = True
                        exit_price = tp_p
                        exit_reason = "TAKE_PROFIT"
                elif side == "SELL":
                    if high_p >= sl_p and low_p <= tp_p:
                        exit_triggered = True
                        exit_price = sl_p
                        exit_reason = "STOP_LOSS"
                    elif high_p >= sl_p:
                        exit_triggered = True
                        exit_price = sl_p
                        exit_reason = "STOP_LOSS"
                    elif low_p <= tp_p:
                        exit_triggered = True
                        exit_price = tp_p
                        exit_reason = "TAKE_PROFIT"

                # If last bar, force close position for simulation accounting
                if not exit_triggered and i == len(df) - 1:
                    exit_triggered = True
                    exit_price = close_p
                    exit_reason = "END_OF_DATA"

                if exit_triggered:
                    if side == "BUY":
                        pnl_usd = (exit_price - entry_p) * qty
                    else:
                        pnl_usd = (entry_p - exit_price) * qty

                    pnl_pct = (pnl_usd / margin_usd) * 100.0
                    current_balance += pnl_usd

                    # Track drawdowns
                    if current_balance > peak_balance:
                        peak_balance = current_balance
                    dd_usd = peak_balance - current_balance
                    dd_pct = (dd_usd / peak_balance) * 100.0 if peak_balance > 0 else 0.0

                    if dd_usd > max_drawdown_usd:
                        max_drawdown_usd = dd_usd
                    if dd_pct > max_drawdown_pct:
                        max_drawdown_pct = dd_pct

                    trade_log.append({
                        "trade_num": len(trade_log) + 1,
                        "entry_time": str(active_position["entry_time"]),
                        "exit_time": str(timestamp),
                        "side": side,
                        "entry_price": entry_p,
                        "exit_price": exit_price,
                        "stop_loss": sl_p,
                        "take_profit": tp_p,
                        "quantity": qty,
                        "margin_usd": margin_usd,
                        "pnl_usd": pnl_usd,
                        "pnl_pct": pnl_pct,
                        "exit_reason": exit_reason,
                        "ai_score": active_position["ai_score"],
                        "balance_after": current_balance
                    })

                    active_position = None

            # 3. Evaluate new trade entry if position is closed
            if active_position is None and i < len(df) - 1:
                trend_dir = "BULLISH" if close_p > vwap_v else ("BEARISH" if close_p < vwap_v else "NEUTRAL")

                # Check BUY setup
                buy_eval = self.ai_scoring_engine.evaluate_setup(
                    vwap=vwap_v,
                    current_price=close_p,
                    atr=atr_v,
                    trend_direction=trend_dir,
                    side="BUY"
                )

                # Check SELL setup
                sell_eval = self.ai_scoring_engine.evaluate_setup(
                    vwap=vwap_v,
                    current_price=close_p,
                    atr=atr_v,
                    trend_direction=trend_dir,
                    side="SELL"
                )

                target_side = None
                ai_score = 0.0

                if buy_eval["confidence_score"] >= self.min_ai_score and buy_eval["confidence_score"] >= sell_eval["confidence_score"]:
                    target_side = "BUY"
                    ai_score = buy_eval["confidence_score"]
                elif sell_eval["confidence_score"] >= self.min_ai_score:
                    target_side = "SELL"
                    ai_score = sell_eval["confidence_score"]

                if target_side is not None:
                    margin_usd = current_balance * (self.trade_allocation_pct / 100.0)
                    position_value = margin_usd * self.leverage
                    quantity = position_value / close_p

                    risk_levels = self.risk_manager.calculate_atr_risk_levels(
                        entry_price=close_p,
                        side=target_side,
                        atr=atr_v,
                        atr_multiplier=self.atr_multiplier
                    )

                    active_position = {
                        "entry_time": timestamp,
                        "side": target_side,
                        "entry_price": close_p,
                        "stop_loss": risk_levels["stop_loss"],
                        "take_profit": risk_levels["take_profit"],
                        "quantity": quantity,
                        "margin_usd": margin_usd,
                        "ai_score": ai_score
                    }

        # Calculate final summary metrics
        total_trades = len(trade_log)
        winning_trades = [t for t in trade_log if t["pnl_usd"] > 0]
        losing_trades = [t for t in trade_log if t["pnl_usd"] < 0]

        win_count = len(winning_trades)
        loss_count = len(losing_trades)
        win_rate = (win_count / total_trades * 100.0) if total_trades > 0 else 0.0

        gross_profit = sum(t["pnl_usd"] for t in winning_trades)
        gross_loss = abs(sum(t["pnl_usd"] for t in losing_trades))
        profit_factor = (gross_profit / gross_loss) if gross_loss > 0 else (float("inf") if gross_profit > 0 else 0.0)

        total_net_pnl = current_balance - self.initial_balance
        total_return_pct = (total_net_pnl / self.initial_balance) * 100.0

        report = {
            "symbol": self.symbol,
            "timeframe": self.timeframe,
            "bars_tested": len(df),
            "initial_balance": self.initial_balance,
            "final_balance": current_balance,
            "total_net_pnl_usd": total_net_pnl,
            "total_return_pct": total_return_pct,
            "total_trades": total_trades,
            "winning_trades": win_count,
            "losing_trades": loss_count,
            "win_rate_pct": win_rate,
            "gross_profit_usd": gross_profit,
            "gross_loss_usd": gross_loss,
            "profit_factor": profit_factor,
            "max_drawdown_usd": max_drawdown_usd,
            "max_drawdown_pct": max_drawdown_pct,
            "trade_log": trade_log
        }

        self.print_summary_report(report)
        return report

    def print_summary_report(self, report: Dict[str, Any]):
        """
        Prints a formatted performance report to the terminal.
        """
        print("\n" + "=" * 80)
        print(" 📈 VWAP + ATR AI STRATEGY HISTORICAL BACKTEST REPORT")
        print("=" * 80)
        print(f" Symbol             : {report['symbol']} ({report['timeframe']} candles)")
        print(f" Bars Processed     : {report['bars_tested']} bars")
        print(f" Initial Capital    : ${report['initial_balance']:,.2f} USDT")
        print(f" Final Capital      : ${report['final_balance']:,.2f} USDT")
        print(f" Net Realized PnL   : ${report['total_net_pnl_usd']:+,.2f} USDT ({report['total_return_pct']:+.2f}%)")
        print("-" * 80)
        print(f" Total Trades       : {report['total_trades']}")
        print(f" Win / Loss Count   : {report['winning_trades']} Wins | {report['losing_trades']} Losses")
        print(f" Win Rate           : {report['win_rate_pct']:.2f}%")
        print(f" Gross Profit       : ${report['gross_profit_usd']:,.2f}")
        print(f" Gross Loss         : ${report['gross_loss_usd']:,.2f}")
        pf_str = f"{report['profit_factor']:.2f}" if report['profit_factor'] != float("inf") else "∞ (No Losses)"
        print(f" Profit Factor      : {pf_str}")
        print(f" Max Drawdown       : ${report['max_drawdown_usd']:,.2f} ({report['max_drawdown_pct']:.2f}%)")
        print("=" * 80)

        trade_log = report.get("trade_log", [])
        if trade_log:
            print("\n📋 DETAILED TRADE LOG:")
            print(f"{'#':<3} | {'Side':<4} | {'Entry Price':<11} | {'Exit Price':<11} | {'SL Price':<11} | {'TP Price':<11} | {'PnL ($)':<10} | {'PnL (%)':<8} | {'Exit Reason':<11}")
            print("-" * 105)
            for t in trade_log:
                pnl_color = "+" if t["pnl_usd"] >= 0 else ""
                print(
                    f"{t['trade_num']:<3} | "
                    f"{t['side']:<4} | "
                    f"${t['entry_price']:<10.2f} | "
                    f"${t['exit_price']:<10.2f} | "
                    f"${t['stop_loss']:<10.2f} | "
                    f"${t['take_profit']:<10.2f} | "
                    f"{pnl_color}${t['pnl_usd']:<9.2f} | "
                    f"{pnl_color}{t['pnl_pct']:<7.2f}% | "
                    f"{t['exit_reason']:<11}"
                )
            print("-" * 105 + "\n")
        else:
            print("\n No trades were triggered during the backtest period (AI score threshold not met).\n")


def main():
    parser = argparse.ArgumentParser(description="VWAP + ATR AI Strategy Backtester")
    parser.add_argument("--symbol", type=str, default="BTC/USDT", help="Trading symbol (e.g. BTC/USDT)")
    parser.add_argument("--timeframe", type=str, default="15m", help="Candle timeframe (e.g. 15m, 1h)")
    parser.add_argument("--limit", type=int, default=300, help="Number of historical candles to backtest")
    parser.add_argument("--balance", type=float, default=10000.0, help="Initial account balance in USDT")
    parser.add_argument("--leverage", type=int, default=10, help="Leverage multiplier")
    parser.add_argument("--atr-multiplier", type=float, default=2.0, help="ATR multiplier for Stop Loss / Take Profit")
    parser.add_argument("--min-score", type=float, default=30.0, help="Minimum AI conviction score to open trade")

    args = parser.parse_args()

    backtester = VWAPATRBacktester(
        symbol=args.symbol,
        timeframe=args.timeframe,
        limit=args.limit,
        initial_balance=args.balance,
        leverage=args.leverage,
        atr_multiplier=args.atr_multiplier,
        min_ai_score=args.min_score
    )

    backtester.run_backtest()


if __name__ == "__main__":
    main()
