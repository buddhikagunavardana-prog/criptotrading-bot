#!/usr/bin/env python3
"""
=============================================================================
                  CryptoBot AI - Modular Algorithmic Trading Engine
=============================================================================
A production-ready algorithmic trading bot engine supporting exchange integration
via CCXT, indicator calculation (RSI & Dual EMA/SMA Crosses), position sizing,
risk management (Stop-Loss & Take-Profit), and persistent file logging.

Requirements:
    pip install ccxt pandas numpy python-dotenv

Usage:
    1. Create a '.env' file in the root directory and specify your API credentials.
    2. Configure target pairs and risk parameters in '.env' or run with defaults.
    3. Run using: python trading_bot_engine.py
"""

import os
import sys
import time
import logging
from datetime import datetime
from typing import Dict, Any, Tuple, Optional, List
import pandas as pd
import numpy as np
from dotenv import load_dotenv
import ccxt
from binance.client import Client
from binance.exceptions import BinanceAPIException
from sqlalchemy import create_engine, Column, Integer, String, Float, DateTime, Boolean
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
from ai_trading_bot_backend.services.alert_service import AlertService

# --- Logging Configuration ---
# Logs to both console and a local 'bot_log.txt' file for audit trailing.
logger = logging.getLogger("TradingBotEngine")
logger.setLevel(logging.INFO)

formatter = logging.Formatter('%(asctime)s [%(levelname)s] %(name)s: %(message)s')

# Console Handler
console_handler = logging.StreamHandler(sys.stdout)
console_handler.setFormatter(formatter)
logger.addHandler(console_handler)

# File Handler
file_handler = logging.FileHandler("bot_log.txt", mode="a", encoding="utf-8")
file_handler.setFormatter(formatter)
logger.addHandler(file_handler)


# --- SQLAlchemy SQLite Database Setup & Models ---
Base = declarative_base()
DATABASE_URL = "sqlite:///trading_bot.db"
engine = create_engine(DATABASE_URL, connect_args={"check_same_thread": False})
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

class TradeOrder(Base):
    __tablename__ = "trades"
    id = Column(Integer, primary_key=True, index=True)
    timestamp = Column(DateTime, default=datetime.utcnow, nullable=False, index=True)
    symbol = Column(String, nullable=False, index=True)
    side = Column(String, nullable=False) # "BUY" or "SELL"
    price = Column(Float, nullable=False)
    amount = Column(Float, nullable=False)
    cost = Column(Float, nullable=False)
    pnl = Column(Float, nullable=True) # Populated on SELL
    pnl_pct = Column(Float, nullable=True) # Populated on SELL

class BotState(Base):
    __tablename__ = "bot_state"
    id = Column(Integer, primary_key=True)
    symbol = Column(String, unique=True, nullable=False, index=True)
    is_in_position = Column(Boolean, default=False, nullable=False)
    entry_price = Column(Float, default=0.0)
    amount = Column(Float, default=0.0)
    stop_loss = Column(Float, default=0.0)
    take_profit = Column(Float, default=0.0)
    last_updated = Column(DateTime, default=datetime.utcnow)

class HistoricalPrice(Base):
    __tablename__ = "historical_prices"
    id = Column(Integer, primary_key=True, index=True)
    timestamp = Column(DateTime, nullable=False, index=True)
    pair = Column(String, nullable=False, index=True)
    open = Column(Float, nullable=False)
    high = Column(Float, nullable=False)
    low = Column(Float, nullable=False)
    close = Column(Float, nullable=False)
    volume = Column(Float, nullable=False)

# Ensure database tables exist
Base.metadata.create_all(bind=engine)



class TradingBotEngine:
    """
    Production-grade Trading Bot Engine that handles fetching OHLCV data,
    calculating technical indicators, executing risk-managed orders, and
    maintaining robust error boundaries.
    """
    def __init__(self):
        # 1. Load environment variables from .env or fallback to .dev
        if os.path.exists(".env"):
            load_dotenv(dotenv_path=".env")
        elif os.path.exists(".dev"):
            load_dotenv(dotenv_path=".dev")
        else:
            load_dotenv()
        
        # 2. Configurable Strategy and Risk Parameters
        self.symbol = os.getenv("TARGET_SYMBOL", "BTC/USDT")
        self.timeframe = os.getenv("TIMEFRAME", "15m")
        self.stop_loss_pct = float(os.getenv("STOP_LOSS_PCT", "0.02"))   # Default: 2%
        self.take_profit_pct = float(os.getenv("TAKE_PROFIT_PCT", "0.04")) # Default: 4%
        self.risk_per_trade_pct = float(os.getenv("RISK_PER_TRADE_PCT", "0.01")) # Default: 1% account balance
        
        # 3. Exchange API Credentials (Securely read from environmental variables)
        self.exchange_id = os.getenv("EXCHANGE_ID", "binance").lower()
        self.api_key = os.getenv("EXCHANGE_API_KEY", "")
        self.api_secret = os.getenv("EXCHANGE_API_SECRET", "")
        self.sandbox_mode = os.getenv("USE_SANDBOX", "True").lower() == "true"
        
        # Initialize Exchange Client
        self.exchange = self._initialize_exchange()
        
        # Initialize direct Binance Client in testnet mode
        self.binance_api_key = os.getenv("BINANCE_API_KEY", "")
        self.binance_api_secret = os.getenv("BINANCE_SECRET_KEY", "")
        self.binance_client = None
        
        if self.binance_api_key and self.binance_api_secret:
            try:
                self.binance_client = Client(self.binance_api_key, self.binance_api_secret, testnet=True)
                # Explicitly target Binance Futures Testnet URL
                self.binance_client.FUTURES_URL = 'https://testnet.binancefuture.com/fapi'
                
                # Sync system time with Binance Futures Server Time
                try:
                    try:
                        server_time = self.binance_client.futures_time()
                    except Exception:
                        server_time = self.binance_client.get_server_time()
                    server_time_ms = server_time.get("serverTime")
                    local_time_ms = int(time.time() * 1000)
                    time_offset = server_time_ms - local_time_ms
                    self.binance_client.timestamp_offset = time_offset
                    logger.info(f"Binance Futures Client initialized in TESTNET mode (URL: {self.binance_client.FUTURES_URL}). Time sync successful (Offset: {time_offset} ms).")
                except Exception as sync_err:
                    logger.warning(f"Could not synchronize time with Binance Futures server: {sync_err}")
            except Exception as e:
                logger.error(f"Failed to initialize Binance Client: {e}")
        else:
            logger.warning("Binance credentials (BINANCE_API_KEY/BINANCE_SECRET_KEY) not found. Direct Binance execution is disabled.")

        self.active_position = self._load_bot_state_from_db()

        # 4. Initialize AlertService for trade execution and loss threshold notifications
        self.alert_service = AlertService()

        # 5. Sentiment and AI Threshold Adjustment Parameters
        self.cached_sentiment_score = 0.0
        self.cached_sentiment_justification = "No sentiment analyzed yet."
        self.last_sentiment_update = datetime.min

    def _send_desktop_notification(self, title: str, message: str):
        """
        Dispatches desktop notifications and email alerts using AlertService.
        """
        try:
            self.alert_service.send_desktop_notification(title=title, message=message)
            self.alert_service.send_email_alert(
                subject=title,
                body=f"CryptoBot AI Notification\n=======================\n\n{message}"
            )
        except Exception as e:
            logger.warning(f"AlertService dispatch encountered error: {e}")

    def _load_bot_state_from_db(self) -> Optional[Dict[str, Any]]:
        """
        Loads the bot's position state from SQLite to guarantee persistent lifecycle survival.
        """
        db = SessionLocal()
        try:
            state = db.query(BotState).filter(BotState.symbol == self.symbol).first()
            if state and state.is_in_position:
                logger.info(f"==> RESTORED active position from SQLite database: Buy Entry at ${state.entry_price:,.2f} for {state.amount:.6f} {self.symbol}")
                return {
                    "entry_price": state.entry_price,
                    "amount": state.amount,
                    "stop_loss": state.stop_loss,
                    "take_profit": state.take_profit,
                    "timestamp": state.last_updated
                }
            return None
        except Exception as e:
            logger.error(f"Error restoring bot state from SQLite: {e}")
            return None
        finally:
            db.close()

    def _save_bot_state_to_db(self, is_in_position: bool, entry_price: float = 0.0, amount: float = 0.0, stop_loss: float = 0.0, take_profit: float = 0.0):
        """
        Saves or updates current position state in SQLite database for robust session resumption.
        """
        db = SessionLocal()
        try:
            state = db.query(BotState).filter(BotState.symbol == self.symbol).first()
            if not state:
                state = BotState(symbol=self.symbol)
                db.add(state)
            
            state.is_in_position = is_in_position
            state.entry_price = entry_price
            state.amount = amount
            state.stop_loss = stop_loss
            state.take_profit = take_profit
            state.last_updated = datetime.utcnow()
            
            db.commit()
            logger.info(f"SQLite Bot State persisted successfully: is_in_position={is_in_position}")
        except Exception as e:
            logger.error(f"Failed to persist bot state in SQLite: {e}")
            db.rollback()
        finally:
            db.close()

    def _log_trade_to_db(self, side: str, price: float, amount: float, cost: float, pnl: Optional[float] = None, pnl_pct: Optional[float] = None):
        """
        Inserts structured trade order audits directly into local queryable SQLite database.
        """
        db = SessionLocal()
        try:
            trade = TradeOrder(
                symbol=self.symbol,
                side=side,
                price=price,
                amount=amount,
                cost=cost,
                pnl=pnl,
                pnl_pct=pnl_pct
            )
            db.add(trade)
            db.commit()
            logger.info(f"Trade successfully logged to SQLite Database (ID: {trade.id}).")
        except Exception as e:
            logger.error(f"Failed to log trade to SQLite Database: {e}")
            db.rollback()
        finally:
            db.close()

    def _initialize_exchange(self) -> ccxt.Exchange:
        """
        Safely initializes the requested exchange client via CCXT.
        """
        if not hasattr(ccxt, self.exchange_id):
            logger.error(f"Exchange '{self.exchange_id}' is not supported by CCXT library. Defaulting to Binance.")
            self.exchange_id = "binance"
            
        exchange_class = getattr(ccxt, self.exchange_id)
        
        # Setup credentials dictionary securely
        config = {
            'apiKey': self.api_key,
            'secret': self.api_secret,
            'enableRateLimit': True,
        }
        
        # Check if we are running in paper-trading/sandbox environment
        try:
            exchange_instance = exchange_class(config)
            if self.sandbox_mode:
                if exchange_instance.has.get('sandbox', False) or hasattr(exchange_instance, 'set_sandbox_mode'):
                    exchange_instance.set_sandbox_mode(True)
                    logger.info(f"Initialized {self.exchange_id.upper()} in SANDBOX/PAPER-TRADING mode.")
                else:
                    logger.warning(f"Exchange {self.exchange_id.upper()} does not explicitly support sandbox mode. Operating in dry-run/mock mode.")
            else:
                if not self.api_key or not self.api_secret:
                    logger.warning("No live exchange API keys detected. Operations will be forced into DRY-RUN mock execution.")
                else:
                    logger.info(f"Initialized {self.exchange_id.upper()} in LIVE trading mode. Trade with caution!")
            return exchange_instance
        except Exception as e:
            logger.error(f"Failed to initialize exchange connection: {e}")
            logger.info("Initializing fallback mock exchange for dry-runs...")
            # Create a mock exchange object if live connection fails
            return None

    # =========================================================================
    #                    TECHNICAL INDICATOR CALCULATIONS
    # =========================================================================
    @staticmethod
    def calculate_rsi(prices: pd.Series, period: int = 14) -> pd.Series:
        """
        Calculates the Relative Strength Index (RSI) using standard Wilder's smoothing.
        """
        delta = prices.diff()
        gain = (delta.where(delta > 0, 0)).copy()
        loss = (-delta.where(delta < 0, 0)).copy()

        # Exponential moving averages for gain & loss
        avg_gain = gain.ewm(com=period - 1, min_periods=period).mean()
        avg_loss = loss.ewm(com=period - 1, min_periods=period).mean()

        rs = avg_gain / avg_loss
        rsi = 100 - (100 / (1 + rs))
        return rsi

    def calculate_indicators(self, df: pd.DataFrame) -> pd.DataFrame:
        """
        Calculates indicators based on requirements:
        - 50 SMA (Moving Average)
        - 200 SMA (Moving Average)
        - 14 RSI (Relative Strength Index)
        """
        df = df.copy()
        df['sma_50'] = df['close'].rolling(window=50).mean()
        df['sma_200'] = df['close'].rolling(window=200).mean()
        df['rsi_14'] = self.calculate_rsi(df['close'], period=14)
        return df

    # =========================================================================
    #                        TRADING STRATEGY LOGIC
    # =========================================================================
    def calculate_signals(self, df: pd.DataFrame) -> Tuple[Optional[str], Dict[str, Any]]:
        """
        Strategy Execution Rules (Optimized with Gemini News Sentiment analysis):
        - Base Buy signal: RSI is below 30 AND 50 MA crosses above 200 MA.
        - Base Sell signal: RSI is above 70 OR 50 MA crosses below 200 MA.
        
        The RSI thresholds are adjusted dynamically:
        - buy_rsi_threshold = 30 + (sentiment_score * 10)
        - sell_rsi_threshold = 70 + (sentiment_score * 10)
        """
        if len(df) < 202:
            return None, {"error": "Not enough candle periods to calculate SMA 50/200."}

        # Dynamically refresh cryptocurrency market news sentiment from Gemini API (every 1 hour)
        current_time = datetime.now()
        if (current_time - self.last_sentiment_update).total_seconds() > 3600 or self.last_sentiment_update == datetime.min:
            try:
                from sentiment_analyzer import analyze_market_news_sentiment
                logger.info(f"Triggering scheduled news headlines sentiment analysis for {self.symbol}...")
                score, justification = analyze_market_news_sentiment(self.symbol)
                self.cached_sentiment_score = score
                self.cached_sentiment_justification = justification
                self.last_sentiment_update = current_time
            except Exception as e:
                logger.error(f"Failed to analyze market news sentiment via Gemini: {e}")

        # Calculate sentiment adjusted thresholds
        # Highly Bullish (+1.0) -> Buy RSI threshold 40 (easier to buy), Sell RSI threshold 80 (let profits run)
        # Highly Bearish (-1.0) -> Buy RSI threshold 20 (extremely strict oversold), Sell RSI threshold 60 (sell sooner)
        buy_rsi_threshold = round(30.0 + (self.cached_sentiment_score * 10.0), 1)
        sell_rsi_threshold = round(70.0 + (self.cached_sentiment_score * 10.0), 1)

        # Retrieve historical indicator steps for crossover checks
        sma_50_curr = df['sma_50'].iloc[-1]
        sma_200_curr = df['sma_200'].iloc[-1]
        sma_50_prev = df['sma_50'].iloc[-2]
        sma_200_prev = df['sma_200'].iloc[-2]
        
        rsi_curr = df['rsi_14'].iloc[-1]
        current_close = df['close'].iloc[-1]

        # Determine Golden/Death Crosses
        bullish_crossover = (sma_50_prev <= sma_200_prev) and (sma_50_curr > sma_200_curr)
        bearish_crossover = (sma_50_prev >= sma_200_prev) and (sma_50_curr < sma_200_curr)

        action = None
        metrics = {
            "close": current_close,
            "rsi_14": rsi_curr,
            "sma_50": sma_50_curr,
            "sma_200": sma_200_curr,
            "bullish_cross": bullish_crossover,
            "bearish_cross": bearish_crossover,
            "sentiment_score": self.cached_sentiment_score,
            "sentiment_justification": self.cached_sentiment_justification,
            "buy_rsi_threshold": buy_rsi_threshold,
            "sell_rsi_threshold": sell_rsi_threshold
        }

        # BUY Signal Logic: RSI < buy_rsi_threshold and 50 MA crossed above 200 MA (Golden Cross)
        if rsi_curr < buy_rsi_threshold and bullish_crossover:
            action = "BUY"
            logger.info(f"==> BUY SIGNAL GENERATED! RSI {rsi_curr:.2f} is below adjusted threshold {buy_rsi_threshold:.1f} (Sentiment: {self.cached_sentiment_score:+.2f}) and Golden Cross occurred.")
            self._send_desktop_notification(
                title=f"🚨 BUY SIGNAL - {self.symbol}",
                message=f"RSI is {rsi_curr:.2f} (Oversold adjusted: < {buy_rsi_threshold:.1f}) and Golden Cross occurred. Sentiment: {self.cached_sentiment_score:+.2f}."
            )
        
        # SELL Signal Logic: RSI > sell_rsi_threshold or 50 MA crossed below 200 MA (Death Cross)
        elif rsi_curr > sell_rsi_threshold or bearish_crossover:
            action = "SELL"
            logger.info(f"==> SELL SIGNAL GENERATED! RSI {rsi_curr:.2f} is above adjusted threshold {sell_rsi_threshold:.1f} or Death Cross occurred. Sentiment: {self.cached_sentiment_score:+.2f}.")
            self._send_desktop_notification(
                title=f"⚠️ SELL SIGNAL - {self.symbol}",
                message=f"RSI is {rsi_curr:.2f} (Overbought adjusted: > {sell_rsi_threshold:.1f}) or Death Cross occurred. Sentiment: {self.cached_sentiment_score:+.2f}."
            )

        return action, metrics

    def detect_fvg(self, candles: Any) -> str:
        """
        Analyzes the last 3 closed OHLCV candlestick data arrays/DataFrame to detect Fair Value Gaps (FVG).
        Returns "BULLISH", "BEARISH", or "NONE".
        """
        if candles is None:
            return "NONE"

        try:
            # Handle pandas DataFrame
            if hasattr(candles, "iloc"):
                if len(candles) < 3:
                    return "NONE"
                candle_1_high = float(candles['high'].iloc[-3])
                candle_1_low = float(candles['low'].iloc[-3])
                candle_3_high = float(candles['high'].iloc[-1])
                candle_3_low = float(candles['low'].iloc[-1])
            # Handle list of lists/tuples
            elif isinstance(candles, list) or isinstance(candles, np.ndarray):
                if len(candles) < 3:
                    return "NONE"
                # OHLCV format: [timestamp, open, high, low, close, volume]
                # Index 2 is High, Index 3 is Low
                candle_1 = candles[-3]
                candle_3 = candles[-1]
                candle_1_high = float(candle_1[2])
                candle_1_low = float(candle_1[3])
                candle_3_high = float(candle_3[2])
                candle_3_low = float(candle_3[3])
            else:
                return "NONE"

            if candle_3_low > candle_1_high:
                return "BULLISH"
            elif candle_3_high < candle_1_low:
                return "BEARISH"
        except Exception as e:
            logger.warning(f"Error while detecting Fair Value Gap (FVG): {e}")

        return "NONE"

    def calculate_confidence_score(self, rsi: float, short_ma: float, long_ma: float, direction: str = "BUY", candles: Any = None, weights: Optional[Dict[str, float]] = None) -> float:
        """
        Calculates a modular confidence score out of 100 based on RSI, Moving Averages, FVG, and Order Blocks.
        Accepts dynamically adjusted weights from Gemini. Fallbacks to static defaults.
        Incorporates real-time manual override multiplier to dampen or amplify AI influence.
        """
        # Define base/static weight defaults
        static_defaults = {"rsi": 40.0, "ma": 60.0, "fvg": 20.0, "ob": 20.0}
        if weights is None:
            weights = static_defaults

        # Load real-time multiplier from ai_config.json
        multiplier = 1.0
        try:
            if os.path.exists("ai_config.json"):
                with open("ai_config.json", "r") as f:
                    import json
                    data = json.load(f)
                    multiplier = float(data.get("multiplier", 1.0))
        except Exception as config_err:
            pass

        # Apply multiplier to blend between static defaults and dynamic AI weights:
        # adjusted_weight = static_default + multiplier * (ai_suggested_weight - static_default)
        rsi_max = max(0.0, static_defaults["rsi"] + multiplier * (float(weights.get("rsi", 40.0)) - static_defaults["rsi"]))
        ma_max = max(0.0, static_defaults["ma"] + multiplier * (float(weights.get("ma", 60.0)) - static_defaults["ma"]))
        fvg_max = max(0.0, static_defaults["fvg"] + multiplier * (float(weights.get("fvg", 20.0)) - static_defaults["fvg"]))
        ob_max = max(0.0, static_defaults["ob"] + multiplier * (float(weights.get("ob", 20.0)) - static_defaults["ob"]))

        logger.info(f"AI Influence Multiplier applied: {multiplier:.2f}x. Final Weights: RSI={rsi_max:.1f}, MA={ma_max:.1f}, FVG={fvg_max:.1f}, OB={ob_max:.1f}")

        rsi_points = 0.0
        ma_points = 0.0
        fvg_points = 0.0
        ob_points = 0.0
        dir_upper = direction.upper()

        if dir_upper == "BUY":
            # RSI Logic
            if rsi <= 30:
                rsi_points = rsi_max
            elif 30 < rsi < 50:
                rsi_points = rsi_max * (50.0 - rsi) / 20.0
            else:
                rsi_points = 0.0
            
            # Moving Average Logic
            if short_ma > long_ma:
                ma_points = ma_max
            else:
                ma_points = 0.0

            # FVG Logic
            if candles is not None:
                fvg_status = self.detect_fvg(candles)
                if fvg_status == "BULLISH":
                    fvg_points = fvg_max
                    logger.info(f"Bullish Fair Value Gap (FVG) detected! Adding {fvg_max} confidence points.")
                
            # Order Block Logic
            if candles is not None:
                try:
                    from strategy_engine import detect_order_block
                    bull_ob, _ = detect_order_block(candles)
                    if bull_ob is not None:
                        ob_low, ob_high = bull_ob
                        print(f"[OB DETECTED] Valid Bullish OB Zone: [{ob_low:.2f} - {ob_high:.2f}]")
                        logger.info(f"Valid Bullish OB Zone detected: [{ob_low:.2f} - {ob_high:.2f}]")
                        
                        # Extract current price
                        current_price = None
                        if hasattr(candles, "iloc"):
                            current_price = float(candles['close'].iloc[-1])
                        elif isinstance(candles, list) or isinstance(candles, np.ndarray):
                            current_price = float(candles[-1][4])
                            
                        if current_price is not None and ob_low <= current_price <= ob_high:
                            ob_points = ob_max
                            print(f"[OB MITIGATION] Price ${current_price:.2f} tests and mitigates Bullish OB! Adding {ob_max} points.")
                            logger.info(f"Price ${current_price:.2f} tests and mitigates Bullish OB! Adding {ob_max} points.")
                except Exception as ex:
                    logger.warning(f"Error checking Order Block mitigation: {ex}")
                
        elif dir_upper == "SELL":
            # RSI Logic
            if rsi >= 70:
                rsi_points = rsi_max
            elif 50 < rsi < 70:
                rsi_points = rsi_max * (rsi - 50.0) / 20.0
            else:
                rsi_points = 0.0
                
            # Moving Average Logic
            if short_ma < long_ma:
                ma_points = ma_max
            else:
                ma_points = 0.0

            # FVG Logic
            if candles is not None:
                fvg_status = self.detect_fvg(candles)
                if fvg_status == "BEARISH":
                    fvg_points = fvg_max
                    logger.info(f"Bearish Fair Value Gap (FVG) detected! Adding {fvg_max} confidence points.")

            # Order Block Logic
            if candles is not None:
                try:
                    from strategy_engine import detect_order_block
                    _, bear_ob = detect_order_block(candles)
                    if bear_ob is not None:
                        ob_low, ob_high = bear_ob
                        print(f"[OB DETECTED] Valid Bearish OB Zone: [{ob_low:.2f} - {ob_high:.2f}]")
                        logger.info(f"Valid Bearish OB Zone detected: [{ob_low:.2f} - {ob_high:.2f}]")
                        
                        # Extract current price
                        current_price = None
                        if hasattr(candles, "iloc"):
                            current_price = float(candles['close'].iloc[-1])
                        elif isinstance(candles, list) or isinstance(candles, np.ndarray):
                            current_price = float(candles[-1][4])
                            
                        if current_price is not None and ob_low <= current_price <= ob_high:
                            ob_points = ob_max
                            print(f"[OB MITIGATION] Price ${current_price:.2f} tests and mitigates Bearish OB! Adding {ob_max} points.")
                            logger.info(f"Price ${current_price:.2f} tests and mitigates Bearish OB! Adding {ob_max} points.")
                except Exception as ex:
                    logger.warning(f"Error checking Order Block mitigation: {ex}")

        score = rsi_points + ma_points + fvg_points + ob_points
        # Cap the total score at 100.0 to strictly conform to "returns a score out of 100"
        return float(round(min(score, 100.0), 2))

    # =========================================================================
    #                       RISK MANAGEMENT ENGINE
    # =========================================================================
    def calculate_position_size(self, current_price: float, stop_loss_price: float) -> float:
        """
        Calculates position size based on account balance, risk tolerance, and distance to Stop-Loss.
        Risks exactly X% of total account equity/balance.
        """
        try:
            # 1. Fetch balance from the exchange
            if self.exchange:
                balance_data = self.exchange.fetch_balance()
                # Assuming USD/USDT quotes
                total_balance = balance_data['total'].get('USDT', 10000.0)
            else:
                total_balance = 10000.0 # Mock default balance
                
            # 2. Risk Amount (USDT)
            risk_usd = total_balance * self.risk_per_trade_pct
            
            # 3. Stop loss distance per unit asset
            risk_per_asset = abs(current_price - stop_loss_price)
            
            if risk_per_asset == 0:
                return 0.0
                
            # 4. Total units to purchase
            position_size = risk_usd / risk_per_asset
            logger.info(f"Risk Sizing: Account Balance = ${total_balance:.2f} | Risk Amount = ${risk_usd:.2f} | Target Position Size = {position_size:.6f} {self.symbol.split('/')[0]}")
            return position_size
        except Exception as e:
            logger.error(f"Error calculating position size: {e}. Falling back to minimal trade unit.")
            return 0.01

    # =========================================================================
    #                     ORDER EXECUTION GATEWAY
    # =========================================================================
    def set_futures_leverage(self, symbol: str, leverage: int = 10) -> Optional[Dict[str, Any]]:
        """
        Dynamically sets the initial leverage for the specified symbol on Binance Futures.
        """
        if not self.binance_client:
            logger.warning("Binance Client is not initialized. Cannot set leverage.")
            return None

        binance_symbol = symbol.replace("/", "").upper()
        try:
            logger.info(f"Setting leverage for {binance_symbol} to {leverage}x...")
            response = self.binance_client.futures_change_leverage(
                symbol=binance_symbol,
                leverage=leverage,
                recvWindow=60000
            )
            logger.info(f"[SUCCESS] Leverage set successfully for {binance_symbol}: {response}")
            return response
        except Exception as e:
            logger.error(f"[ERROR] Failed to set futures leverage for {binance_symbol}: {e}")
            return None

    def execute_market_order(self, symbol: str, side: str, quantity: float) -> Optional[Dict[str, Any]]:
        """
        Safely executes a USD(S)-M Futures market BUY or SELL order on Binance Futures Testnet.
        """
        if not self.binance_client:
            logger.error("Binance Client is not initialized. Cannot execute market order.")
            return None

        # Format symbol to uppercase and remove slashes (e.g., BTC/USDT -> BTCUSDT)
        binance_symbol = symbol.replace("/", "").upper()
        side_upper = side.upper()

        logger.info(f"Initiating direct Binance Futures Testnet market {side_upper} order for {quantity} {binance_symbol}...")

        try:
            # Re-sync server time offset to prevent timestamp errors
            try:
                server_time = self.binance_client.futures_time()
                server_time_ms = server_time.get("serverTime")
                local_time_ms = int(time.time() * 1000)
                self.binance_client.timestamp_offset = server_time_ms - local_time_ms
            except Exception as sync_err:
                logger.debug(f"Optional futures server time synchronization failed: {sync_err}")

            if side_upper == "BUY":
                order = self.binance_client.futures_create_order(
                    symbol=binance_symbol,
                    side="BUY",
                    type="MARKET",
                    quantity=quantity,
                    recvWindow=60000
                )
            elif side_upper == "SELL":
                order = self.binance_client.futures_create_order(
                    symbol=binance_symbol,
                    side="SELL",
                    type="MARKET",
                    quantity=quantity,
                    recvWindow=60000
                )
            else:
                logger.error(f"Unsupported order side: {side}")
                return None

            logger.info(f"[SUCCESS] Binance Futures Testnet market {side_upper} order executed. Order details: {order}")
            return order

        except BinanceAPIException as e:
            logger.error(f"[ERROR] Binance API Exception occurred during direct Futures market order execution:")
            logger.error(f"  - Code: {e.code if hasattr(e, 'code') else e.status_code}")
            logger.error(f"  - Message: {e.message}")
            return None
        except Exception as e:
            logger.error(f"[ERROR] Unexpected exception occurred during direct Futures market order execution: {e}")
            return None

    def execute_order(self, action: str, amount: float, price: float) -> Optional[Dict[str, Any]]:
        """
        Performs actual order placement to exchange via Market / Limit configurations.
        Uses fallback mock triggers if API keys are not supplied.
        """
        if amount <= 0:
            logger.warning(f"Execution skipped: Invalid order amount calculated ({amount}).")
            return None

        # Clean/Format Symbol for CCXT
        symbol = self.symbol
        
        logger.info(f"Initiating {action} order for {amount:.6f} {symbol} at market price (~${price:.2f})...")
        
        if not self.exchange or (not self.api_key and not self.sandbox_mode):
            # Fallback Mock Order Execution
            mock_order = {
                "id": f"mock_order_{int(time.time())}",
                "timestamp": int(time.time() * 1000),
                "datetime": datetime.utcnow().isoformat(),
                "symbol": symbol,
                "type": "market",
                "side": action.lower(),
                "price": price,
                "amount": amount,
                "cost": amount * price,
                "status": "closed"
            }
            logger.info(f"[MOCK ENGINE] Execution Successful. Order ID: {mock_order['id']} | Total Cost: ${mock_order['cost']:.4f}")
            return mock_order

        # Live / Sandbox Execution with full Error Isolation
        try:
            if action.upper() == "BUY":
                order = self.exchange.create_market_buy_order(symbol, amount)
            else:
                order = self.exchange.create_market_sell_order(symbol, amount)
                
            logger.info(f"[{self.exchange_id.upper()}] Order completed successfully! ID: {order.get('id')} | Status: {order.get('status')}")
            return order
        except ccxt.InsufficientFunds as e:
            logger.error(f"Order failed due to insufficient funds: {e}")
        except ccxt.InvalidOrder as e:
            logger.error(f"Order criteria invalid: {e}")
        except ccxt.NetworkError as e:
            logger.error(f"Exchange network communication timed out: {e}")
        except Exception as e:
            logger.error(f"Unknown exception encountered during order execution: {e}")
        return None

    # =========================================================================
    #                         BOT LIFECYCLE LOOP
    # =========================================================================
    def run_cycle(self):
        """
        A single execution tick: checks indicators, runs strategy, evaluates active stops,
        and triggers actions.
        """
        logger.info(f"--- Trading Engine Cycle Started: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')} ---")
        
        try:
            # 1. Retrieve Historical Candles
            if self.exchange:
                logger.info(f"Fetching candles for {self.symbol} on {self.timeframe} timeframe...")
                import time
                start_time = time.time()
                try:
                    ohlcv = self.exchange.fetch_ohlcv(self.symbol, timeframe=self.timeframe, limit=250)
                except Exception as ex_err:
                    logger.warning(f"Failed to fetch live OHLCV data from Binance CCXT: {ex_err}.")
                    raise ex_err
                
                latency = (time.time() - start_time) * 1000.0
                logger.info(f"Binance API Call (fetch_ohlcv) round-trip latency: {latency:.1f}ms")
                if latency > 3000.0:
                    logger.error(f"CRITICAL SAFETY EVENT: Binance API latency of {latency:.1f}ms exceeds safety threshold of 3000ms. Aborting trading cycle to avoid executing stale signals.")
                    return
                
                df = pd.DataFrame(ohlcv, columns=['timestamp', 'open', 'high', 'low', 'close', 'volume'])
                df['timestamp'] = pd.to_datetime(df['timestamp'], unit='ms')
            else:
                # Mock Data Generation if no CCXT Client
                logger.warning("No active exchange client. Simulating real-time market candlesticks...")
                times = pd.date_range(end=datetime.now(), periods=250, freq='15min')
                # Walk with moving averages
                prices = np.sin(np.linspace(0, 10, 250)) * 500 + 30000 + np.random.normal(0, 80, 250)
                df = pd.DataFrame({
                    'timestamp': times,
                    'open': prices - np.random.uniform(10, 30, 250),
                    'high': prices + np.random.uniform(30, 60, 250),
                    'low': prices - np.random.uniform(30, 60, 250),
                    'close': prices,
                    'volume': np.random.uniform(10, 100, 250)
                })

            # 2. Compute Technical Indicators
            df = self.calculate_indicators(df)

            # Compute dynamic weights using Gemini AI Co-pilot
            weights = None
            try:
                from strategy_engine import get_ai_dynamic_weights
                # Extract volatility and trend strength
                last_closes = df['close'].tail(15)
                volatility = last_closes.pct_change().std() * 100
                current_price = df['close'].iloc[-1]
                sma_50 = df['sma_50'].iloc[-1] if 'sma_50' in df.columns else df['close'].mean()
                trend_strength = abs(current_price - sma_50) / sma_50 * 100
                
                market_summary_data = {
                    "symbol": self.symbol,
                    "current_price": float(current_price),
                    "volatility_std": float(volatility) if not pd.isna(volatility) else 0.0,
                    "trend_strength_pct": float(trend_strength),
                    "rsi_14": float(df['rsi_14'].iloc[-1]) if 'rsi_14' in df.columns else 50.0
                }
                
                logger.info("Requesting dynamically adjusted weights from Gemini AI Co-pilot...")
                weights = get_ai_dynamic_weights(market_summary_data)
                logger.info(f"Received dynamically adjusted weights: {weights}")
            except Exception as gemini_err:
                logger.warning(f"Could not calculate AI dynamic weights: {gemini_err}. Defaulting to static weights.")
            
            # 3. Determine Trading Signals
            action, metrics = self.calculate_signals(df)
            current_price = metrics["close"]
            
            logger.info(f"Current Metrics: Price = ${current_price:,.2f} | RSI(14) = {metrics['rsi_14']:.2f} | SMA50 = ${metrics['sma_50']:,.2f} | SMA200 = ${metrics['sma_200']:,.2f}")

            # 4. Risk Management Check for Open Positions
            if self.active_position:
                entry_price = self.active_position["entry_price"]
                position_size = self.active_position["amount"]
                stop_loss = self.active_position["stop_loss"]
                take_profit = self.active_position["take_profit"]

                logger.info(f"Active Position Status: Entry = ${entry_price:,.2f} | Stop Loss = ${stop_loss:,.2f} | Take Profit = ${take_profit:,.2f}")

                # Check Stop-Loss Breach
                if current_price <= stop_loss:
                    logger.warning(f"Stop-Loss breached at ${current_price:,.2f} (Limit: ${stop_loss:,.2f})!")
                    if self.binance_client:
                        order = self.execute_market_order(self.symbol, "SELL", position_size)
                    else:
                        order = self.execute_order("SELL", position_size, current_price)
                    if order:
                        # Extract exact execution details
                        exec_price = current_price
                        exec_qty = position_size
                        if self.binance_client:
                            fills = order.get("fills", [])
                            if fills:
                                total_qty = sum(float(f["qty"]) for f in fills)
                                if total_qty > 0:
                                    exec_price = sum(float(f["price"]) * float(f["qty"]) for f in fills) / total_qty
                                    exec_qty = total_qty
                            elif "price" in order and float(order["price"]) > 0:
                                exec_price = float(order["price"])
                            if "executedQty" in order:
                                exec_qty = float(order["executedQty"])
                        else:
                            exec_price = order.get("price", current_price) or current_price
                            exec_qty = order.get("amount", position_size) or position_size

                        pnl = (exec_price - entry_price) * exec_qty
                        pnl_pct = ((exec_price - entry_price) / entry_price) * 100.0
                        logger.info(f"Position Liquidation Logged (Stop-Loss). Profit/Loss: ${pnl:+.4f} USDT")
                        self.active_position = None
                        self._save_bot_state_to_db(is_in_position=False)
                        self._log_trade_to_db(
                            side="SELL",
                            price=exec_price,
                            amount=exec_qty,
                            cost=exec_qty * exec_price,
                            pnl=pnl,
                            pnl_pct=pnl_pct
                        )
                        self._send_desktop_notification(
                            title=f"🛑 STOP-LOSS BREACHED - {self.symbol}",
                            message=f"Stop-Loss triggered at ${exec_price:,.2f}. Position of {exec_qty} units closed. PnL: {pnl_pct:+.2f}%."
                        )
                        
                # Check Take-Profit Breach
                elif current_price >= take_profit:
                    logger.info(f"Take-Profit target achieved at ${current_price:,.2f} (Target: ${take_profit:,.2f})!")
                    if self.binance_client:
                        order = self.execute_market_order(self.symbol, "SELL", position_size)
                    else:
                        order = self.execute_order("SELL", position_size, current_price)
                    if order:
                        # Extract exact execution details
                        exec_price = current_price
                        exec_qty = position_size
                        if self.binance_client:
                            fills = order.get("fills", [])
                            if fills:
                                total_qty = sum(float(f["qty"]) for f in fills)
                                if total_qty > 0:
                                    exec_price = sum(float(f["price"]) * float(f["qty"]) for f in fills) / total_qty
                                    exec_qty = total_qty
                            elif "price" in order and float(order["price"]) > 0:
                                exec_price = float(order["price"])
                            if "executedQty" in order:
                                exec_qty = float(order["executedQty"])
                        else:
                            exec_price = order.get("price", current_price) or current_price
                            exec_qty = order.get("amount", position_size) or position_size

                        pnl = (exec_price - entry_price) * exec_qty
                        pnl_pct = ((exec_price - entry_price) / entry_price) * 100.0
                        logger.info(f"Position Liquidation Logged (Take-Profit). Profit/Loss: ${pnl:+.4f} USDT")
                        self.active_position = None
                        self._save_bot_state_to_db(is_in_position=False)
                        self._log_trade_to_db(
                            side="SELL",
                            price=exec_price,
                            amount=exec_qty,
                            cost=exec_qty * exec_price,
                            pnl=pnl,
                            pnl_pct=pnl_pct
                        )
                        self._send_desktop_notification(
                            title=f"🎯 TAKE-PROFIT ACHIEVED - {self.symbol}",
                            message=f"Take-Profit triggered at ${exec_price:,.2f}. Position of {exec_qty} units closed. PnL: {pnl_pct:+.2f}%."
                        )
                
                # Check Strategy Liquidation Signal (Death Cross or Overbought RSI)
                elif action == "SELL":
                    # Calculate Bearish Confidence Score
                    rsi_val = metrics.get("rsi_14", 50.0)
                    short_ma_val = metrics.get("sma_50", 0.0)
                    long_ma_val = metrics.get("sma_200", 0.0)
                    bearish_score = self.calculate_confidence_score(rsi_val, short_ma_val, long_ma_val, direction="SELL", candles=df, weights=weights)
                    
                    print(f"\n[SCORE] Calculated Bearish (Sell) Confidence Score: {bearish_score:.2f}/100")
                    logger.info(f"Calculated Bearish (Sell) Confidence Score: {bearish_score:.2f}/100")

                    if bearish_score < 80.0:
                        logger.info(f"Bypassing proactive strategy SELL signal because bearish confidence score ({bearish_score:.2f}) is below the required 80.0 threshold.")
                    else:
                        logger.info(f"Strategy triggered proactive exit signal at ${current_price:,.2f}!")
                        if self.binance_client:
                            order = self.execute_market_order(self.symbol, "SELL", position_size)
                        else:
                            order = self.execute_order("SELL", position_size, current_price)
                        if order:
                            # Extract exact execution details
                            exec_price = current_price
                            exec_qty = position_size
                            if self.binance_client:
                                fills = order.get("fills", [])
                                if fills:
                                    total_qty = sum(float(f["qty"]) for f in fills)
                                    if total_qty > 0:
                                        exec_price = sum(float(f["price"]) * float(f["qty"]) for f in fills) / total_qty
                                        exec_qty = total_qty
                                elif "price" in order and float(order["price"]) > 0:
                                    exec_price = float(order["price"])
                                if "executedQty" in order:
                                    exec_qty = float(order["executedQty"])
                            else:
                                exec_price = order.get("price", current_price) or current_price
                                exec_qty = order.get("amount", position_size) or position_size

                            pnl = (exec_price - entry_price) * exec_qty
                            pnl_pct = ((exec_price - entry_price) / entry_price) * 100.0
                            logger.info(f"Proactive Strategy Liquidation Logged. Profit/Loss: ${pnl:+.4f} USDT")
                            self.active_position = None
                            self._save_bot_state_to_db(is_in_position=False)
                            self._log_trade_to_db(
                                side="SELL",
                                price=exec_price,
                                amount=exec_qty,
                                cost=exec_qty * exec_price,
                                pnl=pnl,
                                pnl_pct=pnl_pct
                            )
                            self._send_desktop_notification(
                                title=f"⚠️ SELL SIGNAL EXECUTED - {self.symbol}",
                                message=f"Direct Binance Market SELL order of {exec_qty} {self.symbol} executed successfully at ~${exec_price:,.2f}. PnL: {pnl_pct:+.2f}%."
                            )

            # 5. Process New Signals (Only if not already in position)
            else:
                if action == "BUY":
                    # Calculate Bullish Confidence Score
                    rsi_val = metrics.get("rsi_14", 50.0)
                    short_ma_val = metrics.get("sma_50", 0.0)
                    long_ma_val = metrics.get("sma_200", 0.0)
                    confidence_score = self.calculate_confidence_score(rsi_val, short_ma_val, long_ma_val, direction="BUY", candles=df, weights=weights)
                    
                    print(f"\n[SCORE] Calculated Buy Confidence Score: {confidence_score:.2f}/100")
                    logger.info(f"Calculated Buy Confidence Score: {confidence_score:.2f}/100")

                    if confidence_score < 80.0:
                        logger.info(f"Bypassing BUY signal because confidence score ({confidence_score:.2f}) is below the required 80.0 threshold.")
                    else:
                        # Compute SL and TP thresholds
                        stop_loss_price = current_price * (1 - self.stop_loss_pct)
                        take_profit_price = current_price * (1 + self.take_profit_pct)
                        
                        if self.binance_client:
                            # Direct Binance Testnet Buy (buy a small fixed quantity, e.g., 0.01)
                            size = 0.01
                            order = self.execute_market_order(self.symbol, "BUY", size)
                        else:
                            # Fallback to CCXT or Mock order with risk-managed size
                            size = self.calculate_position_size(current_price, stop_loss_price)
                            order = self.execute_order("BUY", size, current_price)

                        if order:
                            # Extract exact execution details
                            exec_price = current_price
                            exec_qty = size
                            if self.binance_client:
                                fills = order.get("fills", [])
                                if fills:
                                    total_qty = sum(float(f["qty"]) for f in fills)
                                    if total_qty > 0:
                                        exec_price = sum(float(f["price"]) * float(f["qty"]) for f in fills) / total_qty
                                        exec_qty = total_qty
                                elif "price" in order and float(order["price"]) > 0:
                                    exec_price = float(order["price"])
                                if "executedQty" in order:
                                    exec_qty = float(order["executedQty"])
                            else:
                                exec_price = order.get("price", current_price) or current_price
                                exec_qty = order.get("amount", size) or size

                            # Re-calculate SL/TP thresholds based on actual execution price
                            stop_loss_price = exec_price * (1 - self.stop_loss_pct)
                            take_profit_price = exec_price * (1 + self.take_profit_pct)

                            self.active_position = {
                                "entry_price": exec_price,
                                "amount": exec_qty,
                                "stop_loss": stop_loss_price,
                                "take_profit": take_profit_price,
                                "timestamp": datetime.now()
                            }
                            logger.info(f"Position initialized and recorded securely in memory.")
                            self._save_bot_state_to_db(
                                is_in_position=True,
                                entry_price=exec_price,
                                amount=exec_qty,
                                stop_loss=stop_loss_price,
                                take_profit=take_profit_price
                            )
                            self._log_trade_to_db(
                                side="BUY",
                                price=exec_price,
                                amount=exec_qty,
                                cost=exec_qty * exec_price
                            )
                            self._send_desktop_notification(
                                title=f"🚨 BUY SIGNAL EXECUTED - {self.symbol}",
                                message=f"Direct Binance Market BUY order of {exec_qty} {self.symbol} executed successfully at ~${exec_price:,.2f}."
                            )

        except Exception as e:
            logger.error(f"Severe error in trading cycle execution: {e}", exc_info=True)

    def _handle_entry_order_response(self, order: Dict[str, Any], size: float, closed_price: float):
        exec_price = closed_price
        exec_qty = size
        
        # Check order response fills
        fills = order.get("fills", [])
        if fills:
            total_qty = sum(float(f["qty"]) for f in fills)
            if total_qty > 0:
                exec_price = sum(float(f["price"]) * float(f["qty"]) for f in fills) / total_qty
                exec_qty = total_qty
        elif "price" in order and float(order["price"]) > 0:
            exec_price = float(order["price"])
        elif "avgPrice" in order and float(order["avgPrice"]) > 0:
            exec_price = float(order["avgPrice"])
            
        if "executedQty" in order:
            exec_qty = float(order["executedQty"])
            
        stop_loss_price = exec_price * (1 - self.stop_loss_pct)
        take_profit_price = exec_price * (1 + self.take_profit_pct)
        
        self.active_position = {
            "entry_price": exec_price,
            "amount": exec_qty,
            "stop_loss": stop_loss_price,
            "take_profit": take_profit_price,
            "timestamp": datetime.now()
        }
        
        self._save_bot_state_to_db(
            is_in_position=True,
            entry_price=exec_price,
            amount=exec_qty,
            stop_loss=stop_loss_price,
            take_profit=take_profit_price
        )
        self._log_trade_to_db(
            side="BUY",
            price=exec_price,
            amount=exec_qty,
            cost=exec_qty * exec_price
        )
        print(f"\n[SUCCESS] Position opened successfully: {exec_qty} {self.symbol} at ${exec_price:,.2f} | SL=${stop_loss_price:,.2f} | TP=${take_profit_price:,.2f}")
        
        self._send_desktop_notification(
            title=f"🚨 FUTURES LONG OPENED - {self.symbol}",
            message=f"Binance Futures Market BUY of {exec_qty} {self.symbol} executed successfully at ~${exec_price:,.2f}."
        )

    def _handle_liquidation_order_response(self, order: Dict[str, Any], entry_price: float, position_size: float, current_price: float, reason: str = "STRATEGY"):
        exec_price = current_price
        exec_qty = position_size
        
        fills = order.get("fills", [])
        if fills:
            total_qty = sum(float(f["qty"]) for f in fills)
            if total_qty > 0:
                exec_price = sum(float(f["price"]) * float(f["qty"]) for f in fills) / total_qty
                exec_qty = total_qty
        elif "price" in order and float(order["price"]) > 0:
            exec_price = float(order["price"])
        elif "avgPrice" in order and float(order["avgPrice"]) > 0:
            exec_price = float(order["avgPrice"])
            
        if "executedQty" in order:
            exec_qty = float(order["executedQty"])
            
        pnl = (exec_price - entry_price) * exec_qty
        pnl_pct = ((exec_price - entry_price) / entry_price) * 100.0
        
        logger.info(f"Futures Position Liquidated ({reason}). Profit/Loss: ${pnl:+.4f} USDT")
        print(f"\n[LIQUIDATED] Position Closed ({reason})! Exec Price: ${exec_price:,.2f} | PnL: ${pnl:+.4f} USDT ({pnl_pct:+.2f}%)")
        
        self.active_position = None
        self._save_bot_state_to_db(is_in_position=False)
        self._log_trade_to_db(
            side="SELL",
            price=exec_price,
            amount=exec_qty,
            cost=exec_qty * exec_price,
            pnl=pnl,
            pnl_pct=pnl_pct
        )
        
        self._send_desktop_notification(
            title=f"⚠️ FUTURES LONG CLOSED - {self.symbol}",
            message=f"Binance Futures Market SELL of {exec_qty} {self.symbol} executed successfully at ~${exec_price:,.2f}. PnL: {pnl_pct:+.2f}%."
        )
        if pnl < 0:
            self.alert_service.check_and_notify_loss(self.symbol, pnl, pnl_pct)

    def broadcast_state(self, current_price, rsi=None, sma_50=None, sma_200=None, bullish_score=None, bearish_score=None):
        """
        Sends the current state of the bot engine to the FastAPI backend API
        so it can be broadcast to WebSocket clients.
        """
        import requests
        url = "http://127.0.0.1:8000/api/bot/update"
        payload = {
            "symbol": self.symbol,
            "current_price": float(current_price),
            "is_in_position": bool(self.active_position is not None),
            "status": "IN POSITION" if self.active_position else "WAITING FOR SIGNAL",
            "rsi": float(rsi) if rsi is not None else getattr(self, '_last_rsi', 50.0),
            "sma_50": float(sma_50) if sma_50 is not None else getattr(self, '_last_sma_50', float(current_price)),
            "sma_200": float(sma_200) if sma_200 is not None else getattr(self, '_last_sma_200', float(current_price)),
            "bullish_score": float(bullish_score) if bullish_score is not None else getattr(self, '_last_bullish_score', 0.0),
            "bearish_score": float(bearish_score) if bearish_score is not None else getattr(self, '_last_bearish_score', 0.0),
            "timestamp": datetime.utcnow().isoformat() + "Z"
        }
        # Update cached last values
        if rsi is not None: self._last_rsi = float(rsi)
        if sma_50 is not None: self._last_sma_50 = float(sma_50)
        if sma_200 is not None: self._last_sma_200 = float(sma_200)
        if bullish_score is not None: self._last_bullish_score = float(bullish_score)
        if bearish_score is not None: self._last_bearish_score = float(bearish_score)
        
        try:
            requests.post(url, json=payload, timeout=0.5)
        except Exception:
            # Silently ignore connection errors when backend is not running yet
            pass

    def force_test_trade(self):
        """
        Executes a small force test Market BUY trade to verify connectivity and API key permissions.
        """
        logger.info("Executing Force Test Trade on Binance Futures Testnet...")
        print("\n" + "="*50)
        print("[TEST] EXECUTING FORCE TEST TRADE TO VERIFY API PERMISSIONS...")
        print("="*50)
        
        if not self.binance_client:
            logger.warning("Binance client not initialized. Skipping actual force test trade (running in dry/simulation mode).")
            print("[TEST WARNING] Binance client not initialized. Skipping real test trade execution.")
            print("="*50 + "\n")
            return
            
        try:
            # Set leverage to 10x before placing order just to be safe
            self.set_futures_leverage(self.symbol, leverage=10)
            
            # Place order with a small size of 0.001 BTC
            order = self.execute_market_order(self.symbol, "BUY", 0.001)
            if order:
                order_id = order.get("orderId", "N/A")
                status = order.get("status", "N/A")
                print(f"[TEST SUCCESS] Futures Market BUY executed successfully!")
                print(f" - Order ID: {order_id}")
                print(f" - Status: {status}")
                print(f" - Symbol: {order.get('symbol')}")
                print(f" - Executed Qty: {order.get('executedQty')}")
                print(f" - Avg Price: {order.get('avgPrice')}")
                print("="*50 + "\n")
            else:
                print("[TEST FAILED] Market order execution returned None. Check logs for details.")
                print("="*50 + "\n")
        except Exception as e:
            logger.error(f"Force test trade encountered an error: {e}", exc_info=True)
            print(f"[TEST ERROR] API/Permission Error occurred: {e}")
            print("="*50 + "\n")

    def start_live_futures_trading_loop(self, interval: str = "1m"):
        """
        Continuous loop that fetches real-time Kline/Candlestick data for BTCUSDT from Binance Futures Testnet.
        Evaluates trades when a new candle closes.
        """
        logger.info(f"Starting continuous Live Futures Trading Loop for {self.symbol} on {interval} interval...")
        print(f"\n[INIT] Starting continuous Live Futures Trading Loop for {self.symbol} ({interval})...")
        
        # Ensure leverage is set initially
        if self.binance_client:
            self.set_futures_leverage(self.symbol, leverage=10)
            
        # Execute the force test trade once to verify Futures API keys permissions
        self.force_test_trade()
        
        self.last_processed_candle_time = 0
        
        # Load active position if any
        self.active_position = self._load_bot_state_from_db()
        
        # Keep track of iteration limits for clean execution logs
        iteration = 0
        
        while True:
            try:
                binance_symbol = self.symbol.replace("/", "").upper()
                
                # Fetch recent klines from Binance Futures Testnet or simulate them
                if self.binance_client:
                    klines = self.binance_client.futures_klines(symbol=binance_symbol, interval=interval, limit=250)
                else:
                    # Mock Live Futures Candle stream to allow dry runs without keys
                    if not hasattr(self, 'mock_candles'):
                        self.mock_candles = []
                        base_price = 65000.0
                        start_t = int(time.time() * 1000) - 250 * 60 * 1000
                        for i in range(250):
                            t = start_t + i * 60 * 1000
                            base_price += np.random.normal(0, 50)
                            self.mock_candles.append([
                                t,
                                base_price - 10, # open
                                base_price + 25, # high
                                base_price - 25, # low
                                base_price,      # close
                                np.random.uniform(10, 100), # volume
                                t + 59999, # close_t
                                "quote", "trades", "taker_base", "taker_quote", "ignore"
                            ])
                    
                    current_t = int(time.time() * 1000)
                    last_mock_candle = self.mock_candles[-1]
                    if current_t - last_mock_candle[0] >= 60000:
                        new_open_time = last_mock_candle[0] + 60000
                        new_close = last_mock_candle[4] + np.random.normal(0, 30)
                        self.mock_candles.append([
                            new_open_time,
                            last_mock_candle[4],
                            new_close + np.random.uniform(10, 40),
                            new_close - np.random.uniform(10, 40),
                            new_close,
                            np.random.uniform(10, 100),
                            new_open_time + 59999,
                            "quote", "trades", "taker_base", "taker_quote", "ignore"
                        ])
                        if len(self.mock_candles) > 250:
                            self.mock_candles.pop(0)
                    else:
                        last_mock_candle[4] += np.random.normal(0, 5)
                        if last_mock_candle[4] > last_mock_candle[1]:
                            last_mock_candle[2] = max(last_mock_candle[2], last_mock_candle[4] + 5)
                        else:
                            last_mock_candle[3] = min(last_mock_candle[3], last_mock_candle[4] - 5)
                    
                    klines = self.mock_candles
                
                if not klines or len(klines) < 3:
                    logger.warning("Empty or insufficient klines received.")
                    time.sleep(5)
                    continue
                
                active_kline = klines[-1]
                current_price = float(active_kline[4])
                
                now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                # Print live price update on the same line to keep the terminal extremely clean
                print(f"\r[{now_str}] LIVE PRICE UPDATE: {binance_symbol} = ${current_price:,.2f} | Status: {'IN POSITION' if self.active_position else 'WAITING FOR SIGNAL'}", end="", flush=True)
                
                # Broadcast real-time price updates via WebSocket bridge
                self.broadcast_state(current_price)
                
                closed_kline = klines[-2]
                closed_candle_time = int(closed_kline[0])
                
                # Process the trade evaluation when a new candle closes
                if closed_candle_time > self.last_processed_candle_time:
                    is_first_run = (self.last_processed_candle_time == 0)
                    self.last_processed_candle_time = closed_candle_time
                    
                    if is_first_run:
                        logger.info(f"Initialized last processed candle time to {datetime.fromtimestamp(closed_candle_time/1000)}")
                        print(f"\n[SYSTEM] Live candle tracker synchronized. Last closed candle open time: {datetime.fromtimestamp(closed_candle_time/1000)}")
                    
                    print(f"\n\n[CANDLE CLOSED] New candle finalized at {datetime.fromtimestamp(closed_candle_time/1000)}")
                    logger.info(f"New candle closed. Processing trading strategies...")
                    
                    df = pd.DataFrame(klines[:-1], columns=[
                        'timestamp', 'open', 'high', 'low', 'close', 'volume',
                        'close_time', 'quote_asset_volume', 'number_of_trades',
                        'taker_buy_base_asset_volume', 'taker_buy_quote_asset_volume', 'ignore'
                    ])
                    df['timestamp'] = pd.to_datetime(df['timestamp'], unit='ms')
                    for col in ['open', 'high', 'low', 'close', 'volume']:
                        df[col] = df[col].astype(float)
                        
                    df = self.calculate_indicators(df)
                    
                    last_row = df.iloc[-1]
                    closed_price = float(last_row['close'])
                    rsi_val = float(last_row['rsi_14']) if 'rsi_14' in df.columns else 50.0
                    sma_50 = float(last_row['sma_50']) if 'sma_50' in df.columns else closed_price
                    sma_200 = float(last_row['sma_200']) if 'sma_200' in df.columns else closed_price
                    
                    # Compute dynamic weights using Gemini AI
                    weights = None
                    try:
                        from strategy_engine import get_ai_dynamic_weights
                        last_closes = df['close'].tail(15)
                        volatility = last_closes.pct_change().std() * 100
                        trend_strength = abs(closed_price - sma_50) / sma_50 * 100
                        
                        market_summary_data = {
                            "symbol": self.symbol,
                            "current_price": closed_price,
                            "volatility_std": float(volatility) if not pd.isna(volatility) else 0.0,
                            "trend_strength_pct": float(trend_strength),
                            "rsi_14": rsi_val
                        }
                        
                        logger.info("Requesting dynamically adjusted weights from Gemini AI Co-pilot...")
                        print("[GEMINI] Requesting dynamically adjusted weights for closed candle summary...")
                        weights = get_ai_dynamic_weights(market_summary_data)
                        logger.info(f"Received dynamically adjusted weights: {weights}")
                        print(f"[GEMINI] Adjusted weights received: {weights}")
                    except Exception as gemini_err:
                        logger.warning(f"Could not calculate AI dynamic weights: {gemini_err}. Defaulting to static weights.")
                        print(f"[GEMINI WARNING] Could not calculate dynamic weights: {gemini_err}. Fallback to static defaults.")
                        
                    # Determine Signals
                    action, metrics = self.calculate_signals(df)
                    print(f"[STRATEGY] Signals: action={action} | Metrics: Price=${closed_price:,.2f} | RSI={rsi_val:.1f} | SMA50=${sma_50:,.2f} | SMA200=${sma_200:,.2f}")
                    
                    bullish_score = 0.0
                    bearish_score = 0.0
                    
                    # 4. Check Risk Management if in Active Position
                    if self.active_position:
                        entry_price = self.active_position["entry_price"]
                        position_size = self.active_position["amount"]
                        stop_loss = self.active_position["stop_loss"]
                        take_profit = self.active_position["take_profit"]
                        
                        logger.info(f"Checking position: Entry=${entry_price:,.2f}, SL=${stop_loss:,.2f}, TP=${take_profit:,.2f}")
                        print(f"[POSITION] Monitoring Open Long Position: Entry=${entry_price:,.2f} | StopLoss=${stop_loss:,.2f} | TakeProfit=${take_profit:,.2f}")
                        
                        bearish_score = self.calculate_confidence_score(rsi_val, sma_50, sma_200, direction="SELL", candles=df, weights=weights)
                        print(f"[SCORE] Bearish (Exit) Confidence Score: {bearish_score:.2f}/100")
                        
                        if bearish_score >= 80.0:
                            logger.info(f"Strategy triggered proactive exit signal at ${closed_price:,.2f}!")
                            print(f"\n[EXIT SIGNAL] High bearish confidence score ({bearish_score:.2f}/100). Executing proactive market liquidation...")
                            
                            if self.binance_client:
                                order = self.execute_market_order(self.symbol, "SELL", position_size)
                            else:
                                order = self.execute_order("SELL", position_size, closed_price)
                                
                            if order:
                                self._handle_liquidation_order_response(order, entry_price, position_size, closed_price, reason="STRATEGY_SIGNAL")
                        else:
                            logger.info(f"Bearish confidence score ({bearish_score:.2f}) below required 80.0 exit threshold.")
                            
                    # 5. Process New Entry Signals (Only if not already in position)
                    else:
                        if action == "BUY":
                            confidence_score = self.calculate_confidence_score(rsi_val, sma_50, sma_200, direction="BUY", candles=df, weights=weights)
                            bullish_score = confidence_score
                            print(f"[SCORE] Calculated Bullish (Buy) Confidence Score: {confidence_score:.2f}/100")
                            logger.info(f"Calculated Buy Confidence Score: {confidence_score:.2f}/100")
                            
                            if confidence_score >= 80.0:
                                print(f"\n[ENTRY SIGNAL] High confidence BUY signal ({confidence_score:.2f}/100). Executing Market BUY...")
                                size = 0.01
                                
                                if self.binance_client:
                                    order = self.execute_market_order(self.symbol, "BUY", size)
                                else:
                                    order = self.execute_order("BUY", size, closed_price)
                                    
                                if order:
                                    self._handle_entry_order_response(order, size, closed_price)
                            else:
                                logger.info(f"Bypassing BUY signal because confidence score ({confidence_score:.2f}) is below 80.0 threshold.")
                                print(f"[STRATEGY] Bypassing BUY signal: confidence score ({confidence_score:.2f}) < 80.0")
                    
                    # Broadcast the completed strategy run with all calculated indicators and AI scores
                    self.broadcast_state(
                        closed_price,
                        rsi=rsi_val,
                        sma_50=sma_50,
                        sma_200=sma_200,
                        bullish_score=bullish_score,
                        bearish_score=bearish_score
                    )
                
                # Real-time Stop Loss and Take Profit breach check (evaluated on the active price tick)
                if self.active_position:
                    entry_price = self.active_position["entry_price"]
                    position_size = self.active_position["amount"]
                    stop_loss = self.active_position["stop_loss"]
                    take_profit = self.active_position["take_profit"]
                    
                    if current_price <= stop_loss:
                        print(f"\n[STOP LOSS TRIGGERED] Price (${current_price:,.2f}) hit Stop Loss (${stop_loss:,.2f})!")
                        logger.warning(f"Stop-Loss breached at ${current_price:,.2f}!")
                        
                        if self.binance_client:
                            order = self.execute_market_order(self.symbol, "SELL", position_size)
                        else:
                            order = self.execute_order("SELL", position_size, current_price)
                            
                        if order:
                            self._handle_liquidation_order_response(order, entry_price, position_size, current_price, reason="STOP_LOSS")
                            
                    elif current_price >= take_profit:
                        print(f"\n[TAKE PROFIT TRIGGERED] Price (${current_price:,.2f}) hit Take Profit (${take_profit:,.2f})!")
                        logger.info(f"Take-Profit breached at ${current_price:,.2f}!")
                        
                        if self.binance_client:
                            order = self.execute_market_order(self.symbol, "SELL", position_size)
                        else:
                            order = self.execute_order("SELL", position_size, current_price)
                            
                        if order:
                            self._handle_liquidation_order_response(order, entry_price, position_size, current_price, reason="TAKE_PROFIT")

                # Print iteration status for clean live update checks
                iteration += 1
                time.sleep(3)
                
            except KeyboardInterrupt:
                print("\n[SYSTEM] Live futures loop gracefully interrupted by keyboard.")
                break
            except Exception as loop_err:
                logger.error(f"Error in live futures trading loop: {loop_err}", exc_info=True)
                print(f"\n[ERROR] Exception in live trading loop: {loop_err}. Retrying in 5 seconds...")
                time.sleep(5)


# =========================================================================
#                          BOOTSTRAPPER / ENTRY
# =========================================================================
if __name__ == "__main__":
    print("""
==========================================================
   CryptoBot AI - Interactive Shell Trading Bootstrapper  
==========================================================
    """)
    
    # Instantiate bot engine
    bot = TradingBotEngine()
    
    # Start the continuous live futures trading loop
    try:
        tf = os.getenv("TIMEFRAME", "1m")
        bot.start_live_futures_trading_loop(interval=tf)
    except KeyboardInterrupt:
        print("\n[SYSTEM] Trading engine gracefully stopped by user.")
    
    print("\n----------------------------------------------------------")
    print("Execution complete. All logs persisted inside 'bot_log.txt'")
    print("==========================================================\n")
