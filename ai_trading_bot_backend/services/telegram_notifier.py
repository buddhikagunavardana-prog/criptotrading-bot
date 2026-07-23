import os
import json
import logging
from typing import Optional, Union

try:
    import requests
    REQUESTS_AVAILABLE = True
except ImportError:
    import urllib.request
    import urllib.error
    REQUESTS_AVAILABLE = False

logger = logging.getLogger(__name__)


class TelegramNotifier:
    """Service to send trading alerts and performance reports to Telegram via Bot API."""

    def __init__(
        self,
        bot_token: Optional[str] = None,
        chat_id: Optional[str] = None
    ) -> None:
        """Initialize TelegramNotifier with Bot Token and Chat ID.

        If parameters are not provided, checks environment variables:
        TELEGRAM_BOT_TOKEN / BOT_TOKEN and TELEGRAM_CHAT_ID / CHAT_ID.
        """
        self.bot_token = (
            bot_token
            or os.getenv("TELEGRAM_BOT_TOKEN")
            or os.getenv("BOT_TOKEN")
        )
        self.chat_id = (
            chat_id
            or os.getenv("TELEGRAM_CHAT_ID")
            or os.getenv("CHAT_ID")
        )

        if not self.bot_token or not self.chat_id:
            logger.warning(
                "TelegramNotifier initialized without BOT_TOKEN or CHAT_ID. "
                "Notifications will be disabled until credentials are configured."
            )

    @property
    def is_configured(self) -> bool:
        """Check if Telegram credentials are set."""
        return bool(self.bot_token and self.chat_id)

    def _send_message(self, message_html: str) -> bool:
        """Send formatted HTML message to Telegram API.

        Args:
            message_html: HTML-formatted text string.

        Returns:
            bool: True if sent successfully, False otherwise.
        """
        if not self.is_configured:
            logger.warning("Telegram notification skipped: missing BOT_TOKEN or CHAT_ID.")
            return False

        url = f"https://api.telegram.org/bot{self.bot_token}/sendMessage"
        payload = {
            "chat_id": self.chat_id,
            "text": message_html,
            "parse_mode": "HTML",
            "disable_web_page_preview": True,
        }

        if REQUESTS_AVAILABLE:
            try:
                response = requests.post(url, json=payload, timeout=10)
                response.raise_for_status()
                res_data = response.json()
                if res_data.get("ok"):
                    logger.info("Telegram message dispatched successfully.")
                    return True
                else:
                    logger.error(f"Telegram API error: {res_data.get('description')}")
                    return False
            except requests.RequestException as e:
                logger.error(f"Failed to dispatch Telegram message via requests: {e}")
                return False
        else:
            try:
                data_bytes = json.dumps(payload).encode("utf-8")
                req = urllib.request.Request(
                    url,
                    data=data_bytes,
                    headers={"Content-Type": "application/json"},
                    method="POST"
                )
                with urllib.request.urlopen(req, timeout=10) as resp:
                    res_data = json.loads(resp.read().decode("utf-8"))
                    if res_data.get("ok"):
                        logger.info("Telegram message dispatched successfully via urllib.")
                        return True
                    else:
                        logger.error(f"Telegram API error: {res_data.get('description')}")
                        return False
            except Exception as e:
                logger.error(f"Failed to dispatch Telegram message via urllib: {e}")
                return False

    def send_trade_entry(
        self,
        symbol: str,
        side: str,
        entry_price: Union[float, int],
        stop_loss: Union[float, int],
        take_profit: Union[float, int],
        leverage: Union[float, int]
    ) -> bool:
        """Send trade entry alert to Telegram.

        Args:
            symbol: Trading pair symbol (e.g., BTC/USDT).
            side: 'BUY'/'LONG' or 'SELL'/'SHORT'.
            entry_price: Executed entry price.
            stop_loss: Stop-Loss price level.
            take_profit: Take-Profit price level.
            leverage: Applied leverage multiplier.

        Returns:
            bool: True if notification sent successfully.
        """
        clean_side = side.strip().upper()
        is_long = clean_side in ("BUY", "LONG")
        side_emoji = "🟢" if is_long else "🔴"
        action_text = "BUY (LONG)" if is_long else "SELL (SHORT)"

        message = (
            f"<b>{side_emoji} TRADE ENTRY: {action_text}</b>\n"
            f"━━━━━━━━━━━━━━━━━━━━━━━\n"
            f"• <b>Symbol:</b> <code>{symbol}</code>\n"
            f"• <b>Entry Price:</b> ${float(entry_price):,.2f}\n"
            f"• <b>Leverage:</b> {leverage}x\n"
            f"• <b>Stop Loss:</b> ${float(stop_loss):,.2f}\n"
            f"• <b>Take Profit:</b> ${float(take_profit):,.2f}\n"
            f"━━━━━━━━━━━━━━━━━━━━━━━"
        )
        return self._send_message(message)

    def send_trade_close(
        self,
        symbol: str,
        side: str,
        close_price: Union[float, int],
        profit_loss: Union[float, int],
        is_win: bool,
        current_balance: Union[float, int]
    ) -> bool:
        """Send trade position exit alert to Telegram.

        Args:
            symbol: Trading pair symbol (e.g., BTC/USDT).
            side: 'BUY'/'LONG' or 'SELL'/'SHORT'.
            close_price: Executed exit price.
            profit_loss: Realized profit or loss in USD.
            is_win: True if trade closed with profit, False otherwise.
            current_balance: Updated account balance in USDT.

        Returns:
            bool: True if notification sent successfully.
        """
        clean_side = side.strip().upper()
        pnl = float(profit_loss)
        pnl_emoji = "🟢" if is_win else "🔴"
        result_badge = "PROFITABLE WIN" if is_win else "LOSS"
        pnl_formatted = f"+${pnl:,.2f}" if pnl >= 0 else f"-${abs(pnl):,.2f}"

        message = (
            f"<b>{pnl_emoji} TRADE CLOSED: {clean_side} {symbol}</b>\n"
            f"━━━━━━━━━━━━━━━━━━━━━━━\n"
            f"• <b>Outcome:</b> <b>{result_badge}</b>\n"
            f"• <b>Close Price:</b> ${float(close_price):,.2f}\n"
            f"• <b>Realized P&amp;L:</b> {pnl_formatted}\n"
            f"• <b>Current Balance:</b> ${float(current_balance):,.2f}\n"
            f"━━━━━━━━━━━━━━━━━━━━━━━"
        )
        return self._send_message(message)

    def send_daily_summary(
        self,
        total_pnl: Union[float, int],
        daily_balance: Union[float, int],
        win_rate: Union[float, int],
        total_trades: int,
        wins: int,
        losses: int
    ) -> bool:
        """Send daily performance summary report to Telegram.

        Args:
            total_pnl: Cumulative daily realized PnL.
            daily_balance: Current total account balance.
            win_rate: Win rate percentage (0-100 or 0.0-1.0).
            total_trades: Total trades executed today.
            wins: Number of winning trades.
            losses: Number of losing trades.

        Returns:
            bool: True if notification sent successfully.
        """
        pnl = float(total_pnl)
        pnl_emoji = "📈" if pnl >= 0 else "📉"
        pnl_formatted = f"+${pnl:,.2f}" if pnl >= 0 else f"-${abs(pnl):,.2f}"

        # Normalize win rate if passed as decimal (e.g. 0.65 -> 65.0)
        win_rate_val = float(win_rate)
        if 0.0 <= win_rate_val <= 1.0 and total_trades > 0:
            win_rate_val *= 100.0

        message = (
            f"<b>📊 DAILY TRADING SUMMARY</b> {pnl_emoji}\n"
            f"━━━━━━━━━━━━━━━━━━━━━━━\n"
            f"• <b>Daily P&amp;L:</b> {pnl_formatted}\n"
            f"• <b>Account Balance:</b> ${float(daily_balance):,.2f}\n"
            f"• <b>Win Rate:</b> {win_rate_val:.1f}%\n"
            f"• <b>Total Trades:</b> {total_trades} (🟢 {wins} Wins | 🔴 {losses} Losses)\n"
            f"━━━━━━━━━━━━━━━━━━━━━━━"
        )
        return self._send_message(message)
