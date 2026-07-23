import os
import json
import logging
from dotenv import load_dotenv
import google.generativeai as genai

# Configure logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("test_gemini_connection")

def test_gemini_api():
    logger.info("Initializing standalone Gemini API connection test...")
    
    # 1. Load the GEMINI_API_KEY from the .dev or .env file
    if os.path.exists(".dev"):
        logger.info("Found .dev file. Loading environment variables from .dev...")
        load_dotenv(dotenv_path=".dev")
    elif os.path.exists(".env"):
        logger.info("Found .env file. Loading environment variables from .env...")
        load_dotenv(dotenv_path=".env")
    else:
        logger.info("No explicit .dev or .env file found. Loading default environment...")
        load_dotenv()
        
    api_key = os.getenv("GEMINI_API_KEY")
    if not api_key:
        logger.error("GEMINI_API_KEY is not defined in .dev, .env, or system environment!")
        print("\n[ERROR] Authentication failed: GEMINI_API_KEY is missing.")
        return

    # Masking key for printing
    masked_key = api_key[:4] + "..." + api_key[-4:] if len(api_key) > 8 else "***"
    logger.info(f"GEMINI_API_KEY successfully loaded: {masked_key}")

    # Configure Google Generative AI
    try:
        genai.configure(api_key=api_key)
        logger.info("google-generativeai client configured successfully.")
    except Exception as e:
        logger.error(f"Failed to configure google-generativeai: {e}")
        return

    # 2. Create a dummy market summary string
    dummy_market_summary = {
        "analysis": "High volatility, recent bearish FVG formed, strong downward momentum",
        "volatility": "HIGH",
        "trend_direction": "BEARISH",
        "indicator_status": "Overbought RSI on lower timeframes"
    }
    
    # 3. Use prompt logic matching strategy_engine.py
    prompt = f"""
You are an expert AI Trading Co-pilot. Your task is to dynamically adjust the maximum scoring weights of four technical indicators based on recent market conditions:
1. RSI (Relative Strength Index)
2. MA (Moving Averages)
3. FVG (Fair Value Gaps)
4. OB (Order Blocks)

The default base weightings are:
- rsi: 40.0
- ma: 60.0
- fvg: 20.0
- ob: 20.0

Analyze this market summary data:
{json.dumps(dummy_market_summary, indent=2)}

Guidelines:
- If volatility is high, decrease the weight of Trend/MA indicators (which lag in volatile or ranging markets) and increase the weight of SMC/Price Action indicators (FVG and OB).
- If volatility is low or there is a strong consistent trend, keep or increase MA weighting to prioritize trend-following.
- Adjust weights dynamically. Ensure the weight values are reasonable positive floats.

Return ONLY a valid JSON object with the following schema:
{{
  "rsi": <float>,
  "ma": <float>,
  "fvg": <float>,
  "ob": <float>
}}
Do not include any explanation or markdown blocks outside of the JSON.
"""

    logger.info("Prompt constructed. Sending request to 'gemini-3.5-flash'...")
    
    try:
        # Load the model
        model = genai.GenerativeModel('gemini-3.5-flash')
        
        # Send model request with structured JSON configuration
        response = model.generate_content(
            prompt,
            generation_config={"response_mime_type": "application/json"}
        )
        
        response_text = response.text.strip()
        logger.info("Successfully received response from Gemini API!")
        
        print("\n" + "="*50)
        print("RAW GEMINI RESPONSE:")
        print("="*50)
        print(response_text)
        print("="*50)
        
        # 4. Parse the JSON response
        try:
            parsed_weights = json.loads(response_text)
            print("\nPARSED WEIGHTS ANALYSIS:")
            print("-"*50)
            for key, val in parsed_weights.items():
                print(f" - {key.upper()}: {val}")
            print("-"*50)
            print("\n[SUCCESS] The Gemini API integration is functional and fully responsive!")
        except json.JSONDecodeError as jde:
            logger.error(f"Failed to parse Gemini response as JSON: {jde}")
            print(f"\n[ERROR] Parsing error: Gemini did not return a valid JSON format. Raw output was: {response_text}")
            
    except Exception as e:
        logger.error(f"An error occurred during API communication: {e}")
        print(f"\n[ERROR] API Call Failed: {str(e)}")

if __name__ == "__main__":
    test_gemini_api()
