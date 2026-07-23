import logging
import threading
from typing import Dict, Any, List

logger = logging.getLogger(__name__)


class DailyTracker:
    """Tracks daily trading statistics in memory with thread safety."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._trades: List[Dict[str, Any]] = []

    def log_trade_result(self, profit_loss: float, is_win: bool) -> None:
        """Record a completed trade's PnL and win/loss status.

        Args:
            profit_loss: Realized profit or loss in USD/USDT.
            is_win: True if trade closed with profit, False otherwise.
        """
        with self._lock:
            self._trades.append({
                "pnl": float(profit_loss),
                "is_win": bool(is_win)
            })
            logger.info(
                f"Logged trade result: PnL=${profit_loss:,.2f}, "
                f"Win={is_win}. Total daily trades: {len(self._trades)}"
            )

    def get_daily_stats(self) -> Dict[str, Any]:
        """Calculate and return daily trading performance statistics.

        Returns:
            Dict containing:
                - total_pnl (float)
                - win_rate (float percentage 0-100)
                - total_trades (int)
                - wins (int)
                - losses (int)
        """
        with self._lock:
            total_trades = len(self._trades)
            if total_trades == 0:
                return {
                    "total_pnl": 0.0,
                    "win_rate": 0.0,
                    "total_trades": 0,
                    "wins": 0,
                    "losses": 0
                }

            total_pnl = sum(t["pnl"] for t in self._trades)
            wins = sum(1 for t in self._trades if t["is_win"])
            losses = total_trades - wins
            win_rate = (wins / total_trades) * 100.0

            return {
                "total_pnl": round(total_pnl, 4),
                "win_rate": round(win_rate, 2),
                "total_trades": total_trades,
                "wins": wins,
                "losses": losses
            }

    def reset_daily_stats(self) -> None:
        """Reset and clear all accumulated daily stats."""
        with self._lock:
            count = len(self._trades)
            self._trades.clear()
            logger.info(f"Daily tracker reset complete. Cleared {count} trades.")


# Singleton / module-level instance and convenience functions
_daily_tracker_instance = DailyTracker()


def log_trade_result(profit_loss: float, is_win: bool) -> None:
    """Module-level helper to log trade result to default DailyTracker."""
    _daily_tracker_instance.log_trade_result(profit_loss, is_win)


def get_daily_stats() -> Dict[str, Any]:
    """Module-level helper to retrieve daily stats from default DailyTracker."""
    return _daily_tracker_instance.get_daily_stats()


def reset_daily_stats() -> None:
    """Module-level helper to reset stats in default DailyTracker."""
    _daily_tracker_instance.reset_daily_stats()
