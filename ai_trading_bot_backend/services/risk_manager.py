"""
Risk Manager Service for calculating ATR-based dynamic Stop-Loss and Take-Profit
levels for futures trading positions.
"""

import logging
from typing import Dict, Any, Optional

logger = logging.getLogger(__name__)


class RiskManagerService:
    """
    Handles dynamic stop-loss, take-profit, position sizing, and leverage risk management.
    """

    def __init__(self, default_atr_multiplier: float = 2.0, default_risk_reward_ratio: float = 2.0):
        self.default_atr_multiplier = default_atr_multiplier
        self.default_risk_reward_ratio = default_risk_reward_ratio

    def calculate_atr_risk_levels(
        self,
        entry_price: float,
        side: str,
        atr: float,
        atr_multiplier: Optional[float] = None,
        risk_reward_ratio: Optional[float] = None
    ) -> Dict[str, Any]:
        """
        Calculates dynamic Stop-Loss and Take-Profit price levels using ATR multiples for futures.

        Args:
            entry_price (float): Proposed futures entry price.
            side (str): Trade direction ('BUY'/'LONG' or 'SELL'/'SHORT').
            atr (float): Average True Range value.
            atr_multiplier (Optional[float]): ATR multiplier for SL distance.
            risk_reward_ratio (Optional[float]): Risk to Reward ratio multiplier for TP.

        Returns:
            Dict[str, Any]: Stop-Loss price, Take-Profit price, risk distance, and percentage targets.
        """
        if entry_price <= 0 or atr <= 0:
            logger.error("Invalid entry price or ATR provided for risk level calculation.")
            return {
                "stop_loss": 0.0,
                "take_profit": 0.0,
                "sl_distance": 0.0,
                "tp_distance": 0.0,
                "risk_reward_ratio": 0.0
            }

        mult = atr_multiplier if (atr_multiplier is not None and atr_multiplier > 0) else self.default_atr_multiplier
        rrr = risk_reward_ratio if (risk_reward_ratio is not None and risk_reward_ratio > 0) else self.default_risk_reward_ratio

        sl_distance = atr * mult
        tp_distance = sl_distance * rrr

        is_long = side.upper().strip() in ["BUY", "LONG"]

        if is_long:
            stop_loss = entry_price - sl_distance
            take_profit = entry_price + tp_distance
        else:
            stop_loss = entry_price + sl_distance
            take_profit = entry_price - tp_distance

        sl_pct = (sl_distance / entry_price) * 100.0
        tp_pct = (tp_distance / entry_price) * 100.0

        logger.info(
            f"Risk Management ({side.upper()}): Entry=${entry_price:.2f} | ATR=${atr:.2f} (x{mult}) "
            f"=> SL=${stop_loss:.2f} (-{sl_pct:.2f}%) | TP=${take_profit:.2f} (+{tp_pct:.2f}%) | RRR={rrr}:1"
        )

        return {
            "entry_price": round(entry_price, 4),
            "side": side.upper(),
            "atr": round(atr, 4),
            "atr_multiplier": mult,
            "stop_loss": round(stop_loss, 4),
            "take_profit": round(take_profit, 4),
            "sl_distance": round(sl_distance, 4),
            "tp_distance": round(tp_distance, 4),
            "sl_pct": round(sl_pct, 2),
            "tp_pct": round(tp_pct, 2),
            "risk_reward_ratio": rrr
        }

    def calculate_position_sizing(
        self,
        account_balance: float,
        risk_per_trade_pct: float,
        sl_distance: float,
        entry_price: float,
        leverage: int = 10
    ) -> Dict[str, Any]:
        """
        Calculates optimal position size based on account balance and risk percentage.
        """
        if account_balance <= 0 or sl_distance <= 0 or entry_price <= 0:
            return {"amount": 0.0, "notional_usd": 0.0, "margin_required_usd": 0.0}

        risk_amount_usd = account_balance * (risk_per_trade_pct / 100.0)
        amount = risk_amount_usd / sl_distance
        notional_usd = amount * entry_price
        margin_required = notional_usd / max(1, leverage)

        return {
            "amount": round(amount, 6),
            "notional_usd": round(notional_usd, 2),
            "margin_required_usd": round(margin_required, 2),
            "risk_amount_usd": round(risk_amount_usd, 2)
        }
