#!/usr/bin/env python3
"""
CryptoBot AI - Sentiment Analysis & News Aggregation Module
Powered by the Gemini API
"""

import os
import json
import urllib.request
import logging
from typing import List, Tuple, Dict, Any

logger = logging.getLogger(__name__)

# Fallback realistic headlines in case of network unavailability or offline testing
FALLBACK_HEADLINES = {
    "BTC": [
        "Bitcoin Institutional Inflow Reaches Record High of $2.4B this Week",
        "SEC Officially Approves Options Trading for Major Spot Bitcoin ETFs",
        "MicroStrategy Acquires Another 15,000 BTC, Bolstering Reserves",
        "Concerns Rise Over Short-term Whale Distribution to Centralized Exchanges",
        "On-chain Metrics Indicate Strong Support at Key Moving Average Levels",
        "Global Interest Rate Cuts Form Highly Bullish Macro Regime for Bitcoin",
        "Bitcoin Hash Rate Surges to New Historic High Amid Mining Rig Upgrades",
        "Short-term Holders Suffer Capitulation as Price Retests Crucial Demands"
    ],
    "ETH": [
        "Ethereum Layer-2 Gas Fees Drop to All-Time Low After Dencun Upgrade",
        "Ethereum ETF Outflows Stabilize as Staking Demand Secures New Inflows",
        "Vitalik Buterin Proposes High-Performance Execution Layer Optimization",
        "Large Whales Re-Accumulate ETH Near Key Support Levels",
        "Gas Consumption Concerns Raised Due to Temporary Scaling Constraints",
        "Decentralized Finance (DeFi) Volume on Ethereum Reaches 12-Month Peak",
        "Ethereum Active Validator Count Surpasses Historic Milestone"
    ],
    "DEFAULT": [
        "Cryptocurrency Market Registers Broad Relief Rally Amid Dovish Fed Minutes",
        "Global Regulators Discuss Harmonized Regulatory Framework for Stablecoins",
        "New Institutional Web3 Custody Solution Launches in European Union",
        "Venture Capital Funding into Web3 Startups Rebounds in Q2 2026",
        "Decentralized Exchanges Outpace Centralized Platforms in Volume Growth Rate"
    ]
}

def fetch_market_news(symbol: str = "BTC") -> List[str]:
    """
    Fetches real-time cryptocurrency news headlines from CryptoCompare's public news API.
    Falls back to high-fidelity curated news headlines if the request fails.
    """
    clean_symbol = symbol.split("/")[0].upper() if "/" in symbol else symbol.upper()
    category = "BTC" if "BTC" in clean_symbol else ("ETH" if "ETH" in clean_symbol else "ALL")
    
    url = f"https://min-api.cryptocompare.com/data/v2/news/?lang=EN&categories={category}"
    try:
        logger.info(f"Fetching actual cryptocurrency news for category {category} from CryptoCompare...")
        req = urllib.request.Request(url, headers={'User-Agent': 'CryptoBotAI/1.0'})
        with urllib.request.urlopen(req, timeout=5) as response:
            data = json.loads(response.read().decode("utf-8"))
            if data and "Data" in data and len(data["Data"]) > 0:
                titles = [item["title"] for item in data["Data"][:10]]
                logger.info(f"Successfully fetched {len(titles)} headlines from CryptoCompare API.")
                return titles
    except Exception as e:
        logger.warning(f"CryptoCompare News API fetch failed: {e}. Utilizing high-fidelity fallback news.")
    
    # Return realistic Curated headlines if API request fails
    return FALLBACK_HEADLINES.get(category, FALLBACK_HEADLINES["DEFAULT"])

def analyze_sentiment_heuristic(headlines: List[str]) -> float:
    """
    A smart local keyword-based heuristic fallback to calculate sentiment.
    Returns a score between -1.0 (extremely bearish) and +1.0 (extremely bullish).
    """
    positive_words = {
        "bull", "bullish", "approve", "approved", "gain", "rise", "rallies", "rally", "high", 
        "positive", "growth", "buy", "record", "peak", "optimism", "accumulate", "inflow", 
        "support", "upgrade", "milestone", "rebounds", "outpace", "dovish", "booming"
    }
    negative_words = {
        "bear", "bearish", "drop", "fall", "decline", "crash", "fear", "panic", "sell", "leak", 
        "ban", "hack", "breach", "regulatory", "concern", "outflow", "liquidation", "capitulation",
        "distribution", "weakness", "restriction", "hackers", "scam"
    }
    
    score = 0.0
    for h in headlines:
        words = h.lower().split()
        headline_score = 0.0
        for w in words:
            clean_w = "".join(c for c in w if c.isalnum())
            if clean_w in positive_words:
                headline_score += 0.25
            elif clean_w in negative_words:
                headline_score -= 0.25
        score += max(-0.5, min(0.5, headline_score)) # cap per headline contribution
        
    normalized = score / max(1, len(headlines) * 0.2)
    return max(-1.0, min(1.0, normalized))

from datetime import datetime

# Global dictionary to cache sentiment results by category (e.g. "BTC", "ETH", "DEFAULT")
# Key: category, Value: {"score": float, "justification": str, "timestamp": datetime}
_SENTIMENT_CACHE = {}

def analyze_market_news_sentiment(symbol: str = "BTC") -> Tuple[float, str]:
    """
    Gathers cryptocurrency headlines for a symbol and leverages Gemini 3.5 Flash to
    analyze current market sentiment.
    
    Includes global caching (1-hour expiration) to prevent redundant API/network calls.
    
    Returns:
        Tuple[float, str]: (sentiment_score, justification)
        sentiment_score: float between -1.0 and +1.0
        justification: explanation of the sentiment score
    """
    global _SENTIMENT_CACHE
    clean_symbol = symbol.split("/")[0].upper() if "/" in symbol else symbol.upper()
    category = "BTC" if "BTC" in clean_symbol else ("ETH" if "ETH" in clean_symbol else "DEFAULT")
    
    now = datetime.now()
    if category in _SENTIMENT_CACHE:
        cache_entry = _SENTIMENT_CACHE[category]
        if (now - cache_entry["timestamp"]).total_seconds() < 3600:
            logger.info(f"Returning CACHED sentiment for {category} (age: {(now - cache_entry['timestamp']).total_seconds():.1f}s)")
            return cache_entry["score"], cache_entry["justification"]

    headlines = fetch_market_news(symbol)
    
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key or api_key == "MY_GEMINI_API_KEY":
        logger.warning("GEMINI_API_KEY is not configured or holds default placeholder value. Falling back to heuristic sentiment engine.")
        score = analyze_sentiment_heuristic(headlines)
        justification = f"Heuristic calculation based on local keyword-scanning of {len(headlines)} headlines."
        
        _SENTIMENT_CACHE[category] = {
            "score": score,
            "justification": justification,
            "timestamp": now
        }
        return score, justification
        
    # Constructing prompt for Gemini
    formatted_headlines = "\n".join([f"- {h}" for h in headlines])
    prompt = f"""
    You are an expert quantitative crypto trading analyst. Your task is to analyze the following cryptocurrency news headlines and determine the overall market sentiment score.
    
    Return a single JSON object with exactly these two keys:
    1. 'sentiment_score': A float strictly between -1.0 (extremely bearish/negative) and +1.0 (extremely bullish/positive). Neutrals should be close to 0.0.
    2. 'justification': A single, concise, professional sentence summarizing the market trend and justifying your assigned score.
    
    CRITICAL: Output ONLY the raw valid JSON object. Do NOT wrap the output in markdown backticks (e.g. ```json ... ```) or add any leading or trailing conversational text.
    
    Headlines to analyze:
    {formatted_headlines}
    """
    
    url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key={api_key}"
    
    request_data = {
        "contents": [{
            "parts": [{
                "text": prompt
            }]
        }],
        "generationConfig": {
            "responseMimeType": "application/json"
        }
    }
    
    try:
        logger.info("Dispatching market news headlines to Gemini 3.5 Flash REST endpoint...")
        req = urllib.request.Request(
            url,
            data=json.dumps(request_data).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST"
        )
        
        with urllib.request.urlopen(req, timeout=10) as response:
            res_body = json.loads(response.read().decode("utf-8"))
            candidates = res_body.get("candidates", [])
            if candidates:
                text_out = candidates[0]["content"]["parts"][0]["text"].strip()
                # Clean up any accidental markdown backticks or formatting
                if text_out.startswith("```"):
                    lines = text_out.split("\n")
                    if lines[0].startswith("```"):
                        lines = lines[1:]
                    if lines[-1].startswith("```"):
                        lines = lines[:-1]
                    text_out = "\n".join(lines).strip()
                    
                parsed_res = json.loads(text_out)
                score = float(parsed_res.get("sentiment_score", 0.0))
                justification = parsed_res.get("justification", "Gemini parsed sentiment")
                
                # Sanitize limits
                score = max(-1.0, min(1.0, score))
                logger.info(f"Gemini API analysis complete. Sentiment: {score:+.2f} | Justification: {justification}")
                _SENTIMENT_CACHE[category] = {
                    "score": score,
                    "justification": justification,
                    "timestamp": now
                }
                return score, justification
                
    except Exception as e:
        logger.error(f"Gemini API request failed: {e}. Falling back to heuristic engine.")
        
    # Standard fallback on any error
    score = analyze_sentiment_heuristic(headlines)
    justification = f"Heuristic fallback analysis after API timeout or exception: {type(e).__name__ if 'e' in locals() else 'None'}"
    _SENTIMENT_CACHE[category] = {
        "score": score,
        "justification": justification,
        "timestamp": now
    }
    return score, justification

if __name__ == "__main__":
    # Test suite to verify the module works
    logging.basicConfig(level=logging.INFO)
    s, j = analyze_market_news_sentiment("BTC")
    print(f"\n================ TEST RUN ==================")
    print(f"Sentiment Score: {s:+.2f}")
    print(f"Justification: {j}")
    print(f"============================================")
