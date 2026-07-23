"""
Test Runner Script for Futures Cryptocurrency Trading Bot
Sends POST requests to /api/start_bot for top cryptocurrency futures pairs
using the 'vwap_atr_ai' strategy and prints highlighted trade evaluation metrics.
"""

import json
import sys
import time

# Attempt import of requests; fallback to urllib.request wrapper if not installed
try:
    import requests
    USE_REQUESTS = True
except ImportError:
    import urllib.request
    import urllib.error
    USE_REQUESTS = False

# Local FastAPI endpoint URL
ENDPOINT_URL = "http://127.0.0.1:8000/api/start_bot"

# Target Futures Trading Pairs
SYMBOLS = [
    "BTC/USDT:USDT",
    "ETH/USDT:USDT",
    "SOL/USDT:USDT",
    "DOGE/USDT:USDT",
    "XRP/USDT:USDT"
]

# Minimum valid futures trading quantities per pair
MIN_AMOUNTS = {
    "BTC/USDT:USDT": 0.005,
    "ETH/USDT:USDT": 0.05,
    "SOL/USDT:USDT": 0.5,
    "DOGE/USDT:USDT": 100.0,
    "XRP/USDT:USDT": 50.0
}


def send_post_request(url: str, payload: dict) -> dict:
    """
    Helper function to send a JSON POST request using requests or urllib.
    """
    headers = {
        "Content-Type": "application/json",
        "User-Agent": "FuturesTradingBotTestRunner/1.0"
    }

    if USE_REQUESTS:
        try:
            resp = requests.post(url, json=payload, headers=headers, timeout=10)
            return {
                "status_code": resp.status_code,
                "data": resp.json() if resp.status_code == 200 else resp.text
            }
        except Exception as e:
            return {"status_code": 500, "error": str(e)}
    else:
        try:
            json_bytes = json.dumps(payload).encode("utf-8")
            req = urllib.request.Request(url, data=json_bytes, headers=headers, method="POST")
            with urllib.request.urlopen(req, timeout=10) as response:
                resp_bytes = response.read()
                return {
                    "status_code": response.status,
                    "data": json.loads(resp_bytes.decode("utf-8"))
                }
        except urllib.error.HTTPError as e:
            err_text = e.read().decode("utf-8")
            try:
                err_json = json.loads(err_text)
            except Exception:
                err_json = err_text
            return {"status_code": e.code, "error": err_json}
        except Exception as e:
            return {"status_code": 500, "error": str(e)}


def run_tests():
    """
    Iterates through all target symbols, sends trade execution requests,
    and highlights AI approval, entry price, SL, TP, and order status.
    """
    print("\n" + "=" * 75)
    print(" 🚀 FUTURES TRADING BOT ENDPOINT TEST RUNNER")
    print(f" Target Endpoint: {ENDPOINT_URL}")
    print(f" Strategy: vwap_atr_ai | Side: BUY | Leverage: 10x | ATR Multiplier: 2.0")
    print("=" * 75)

    summary_results = []

    for idx, symbol in enumerate(SYMBOLS, 1):
        amount = MIN_AMOUNTS.get(symbol, 0.005)
        payload = {
            "symbol": symbol,
            "specific_strategy": "vwap_atr_ai",
            "timeframe": "15m",
            "side": "buy",
            "leverage": 10,
            "atr_multiplier": 2.0,
            "amount": amount
        }

        print(f"\n[{idx}/{len(SYMBOLS)}] --------------------------------------------------")
        print(f"Testing Symbol : {symbol}")
        print(f"Payload Amount : {amount} base units")

        start_time = time.time()
        res = send_post_request(ENDPOINT_URL, payload)
        latency = (time.time() - start_time) * 1000

        status_code = res.get("status_code")
        data = res.get("data", {})

        if status_code == 200 and isinstance(data, dict):
            strategy_exec = data.get("strategy_execution", {})
            ai_eval = strategy_exec.get("ai_evaluation", {})
            risk_mgmt = strategy_exec.get("risk_management", {})
            order_exec = strategy_exec.get("order_execution", {})

            # Extract metrics
            ai_score = ai_eval.get("score", 0.0)
            is_approved = ai_eval.get("approved", False)
            entry_price = risk_mgmt.get("entry_price", strategy_exec.get("current_price", 0.0))
            stop_loss = risk_mgmt.get("stop_loss", 0.0)
            take_profit = risk_mgmt.get("take_profit", 0.0)

            order_status = order_exec.get("status") if order_exec else "N/A"
            order_id = order_exec.get("order_id", "N/A") if order_exec else "N/A"
            mode = order_exec.get("mode", "SIMULATION") if order_exec else "N/A"
            order_placed = order_status == "success" or order_id != "N/A"

            # Highlights Output
            print(f"Status Code    : {status_code} OK ({latency:.1f}ms)")
            print(f"AI Decision    : {'✅ APPROVED' if is_approved else '❌ REJECTED'} (AI Score: {ai_score:.1f}/100)")
            print(f"Entry Price    : ${entry_price:,.4f}" if entry_price < 10 else f"Entry Price    : ${entry_price:,.2f}")
            print(f"Stop-Loss (SL) : ${stop_loss:,.4f}" if stop_loss < 10 else f"Stop-Loss (SL) : ${stop_loss:,.2f}")
            print(f"Take-Profit(TP): ${take_profit:,.4f}" if take_profit < 10 else f"Take-Profit(TP): ${take_profit:,.2f}")
            print(f"Exchange Order : {'✅ SUCCESS' if order_placed else '❌ FAILED'} (Mode: {mode}, ID: {order_id})")

            summary_results.append({
                "symbol": symbol,
                "approved": is_approved,
                "score": ai_score,
                "entry": entry_price,
                "sl": stop_loss,
                "tp": take_profit,
                "order_success": order_placed
            })
        else:
            err_msg = res.get("error", data)
            print(f"Status Code    : {status_code}")
            print(f"Response Error : {err_msg}")
            summary_results.append({
                "symbol": symbol,
                "approved": False,
                "score": 0,
                "entry": 0,
                "sl": 0,
                "tp": 0,
                "order_success": False
            })

    print("\n" + "=" * 75)
    print(" 📊 SUMMARY EXECUTION REPORT")
    print("=" * 75)
    for item in summary_results:
        app_str = "APPROVED" if item["approved"] else "REJECTED"
        ord_str = "SUCCESS" if item["order_success"] else "FAILED"
        print(f" • {item['symbol']:15s} | AI: {app_str:8s} ({item['score']:5.1f}/100) | SL: ${item['sl']:<9.2f} | TP: ${item['tp']:<9.2f} | Order: {ord_str}")
    print("=" * 75 + "\n")


if __name__ == "__main__":
    run_tests()
