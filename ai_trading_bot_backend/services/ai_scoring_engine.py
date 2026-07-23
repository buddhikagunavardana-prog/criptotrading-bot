"""
AI Scoring Engine for evaluating market setup conviction based on VWAP, Price, ATR,
and trend direction indicators.
"""

import logging
from typing import Dict, Any, Tuple

logger = logging.getLogger(__name__)


class AIScoringEngine:
    """
    Computes setup confidence score (0 - 100) and market recommendations
    for quantitative crypto futures trades.
    """

    def __init__(self, strong_buy_threshold: float = 30.0, buy_threshold: float = 60.0):
        self.strong_buy_threshold = strong_buy_threshold
        self.buy_threshold = buy_threshold

    def evaluate_setup(
        self,
        vwap: float,
        current_price: float,
        atr: float,
        trend_direction: str,
        side: str = "BUY"
    ) -> Dict[str, Any]:
        """
        Evaluates trade setup parameters and computes a confidence score (0 - 100).

        Args:
            vwap (float): Volume Weighted Average Price.
            current_price (float): Current market ticker price.
            atr (float): Average True Range volatility value.
            trend_direction (str): Market trend direction ('BULLISH', 'BEARISH', or 'NEUTRAL').
            side (str): Desired trade side ('BUY' or 'SELL').

        Returns:
            Dict[str, Any]: Contains score, recommendation, and score breakdown.
        """
        if vwap <= 0 or current_price <= 0 or atr <= 0:
            logger.error("Invalid indicator inputs provided to AIScoringEngine.")
            return {
                "confidence_score": 0.0,
                "recommendation": "HOLD",
                "approved": False,
                "reason": "Invalid indicator values"
            }

        trend_upper = trend_direction.upper().strip()
        side_upper = side.upper().strip()
        is_buy = side_upper in ["BUY", "LONG"]

        # 1. Base Score setup
        score = 50.0

        # 2. Trend Alignment Score (+/- 20 pts)
        if (is_buy and trend_upper in ["BULLISH", "UP"]) or (not is_buy and trend_upper in ["BEARISH", "DOWN"]):
            trend_score = 25.0
        elif trend_upper == "NEUTRAL":
            trend_score = 10.0
        else:
            trend_score = -20.0

        # 3. VWAP Position & Mean Reversion / Trend Confluence (+/- 25 pts)
        price_vwap_diff = current_price - vwap
        atr_distance = price_vwap_diff / atr if atr > 0 else 0.0

        if is_buy:
            # Optimal Buy: Price slightly above VWAP (0 to 1.5 ATR) or pulling back right at VWAP (-0.5 to 0.5 ATR)
            if -0.5 <= atr_distance <= 1.5:
                vwap_score = 25.0
            elif 1.5 < atr_distance <= 3.0:
                vwap_score = 15.0  # Slightly extended
            elif atr_distance < -0.5:
                vwap_score = 10.0  # Below VWAP but possibly deep value
            else:
                vwap_score = -10.0 # Overextended (> 3 ATR)
        else:
            # Optimal Sell: Price slightly below VWAP (-1.5 to 0 ATR) or testing near VWAP (-0.5 to 0.5 ATR)
            if -1.5 <= atr_distance <= 0.5:
                vwap_score = 25.0
            elif -3.0 <= atr_distance < -1.5:
                vwap_score = 15.0
            elif atr_distance > 0.5:
                vwap_score = 10.0
            else:
                vwap_score = -10.0

        # 4. Volatility / ATR Expansion Factor (+/- 15 pts)
        atr_pct = (atr / current_price) * 100.0
        if 0.5 <= atr_pct <= 4.0:
            volatility_score = 15.0  # Healthy volatility
        elif atr_pct > 4.0:
            volatility_score = 8.0   # High volatility / slippage risk
        else:
            volatility_score = 5.0   # Low liquidity / compressed range

        # Total Calculation
        final_score = max(0.0, min(100.0, score + trend_score + vwap_score + volatility_score - 15.0))

        # Determine Recommendation
        if is_buy:
            if final_score >= self.strong_buy_threshold:
                recommendation = "STRONG_BUY"
            elif final_score >= self.buy_threshold:
                recommendation = "BUY"
            elif final_score <= 35.0:
                recommendation = "STRONG_SELL"
            else:
                recommendation = "HOLD"
        else:
            if final_score >= self.strong_buy_threshold:
                recommendation = "STRONG_SELL"
            elif final_score >= self.buy_threshold:
                recommendation = "SELL"
            elif final_score <= 35.0:
                recommendation = "STRONG_BUY"
            else:
                recommendation = "HOLD"

        approved = final_score >= self.strong_buy_threshold

        logger.info(
            f"AI Scoring: {side_upper} | Price=${current_price:.2f} | VWAP=${vwap:.2f} | "
            f"ATR=${atr:.2f} | Trend={trend_upper} => Score={final_score:.1f}/100 ({recommendation})"
        )

        return {
            "confidence_score": round(final_score, 1),
            "recommendation": recommendation,
            "approved": approved,
            "threshold_required": self.strong_buy_threshold,
            "metrics": {
                "current_price": current_price,
                "vwap": round(vwap, 2),
                "atr": round(atr, 2),
                "atr_distance_from_vwap": round(atr_distance, 2),
                "trend_direction": trend_upper,
                "volatility_pct": round(atr_pct, 2)
            }
        }
