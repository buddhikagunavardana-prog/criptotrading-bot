import os
import json
import pandas as pd
import numpy as np
import logging
from typing import Dict, Any, Tuple, Optional, List
from dotenv import load_dotenv
import google.generativeai as genai

logger = logging.getLogger(__name__)

# Load dotenv to read the GEMINI_API_KEY from .env / .dev
if os.path.exists(".env"):
    load_dotenv(dotenv_path=".env")
elif os.path.exists(".dev"):
    load_dotenv(dotenv_path=".dev")
else:
    load_dotenv()

# Configure the Gemini API key
api_key = os.getenv("GEMINI_API_KEY")
if api_key:
    genai.configure(api_key=api_key)
else:
    logger.warning("GEMINI_API_KEY is not defined in the environment or .dev/.env files.")

def get_ai_dynamic_weights(market_summary_data: Dict[str, Any]) -> Dict[str, float]:
    """
    Sends recent market volatility and trend strength to the Gemini API.
    Instructs the Gemini model to analyze the data and return dynamically adjusted
    maximum weightings for RSI, MA, FVG, and Order Blocks in a JSON structure.
    
    Returns:
        Dict[str, float]: Dynamically adjusted weights with keys 'rsi', 'ma', 'fvg', 'ob'.
    """
    default_weights = {"rsi": 40.0, "ma": 60.0, "fvg": 20.0, "ob": 20.0}
    
    if not api_key:
        logger.warning("No Gemini API key available. Falling back to default static weights.")
        return default_weights

    try:
        # Use gemini-3.5-flash as the standard model for dynamic weights / text tasks
        model = genai.GenerativeModel('gemini-3.5-flash')
        
        prompt = f"""
You are an expert AI Trading Co-pilot. Your task is to dynamically adjust the maximum scoring weights of four technical indicators based on recent market conditions:
1. RSI (Relative Strength Index)
2. MA (Moving Averages)
3. FVG (Fair Value Gaps)
4. OB (Order Blocks)

The default base weightings are:
- rsi: 40.0
- ma: 60.0
- fvg: 20.0
- ob: 20.0

Analyze this market summary data:
{json.dumps(market_summary_data, indent=2)}

Guidelines:
- If volatility is high, decrease the weight of Trend/MA indicators (which lag in volatile or ranging markets) and increase the weight of SMC/Price Action indicators (FVG and OB).
- If volatility is low or there is a strong consistent trend, keep or increase MA weighting to prioritize trend-following.
- Adjust weights dynamically. Ensure the weight values are reasonable positive floats.

Return ONLY a valid JSON object with the following schema:
{{
  "rsi": <float>,
  "ma": <float>,
  "fvg": <float>,
  "ob": <float>
}}
Do not include any explanation or markdown blocks outside of the JSON.
"""
        
        response = model.generate_content(
            prompt,
            generation_config={"response_mime_type": "application/json"}
        )
        
        response_text = response.text.strip()
        logger.info(f"Gemini dynamic weights response: {response_text}")
        
        # Parse JSON
        parsed_weights = json.loads(response_text)
        
        # Validate keys and types
        validated_weights = {}
        for key in ["rsi", "ma", "fvg", "ob"]:
            if key in parsed_weights:
                val = float(parsed_weights[key])
                if val >= 0:
                    validated_weights[key] = val
                else:
                    validated_weights[key] = default_weights[key]
            else:
                validated_weights[key] = default_weights[key]
                
        return validated_weights

    except Exception as e:
        logger.error(f"Error calling Gemini API or parsing response, falling back to static weights: {e}", exc_info=True)
        return default_weights


class BaseStrategy:
    """
    Base Strategy Class for all Quantitative Trading Strategies in the modular Strategy Engine.
    Scalable and easy to extend by subclassing.
    """
    def __init__(self, name: str, description: str = ""):
        self.name = name
        self.description = description

    def calculate_signals(self, df: pd.DataFrame) -> Tuple[Optional[str], Dict[str, Any]]:
        """
        Calculates trading signals based on historical and current OHLCV data.
        
        Args:
            df (pd.DataFrame): DataFrame containing 'open', 'high', 'low', 'close', 'volume' columns.
            
        Returns:
            Tuple[Optional[str], Dict[str, Any]]: 
                - action: "BUY", "SELL", or None (Hold)
                - context: Dictionary of calculated indicators/metrics for audit logging and performance checking.
        """
        raise NotImplementedError("Each strategy must implement calculate_signals.")


# =====================================================================
# 1. SMART MONEY CONCEPTS (SMC) STRATEGIES
# =====================================================================

def detect_order_block(candles: Any) -> Tuple[Optional[Tuple[float, float]], Optional[Tuple[float, float]]]:
    """
    Analyzes recent price action (minimum last 3-5 closed candles) to detect Order Blocks (OB).
    
    Logic:
    Bullish OB: Identify the last bearish (red) candle that immediately precedes a strong bullish impulse 
                (a sequence of green candles that break recent highs or form a Bullish FVG). 
                The High and Low of this bearish candle become the OB zone.
    Bearish OB: Identify the last bullish (green) candle preceding a strong bearish impulse 
                (a sequence of red candles that break recent lows or form a Bearish FVG). 
                The High and Low of this green candle become the bearish OB zone.
                
    Returns:
        Tuple[Optional[Tuple[float, float]], Optional[Tuple[float, float]]]:
            (bullish_ob_zone, bearish_ob_zone) where each zone is a tuple (low, high) or None.
    """
    if candles is None:
        return None, None

    try:
        # Convert input to DataFrame if needed
        if not isinstance(candles, pd.DataFrame):
            if hasattr(candles, "iloc"):
                df = candles
            else:
                df = pd.DataFrame(candles, columns=['timestamp', 'open', 'high', 'low', 'close', 'volume'])
        else:
            df = candles

        if len(df) < 5:
            return None, None

        bullish_ob_zone = None
        bearish_ob_zone = None

        # 1. Search for Bullish OB
        # Scan backwards starting from 3 candles ago to find a bearish candle followed by a bullish impulse
        for i in range(len(df) - 3, 1, -1):
            c_i = df.iloc[i]
            # Red candle (bearish)
            if c_i['close'] < c_i['open']:
                # Need strong bullish impulse immediately following candle i
                c_next1 = df.iloc[i+1]
                c_next2 = df.iloc[i+2]
                
                # Check for Bullish FVG (Low of next2 > High of i)
                is_fvg = c_next2['low'] > c_i['high']
                
                # Check for consecutive green candles breaking recent high (the high of candle i)
                is_consecutive_green_break = (
                    c_next1['close'] > c_next1['open'] and 
                    c_next2['close'] > c_next2['open'] and 
                    c_next2['close'] > c_i['high']
                )
                
                if is_fvg or is_consecutive_green_break:
                    bullish_ob_zone = (float(c_i['low']), float(c_i['high']))
                    break

        # 2. Search for Bearish OB
        # Scan backwards starting from 3 candles ago to find a bullish candle followed by a bearish impulse
        for i in range(len(df) - 3, 1, -1):
            c_i = df.iloc[i]
            # Green candle (bullish)
            if c_i['close'] > c_i['open']:
                # Need strong bearish impulse immediately following candle i
                c_next1 = df.iloc[i+1]
                c_next2 = df.iloc[i+2]
                
                # Check for Bearish FVG (High of next2 < Low of i)
                is_fvg = c_next2['high'] < c_i['low']
                
                # Check for consecutive red candles breaking recent low (the low of candle i)
                is_consecutive_red_break = (
                    c_next1['close'] < c_next1['open'] and 
                    c_next2['close'] < c_next2['open'] and 
                    c_next2['close'] < c_i['low']
                )
                
                if is_fvg or is_consecutive_red_break:
                    bearish_ob_zone = (float(c_i['low']), float(c_i['high']))
                    break

        return bullish_ob_zone, bearish_ob_zone

    except Exception as e:
        # Avoid crashing, just log warning and return None, None
        logging.warning(f"Error inside detect_order_block: {e}")
        return None, None


class SMCOrderBlockStrategy(BaseStrategy):
    """
    SMC Order Block Expansion Strategy.
    
    Concept:
    Institutional Buy/Sell Order Blocks are specific price ranges where large institutions 
    accumulate or distribute their orders. A bullish Order Block (OB) is defined as the 
    last bearish candlestick prior to a strong bullish expansion that breaks market structure 
    (Breaks above a recent swing high / Market Structure Shift). When the price retraces and 
    sweeps back into this OB range, a highly favorable BUY order is triggered.
    
    Recommended Python Libraries:
    - scipy.signal: For locating exact peaks and swing high/low points via argrelextrema.
    - pandas_ta: For ATR and auxiliary indicators.
    - numpy: For vectorized rolling window structure break evaluations.
    """
    def __init__(self):
        super().__init__(
            name="SMC Order Block Expansion",
            description="Identifies institutional accumulation/distribution blocks and executes on dynamic retests."
        )

    def detect_order_block(self, candles: Any) -> Tuple[Optional[Tuple[float, float]], Optional[Tuple[float, float]]]:
        """Method interface for detect_order_block inside the strategy class."""
        return detect_order_block(candles)

    def calculate_signals(self, df: pd.DataFrame) -> Tuple[Optional[str], Dict[str, Any]]:
        if len(df) < 15:
            return None, {"message": "Insufficient data for SMC Order Block"}

        df = df.copy()
        # Find swing highs and swing lows using a 5-candle window
        df['swing_high'] = df['high'].rolling(window=5, center=True).max()
        df['swing_low'] = df['low'].rolling(window=5, center=True).min()
        
        # Identify Order Blocks
        bullish_ob_low = None
        bullish_ob_high = None
        bearish_ob_low = None
        bearish_ob_high = None
        
        # Iterate backwards to find the most recent structure break and its order block
        # A structure break happens when close crosses above the recent swing high or below the recent swing low
        for idx in range(len(df) - 3, 5, -1):
            row = df.iloc[idx]
            prev_swing_high = df.iloc[idx-1]['swing_high']
            prev_swing_low = df.iloc[idx-1]['swing_low']
            
            # Bullish Break of Structure (BOS)
            if row['close'] > prev_swing_high and not pd.isna(prev_swing_high):
                # Bullish OB is the last bearish candle prior to this break
                for ob_idx in range(idx, 2, -1):
                    cand = df.iloc[ob_idx]
                    if cand['close'] < cand['open']: # bearish candle
                        bullish_ob_low = cand['low']
                        bullish_ob_high = cand['high']
                        break
                if bullish_ob_high is not None:
                    break
                    
            # Bearish Break of Structure (BOS)
            elif row['close'] < prev_swing_low and not pd.isna(prev_swing_low):
                # Bearish OB is the last bullish candle prior to this break
                for ob_idx in range(idx, 2, -1):
                    cand = df.iloc[ob_idx]
                    if cand['close'] > cand['open']: # bullish candle
                        bearish_ob_low = cand['low']
                        bearish_ob_high = cand['high']
                        break
                if bearish_ob_high is not None:
                    break

        current_close = df.iloc[-1]['close']
        action = None
        context = {
            "bullish_ob_low": float(bullish_ob_low) if bullish_ob_low else None,
            "bullish_ob_high": float(bullish_ob_high) if bullish_ob_high else None,
            "bearish_ob_low": float(bearish_ob_low) if bearish_ob_low else None,
            "bearish_ob_high": float(bearish_ob_high) if bearish_ob_high else None,
            "current_close": float(current_close)
        }

        # Signal generation
        if bullish_ob_low is not None and bullish_ob_high is not None:
            # Retest of bullish OB (Price enters the order block zone)
            if bullish_ob_low <= current_close <= bullish_ob_high:
                action = "BUY"
                context["trigger"] = "Bullish Order Block Retest"
                
        if bearish_ob_low is not None and bearish_ob_high is not None:
            # Retest of bearish OB (Price enters the bearish block zone)
            if bearish_ob_low <= current_close <= bearish_ob_high:
                action = "SELL"
                context["trigger"] = "Bearish Order Block Retest"

        return action, context


class SMCBreakerMitigationStrategy(BaseStrategy):
    """
    SMC High-Probability Mitigation Zone (Breaker Blocks) Strategy.
    
    Concept:
    Mitigation Zones represent "failed" order blocks. When an established bullish order block 
    is violated (i.e. the market crashes straight through it), it "flips" into a Bearish Breaker 
    Mitigation Zone. Conversely, when a bearish order block is breached to the upside, it becomes 
    a Bullish Breaker Zone. Institutional traders retest these zones to mitigate (close out) 
    their remaining underwater positions.
    
    Recommended Python Libraries:
    - pandas: For rolling extrema and data manipulation.
    - pandas_ta: For multi-timeframe analysis and signal integration.
    """
    def __init__(self):
        super().__init__(
            name="SMC High-Probability Mitigation Zone",
            description="Detects failed/broken order blocks (Breaker Blocks) and targets retest mitigation."
        )

    def calculate_signals(self, df: pd.DataFrame) -> Tuple[Optional[str], Dict[str, Any]]:
        if len(df) < 15:
            return None, {"message": "Insufficient data for SMC Mitigation Zone"}

        df = df.copy()
        current_close = df.iloc[-1]['close']
        
        # Simple rolling peak/troughs to identify older structural levels
        df['local_high'] = df['high'].rolling(window=10).max()
        df['local_low'] = df['low'].rolling(window=10).min()
        
        # A Breaker Block is defined as a broken key pivot high/low.
        # Bullish Breaker: A prior high that was breached, acting as support.
        # Bearish Breaker: A prior low that was breached, acting as resistance.
        prior_high = df.iloc[-5]['local_high']
        prior_low = df.iloc[-5]['local_low']
        
        action = None
        context = {
            "prior_high_resistance": float(prior_high) if not pd.isna(prior_high) else None,
            "prior_low_support": float(prior_low) if not pd.isna(prior_low) else None,
            "current_close": float(current_close)
        }

        # Check for breaker flips (Inverted S/R retest)
        if not pd.isna(prior_high) and current_close > prior_high:
            # Price breached prior high resistance, now retesting it as support
            if abs(current_close - prior_high) / prior_high < 0.005: # within 0.5% threshold
                action = "BUY"
                context["trigger"] = "Bullish Breaker Mitigation Retest"
                
        elif not pd.isna(prior_low) and current_close < prior_low:
            # Price breached prior low support, now retesting it as resistance
            if abs(current_close - prior_low) / prior_low < 0.005:
                action = "SELL"
                context["trigger"] = "Bearish Breaker Mitigation Retest"

        return action, context


class SMCFairValueGapStrategy(BaseStrategy):
    """
    SMC Fair Value Gap (FVG) Inversion Strategy.
    
    Concept:
    An FVG is a 3-candle price imbalance created by high-momentum market moves. 
    A Bullish FVG occurs when the High of Candle 1 is lower than the Low of Candle 3, leaving a 
    gap (imbalance) in Candle 2. An FVG Inversion happens when price invalidates this gap by 
    closing fully below/above it. This broken FVG flips its role: a broken Bullish FVG becomes resistance, 
    and a broken Bearish FVG becomes a strong support.
    
    Recommended Python Libraries:
    - pandas: For sliding window calculations.
    - numpy: For robust conditional gap assessments.
    """
    def __init__(self):
        super().__init__(
            name="SMC Fair Value Gap (FVG) Inversion",
            description="Identifies 3-candle liquidity imbalances (FVG) and tracks subsequent support/resistance role flips."
        )

    def calculate_signals(self, df: pd.DataFrame) -> Tuple[Optional[str], Dict[str, Any]]:
        if len(df) < 5:
            return None, {"message": "Insufficient data for SMC FVG Inversion"}

        df = df.copy()
        current_close = df.iloc[-1]['close']
        
        # Scan for an FVG in the last few candles (excluding the active candle)
        # Candle 1 (idx -4), Candle 2 (idx -3), Candle 3 (idx -2)
        c1_high = df.iloc[-4]['high']
        c1_low = df.iloc[-4]['low']
        c3_high = df.iloc[-2]['high']
        c3_low = df.iloc[-2]['low']
        
        fvg_type = None
        fvg_top = None
        fvg_bottom = None
        
        # Bullish FVG: Candle 1 High < Candle 3 Low (Gap is between c1_high and c3_low)
        if c1_high < c3_low:
            fvg_type = "BULLISH"
            fvg_top = c3_low
            fvg_bottom = c1_high
            
        # Bearish FVG: Candle 1 Low > Candle 3 High (Gap is between c3_high and c1_low)
        elif c1_low > c3_high:
            fvg_type = "BEARISH"
            fvg_top = c1_low
            fvg_bottom = c3_high
            
        action = None
        context = {
            "fvg_detected": fvg_type,
            "fvg_top": float(fvg_top) if fvg_top else None,
            "fvg_bottom": float(fvg_bottom) if fvg_bottom else None,
            "current_close": float(current_close)
        }

        # Check for FVG Inversion
        if fvg_type == "BEARISH" and fvg_top is not None:
            # Bearish FVG inverted (price closes above the top of the bearish imbalance gap) -> Turns Support
            if current_close > fvg_top:
                # Retest of inverted gap support
                if abs(current_close - fvg_top) / fvg_top < 0.003:
                    action = "BUY"
                    context["trigger"] = "Bearish FVG Inversion Support Retest"
                    
        elif fvg_type == "BULLISH" and fvg_bottom is not None:
            # Bullish FVG inverted (price closes below the bottom of the bullish imbalance gap) -> Turns Resistance
            if current_close < fvg_bottom:
                # Retest of inverted gap resistance
                if abs(current_close - fvg_bottom) / fvg_bottom < 0.003:
                    action = "SELL"
                    context["trigger"] = "Bullish FVG Inversion Resistance Retest"

        return action, context


class SMCLiquiditySweepStrategy(BaseStrategy):
    """
    SMC Liquidity Sweep Core Strategy.
    
    Concept:
    Liquidity Sweeps exploit retail stop-loss clusters located just above major swing highs (buy stops) 
    or below major swing lows (sell stops). A bullish sweep is identified when price spikes below 
    a key swing low to trigger stops, but quickly closes back above that level, signaling 
    institutional buying/absorption.
    
    Recommended Python Libraries:
    - pandas: For sliding rolling windows.
    - scipy.signal: For finding prominent extrema.
    """
    def __init__(self):
        super().__init__(
            name="SMC Liquidity Sweep Core",
            description="Targets major swing levels to capture retail stop-out spikes and immediate institutional absorption."
        )

    def calculate_signals(self, df: pd.DataFrame) -> Tuple[Optional[str], Dict[str, Any]]:
        if len(df) < 20:
            return None, {"message": "Insufficient data for SMC Liquidity Sweep"}

        df = df.copy()
        current_candle = df.iloc[-1]
        prev_candle = df.iloc[-2]
        
        # Calculate standard 20-candle swing high and low ranges
        swing_high = df.iloc[:-1]['high'].rolling(window=15).max().iloc[-1]
        swing_low = df.iloc[:-1]['low'].rolling(window=15).min().iloc[-1]
        
        action = None
        context = {
            "swing_high_liquidity": float(swing_high),
            "swing_low_liquidity": float(swing_low),
            "current_low": float(current_candle['low']),
            "current_high": float(current_candle['high']),
            "current_close": float(current_candle['close'])
        }

        # Bullish Liquidity Sweep:
        # Current or previous candle low is below swing low, but candle close is safely above swing low
        if current_candle['low'] < swing_low and current_candle['close'] > swing_low:
            action = "BUY"
            context["trigger"] = "Bullish Liquidity Sweep (Stop Grab)"
            
        # Bearish Liquidity Sweep:
        # Current or previous candle high is above swing high, but candle close is safely below swing high
        elif current_candle['high'] > swing_high and current_candle['close'] < swing_high:
            action = "SELL"
            context["trigger"] = "Bearish Liquidity Sweep (Stop Sweep)"

        return action, context


# =====================================================================
# 2. STANDARD / VOLATILITY / OSCILLATOR STRATEGIES
# =====================================================================

class BollingerBreakoutStrategy(BaseStrategy):
    """
    Bollinger Volatility Breakout Strategy.
    
    Concept:
    Bollinger Bands represent a dynamic volatility channel. This strategy identifies 
    high-momentum breakouts when price closes outside the bands.
    
    Recommended Python Libraries:
    - pandas_ta: `df.ta.bbands(length=20, std=2)`
    - TA-Lib: `talib.BBANDS`
    """
    def __init__(self):
        super().__init__(
            name="Bollinger Volatility Breakout",
            description="Triggers trades when dynamic volatility standard deviations are broken with momentum."
        )

    def calculate_signals(self, df: pd.DataFrame) -> Tuple[Optional[str], Dict[str, Any]]:
        if len(df) < 20:
            return None, {"message": "Insufficient data for Bollinger Breakout"}

        df = df.copy()
        df['sma_20'] = df['close'].rolling(window=20).mean()
        df['std_20'] = df['close'].rolling(window=20).std()
        df['upper_band'] = df['sma_20'] + (2.0 * df['std_20'])
        df['lower_band'] = df['sma_20'] - (2.0 * df['std_20'])

        current_row = df.iloc[-1]
        prev_row = df.iloc[-2]
        
        action = None
        context = {
            "middle_band": float(current_row['sma_20']),
            "upper_band": float(current_row['upper_band']),
            "lower_band": float(current_row['lower_band']),
            "current_close": float(current_row['close'])
        }

        # Breakout signals
        if current_row['close'] > current_row['upper_band'] and prev_row['close'] <= prev_row['upper_band']:
            action = "BUY"
            context["trigger"] = "Bollinger Upper Band Breakout"
        elif current_row['close'] < current_row['lower_band'] and prev_row['close'] >= prev_row['lower_band']:
            action = "SELL"
            context["trigger"] = "Bollinger Lower Band Breakout"

        return action, context


class ADXTrendStrategy(BaseStrategy):
    """
    ADX Multi-Timeframe Trend Strength Strategy.
    
    Concept:
    The Average Directional Index (ADX) quantifies trend strength without regard to direction. 
    When ADX is high (> 25), a strong trend exists. Direction is filtered via the Plus (+DI) 
    and Minus (-DI) Directional Indicators.
    
    Recommended Python Libraries:
    - pandas_ta: `df.ta.adx(length=14)`
    - TA-Lib: `talib.ADX`, `talib.MINUS_DI`, `talib.PLUS_DI`
    """
    def __init__(self):
        super().__init__(
            name="ADX Multi-Timeframe Trend",
            description="Filters trend strength using ADX, executing high-strength trend follows."
        )

    def calculate_signals(self, df: pd.DataFrame) -> Tuple[Optional[str], Dict[str, Any]]:
        if len(df) < 20:
            return None, {"message": "Insufficient data for ADX Trend"}

        df = df.copy()
        period = 14
        
        # Calculate True Range (TR)
        high_low = df['high'] - df['low']
        high_close_prev = (df['high'] - df['close'].shift(1)).abs()
        low_close_prev = (df['low'] - df['close'].shift(1)).abs()
        df['tr'] = pd.concat([high_low, high_close_prev, low_close_prev], axis=1).max(axis=1)
        
        # Directional Movement (+DM, -DM)
        up_move = df['high'] - df['high'].shift(1)
        down_move = df['low'].shift(1) - df['low']
        
        df['plus_dm'] = np.where((up_move > down_move) & (up_move > 0), up_move, 0.0)
        df['minus_dm'] = np.where((down_move > up_move) & (down_move > 0), down_move, 0.0)
        
        # Smoothings
        tr_smooth = df['tr'].rolling(window=period).sum()
        plus_dm_smooth = df['plus_dm'].rolling(window=period).sum()
        minus_dm_smooth = df['minus_dm'].rolling(window=period).sum()
        
        df['plus_di'] = 100 * (plus_dm_smooth / tr_smooth)
        df['minus_di'] = 100 * (minus_dm_smooth / tr_smooth)
        
        df['dx'] = 100 * (df['plus_di'] - df['minus_di']).abs() / (df['plus_di'] + df['minus_di'])
        df['adx'] = df['dx'].rolling(window=period).mean()

        current_row = df.iloc[-1]
        
        action = None
        context = {
            "adx": float(current_row['adx']) if not pd.isna(current_row['adx']) else 0.0,
            "plus_di": float(current_row['plus_di']) if not pd.isna(current_row['plus_di']) else 0.0,
            "minus_di": float(current_row['minus_di']) if not pd.isna(current_row['minus_di']) else 0.0,
            "current_close": float(current_row['close'])
        }

        # Check Trend Strength
        if context["adx"] > 25.0:
            if context["plus_di"] > context["minus_di"]:
                action = "BUY"
                context["trigger"] = "Strong Bullish Trend Follow (+DI > -DI)"
            elif context["minus_di"] > context["plus_di"]:
                action = "SELL"
                context["trigger"] = "Strong Bearish Trend Follow (-DI > +DI)"

        return action, context


class VWAPMeanReversionStrategy(BaseStrategy):
    """
    VWAP Anchored Mean Reversion Strategy.
    
    Concept:
    Volume Weighted Average Price (VWAP) is a benchmark price reflecting the true volume-supported average.
    Extreme price deviations from VWAP are unstable and prone to revert to mean average values.
    
    Recommended Python Libraries:
    - pandas_ta: `df.ta.vwap()`
    - numpy: Vectorized volume-weighted cumulative sums.
    """
    def __init__(self):
        super().__init__(
            name="VWAP Anchored Mean Reversion",
            description="Targets standard deviation deviations from Volume Weighted Average Price for mean reversion entry."
        )

    def calculate_signals(self, df: pd.DataFrame) -> Tuple[Optional[str], Dict[str, Any]]:
        if len(df) < 15:
            return None, {"message": "Insufficient data for VWAP Mean Reversion"}

        df = df.copy()
        
        # Calculate VWAP
        tp = (df['high'] + df['low'] + df['close']) / 3.0
        df['vwap'] = (tp * df['volume']).cumsum() / df['volume'].cumsum()
        
        # Calculate custom rolling deviation standard from VWAP
        df['dev_from_vwap'] = df['close'] - df['vwap']
        std_dev = df['dev_from_vwap'].std()
        
        current_row = df.iloc[-1]
        vwap_val = current_row['vwap']
        close_val = current_row['close']
        
        action = None
        context = {
            "vwap": float(vwap_val),
            "std_deviation": float(std_dev),
            "current_close": float(close_val),
            "deviation_ratio": float(current_row['dev_from_vwap'] / std_dev) if std_dev > 0 else 0.0
        }

        # If price stretches 2 standard deviations away from VWAP, anticipate mean reversion
        if std_dev > 0:
            # Over-extended to the downside (BUY the undervalued dip)
            if (close_val < vwap_val - (2.0 * std_dev)):
                action = "BUY"
                context["trigger"] = "Bullish Mean Reversion (Price far below VWAP)"
            # Over-extended to the upside (SELL the overvalued peak)
            elif (close_val > vwap_val + (2.0 * std_dev)):
                action = "SELL"
                context["trigger"] = "Bearish Mean Reversion (Price far above VWAP)"

        return action, context


class RSIDivergenceStrategy(BaseStrategy):
    """
    RSI Extreme Divergence Strategy.
    
    Concept:
    RSI Divergence highlights loss of momentum. Bullish divergence occurs when the asset price 
    makes a lower low while the RSI registers a higher low (buying momentum starting to build).
    Bearish divergence is when price makes a higher high, but RSI makes a lower high.
    
    Recommended Python Libraries:
    - pandas_ta: `df.ta.rsi(length=14)`
    - TA-Lib: `talib.RSI`
    """
    def __init__(self):
        super().__init__(
            name="RSI Extreme Divergence",
            description="Locates price vs momentum divergences to catch clean structural trend reversals."
        )

    def calculate_signals(self, df: pd.DataFrame) -> Tuple[Optional[str], Dict[str, Any]]:
        if len(df) < 25:
            return None, {"message": "Insufficient data for RSI Divergence"}

        df = df.copy()
        period = 14
        
        # Compute RSI
        delta = df['close'].diff()
        gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
        loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()
        rs = gain / loss
        df['rsi'] = 100 - (100 / (1 + rs))

        # Check for Local Lows & Highs in Price & RSI
        price_now = df.iloc[-1]['close']
        price_prev_low = df.iloc[-15:-1]['close'].min()
        price_prev_high = df.iloc[-15:-1]['close'].max()
        
        rsi_now = df.iloc[-1]['rsi']
        rsi_prev_low = df.iloc[-15:-1]['rsi'].min()
        rsi_prev_high = df.iloc[-15:-1]['rsi'].max()
        
        action = None
        context = {
            "rsi": float(rsi_now) if not pd.isna(rsi_now) else 50.0,
            "rsi_prev_low": float(rsi_prev_low) if not pd.isna(rsi_prev_low) else 50.0,
            "rsi_prev_high": float(rsi_prev_high) if not pd.isna(rsi_prev_high) else 50.0,
            "current_close": float(price_now)
        }

        # Check Divergences
        if not pd.isna(rsi_now) and not pd.isna(rsi_prev_low):
            # Bullish Divergence: Price hits lower low, but RSI is higher
            if price_now < price_prev_low and rsi_now > rsi_prev_low and rsi_now < 35.0:
                action = "BUY"
                context["trigger"] = "Bullish RSI Divergence (Price Lower Low, RSI Higher Low)"
            
            # Bearish Divergence: Price hits higher high, but RSI is lower
            elif price_now > price_prev_high and rsi_now < rsi_prev_high and rsi_now > 65.0:
                action = "SELL"
                context["trigger"] = "Bearish RSI Divergence (Price Higher High, RSI Lower High)"

        return action, context


class ATRTrailingStrategy(BaseStrategy):
    """
    ATR Dynamic Trailing Stop-Loss Edge Strategy.
    
    Concept:
    Average True Range (ATR) measures market volatility. By trailing behind high-momentum trends 
    at a multiplier of ATR (Chandelier Exit), we protect profits and dynamically entry 
    long/short trends once the volatility trailing stop line is cleanly crossed.
    
    Recommended Python Libraries:
    - pandas_ta: `df.ta.atr(length=14)`
    - TA-Lib: `talib.ATR`
    """
    def __init__(self):
        super().__init__(
            name="ATR Dynamic Trailing Edge",
            description="Maintains responsive stop-loss boundaries based on dynamic volatility multipliers."
        )

    def calculate_signals(self, df: pd.DataFrame) -> Tuple[Optional[str], Dict[str, Any]]:
        if len(df) < 15:
            return None, {"message": "Insufficient data for ATR Trailing Edge"}

        df = df.copy()
        period = 14
        
        # Calculate ATR
        high_low = df['high'] - df['low']
        high_close_prev = (df['high'] - df['close'].shift(1)).abs()
        low_close_prev = (df['low'] - df['close'].shift(1)).abs()
        df['tr'] = pd.concat([high_low, high_close_prev, low_close_prev], axis=1).max(axis=1)
        df['atr'] = df['tr'].rolling(window=period).mean()

        # Dynamic Trailing stop limits (Multiplier 2.5)
        df['atr_multiplier'] = df['atr'] * 2.5
        df['trailing_stop_buy'] = df['close'].rolling(window=10).max() - df['atr_multiplier']
        df['trailing_stop_sell'] = df['close'].rolling(window=10).min() + df['atr_multiplier']

        current_row = df.iloc[-1]
        prev_row = df.iloc[-2]
        
        action = None
        context = {
            "atr": float(current_row['atr']) if not pd.isna(current_row['atr']) else 0.0,
            "trailing_stop_buy_support": float(current_row['trailing_stop_buy']) if not pd.isna(current_row['trailing_stop_buy']) else 0.0,
            "trailing_stop_sell_resistance": float(current_row['trailing_stop_sell']) if not pd.isna(current_row['trailing_stop_sell']) else 0.0,
            "current_close": float(current_row['close'])
        }

        # Signals
        if current_row['close'] > prev_row['trailing_stop_sell'] and prev_row['close'] <= prev_row['trailing_stop_sell']:
            action = "BUY"
            context["trigger"] = "Price crossed above ATR Trailing Stop Line"
        elif current_row['close'] < prev_row['trailing_stop_buy'] and prev_row['close'] >= prev_row['trailing_stop_buy']:
            action = "SELL"
            context["trigger"] = "Price crossed below ATR Trailing Stop Line"

        return action, context


class MainBotStrategy(BaseStrategy):
    """
    Standard Trading Bot Strategy (SMA 50/200 Cross + RSI 14).
    
    Concept:
    - BUY when: RSI is below 30 AND SMA 50 crosses above SMA 200 (Golden Cross).
    - SELL when: RSI is above 70 OR SMA 50 crosses below SMA 200 (Death Cross).
    """
    def __init__(self):
        super().__init__(
            name="Main Bot Strategy",
            description="The core indicator-based trading strategy utilizing SMA 50/200 crossovers and oversold/overbought RSI filters."
        )

    def calculate_signals(self, df: pd.DataFrame) -> Tuple[Optional[str], Dict[str, Any]]:
        if len(df) < 202:
            return None, {"message": "Not enough candle periods to calculate SMA 50/200."}

        df = df.copy()
        
        # Calculate RSI 14 matching Wilder's smoothing
        period = 14
        delta = df['close'].diff()
        gain = (delta.where(delta > 0, 0)).copy()
        loss = (-delta.where(delta < 0, 0)).copy()
        avg_gain = gain.ewm(com=period - 1, min_periods=period).mean()
        avg_loss = loss.ewm(com=period - 1, min_periods=period).mean()
        rs = avg_gain / avg_loss
        df['rsi_14'] = 100 - (100 / (1 + rs))

        # Calculate SMA 50/200
        df['sma_50'] = df['close'].rolling(window=50).mean()
        df['sma_200'] = df['close'].rolling(window=200).mean()

        sma_50_curr = df['sma_50'].iloc[-1]
        sma_200_curr = df['sma_200'].iloc[-1]
        sma_50_prev = df['sma_50'].iloc[-2]
        sma_200_prev = df['sma_200'].iloc[-2]
        
        rsi_curr = df['rsi_14'].iloc[-1]
        current_close = df['close'].iloc[-1]

        bullish_crossover = (sma_50_prev <= sma_200_prev) and (sma_50_curr > sma_200_curr)
        bearish_crossover = (sma_50_prev >= sma_200_prev) and (sma_50_curr < sma_200_curr)

        action = None
        
        # Analyze news sentiment and calculate adjusted RSI thresholds
        sentiment_score = 0.0
        sentiment_justification = "Neutral fallback"
        try:
            from sentiment_analyzer import analyze_market_news_sentiment
            import os
            # Heuristic guess for symbol based on close price
            symbol = "BTC"
            if current_close > 10000.0:
                symbol = "BTC"
            elif 800.0 < current_close < 8000.0:
                symbol = "ETH"
            else:
                symbol = os.getenv("TARGET_SYMBOL", "BTC")
                
            sentiment_score, sentiment_justification = analyze_market_news_sentiment(symbol)
        except Exception as e:
            pass
            
        buy_rsi_threshold = 30.0 + (sentiment_score * 10.0)
        sell_rsi_threshold = 70.0 + (sentiment_score * 10.0)

        context = {
            "close": float(current_close),
            "rsi_14": float(rsi_curr) if not pd.isna(rsi_curr) else 50.0,
            "sma_50": float(sma_50_curr) if not pd.isna(sma_50_curr) else 0.0,
            "sma_200": float(sma_200_curr) if not pd.isna(sma_200_curr) else 0.0,
            "bullish_cross": bool(bullish_crossover),
            "bearish_cross": bool(bearish_crossover),
            "sentiment_score": float(sentiment_score),
            "sentiment_justification": str(sentiment_justification),
            "buy_rsi_threshold": float(buy_rsi_threshold),
            "sell_rsi_threshold": float(sell_rsi_threshold)
        }

        if rsi_curr < buy_rsi_threshold and bullish_crossover:
            action = "BUY"
            context["trigger"] = f"Golden Cross & RSI below adjusted threshold {buy_rsi_threshold:.1f} (Sentiment: {sentiment_score:+.2f})"
        elif rsi_curr > sell_rsi_threshold or bearish_crossover:
            action = "SELL"
            context["trigger"] = f"Death Cross or RSI above adjusted threshold {sell_rsi_threshold:.1f} (Sentiment: {sentiment_score:+.2f})"

        return action, context


# =====================================================================
# 3. DYNAMIC STRATEGY ROUTER / ENGINE
# =====================================================================

class StrategyEngine:
    """
    Strategy Router and execution core.
    Coordinates all available strategies, and runs the active chosen strategy dynamically.
    Scalable for any number of future strategy registrations.
    """
    def __init__(self):
        self._strategies: Dict[str, BaseStrategy] = {}
        self._register_strategies()

    def _register_strategies(self):
        """
        Dynamically registers all strategies. Easy to extend with new modular additions.
        """
        self.register(SMCOrderBlockStrategy())
        self.register(SMCBreakerMitigationStrategy())
        self.register(SMCFairValueGapStrategy())
        self.register(SMCLiquiditySweepStrategy())
        self.register(BollingerBreakoutStrategy())
        self.register(ADXTrendStrategy())
        self.register(VWAPMeanReversionStrategy())
        self.register(RSIDivergenceStrategy())
        self.register(ATRTrailingStrategy())
        self.register(MainBotStrategy())

    def register(self, strategy: BaseStrategy):
        """Adds a strategy module to the engine."""
        self._strategies[strategy.name.lower()] = strategy
        # Support fallback shorthand/stripped lookups for robust integration
        simplified_name = strategy.name.replace(" ", "").replace("_", "").lower()
        self._strategies[simplified_name] = strategy

    def list_available_strategies(self) -> List[str]:
        """Returns registered strategy naming labels."""
        return [strat.name for name, strat in self._strategies.items() if " " in strat.name]

    def get_strategy(self, strategy_name: str) -> Optional[BaseStrategy]:
        """
        Retrieves the strategy function module based on user selection.
        Supports fuzzy name lookups for robust compatibility.
        """
        if not strategy_name:
            return None
            
        key = strategy_name.lower()
        if key in self._strategies:
            return self._strategies[key]
            
        # Try simplified name lookup (remove spaces, symbols)
        simplified = strategy_name.replace(" ", "").replace("_", "").replace("-", "").lower()
        for k, strat in self._strategies.items():
            strat_simplified = strat.name.replace(" ", "").replace("_", "").replace("-", "").lower()
            if simplified in strat_simplified or strat_simplified in simplified:
                return strat
                
        return None

    def evaluate(self, strategy_name: str, df: pd.DataFrame) -> Tuple[Optional[str], Dict[str, Any]]:
        """
        Dynamically selects the active strategy based on user's choice,
        runs its signal engine, and returns trading decisions.
        """
        strategy = self.get_strategy(strategy_name)
        if not strategy:
            logger.warning(f"Requested strategy '{strategy_name}' was not found. Using default SMCOrderBlockStrategy fallback.")
            strategy = SMCOrderBlockStrategy()
            
        try:
            return strategy.calculate_signals(df)
        except Exception as e:
            logger.error(f"Error executing signals on strategy '{strategy.name}': {e}", exc_info=True)
            return None, {"error": str(e), "strategy_failed": strategy.name}

# Single shared global Strategy Engine instance
strategy_engine = StrategyEngine()
