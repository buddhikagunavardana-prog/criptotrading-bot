import logging
from typing import Dict, Any, Type, Optional, List
from strategy_engine import (
    BaseStrategy,
    SMCOrderBlockStrategy,
    SMCBreakerMitigationStrategy,
    SMCFairValueGapStrategy,
    SMCLiquiditySweepStrategy,
    BollingerBreakoutStrategy,
    ADXTrendStrategy,
    VWAPMeanReversionStrategy,
    RSIDivergenceStrategy,
    ATRTrailingStrategy,
    MainBotStrategy
)

logger = logging.getLogger(__name__)


class StrategyFactory:
    """
    Factory pattern class that dynamically loads, maps, and instantiates
    trading strategy classes based on strategy names and requested types.
    """

    # Register strategy classes and metadata
    _REGISTRY: Dict[str, Dict[str, Any]] = {
        "smc order block expansion": {
            "class": SMCOrderBlockStrategy,
            "category": "Smart Money Concepts (SMC)",
            "description": "Detects institutional Order Blocks and enters positions on mitigation retests.",
            "indicators": ["OrderBlock", "FVG", "RSI"]
        },
        "high-probability mitigation zone": {
            "class": SMCBreakerMitigationStrategy,
            "category": "Smart Money Concepts (SMC)",
            "description": "Filters breaker blocks and mitigation zones with multi-timeframe confluence.",
            "indicators": ["BreakerBlock", "MitigationZone", "ATR"]
        },
        "fvg inversion": {
            "class": SMCFairValueGapStrategy,
            "category": "Smart Money Concepts (SMC)",
            "description": "Tracks Fair Value Gaps (FVG) and inversion gaps across recent market structure.",
            "indicators": ["FVG", "InversionGap", "VolumeProfile"]
        },
        "liquidity sweep core": {
            "class": SMCLiquiditySweepStrategy,
            "category": "Smart Money Concepts (SMC)",
            "description": "Identifies key high/low liquidity sweeps followed by strong displacement.",
            "indicators": ["LiquiditySweep", "Displacement", "RSI"]
        },
        "bollinger volatility breakout": {
            "class": BollingerBreakoutStrategy,
            "category": "Statistical & Momentum",
            "description": "Trades volatility expansions outside Bollinger Bands with volume confirmation.",
            "indicators": ["BollingerBands", "ADX", "Volume"]
        },
        "adx multi-timeframe trend": {
            "class": ADXTrendStrategy,
            "category": "Statistical & Momentum",
            "description": "Multi-timeframe trend following relying on ADX strength and EMA crossovers.",
            "indicators": ["ADX", "EMA_50", "EMA_200"]
        },
        "vwap anchored mean reversion": {
            "class": VWAPMeanReversionStrategy,
            "category": "Statistical & Momentum",
            "description": "Anchored VWAP standard deviation band mean reversion system.",
            "indicators": ["AnchoredVWAP", "StandardDeviationBands", "RSI"]
        },
        "rsi extreme divergence": {
            "class": RSIDivergenceStrategy,
            "category": "Statistical & Momentum",
            "description": "Detects bullish and bearish RSI divergences at key oversold and overbought levels.",
            "indicators": ["RSI_Divergence", "MACD", "ATR"]
        },
        "atr dynamic trailing edge": {
            "class": ATRTrailingStrategy,
            "category": "Statistical & Momentum",
            "description": "Adaptive trailing strategy utilizing dynamic ATR multiples for risk management.",
            "indicators": ["ATR", "KeltnerChannels", "SMA"]
        },
        "triple screen trading system": {
            "class": MainBotStrategy,
            "category": "Trading Frameworks",
            "description": "Alexander Elder's Triple Screen framework integrating trend, wave, and entry signals.",
            "indicators": ["EMA_Trend", "Stochastic", "ForceIndex"]
        }
    }

    @classmethod
    def normalize_name(cls, name: str) -> str:
        """Normalizes user input string for dictionary key matching."""
        return name.lower().strip() if name else ""

    @classmethod
    def get_registered_strategies(cls) -> List[str]:
        """Returns a list of all registered strategy names formatted in Title Case."""
        return [key.title() for key in cls._REGISTRY.keys()]

    @classmethod
    def _find_matching_key(cls, strategy_name: str) -> Optional[str]:
        """Finds direct or partial key match in the strategy registry."""
        norm_name = cls.normalize_name(strategy_name)
        if not norm_name:
            return None

        # Direct match
        if norm_name in cls._REGISTRY:
            return norm_name

        # Partial substring match
        for key in cls._REGISTRY.keys():
            if key in norm_name or norm_name in key:
                return key

        return None

    @classmethod
    def create_strategy(cls, strategy_name: str, **kwargs) -> BaseStrategy:
        """
        Dynamically instantiates and returns a strategy object derived from BaseStrategy.
        
        Args:
            strategy_name (str): Name or keyword of the requested strategy.
            **kwargs: Additional parameters passed to the strategy constructor.

        Returns:
            BaseStrategy: Instantiated strategy object ready for signal generation.
        """
        matched_key = cls._find_matching_key(strategy_name)

        if matched_key:
            strategy_class: Type[BaseStrategy] = cls._REGISTRY[matched_key]["class"]
            logger.info(f"StrategyFactory: Instantiating strategy '{matched_key.title()}' ({strategy_class.__name__})")
            return strategy_class(**kwargs)
        else:
            logger.warning(
                f"StrategyFactory: '{strategy_name}' not found in registry. Instantiating default MainBotStrategy."
            )
            return MainBotStrategy(**kwargs)

    @classmethod
    def load_strategy(cls, strategy_name: str) -> Dict[str, Any]:
        """
        Loads strategy configuration, metadata, and required indicators.
        
        Args:
            strategy_name (str): Name of the strategy to inspect/load.

        Returns:
            Dict[str, Any]: Strategy configuration metadata.
        """
        matched_key = cls._find_matching_key(strategy_name)

        if matched_key:
            info = cls._REGISTRY[matched_key]
            return {
                "strategy_name": matched_key.title(),
                "class_name": info["class"].__name__,
                "category": info["category"],
                "description": info["description"],
                "indicators": info["indicators"],
                "loaded_successfully": True
            }
        else:
            return {
                "strategy_name": strategy_name.title() if strategy_name else "Default Engine",
                "class_name": "MainBotStrategy",
                "category": "Custom / General Framework",
                "description": f"Dynamic execution framework for '{strategy_name}'",
                "indicators": ["RSI", "SMA_50", "SMA_200", "ATR"],
                "loaded_successfully": True
            }


# Shortcut helper function
def get_strategy(strategy_name: str, **kwargs) -> BaseStrategy:
    """Helper function to quickly instantiate a strategy class via StrategyFactory."""
    return StrategyFactory.create_strategy(strategy_name, **kwargs)
