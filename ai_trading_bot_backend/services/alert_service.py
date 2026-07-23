"""
Notification and Alert Service for AI Trading Bot.
Handles sending desktop notifications and SMTP email alerts for trade executions
and major loss threshold breaches.
"""

import os
import platform
import logging
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from typing import Dict, Any, Optional

logger = logging.getLogger(__name__)


class AlertService:
    """
    AlertService provides notification mechanisms (desktop & email alerts) for:
    1. Trade Executions (BUY, SELL, Open, Close, TP/SL).
    2. Major Loss Threshold Breaches (Drawdown & Max Loss Limits).
    """

    def __init__(
        self,
        smtp_host: Optional[str] = None,
        smtp_port: Optional[int] = None,
        sender_email: Optional[str] = None,
        sender_password: Optional[str] = None,
        recipient_email: Optional[str] = None,
        enable_desktop: Optional[bool] = None,
        enable_email: Optional[bool] = None,
        max_loss_threshold_usd: float = 50.0,
        max_drawdown_pct: float = 3.0
    ):
        # Email / SMTP Settings with environment variable fallbacks
        self.smtp_host = smtp_host or os.getenv("SMTP_HOST", "smtp.gmail.com")
        self.smtp_port = smtp_port or int(os.getenv("SMTP_PORT", "587"))
        self.sender_email = sender_email or os.getenv("SENDER_EMAIL", "")
        self.sender_password = sender_password or os.getenv("SENDER_PASSWORD", "")
        self.recipient_email = recipient_email or os.getenv("RECIPIENT_EMAIL", self.sender_email)

        # Toggle flags
        env_desktop = os.getenv("ENABLE_DESKTOP_ALERTS", "true").lower() in ("true", "1", "yes")
        env_email = os.getenv("ENABLE_EMAIL_ALERTS", "false").lower() in ("true", "1", "yes")
        
        self.enable_desktop = enable_desktop if enable_desktop is not None else env_desktop
        self.enable_email = enable_email if enable_email is not None else (env_email or bool(self.sender_email and self.sender_password))

        # Risk / Loss thresholds
        self.max_loss_threshold_usd = float(os.getenv("MAX_LOSS_THRESHOLD_USD", str(max_loss_threshold_usd)))
        self.max_drawdown_pct = float(os.getenv("MAX_DRAWDOWN_PCT", str(max_drawdown_pct)))

    def send_desktop_notification(self, title: str, message: str) -> bool:
        """
        Sends a desktop notification using system utilities (Linux notify-send, macOS osascript, or Windows).
        Falls back to logging if notification commands are unavailable or fail.
        """
        if not self.enable_desktop:
            logger.debug("Desktop notifications are disabled.")
            return False

        logger.info(f"[DESKTOP NOTIFICATION] {title}: {message}")
        system = platform.system()

        try:
            if system == "Linux":
                os.system(f'notify-send "{title}" "{message}" >/dev/null 2>&1')
                return True
            elif system == "Darwin":  # macOS
                script = f'display notification "{message}" with title "{title}"'
                os.system(f"osascript -e '{script}' >/dev/null 2>&1")
                return True
            elif system == "Windows":
                # Windows fallback script
                ps_cmd = f'powershell -Command "[reflection.assembly]::loadwithpartialname(\'System.Windows.Forms\'); [System.Windows.Forms.MessageBox]::Show(\'{message}\', \'{title}\')"'
                os.system(f"{ps_cmd} >/dev/null 2>&1")
                return True
            else:
                logger.warning(f"Unsupported OS platform for native desktop alerts: {system}")
                return False
        except Exception as e:
            logger.error(f"Failed to execute desktop notification: {e}")
            return False

    def send_email_alert(self, subject: str, body: str, to_email: Optional[str] = None) -> bool:
        """
        Sends an email alert via SMTP.

        Args:
            subject (str): Email subject line.
            body (str): Email body text (plain text or simple formatting).
            to_email (Optional[str]): Recipient email address.

        Returns:
            bool: True if email sent successfully, False otherwise.
        """
        target_email = to_email or self.recipient_email

        if not self.enable_email:
            logger.info(f"[EMAIL SIMULATION (Disabled)] Subject: {subject} | Body: {body}")
            return False

        if not self.sender_email or not self.sender_password or not target_email:
            logger.warning("[EMAIL ALERT CANCELLED] SMTP sender/recipient credentials not configured in environment.")
            return False

        try:
            msg = MIMEMultipart("alternative")
            msg["Subject"] = subject
            msg["From"] = self.sender_email
            msg["To"] = target_email

            msg.attach(MIMEText(body, "plain"))

            logger.info(f"Connecting to SMTP server {self.smtp_host}:{self.smtp_port}...")
            with smtplib.SMTP(self.smtp_host, self.smtp_port, timeout=10) as server:
                server.starttls()
                server.login(self.sender_email, self.sender_password)
                server.sendmail(self.sender_email, target_email, msg.as_string())

            logger.info(f"Email alert successfully sent to {target_email}: '{subject}'")
            return True
        except Exception as e:
            logger.error(f"Failed to send email alert via SMTP: {e}")
            return False

    def send_trade_execution_alert(
        self,
        symbol: str,
        side: str,
        amount: float,
        price: float,
        stop_loss: Optional[float] = None,
        take_profit: Optional[float] = None,
        leverage: int = 10,
        pnl: Optional[float] = None,
        pnl_pct: Optional[float] = None,
        mode: str = "LIVE"
    ) -> Dict[str, Any]:
        """
        Formats and dispatches alerts when a trade is executed (entry or exit).

        Args:
            symbol (str): Trading pair symbol (e.g. BTC/USDT)
            side (str): Trade direction ('BUY'/'LONG' or 'SELL'/'SHORT')
            amount (float): Order quantity
            price (float): Execution price
            stop_loss (Optional[float]): Stop-Loss level
            take_profit (Optional[float]): Take-Profit level
            leverage (int): Leverage multiplier
            pnl (Optional[float]): Realized PnL in USDT if closing trade
            pnl_pct (Optional[float]): Realized PnL percentage if closing trade
            mode (str): Execution mode ('LIVE' or 'SIMULATION')

        Returns:
            Dict[str, Any]: Notification execution status
        """
        side_clean = side.upper().strip()
        is_closing = pnl is not None or side_clean in ["SELL", "SHORT"]

        if is_closing and pnl is not None:
            pnl_str = f"PnL: {'+' if pnl >= 0 else ''}${pnl:.2f} ({'+' if (pnl_pct or 0) >= 0 else ''}{pnl_pct:.2f}%)"
            title = f"[{mode}] 🔄 TRADE CLOSED - {symbol} ({side_clean})"
            message = (
                f"Trade Closed: {side_clean} {amount} {symbol} @ ${price:,.2f}.\n"
                f"{pnl_str}\n"
                f"Leverage: {leverage}x"
            )
        else:
            sl_str = f"${stop_loss:,.2f}" if stop_loss else "N/A"
            tp_str = f"${take_profit:,.2f}" if take_profit else "N/A"
            title = f"[{mode}] 🚀 TRADE EXECUTED - {symbol} ({side_clean})"
            message = (
                f"Order Executed: {side_clean} {amount} {symbol} @ ${price:,.2f}.\n"
                f"Stop Loss: {sl_str} | Take Profit: {tp_str}\n"
                f"Leverage: {leverage}x"
            )

        desktop_sent = self.send_desktop_notification(title, message)
        email_sent = self.send_email_alert(
            subject=title,
            body=f"AI Trading Bot Alert System\n\n{message}\n\nTimestamp: {os.popen('date').read().strip()}"
        )

        # Check for loss threshold if trade resulted in loss
        if pnl is not None and pnl < 0:
            self.check_and_notify_loss(symbol, pnl, pnl_pct or 0.0)

        return {
            "title": title,
            "message": message,
            "desktop_sent": desktop_sent,
            "email_sent": email_sent
        }

    def send_loss_threshold_alert(
        self,
        symbol: str,
        pnl: float,
        pnl_pct: float,
        threshold_usd: Optional[float] = None,
        threshold_pct: Optional[float] = None
    ) -> Dict[str, Any]:
        """
        Formats and dispatches urgent alerts when a major loss threshold is breached.

        Args:
            symbol (str): Trading pair symbol
            pnl (float): Loss amount (negative number in USD)
            pnl_pct (float): Loss percentage (negative number)
            threshold_usd (Optional[float]): Custom USD loss threshold
            threshold_pct (Optional[float]): Custom percentage drawdown threshold

        Returns:
            Dict[str, Any]: Alert dispatch details
        """
        limit_usd = threshold_usd or self.max_loss_threshold_usd
        limit_pct = threshold_pct or self.max_drawdown_pct

        title = f"🚨 CRITICAL LOSS THRESHOLD BREACH - {symbol}"
        message = (
            f"WARNING: Major loss threshold exceeded on {symbol}!\n"
            f"• Realized Loss: ${pnl:,.2f} USDT ({pnl_pct:.2f}%)\n"
            f"• Max Loss Limit (USD): ${limit_usd:,.2f}\n"
            f"• Max Drawdown Limit (%): {limit_pct:.2f}%\n"
            f"Immediate risk inspection recommended."
        )

        logger.warning(f"[MAJOR LOSS THRESHOLD BREACHED] {message}")

        desktop_sent = self.send_desktop_notification(title, message)
        email_sent = self.send_email_alert(
            subject=f"URGENT: {title}",
            body=f"CRITICAL RISK WARNING\n=====================\n\n{message}"
        )

        return {
            "alert": "MAJOR_LOSS_BREACH",
            "title": title,
            "message": message,
            "desktop_sent": desktop_sent,
            "email_sent": email_sent
        }

    def check_and_notify_loss(self, symbol: str, pnl: float, pnl_pct: float) -> bool:
        """
        Checks if a trade PnL or drawdown breaches the configured loss limits and sends an alert.

        Args:
            symbol (str): Symbol name
            pnl (float): Trade PnL in USD (negative if loss)
            pnl_pct (float): Trade PnL percentage (negative if loss)

        Returns:
            bool: True if loss threshold was breached and alert was dispatched.
        """
        loss_usd = abs(pnl) if pnl < 0 else 0.0
        loss_pct = abs(pnl_pct) if pnl_pct < 0 else 0.0

        if loss_usd >= self.max_loss_threshold_usd or loss_pct >= self.max_drawdown_pct:
            self.send_loss_threshold_alert(symbol=symbol, pnl=pnl, pnl_pct=pnl_pct)
            return True

        return False


# Global default instance for convenience
default_alert_service = AlertService()
