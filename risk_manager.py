import logging
from dataclasses import dataclass, field
from typing import Dict, Any, Optional, Tuple, List
from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)


class RiskConfig(BaseModel):
    """
    Configuration settings for Risk Management parameters.
    """
    max_risk_per_trade_pct: float = Field(2.0, ge=0.1, le=10.0, description="Max percent of total equity risked per trade")
    max_account_drawdown_pct: float = Field(15.0, ge=1.0, le=50.0, description="Max allowed total drawdown percentage before circuit breaker")
    max_open_positions: int = Field(5, ge=1, le=20, description="Maximum concurrent active open positions")
    max_leverage: int = Field(50, ge=1, le=125, description="Maximum allowed leverage multiplier")
    default_risk_reward_ratio: float = Field(2.0, ge=1.0, le=10.0, description="Default Risk-to-Reward ratio (TP distance / SL distance)")
    atr_sl_multiplier: float = Field(1.5, ge=0.5, le=5.0, description="ATR multiplier for Stop-Loss calculation")
    atr_tp_multiplier: float = Field(3.0, ge=1.0, le=10.0, description="ATR multiplier for Take-Profit calculation")
    max_position_size_usdt: float = Field(50000.0, ge=100.0, description="Cap on total notional position size in USDT")


class RiskManager:
    """
    Risk Management Module responsible for position sizing, stop-loss / take-profit calculations,
    trailing stop updates, and pre-order safety validation.
    """

    def __init__(self, config: Optional[RiskConfig] = None):
        self.config = config or RiskConfig()
        logger.info(f"RiskManager initialized with max risk per trade: {self.config.max_risk_per_trade_pct}%")

    def calculate_position_size(
        self,
        account_balance: float,
        entry_price: float,
        stop_loss_price: float,
        leverage: int = 1
    ) -> Dict[str, Any]:
        """
        Calculates optimal position size based on account balance, risk tolerance, and SL distance.

        Args:
            account_balance (float): Total account balance / equity in USDT.
            entry_price (float): Proposed order entry price.
            stop_loss_price (float): Proposed stop loss price.
            leverage (int): Futures leverage multiplier.

        Returns:
            Dict[str, Any]: Position sizing metrics including units, notional value, required margin, and risk amount.
        """
        if account_balance <= 0 or entry_price <= 0:
            logger.error("Invalid account balance or entry price provided for position sizing.")
            return {
                "position_units": 0.0,
                "notional_value_usdt": 0.0,
                "required_margin_usdt": 0.0,
                "risk_amount_usdt": 0.0,
                "error": "Account balance and entry price must be strictly positive."
            }

        sl_distance = abs(entry_price - stop_loss_price)
        if sl_distance == 0:
            logger.warning("Stop loss price equals entry price. Cannot calculate position size safely.")
            return {
                "position_units": 0.0,
                "notional_value_usdt": 0.0,
                "required_margin_usdt": 0.0,
                "risk_amount_usdt": 0.0,
                "error": "Stop loss distance is zero."
            }

        # 1. Calculate maximum risk amount in USDT
        risk_usd = account_balance * (self.config.max_risk_per_trade_pct / 100.0)

        # 2. Position size in base asset units (e.g. BTC)
        position_units = risk_usd / sl_distance

        # 3. Calculate Notional Value & Required Margin
        notional_value = position_units * entry_price
        
        # Capped by max allowed position size USDT
        if notional_value > self.config.max_position_size_usdt:
            logger.info(f"Capping notional position value from {notional_value:.2f} to max {self.config.max_position_size_usdt:.2f} USDT")
            notional_value = self.config.max_position_size_usdt
            position_units = notional_value / entry_price
            risk_usd = position_units * sl_distance

        effective_leverage = max(1, min(leverage, self.config.max_leverage))
        required_margin = notional_value / effective_leverage

        logger.info(
            f"Risk Sizing: Equity = ${account_balance:.2f} | Risk = ${risk_usd:.2f} ({self.config.max_risk_per_trade_pct}%) "
            f"| Position Units = {position_units:.6f} | Notional = ${notional_value:.2f} | Margin = ${required_margin:.2f} ({effective_leverage}x)"
        )

        return {
            "position_units": round(position_units, 6),
            "notional_value_usdt": round(notional_value, 2),
            "required_margin_usdt": round(required_margin, 2),
            "risk_amount_usdt": round(risk_usd, 2),
            "risk_pct_of_balance": round((risk_usd / account_balance) * 100.0, 2),
            "effective_leverage": effective_leverage,
            "error": None
        }

    def calculate_sl_tp_levels(
        self,
        entry_price: float,
        side: str,
        atr: Optional[float] = None,
        custom_sl_price: Optional[float] = None,
        custom_tp_price: Optional[float] = None,
        risk_reward_ratio: Optional[float] = None
    ) -> Dict[str, float]:
        """
        Calculates Stop-Loss and Take-Profit target prices.

        Args:
            entry_price (float): Proposed entry price.
            side (str): Trade direction ('BUY'/'LONG' or 'SELL'/'SHORT').
            atr (Optional[float]): Average True Range value for volatility-based SL/TP.
            custom_sl_price (Optional[float]): Explicit stop-loss override.
            custom_tp_price (Optional[float]): Explicit take-profit override.
            risk_reward_ratio (Optional[float]): Target RRR (default from config if None).

        Returns:
            Dict[str, float]: Calculated stop_loss, take_profit, and risk_reward_ratio.
        """
        side_upper = side.upper().strip()
        is_long = side_upper in ["BUY", "LONG"]
        rrr = risk_reward_ratio or self.config.default_risk_reward_ratio

        # 1. Stop Loss Calculation
        if custom_sl_price is not None and custom_sl_price > 0:
            stop_loss = custom_sl_price
        elif atr is not None and atr > 0:
            sl_distance = atr * self.config.atr_sl_multiplier
            stop_loss = entry_price - sl_distance if is_long else entry_price + sl_distance
        else:
            # Default 1.5% fixed distance fallback
            default_sl_pct = 0.015
            stop_loss = entry_price * (1.0 - default_sl_pct) if is_long else entry_price * (1.0 + default_sl_pct)

        # Ensure SL is on the correct side of entry
        if is_long and stop_loss >= entry_price:
            stop_loss = entry_price * 0.985
        elif not is_long and stop_loss <= entry_price:
            stop_loss = entry_price * 1.015

        # 2. Take Profit Calculation
        sl_distance = abs(entry_price - stop_loss)
        if custom_tp_price is not None and custom_tp_price > 0:
            take_profit = custom_tp_price
        elif atr is not None and atr > 0:
            tp_distance = atr * self.config.atr_tp_multiplier
            take_profit = entry_price + tp_distance if is_long else entry_price - tp_distance
        else:
            tp_distance = sl_distance * rrr
            take_profit = entry_price + tp_distance if is_long else entry_price - tp_distance

        # Recalculate actual RRR
        actual_tp_distance = abs(take_profit - entry_price)
        actual_rrr = round(actual_tp_distance / sl_distance, 2) if sl_distance > 0 else 0.0

        return {
            "entry_price": round(entry_price, 4),
            "stop_loss": round(stop_loss, 4),
            "take_profit": round(take_profit, 4),
            "risk_reward_ratio": actual_rrr,
            "sl_distance_pct": round((sl_distance / entry_price) * 100.0, 2),
            "tp_distance_pct": round((actual_tp_distance / entry_price) * 100.0, 2)
        }

    def update_trailing_stop(
        self,
        current_price: float,
        entry_price: float,
        current_sl: float,
        side: str,
        atr: Optional[float] = None,
        activation_pct: float = 1.0
    ) -> Tuple[float, bool]:
        """
        Updates trailing stop-loss price if price moves favorably past the activation threshold.

        Returns:
            Tuple[float, bool]: (updated_stop_loss, was_updated)
        """
        is_long = side.upper() in ["BUY", "LONG"]
        
        # Check if trade is in profit beyond activation threshold
        if is_long:
            price_gain_pct = ((current_price - entry_price) / entry_price) * 100.0
            if price_gain_pct >= activation_pct:
                trail_distance = (atr * self.config.atr_sl_multiplier) if atr else (current_price * 0.01)
                proposed_sl = current_price - trail_distance
                if proposed_sl > current_sl:
                    logger.info(f"Trailing SL updated LONG: {current_sl:.4f} -> {proposed_sl:.4f}")
                    return round(proposed_sl, 4), True
        else:
            price_gain_pct = ((entry_price - current_price) / entry_price) * 100.0
            if price_gain_pct >= activation_pct:
                trail_distance = (atr * self.config.atr_sl_multiplier) if atr else (current_price * 0.01)
                proposed_sl = current_price + trail_distance
                if proposed_sl < current_sl:
                    logger.info(f"Trailing SL updated SHORT: {current_sl:.4f} -> {proposed_sl:.4f}")
                    return round(proposed_sl, 4), True

        return current_sl, False

    def validate_order_safety(
        self,
        account_balance: float,
        entry_price: float,
        stop_loss_price: float,
        take_profit_price: float,
        side: str,
        leverage: int,
        open_positions_count: int = 0,
        current_drawdown_pct: float = 0.0
    ) -> Tuple[bool, List[str]]:
        """
        Performs comprehensive pre-execution risk and safety validations.

        Returns:
            Tuple[bool, List[str]]: (is_approved, list_of_rejection_reasons)
        """
        rejection_reasons = []

        # 1. Circuit Breaker / Account Drawdown Check
        if current_drawdown_pct >= self.config.max_account_drawdown_pct:
            rejection_reasons.append(
                f"Account drawdown ({current_drawdown_pct:.1f}%) exceeds maximum threshold ({self.config.max_account_drawdown_pct:.1f}%)."
            )

        # 2. Maximum Open Positions Check
        if open_positions_count >= self.config.max_open_positions:
            rejection_reasons.append(
                f"Open position limit reached ({open_positions_count}/{self.config.max_open_positions})."
            )

        # 3. Leverage Threshold Check
        if leverage > self.config.max_leverage:
            rejection_reasons.append(
                f"Requested leverage ({leverage}x) exceeds maximum allowed ({self.config.max_leverage}x)."
            )

        # 4. Stop-Loss Direction Sanity Check
        is_long = side.upper() in ["BUY", "LONG"]
        if is_long and stop_loss_price >= entry_price:
            rejection_reasons.append("Stop-loss price must be strictly below entry price for LONG position.")
        elif not is_long and stop_loss_price <= entry_price:
            rejection_reasons.append("Stop-loss price must be strictly above entry price for SHORT position.")

        # 5. Take-Profit Direction Sanity Check
        if is_long and take_profit_price <= entry_price:
            rejection_reasons.append("Take-profit price must be strictly above entry price for LONG position.")
        elif not is_long and take_profit_price >= entry_price:
            rejection_reasons.append("Take-profit price must be strictly below entry price for SHORT position.")

        # 6. Sizing & Margin Validation
        sizing = self.calculate_position_size(account_balance, entry_price, stop_loss_price, leverage)
        if sizing.get("error"):
            rejection_reasons.append(f"Position sizing error: {sizing['error']}")
        else:
            required_margin = sizing["required_margin_usdt"]
            if required_margin > account_balance:
                rejection_reasons.append(
                    f"Insufficient balance for margin: Required ${required_margin:.2f}, Available ${account_balance:.2f}."
                )

        is_approved = len(rejection_reasons) == 0
        if is_approved:
            logger.info("Order safety validation PASSED.")
        else:
            logger.warning(f"Order safety validation FAILED: {'; '.join(rejection_reasons)}")

        return is_approved, rejection_reasons

    def evaluate_trade_risk(
        self,
        account_balance: float,
        entry_price: float,
        side: str,
        leverage: int = 10,
        atr: Optional[float] = None,
        custom_sl_price: Optional[float] = None,
        custom_tp_price: Optional[float] = None,
        open_positions_count: int = 0,
        current_drawdown_pct: float = 0.0
    ) -> Dict[str, Any]:
        """
        High-level method that combines SL/TP calculation, position sizing, and pre-execution safety validation.
        """
        # 1. Calculate SL and TP levels
        levels = self.calculate_sl_tp_levels(
            entry_price=entry_price,
            side=side,
            atr=atr,
            custom_sl_price=custom_sl_price,
            custom_tp_price=custom_tp_price
        )

        stop_loss = levels["stop_loss"]
        take_profit = levels["take_profit"]

        # 2. Calculate Sizing
        sizing = self.calculate_position_size(
            account_balance=account_balance,
            entry_price=entry_price,
            stop_loss_price=stop_loss,
            leverage=leverage
        )

        # 3. Validate Safety
        is_approved, rejection_reasons = self.validate_order_safety(
            account_balance=account_balance,
            entry_price=entry_price,
            stop_loss_price=stop_loss,
            take_profit_price=take_profit,
            side=side,
            leverage=leverage,
            open_positions_count=open_positions_count,
            current_drawdown_pct=current_drawdown_pct
        )

        return {
            "approved": is_approved,
            "rejection_reasons": rejection_reasons,
            "side": side.upper(),
            "entry_price": entry_price,
            "stop_loss": stop_loss,
            "take_profit": take_profit,
            "risk_reward_ratio": levels["risk_reward_ratio"],
            "sl_distance_pct": levels["sl_distance_pct"],
            "tp_distance_pct": levels["tp_distance_pct"],
            "position_units": sizing.get("position_units", 0.0),
            "notional_value_usdt": sizing.get("notional_value_usdt", 0.0),
            "required_margin_usdt": sizing.get("required_margin_usdt", 0.0),
            "risk_amount_usdt": sizing.get("risk_amount_usdt", 0.0),
            "risk_pct_of_balance": sizing.get("risk_pct_of_balance", 0.0),
            "leverage": leverage
        }
