#!/usr/bin/env python3
"""
Performance Tracking Script for Paper Trading Mode.
Calculates Win Rate, Total Profit/Loss, and Max Drawdown based solely on 'paper_trade_orders' table.
"""

import os
import sys
import json
from datetime import datetime
from sqlalchemy import create_engine, text

def calculate_performance():
    # Retrieve database URL from environment
    database_url = os.getenv("DATABASE_URL", "postgresql://postgres:postgres@localhost:5432/trading_db")
    
    print("==========================================================")
    print("      CryptoBot AI - Paper Trading Performance Report     ")
    print("==========================================================")
    print(f"Database: {database_url.split('@')[-1] if '@' in database_url else database_url}")
    print(f"Timestamp: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("----------------------------------------------------------")

    try:
        engine = create_engine(database_url)
        with engine.connect() as conn:
            # Check if paper_trade_orders table exists
            table_check = conn.execute(text(
                "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'paper_trade_orders')"
            )).scalar()
            
            if not table_check:
                # If using SQLite or table doesn't exist, we handles graceful check
                print("Error: 'paper_trade_orders' table not found in the database.")
                print("Please ensure the backend has run at least once to create the tables.")
                return

            # Retrieve all SELL orders to calculate performance
            orders_query = text("""
                SELECT timestamp, pair, type, price, amount, total_usdt, profit_loss, profit_loss_pct 
                FROM paper_trade_orders 
                ORDER BY timestamp ASC
            """)
            result = conn.execute(orders_query).fetchall()

            if not result:
                print("No paper trading orders found yet in 'paper_trade_orders' table.")
                print("Start active trading bots to generate paper trading activity!")
                return

            # Separate and count orders
            buys = [row for row in result if row[2] == 'BUY']
            sells = [row for row in result if row[2] == 'SELL']

            print(f"Total Completed Trade Cycles: {len(sells)}")
            print(f"Total BUY Orders: {len(buys)}")
            print(f"Total SELL Orders: {len(sells)}")
            print("----------------------------------------------------------")

            if not sells:
                print("No completed trade cycles (BUY followed by SELL) to calculate performance metrics.")
                return

            # 1. Total Profit/Loss
            total_pnl = sum(row[6] for row in sells if row[6] is not None)
            
            # 2. Win Rate
            wins = sum(1 for row in sells if row[6] is not None and row[6] > 0)
            win_rate = (wins / len(sells)) * 100.0

            # 3. Max Drawdown Calculation
            # Initial starting balance of the paper wallet (default: 100 USDT)
            initial_balance = 100.0
            balance_history = [initial_balance]
            
            current_balance = initial_balance
            for sell_order in sells:
                pnl = sell_order[6] if sell_order[6] is not None else 0.0
                current_balance += pnl
                balance_history.append(current_balance)

            # Compute Peak and Drawdowns
            max_drawdown = 0.0
            peak = initial_balance
            
            for balance in balance_history:
                if balance > peak:
                    peak = balance
                drawdown = ((peak - balance) / peak) * 100.0 if peak > 0 else 0.0
                if drawdown > max_drawdown:
                    max_drawdown = drawdown

            # Format and Output Results
            print(f"Performance Metrics Summary:")
            print(f"  • Total Profit/Loss: {total_pnl:+.4f} USDT ({((total_pnl / initial_balance) * 100.0):+.2f}%)")
            print(f"  • Win Rate:          {win_rate:.2f}% ({wins} wins out of {len(sells)} trades)")
            print(f"  • Max Drawdown:      {max_drawdown:.2f}%")
            print("----------------------------------------------------------")
            
            # Detailed Trades List
            print("Completed Trades History:")
            print("Timestamp            | Pair       | Buy Price  | Sell Price | Amount     | Profit/Loss (USDT)")
            print("-----------------------------------------------------------------------------------------")
            
            # Match BUYS and SELLS chronologically for display
            matched_trades = []
            buy_map = {} # pair -> last buy
            for order in result:
                pair = order[1]
                order_type = order[2]
                if order_type == 'BUY':
                    buy_map[pair] = order
                elif order_type == 'SELL' and pair in buy_map:
                    b_order = buy_map[pair]
                    matched_trades.append({
                        "timestamp": order[0].strftime('%Y-%m-%d %H:%M:%S') if isinstance(order[0], datetime) else str(order[0]),
                        "pair": pair,
                        "buy_price": b_order[3],
                        "sell_price": order[3],
                        "amount": order[4],
                        "pnl": order[6]
                    })
            
            for trade in matched_trades:
                print(f"{trade['timestamp']}  | {trade['pair']:<10} | ${trade['buy_price']:<10,.2f} | ${trade['sell_price']:<10,.2f} | {trade['amount']:<10.4f} | {trade['pnl']:+.4f} USDT")
            print("==========================================================")

    except Exception as e:
        print(f"An error occurred while calculating paper trading performance: {e}")

if __name__ == "__main__":
    calculate_performance()
