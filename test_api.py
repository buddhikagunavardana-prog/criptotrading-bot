#!/usr/bin/env python3
"""
Binance Testnet Connectivity and Account Status Verifier
Powered by python-binance and python-dotenv
"""

import os
import sys
from dotenv import load_dotenv

def test_binance_connectivity():
    print("====================================================")
    print("  Binance Testnet Credentials & Connection Verifier")
    print("====================================================\n")

    # 1. Load API keys from environment
    # First, try loading from .env, if not present fall back to loading from .dev
    if os.path.exists(".env"):
        print("[INFO] Loading configuration from '.env' file...")
        load_dotenv(dotenv_path=".env")
    elif os.path.exists(".dev"):
        print("[INFO] Loading configuration from '.dev' file...")
        load_dotenv(dotenv_path=".dev")
    else:
        print("[WARNING] Neither '.env' nor '.dev' was found. Reading system environments directly...")

    api_key = os.getenv("BINANCE_API_KEY")
    api_secret = os.getenv("BINANCE_SECRET_KEY")

    if not api_key or not api_secret:
        print("[ERROR] Failed: BINANCE_API_KEY or BINANCE_SECRET_KEY is missing or undefined!")
        print("Please configure them inside your secrets panel or add them into your .env/.dev file.")
        sys.exit(1)

    print(f"[INFO] API Key detected: {api_key[:6]}...{api_key[-6:] if len(api_key) > 12 else ''}")
    
    # 2. Attempt Connection via python-binance
    try:
        from binance.client import Client
        from binance.exceptions import BinanceAPIException, BinanceRequestException
    except ImportError:
        print("[ERROR] Required package 'python-binance' is not installed in your execution environment.")
        print("Please run: pip install python-binance")
        sys.exit(1)

    print("[INFO] Initializing python-binance client connected to Testnet...")
    try:
        # Initialize client in Testnet mode
        client = Client(api_key, api_secret, testnet=True)
        
        # Sync system time with Binance Server Time
        try:
            import time
            server_time = client.get_server_time()
            server_time_ms = server_time.get("serverTime")
            local_time_ms = int(time.time() * 1000)
            time_offset = server_time_ms - local_time_ms
            client.timestamp_offset = time_offset
            print(f"[INFO] Time synchronization successful:")
            print(f"  - Local Time (ms):  {local_time_ms}")
            print(f"  - Server Time (ms): {server_time_ms}")
            print(f"  - Calculated Offset: {time_offset} ms")
        except Exception as sync_err:
            print(f"[WARNING] Could not synchronize time with Binance server: {sync_err}")
        
        # 3. Check Binance System Status
        print("[INFO] Fetching Binance system status...")
        system_status = client.get_system_status()
        status_msg = system_status.get("msg", "Unknown")
        status_code = system_status.get("status", -1)
        
        status_str = "Normal" if status_code == 0 else "System Maintenance/Issues"
        print(f"[SUCCESS] Connection Successful! Binance System Status: {status_str} (Code: {status_code}, Message: {status_msg})")

        # 4. Fetch Account Balance (USDT)
        print("[INFO] Fetching account details and USDT balances from testnet...")
        # Use an increased recvWindow to avoid signature issues related to timing
        account_info = client.get_account(recvWindow=60000)
        balances = account_info.get("balances", [])
        
        usdt_balance_entry = next((item for item in balances if item["asset"] == "USDT"), None)
        
        print("\n================ ACCOUNT SUMMARY ================")
        if usdt_balance_entry:
            free_bal = usdt_balance_entry.get("free", "0.00")
            locked_bal = usdt_balance_entry.get("locked", "0.00")
            print(f" Asset:           USDT")
            print(f" Available/Free:  {free_bal} USDT")
            print(f" Locked in Trade: {locked_bal} USDT")
        else:
            print(" No USDT balance entry detected in this testnet account portfolio.")
            # Print non-zero balances for utility
            non_zero = [b for b in balances if float(b["free"]) > 0 or float(b["locked"]) > 0]
            if non_zero:
                print("\n Other Non-Zero Assets found:")
                for b in non_zero:
                    print(f" - {b['asset']}: Free={b['free']} / Locked={b['locked']}")
            else:
                print(" Portfolio is currently empty.")
        print("=================================================\n")
        
        print("[SUCCESS] All connectivity & portfolio tests executed flawlessly.")

    except BinanceAPIException as api_err:
        print("\n[ERROR] Binance API Exception occurred during connection:")
        print(f"  - Code:    {api_err.code}")
        print(f"  - Message: {api_err.message}")
        print("Please check if your keys are correct and active for the Binance Testnet environment.")
    except BinanceRequestException as req_err:
        print(f"\n[ERROR] Request Exception: Failed to contact Binance Servers. {req_err}")
    except Exception as general_err:
        print(f"\n[ERROR] An unexpected error occurred: {general_err}")

if __name__ == "__main__":
    test_binance_connectivity()
