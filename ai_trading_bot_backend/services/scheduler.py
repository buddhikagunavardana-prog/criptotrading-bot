import time
import logging
import threading
from datetime import datetime
from typing import Callable, Optional, Dict, Any, Union

logger = logging.getLogger(__name__)

try:
    from apscheduler.schedulers.background import BackgroundScheduler
    APSCHEDULER_AVAILABLE = True
except ImportError:
    APSCHEDULER_AVAILABLE = False


class FallbackCronScheduler:
    """Lightweight background cron scheduler fallback when APScheduler is unavailable."""

    def __init__(self) -> None:
        self._jobs: list[Dict[str, Any]] = []
        self._running = False
        self._thread: Optional[threading.Thread] = None

    def add_job(
        self,
        func: Callable,
        trigger: str = "cron",
        hour: int = 18,
        minute: int = 0,
        id: Optional[str] = None
    ) -> None:
        self._jobs.append({
            "func": func,
            "trigger": trigger,
            "hour": hour,
            "minute": minute,
            "id": id or f"job_{len(self._jobs)}",
            "last_run_day": None
        })
        logger.info(f"Scheduled job '{id or 'cron_job'}' registered for daily execution at {hour:02d}:{minute:02d}.")

    def _worker(self) -> None:
        while self._running:
            now = datetime.now()
            for job in self._jobs:
                if job["trigger"] == "cron":
                    if now.hour == job["hour"] and now.minute == job["minute"]:
                        today_str = now.strftime("%Y-%m-%d")
                        if job["last_run_day"] != today_str:
                            job["last_run_day"] = today_str
                            try:
                                logger.info(f"Triggering scheduled job '{job['id']}'...")
                                job["func"]()
                            except Exception as e:
                                logger.error(f"Error executing scheduled job '{job['id']}': {e}", exc_info=True)
            time.sleep(15)

    def start(self) -> None:
        if not self._running:
            self._running = True
            self._thread = threading.Thread(target=self._worker, daemon=True)
            self._thread.start()
            logger.info("Background cron scheduler started.")

    def shutdown(self, wait: bool = True) -> None:
        if self._running:
            self._running = False
            if self._thread and wait:
                self._thread.join(timeout=2.0)
            logger.info("Background cron scheduler shut down.")


def create_daily_scheduler() -> Union[Any, FallbackCronScheduler]:
    """Factory function to instantiate BackgroundScheduler or FallbackCronScheduler."""
    if APSCHEDULER_AVAILABLE:
        logger.info("Initializing APScheduler BackgroundScheduler.")
        return BackgroundScheduler()
    else:
        logger.info("Initializing FallbackCronScheduler.")
        return FallbackCronScheduler()
