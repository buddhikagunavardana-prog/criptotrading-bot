"""
CCXT Exchange Handler for Binance Futures integration supporting testnet/sandbox mode,
margin configuration, leverage control, and market order execution.
"""

import os
import time
import logging
from typing import Dict, Any, Optional
import ccxt

logger = logging.getLogger(__name__)


class CCXTHandler:
    """
    Handles connection, leverage settings, margin mode, and order execution
    on Binance Futures using CCXT.
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        api_secret: Optional[str] = None,
        testnet: bool = True
    ):
        self.api_key = api_key or os.getenv("BINANCE_API_KEY", "")
        self.api_secret = api_secret or os.getenv("BINANCE_API_SECRET", "")
        self.testnet = testnet

        # Initialize CCXT Binance exchange configured for USDT-M Futures
        try:
            if hasattr(ccxt, 'binance'):
                self.exchange = ccxt.binance({
                    'apiKey': self.api_key,
                    'secret': self.api_secret,
                    'enableRateLimit': True,
                    'options': {
                        'defaultType': 'future',
                        'adjustForTimeDifference': True
                    }
                })
            else:
                raise AttributeError("ccxt has no 'binance' attribute")
        except Exception as e:
            logger.info(f"CCXT Binance initialization fallback to Mock exchange: {e}")
            class MockExchange:
                def __init__(self):
                    self.options = {"defaultType": "future"}
                    mock_market = {
                        "symbol": "BTC/USDT:USDT",
                        "id": "BTCUSDT",
                        "linear": True,
                        "inverse": False,
                        "type": "swap",
                        "spot": False,
                        "swap": True,
                        "future": False,
                        "active": True,
                        "settle": "USDT",
                        "settlePay": "USDT",
                        "quote": "USDT",
                        "base": "BTC",
                        "limits": {"amount": {"min": 0.001, "max": 1000.0}, "cost": {"min": 5.0, "max": 2000000.0}},
                        "precision": {"price": 2, "amount": 3}
                    }
                    self.markets = {
                        "BTC/USDT": mock_market,
                        "BTC/USDT:USDT": mock_market
                    }
                    self._sim_position = 0.0
                    self._sim_orders = []
                    self._sim_leverage = 10
                    self._sim_margin_mode = "ISOLATED"

                def fetch_time(self):
                    return int(time.time() * 1000)

                def set_sandbox_mode(self, enabled=True):
                    pass

                def load_markets(self, reload=False):
                    return self.markets

                def market(self, symbol):
                    return self.markets.get(symbol, {
                        "symbol": symbol,
                        "limits": {"amount": {"min": 0.001}, "cost": {"min": 5.0}},
                        "precision": {"price": 2, "amount": 3}
                    })

                def fetch_ticker(self, symbol):
                    return {"last": 65000.0, "bid": 64990.0, "ask": 65010.0}

                def fetch_balance(self, params=None):
                    return {"USDT": {"free": 10000.0, "used": 0.0, "total": 10000.0}}

                def fetch_ohlcv(self, symbol, timeframe='15m', limit=300):
                    return []

                def set_leverage(self, leverage, symbol):
                    self._sim_leverage = leverage
                    return {"symbol": symbol, "leverage": leverage}

                def set_margin_mode(self, margin_mode, symbol):
                    self._sim_margin_mode = margin_mode.upper()
                    return {"symbol": symbol, "marginMode": margin_mode}

                def create_market_order(self, symbol, side, amount, params=None):
                    if side.lower() == 'buy':
                        self._sim_position += amount
                    else:
                        self._sim_position = max(0.0, self._sim_position - amount)
                    return {"id": f"MOCK_ORD_{int(ccxt.Exchange.milliseconds() if hasattr(ccxt, 'Exchange') else 1000)}", "status": "closed", "price": 65000.0, "amount": amount}

                def create_order(self, symbol, type, side, amount, price=None, params=None):
                    params = params or {}
                    order_id = f"MOCK_COND_{int(time.time() * 1000)}"
                    client_order_id = params.get("clientOrderId") or params.get("newClientOrderId") or f"c_{order_id}"
                    stop_price = params.get("stopPrice") or params.get("triggerPrice") or price
                    reduce_only = params.get("reduceOnly", True)
                    close_position = params.get("closePosition", False)
                    working_type = params.get("workingType", "MARK_PRICE")
                    position_side = params.get("positionSide", "LONG" if side.lower() == "sell" else "SHORT")

                    order = {
                        "id": order_id,
                        "clientOrderId": client_order_id,
                        "symbol": symbol,
                        "type": type.upper(),
                        "side": side.lower(),
                        "positionSide": position_side,
                        "amount": amount,
                        "origQty": amount,
                        "price": price or stop_price,
                        "stopPrice": stop_price,
                        "triggerPrice": stop_price,
                        "reduceOnly": reduce_only,
                        "closePosition": close_position,
                        "workingType": working_type,
                        "status": "open",
                        "params": params,
                        "info": {
                            "orderId": order_id,
                            "clientOrderId": client_order_id,
                            "symbol": symbol,
                            "type": type.upper(),
                            "side": side.upper(),
                            "positionSide": position_side,
                            "stopPrice": str(stop_price),
                            "workingType": working_type,
                            "origQty": str(amount),
                            "reduceOnly": reduce_only,
                            "closePosition": close_position,
                            "status": "NEW"
                        }
                    }
                    self._sim_orders.append(order)
                    return order

                def fetch_positions(self, symbols=None, params=None):
                    sym = symbols[0] if symbols else "BTC/USDT"
                    contracts = self._sim_position
                    side = "long" if contracts > 0 else "none"
                    entry_price = 65000.0
                    mark_price = 65005.0
                    unrealized_pnl = (mark_price - entry_price) * contracts if contracts > 0 else 0.0
                    notional = contracts * mark_price
                    leverage = self._sim_leverage
                    initial_margin = (notional / leverage) if leverage > 0 else 0.0
                    maint_margin = initial_margin * 0.4
                    liquidation_price = (entry_price * (1 - 1 / leverage)) if contracts > 0 else 0.0

                    return [{
                        "symbol": sym,
                        "contracts": contracts,
                        "contractSize": 1.0,
                        "side": side,
                        "positionSide": side.upper(),
                        "entryPrice": entry_price,
                        "markPrice": mark_price,
                        "unrealizedPnl": unrealized_pnl,
                        "initialMargin": initial_margin,
                        "maintMargin": maint_margin,
                        "liquidationPrice": liquidation_price,
                        "leverage": leverage,
                        "marginMode": self._sim_margin_mode.lower(),
                        "marginType": self._sim_margin_mode.lower(),
                        "notional": notional,
                        "percentage": 0.0,
                        "info": {
                            "symbol": sym.replace("/", "").replace(":USDT", ""),
                            "positionAmt": str(contracts),
                            "entryPrice": str(entry_price),
                            "markPrice": str(mark_price),
                            "unRealizedProfit": str(unrealized_pnl),
                            "liquidationPrice": str(liquidation_price),
                            "leverage": str(leverage),
                            "marginType": self._sim_margin_mode.lower(),
                            "isolatedMargin": str(initial_margin),
                            "notional": str(notional)
                        }
                    }]

                def fetch_open_orders(self, symbol=None, params=None):
                    return [o for o in self._sim_orders if o["status"] == "open"]

                def cancel_all_orders(self, symbol=None, params=None):
                    canceled = len(self._sim_orders)
                    self._sim_orders.clear()
                    return {"status": "success", "canceled_count": canceled}

                def amount_to_precision(self, symbol, amount):
                    m = self.market(symbol)
                    p = m.get("precision", {}).get("amount", 3)
                    return f"{amount:.{p}f}" if isinstance(p, int) else f"{amount:.3f}"

                def price_to_precision(self, symbol, price):
                    m = self.market(symbol)
                    p = m.get("precision", {}).get("price", 2)
                    return f"{price:.{p}f}" if isinstance(p, int) else f"{price:.2f}"
            self.exchange = MockExchange()

        if self.testnet:
            try:
                self.exchange.set_sandbox_mode(True)
                logger.info("CCXTHandler: Set sandbox/testnet mode ON for Binance Futures.")
            except Exception as e:
                logger.warning(f"Could not enable CCXT sandbox mode automatically: {e}")

        # Check if live/testnet API keys are active or mock mode
        self.is_connected = bool(self.api_key and self.api_secret)
        if not self.is_connected:
            logger.info("CCXTHandler initialized in SIMULATION/MOCK mode (No API keys provided).")

    def set_sandbox_mode(self, enabled: bool = True) -> None:
        """Enables or disables sandbox/testnet mode on the underlying CCXT exchange."""
        self.testnet = enabled
        if hasattr(self.exchange, 'set_sandbox_mode'):
            try:
                self.exchange.set_sandbox_mode(enabled)
                logger.info(f"CCXTHandler: Explicitly set sandbox/testnet mode to {enabled}")
            except Exception as e:
                logger.warning(f"Could not set exchange sandbox mode: {e}")

    def format_symbol(self, symbol: str) -> str:
        """Formats trading symbol into standard CCXT Futures format (e.g., BTC/USDT)."""
        formatted = symbol.upper().replace('-', '/').replace('_', '/')
        if formatted.endswith(':USDT'):
            formatted = formatted[:-5]
        if '/' not in formatted and formatted.endswith('USDT'):
            base = formatted[:-4]
            formatted = f"{base}/USDT"
        return formatted

    def set_leverage(self, leverage: int, symbol: str) -> Dict[str, Any]:
        """
        Sets futures position leverage for the specified symbol.
        """
        formatted_symbol = self.format_symbol(symbol)
        if not self.is_connected:
            logger.info(f"[SIMULATION] Setting leverage to {leverage}x for {formatted_symbol}")
            return {"status": "success", "mode": "simulation", "symbol": formatted_symbol, "leverage": leverage}

        try:
            response = self.exchange.set_leverage(leverage, formatted_symbol)
            logger.info(f"Leverage successfully set to {leverage}x for {formatted_symbol}")
            return {"status": "success", "response": response, "leverage": leverage}
        except Exception as e:
            logger.error(f"Failed to set leverage for {formatted_symbol}: {e}")
            # Non-blocking fallback
            return {"status": "fallback", "error": str(e), "leverage": leverage}

    def set_margin_mode(self, margin_mode: str = "ISOLATED", symbol: str = "BTC/USDT") -> Dict[str, Any]:
        """
        Configures margin mode ('ISOLATED' or 'CROSSED') for the given symbol.
        """
        formatted_symbol = self.format_symbol(symbol)
        mode_upper = margin_mode.upper()
        if not self.is_connected:
            logger.info(f"[SIMULATION] Margin mode set to {mode_upper} for {formatted_symbol}")
            return {"status": "success", "mode": "simulation", "symbol": formatted_symbol, "margin_mode": mode_upper}

        try:
            response = self.exchange.set_margin_mode(mode_upper, formatted_symbol)
            logger.info(f"Margin mode set to {mode_upper} for {formatted_symbol}")
            return {"status": "success", "response": response, "margin_mode": mode_upper}
        except Exception as e:
            # Often fails if margin mode is already set to target mode on Binance
            logger.warning(f"Margin mode notice for {formatted_symbol}: {e}")
            return {"status": "notice", "message": str(e), "margin_mode": mode_upper}

    def fetch_ticker_price(self, symbol: str) -> float:
        """Fetches current ticker price for symbol."""
        formatted_symbol = self.format_symbol(symbol)
        if not self.is_connected:
            # Mock pricing fallback for top 5 futures pairs
            mock_prices = {
                "BTC/USDT": 66500.0,
                "ETH/USDT": 3480.0,
                "SOL/USDT": 155.0,
                "DOGE/USDT": 0.1250,
                "XRP/USDT": 0.5840
            }
            return mock_prices.get(formatted_symbol, 100.0)

        try:
            ticker = self.exchange.fetch_ticker(formatted_symbol)
            return float(ticker['last'])
        except Exception as e:
            logger.error(f"Error fetching ticker for {formatted_symbol}: {e}")
            return 65000.0

    def fetch_balance(self, asset: str = "USDT") -> Dict[str, Any]:
        """
        Fetches the current account balance (e.g., USDT) on Binance Futures.
        Returns a dictionary with 'free', 'used', and 'total' balance info.
        """
        if not self.is_connected:
            try:
                bal = self.exchange.fetch_balance({"type": "future"})
                asset_balance = bal.get(asset, {})
                free_val = float(asset_balance.get("free", 10000.0))
                used_val = float(asset_balance.get("used", 0.0))
                total_val = float(asset_balance.get("total", free_val + used_val))
                return {
                    "asset": asset,
                    "free": free_val,
                    "used": used_val,
                    "total": total_val,
                    "status": "success",
                    "mode": "simulation"
                }
            except Exception:
                return {
                    "asset": asset,
                    "free": 10000.0,
                    "used": 0.0,
                    "total": 10000.0,
                    "status": "success",
                    "mode": "simulation"
                }

        try:
            balance = self.exchange.fetch_balance({"type": "future"})
            asset_balance = balance.get(asset, {})
            free_val = float(asset_balance.get("free", 0.0))
            used_val = float(asset_balance.get("used", 0.0))
            total_val = float(asset_balance.get("total", free_val + used_val))

            return {
                "asset": asset,
                "free": free_val,
                "used": used_val,
                "total": total_val,
                "status": "success",
                "mode": "live_exchange",
                "raw_balance": balance
            }
        except Exception as e:
            logger.error(f"Failed to fetch {asset} futures balance: {e}")
            return {
                "asset": asset,
                "free": 0.0,
                "used": 0.0,
                "total": 0.0,
                "status": "failed",
                "error": str(e)
            }

    def execute_order(
        self,
        symbol: str,
        side: str,
        amount: float,
        stop_loss: Optional[float] = None,
        take_profit: Optional[float] = None,
        leverage: int = 10,
        order_type: str = "market",
        price: Optional[float] = None,
        client_order_id: Optional[str] = None,
        params: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """
        Executes an order on Binance Futures via CCXT with optional Stop-Loss and Take-Profit.
        Attaches SL and TP using params={'stopLossPrice': stop_loss, 'takeProfitPrice': take_profit}.
        Falls back to separate conditional orders if the attached parameters method fails.

        Args:
            symbol (str): Trading pair (e.g. 'BTC/USDT')
            side (str): Order direction ('buy' or 'sell')
            amount (float): Quantity in base asset units
            stop_loss (Optional[float]): Stop-loss price target
            take_profit (Optional[float]): Take-profit price target
            leverage (int): Leverage multiplier (default: 10)
            order_type (str): Order type ('market' or 'limit')
            price (Optional[float]): Limit price if order_type is 'limit'
            client_order_id (Optional[str]): Unique client order ID
            params (Optional[Dict[str, Any]]): Additional CCXT order parameters

        Returns:
            Dict[str, Any]: Order execution details
        """
        formatted_symbol = self.format_symbol(symbol)
        side_clean = side.lower().strip()
        exit_side = "sell" if side_clean == "buy" else "buy"

        # Configure margin mode and leverage prior to order placement
        self.set_margin_mode("ISOLATED", formatted_symbol)
        self.set_leverage(leverage, formatted_symbol)

        if not self.is_connected:
            current_price = price or self.fetch_ticker_price(formatted_symbol)
            ord_id = client_order_id or f"sim_{int(time.time() * 1000)}"
            fee_cost = round(current_price * amount * 0.0005, 4)

            if hasattr(self.exchange, "_usdt_balance"):
                self.exchange._usdt_balance -= fee_cost
            
            if side_clean == "buy":
                if hasattr(self.exchange, "_sim_position"):
                    self.exchange._sim_position += amount
                if hasattr(self.exchange, "_position_size"):
                    self.exchange._position_size += amount
            else:
                if hasattr(self.exchange, "_sim_position"):
                    self.exchange._sim_position = max(0.0, getattr(self.exchange, "_sim_position", 0.0) - amount)
                if hasattr(self.exchange, "_position_size"):
                    self.exchange._position_size = max(0.0, getattr(self.exchange, "_position_size", 0.0) - amount)

            logger.info(
                f"[SIMULATION ORDER EXECUTED] {side_clean.upper()} {amount} {formatted_symbol} "
                f"@ ~${current_price:.2f} | Leverage: {leverage}x | ClientID: {client_order_id or 'N/A'}"
            )
            return {
                "status": "success",
                "mode": "simulation",
                "order_id": ord_id,
                "client_order_id": client_order_id or ord_id,
                "symbol": formatted_symbol,
                "side": side_clean.upper(),
                "amount": amount,
                "executed_price": current_price,
                "leverage": leverage,
                "stop_loss": stop_loss,
                "take_profit": take_profit,
                "message": f"Simulation {side_clean.upper()} order executed successfully."
            }

        order_params: Dict[str, Any] = dict(params or {})
        if client_order_id:
            order_params['newClientOrderId'] = client_order_id
            order_params['clientOrderId'] = client_order_id

        if stop_loss is not None:
            order_params['stopLossPrice'] = stop_loss
        if take_profit is not None:
            order_params['takeProfitPrice'] = take_profit

        sl_tp_attached = True
        order = None

        try:
            if order_type.lower() == "limit" and price is not None:
                order = self.exchange.create_limit_order(
                    symbol=formatted_symbol,
                    side=side_clean,
                    amount=amount,
                    price=price,
                    params=order_params
                )
            else:
                order = self.exchange.create_market_order(
                    symbol=formatted_symbol,
                    side=side_clean,
                    amount=amount,
                    params=order_params
                )
            logger.info(f"Live Order Executed: ID {order.get('id')} with attached SL/TP params for {formatted_symbol}")
        except Exception as primary_err:
            logger.warning(
                f"Order placement with attached SL/TP params failed ({primary_err}). "
                "Retrying primary order without attached SL/TP and creating separate conditional orders..."
            )
            sl_tp_attached = False
            try:
                if order_type.lower() == "limit" and price is not None:
                    order = self.exchange.create_limit_order(
                        symbol=formatted_symbol,
                        side=side_clean,
                        amount=amount,
                        price=price
                    )
                else:
                    order = self.exchange.create_market_order(
                        symbol=formatted_symbol,
                        side=side_clean,
                        amount=amount
                    )
                logger.info(f"Primary Order Executed (without attached SL/TP): ID {order.get('id')} for {formatted_symbol}")
            except Exception as e:
                logger.error(f"Failed to execute primary CCXT order for {formatted_symbol}: {e}")
                return {
                    "status": "failed",
                    "error": str(e),
                    "symbol": formatted_symbol,
                    "side": side_clean.upper(),
                    "amount": amount
                }

        # Place separate conditional SL/TP orders if attached parameters method failed
        separate_sl_order = None
        separate_tp_order = None

        if not sl_tp_attached:
            if stop_loss is not None:
                try:
                    sl_params = {'stopPrice': stop_loss, 'reduceOnly': True}
                    separate_sl_order = self.exchange.create_order(
                        symbol=formatted_symbol,
                        type='STOP_MARKET',
                        side=exit_side,
                        amount=amount,
                        params=sl_params
                    )
                    logger.info(f"Separate Stop Loss order placed: ID {separate_sl_order.get('id')} at ${stop_loss}")
                except Exception as sl_err:
                    logger.error(f"Failed to place separate Stop Loss order: {sl_err}")

            if take_profit is not None:
                try:
                    tp_params = {'stopPrice': take_profit, 'reduceOnly': True}
                    separate_tp_order = self.exchange.create_order(
                        symbol=formatted_symbol,
                        type='TAKE_PROFIT_MARKET',
                        side=exit_side,
                        amount=amount,
                        params=tp_params
                    )
                    logger.info(f"Separate Take Profit order placed: ID {separate_tp_order.get('id')} at ${take_profit}")
                except Exception as tp_err:
                    logger.error(f"Failed to place separate Take Profit order: {tp_err}")

        return {
            "status": "success",
            "mode": "live_exchange",
            "order_id": order.get('id') if order else None,
            "symbol": formatted_symbol,
            "side": side_clean.upper(),
            "amount": amount,
            "executed_price": (order.get('price') or order.get('average')) if order else None,
            "stop_loss": stop_loss,
            "take_profit": take_profit,
            "sl_tp_attached": sl_tp_attached,
            "separate_sl_order": separate_sl_order,
            "separate_tp_order": separate_tp_order,
            "raw_order": order
        }

    def execute_market_order(
        self,
        symbol: str,
        side: str,
        amount: float,
        stop_loss: Optional[float] = None,
        take_profit: Optional[float] = None,
        leverage: int = 10,
        client_order_id: Optional[str] = None,
        params: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """
        Executes a market order on Binance Futures with optional SL/TP attached and client order ID.
        """
        return self.execute_order(
            symbol=symbol,
            side=side,
            amount=amount,
            stop_loss=stop_loss,
            take_profit=take_profit,
            leverage=leverage,
            order_type="market",
            client_order_id=client_order_id,
            params=params
        )

    def create_conditional_order(
        self,
        symbol: str,
        order_type: str,
        side: str,
        amount: float,
        stop_price: float,
        client_order_id: Optional[str] = None,
        params: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """
        Creates a conditional order (e.g. STOP_MARKET, TAKE_PROFIT_MARKET) on the exchange.
        """
        formatted_symbol = self.format_symbol(symbol)
        order_params = dict(params or {})
        order_params["stopPrice"] = stop_price
        order_params["reduceOnly"] = order_params.get("reduceOnly", True)
        if client_order_id:
            order_params["clientOrderId"] = client_order_id
            order_params["newClientOrderId"] = client_order_id

        if hasattr(self.exchange, "create_order") and (self.is_connected or isinstance(self.exchange, object)):
            try:
                return self.exchange.create_order(
                    symbol=formatted_symbol,
                    type=order_type.upper(),
                    side=side.lower(),
                    amount=amount,
                    price=stop_price,
                    params=order_params
                )
            except Exception as e:
                logger.error(f"Failed to create conditional order {order_type} for {formatted_symbol}: {e}")
                if self.is_connected:
                    raise e

        # Fallback simulation order dictionary
        order_id = client_order_id or f"MOCK_COND_{int(time.time() * 1000)}"
        cond_order = {
            "id": order_id,
            "clientOrderId": client_order_id or order_id,
            "symbol": formatted_symbol,
            "type": order_type.upper(),
            "side": side.lower(),
            "positionSide": order_params.get("positionSide", "LONG" if side.lower() == "sell" else "SHORT"),
            "amount": amount,
            "origQty": amount,
            "price": stop_price,
            "stopPrice": stop_price,
            "triggerPrice": stop_price,
            "reduceOnly": order_params.get("reduceOnly", True),
            "closePosition": order_params.get("closePosition", False),
            "workingType": order_params.get("workingType", "MARK_PRICE"),
            "status": "open",
            "params": order_params,
            "info": {
                "orderId": order_id,
                "clientOrderId": client_order_id or order_id,
                "symbol": formatted_symbol,
                "type": order_type.upper(),
                "side": side.upper(),
                "stopPrice": str(stop_price),
                "workingType": order_params.get("workingType", "MARK_PRICE"),
                "origQty": str(amount),
                "reduceOnly": order_params.get("reduceOnly", True),
                "closePosition": order_params.get("closePosition", False),
                "status": "NEW"
            }
        }
        if hasattr(self.exchange, '_sim_orders'):
            self.exchange._sim_orders.append(cond_order)
        if hasattr(self.exchange, '_open_orders'):
            self.exchange._open_orders.append(cond_order)
        return cond_order

    def get_market_rules(self, symbol: str) -> Dict[str, Any]:
        """
        Fetches market rules including min order amount, min order cost, and precision.
        """
        formatted_symbol = self.format_symbol(symbol)
        try:
            if hasattr(self.exchange, 'load_markets'):
                markets = self.exchange.load_markets()
                market = markets.get(formatted_symbol, {})
            else:
                market = {}

            if not market and hasattr(self.exchange, 'market'):
                try:
                    market = self.exchange.market(formatted_symbol)
                except Exception:
                    market = {}

            limits = market.get('limits', {})
            amount_limits = limits.get('amount', {})
            cost_limits = limits.get('cost', {})
            precision = market.get('precision', {})

            min_amount = float(amount_limits.get('min', 0.001)) if amount_limits.get('min') is not None else 0.001
            min_cost = float(cost_limits.get('min', 5.0)) if cost_limits.get('min') is not None else 5.0
            price_precision = precision.get('price', 2)
            amount_precision = precision.get('amount', 3)

            return {
                "symbol": formatted_symbol,
                "min_amount": min_amount,
                "min_cost": min_cost,
                "price_precision": price_precision,
                "amount_precision": amount_precision,
                "raw_market": market
            }
        except Exception as e:
            logger.warning(f"Error fetching market rules for {formatted_symbol}: {e}")
            return {
                "symbol": formatted_symbol,
                "min_amount": 0.001,
                "min_cost": 5.0,
                "price_precision": 2,
                "amount_precision": 3,
                "error": str(e)
            }

    def fetch_open_positions(self, symbol: Optional[str] = None) -> list:
        """
        Fetches open positions for the given symbol or all symbols.
        """
        formatted_symbol = self.format_symbol(symbol) if symbol else None
        try:
            if hasattr(self.exchange, 'fetch_positions'):
                if formatted_symbol:
                    positions = self.exchange.fetch_positions([formatted_symbol])
                else:
                    positions = self.exchange.fetch_positions()
                return positions or []
            return []
        except Exception as e:
            logger.error(f"Error fetching open positions: {e}")
            return []

    def fetch_order(self, id: str, symbol: Optional[str] = None) -> Dict[str, Any]:
        """
        Fetches details of a specific order by ID on Binance Futures.
        """
        formatted_symbol = self.format_symbol(symbol) if symbol else None
        try:
            if hasattr(self.exchange, 'fetch_order'):
                return self.exchange.fetch_order(id, formatted_symbol) or {}
            return {"id": id, "status": "closed", "symbol": formatted_symbol}
        except Exception as e:
            logger.error(f"Error fetching order {id}: {e}")
            return {"id": id, "error": str(e)}

    def fetch_open_orders(self, symbol: Optional[str] = None) -> list:
        """
        Fetches pending open orders for symbol or all symbols.
        """
        formatted_symbol = self.format_symbol(symbol) if symbol else None
        try:
            if hasattr(self.exchange, 'fetch_open_orders'):
                return self.exchange.fetch_open_orders(formatted_symbol) or []
            return []
        except Exception as e:
            logger.error(f"Error fetching open orders: {e}")
            return []

    def cancel_all_orders(self, symbol: str) -> Dict[str, Any]:
        """
        Cancels all open orders for a given symbol.
        """
        formatted_symbol = self.format_symbol(symbol)
        try:
            if hasattr(self.exchange, 'cancel_all_orders'):
                result = self.exchange.cancel_all_orders(formatted_symbol)
                logger.info(f"Successfully canceled all open orders for {formatted_symbol}")
                return {"status": "success", "result": result}
            return {"status": "success", "message": "No order cancellation method on exchange"}
        except Exception as e:
            logger.error(f"Failed to cancel all orders for {formatted_symbol}: {e}")
            return {"status": "failed", "error": str(e)}

    def fetch_position_config(self, symbol: str) -> Dict[str, Any]:
        """
        Fetches the active position and account configuration (margin mode and leverage) for the symbol.
        """
        formatted_symbol = self.format_symbol(symbol)
        try:
            positions = self.fetch_open_positions(formatted_symbol)
            if positions:
                pos = positions[0]
                margin_mode = (pos.get("marginMode") or pos.get("marginType") or "ISOLATED").upper()
                leverage = int(pos.get("leverage") or 10)
                return {
                    "status": "success",
                    "symbol": formatted_symbol,
                    "margin_mode": margin_mode,
                    "leverage": leverage,
                    "raw_position": pos
                }
        except Exception as e:
            logger.warning(f"Could not fetch position config for {formatted_symbol}: {e}")

        return {
            "status": "success",
            "symbol": formatted_symbol,
            "margin_mode": "ISOLATED",
            "leverage": 10
        }

    def calculate_min_order_quantity(self, symbol: str, ticker_price: float) -> float:
        """
        Calculates the absolute minimum valid order quantity using CCXT market rules
        (min amount, min cost, market price, amount precision, contract size)
        and safely rounds it using CCXT precision helpers ensuring all exchange limits are satisfied.
        """
        import math
        rules = self.get_market_rules(symbol)
        formatted_symbol = self.format_symbol(symbol)
        raw_market = rules.get("raw_market", {})

        min_amount = float(rules.get("min_amount", 0.001))
        min_cost = float(rules.get("min_cost", 5.0))
        amount_precision = rules.get("amount_precision", 3)
        contract_size = float(raw_market.get("contractSize", 1.0)) if raw_market.get("contractSize") is not None else 1.0

        notional_per_contract = ticker_price * contract_size
        if notional_per_contract <= 0:
            notional_per_contract = ticker_price

        min_qty_for_cost = (min_cost / notional_per_contract) if notional_per_contract > 0 else 0.0
        raw_min_qty = max(min_amount, min_qty_for_cost)

        # Determine step size
        if isinstance(amount_precision, int):
            step_size = 10.0 ** (-amount_precision)
        else:
            step_size = float(amount_precision) if float(amount_precision) > 0 else 0.001

        steps = math.ceil(raw_min_qty / step_size)
        candidate_qty = steps * step_size

        if hasattr(self.exchange, 'amount_to_precision'):
            try:
                candidate_qty = float(self.exchange.amount_to_precision(formatted_symbol, candidate_qty))
            except Exception as e:
                logger.warning(f"amount_to_precision notice: {e}")
                prec_digits = amount_precision if isinstance(amount_precision, int) else 3
                candidate_qty = round(candidate_qty, prec_digits)
        else:
            prec_digits = amount_precision if isinstance(amount_precision, int) else 3
            candidate_qty = round(candidate_qty, prec_digits)

        # Strictly ensure limits satisfied
        while candidate_qty < min_amount or (candidate_qty * notional_per_contract) < min_cost:
            candidate_qty += step_size
            if hasattr(self.exchange, 'amount_to_precision'):
                try:
                    candidate_qty = float(self.exchange.amount_to_precision(formatted_symbol, candidate_qty))
                except Exception:
                    prec_digits = amount_precision if isinstance(amount_precision, int) else 3
                    candidate_qty = round(candidate_qty, prec_digits)

        return candidate_qty


# Alias for Futures handler
CCXTFuturesHandler = CCXTHandler
