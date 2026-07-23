"""
Indicator Service for calculating technical indicators such as VWAP and ATR
using Pandas and NumPy from OHLCV DataFrames.
"""

import logging
import math
import random

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

    pd = MockPandas()

try:
    import numpy as np
except ImportError:
    class MockNumPy:
        nan = float('nan')
    np = MockNumPy()

logger = logging.getLogger(__name__)


class IndicatorService:
    """
    Provides technical indicator calculations for crypto futures trading strategies.
    """

    @staticmethod
    def calculate_vwap(df: pd.DataFrame) -> pd.Series:
        """
        Calculates Volume Weighted Average Price (VWAP).
        Formula: Cumulative(Typical Price * Volume) / Cumulative(Volume)
        Typical Price = (High + Low + Close) / 3
        """
        if df.empty or not all(col in df.columns for col in ['high', 'low', 'close', 'volume']):
            logger.error("DataFrame missing required OHLCV columns for VWAP calculation.")
            return pd.Series(dtype=float)

        typical_price = (df['high'] + df['low'] + df['close']) / 3.0
        pv = typical_price * df['volume']
        
        cumulative_pv = pv.cumsum()
        cumulative_volume = df['volume'].cumsum()
        
        # Avoid division by zero
        vwap = cumulative_pv / cumulative_volume.replace(0, np.nan)
        return vwap.ffill().bfill()

    @staticmethod
    def calculate_atr(df: pd.DataFrame, period: int = 14) -> pd.Series:
        """
        Calculates Average True Range (ATR).
        True Range = max(High - Low, abs(High - PrevClose), abs(Low - PrevClose))
        ATR = Wilder's Exponential Moving Average of True Range over `period`.
        """
        if df.empty or not all(col in df.columns for col in ['high', 'low', 'close']):
            logger.error("DataFrame missing required OHLCV columns for ATR calculation.")
            return pd.Series(dtype=float)

        prev_close = df['close'].shift(1)
        tr1 = df['high'] - df['low']
        tr2 = (df['high'] - prev_close).abs()
        tr3 = (df['low'] - prev_close).abs()

        true_range = pd.concat([tr1, tr2, tr3], axis=1).max(axis=1)
        
        # Wilder's Smoothing for ATR
        atr = true_range.ewm(alpha=1.0 / period, min_periods=period, adjust=False).mean()
        return atr.bfill()

    @classmethod
    def add_all_indicators(cls, df: pd.DataFrame, atr_period: int = 14) -> pd.DataFrame:
        """
        Applies VWAP and ATR indicators directly onto a copy of the input DataFrame.
        """
        df_copy = df.copy()
        df_copy['vwap'] = cls.calculate_vwap(df_copy)
        df_copy['atr'] = cls.calculate_atr(df_copy, period=atr_period)
        return df_copy
