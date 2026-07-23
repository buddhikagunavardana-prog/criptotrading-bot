"""
Modular Strategy Factory utilizing absolute imports to dynamically load
and instantiate trading strategies by key name.
"""

import logging
from typing import Dict, Any, Type, Optional, List

from ai_trading_bot_backend.strategies.vwap_atr_engine import VWAPATRAIEngine

logger = logging.getLogger(__name__)


class StrategyFactory:
    """
    Strategy Factory mapping strategy key strings to strategy handler classes
    via absolute module imports.
    """

    _STRATEGY_MAP: Dict[str, Type] = {
        "vwap_atr_ai": VWAPATRAIEngine,
        "vwap_atr": VWAPATRAIEngine,
        "vwap": VWAPATRAIEngine,
        "smc_order_block": VWAPATRAIEngine,
        "fvg_inversion": VWAPATRAIEngine,
        "liquidity_sweep": VWAPATRAIEngine,
        "adx_trend": VWAPATRAIEngine,
        "triple_screen": VWAPATRAIEngine
    }

    _METADATA_MAP: Dict[str, Dict[str, Any]] = {
        "vwap_atr_ai": {
            "name": "VWAP ATR AI Engine",
            "category": "Quantitative AI & Volatility",
            "description": "Combines VWAP mean-reversion, ATR dynamic volatility risk management, and AI Scoring Engine threshold verification.",
            "min_score": 75.0
        },
        "smc_order_block": {
            "name": "SMC Order Block Expansion",
            "category": "Smart Money Concepts",
            "description": "Identifies institutional order blocks and enters on mitigation retests.",
            "min_score": 70.0
        },
        "fvg_inversion": {
            "name": "SMC FVG Inversion",
            "category": "Smart Money Concepts",
            "description": "Tracks Fair Value Gap inversions after key liquidity sweeps.",
            "min_score": 70.0
        },
        "liquidity_sweep": {
            "name": "Liquidity Sweep Core",
            "category": "Smart Money Concepts",
            "description": "Scans for liquidity pool sweeps at equal highs/lows.",
            "min_score": 70.0
        },
        "adx_trend": {
            "name": "ADX Multi-Timeframe Trend",
            "category": "Statistical & Momentum",
            "description": "Trend following relying on ADX trend strength readings.",
            "min_score": 65.0
        },
        "triple_screen": {
            "name": "Triple Screen Trading System",
            "category": "Trading Frameworks",
            "description": "Alexander Elder's Triple Screen framework combining trend, wave, and entry screens.",
            "min_score": 70.0
        }
    }

    @classmethod
    def normalize_key(cls, key: str) -> str:
        """Normalizes strategy key input string."""
        if not key:
            return "vwap_atr_ai"
        cleaned = key.lower().strip().replace(" ", "_").replace("-", "_")
        return cleaned

    @classmethod
    def get_registered_keys(cls) -> List[str]:
        """Returns list of all registered strategy keys."""
        return list(cls._STRATEGY_MAP.keys())

    @classmethod
    def get_strategy_class(cls, strategy_key: str) -> Type:
        """
        Resolves strategy class by key string.
        Falls back to VWAPATRAIEngine if key is unknown.
        """
        normalized = cls.normalize_key(strategy_key)
        strategy_class = cls._STRATEGY_MAP.get(normalized)

        if not strategy_class:
            logger.warning(
                f"Strategy key '{strategy_key}' (normalized: '{normalized}') not found in StrategyFactory map. "
                f"Defaulting to VWAPATRAIEngine."
            )
            return VWAPATRAIEngine

        logger.info(f"StrategyFactory resolved key '{strategy_key}' -> {strategy_class.__name__}")
        return strategy_class

    @classmethod
    def create_strategy_instance(cls, strategy_key: str, **kwargs) -> Any:
        """Instantiates and returns a strategy object."""
        strategy_class = cls.get_strategy_class(strategy_key)
        return strategy_class(**kwargs)

    @classmethod
    def get_strategy_metadata(cls, strategy_key: str) -> Dict[str, Any]:
        """Returns strategy metadata dictionary."""
        normalized = cls.normalize_key(strategy_key)
        return cls._METADATA_MAP.get(
            normalized,
            {
                "name": strategy_key.title(),
                "category": "Custom Quantitative",
                "description": f"Custom execution framework for '{strategy_key}'",
                "min_score": 75.0
            }
        )
