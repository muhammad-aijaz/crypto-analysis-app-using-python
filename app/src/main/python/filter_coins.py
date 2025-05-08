import ccxt
import pandas as pd
import pandas_ta as ta
from typing import List, Dict, Optional, Tuple
import json

# Initialize the exchange (Bitget)
exchange = ccxt.bitget({
    'enableRateLimit': True,
    'options': {
        'defaultType': 'swap'
    }
})

def get_perpetual_usdt_pairs() -> List[str]:
    """Get all USDT perpetual trading pairs"""
    try:
        markets = exchange.load_markets()
        return [
            symbol for symbol, market in markets.items()
            if market.get('swap') and market.get('quote') == 'USDT'
        ]
    except Exception as e:
        return []

def fetch_ohlcv(symbol: str, timeframe: str = '1h', limit: int = 150) -> Optional[pd.DataFrame]:
    """Fetch OHLCV data for a symbol"""
    try:
        ohlcv = exchange.fetch_ohlcv(symbol, timeframe, limit=limit)
        if not ohlcv:
            return None

        df = pd.DataFrame(ohlcv, columns=['timestamp', 'open', 'high', 'low', 'close', 'volume'])
        df['timestamp'] = pd.to_datetime(df['timestamp'], unit='ms')
        return df if len(df) >= 2 else None
    except Exception:
        return None

def get_candle_color(df: pd.DataFrame) -> str:
    """Determine if previous candle was green (up) or red (down)"""
    if df is None or len(df) < 2:
        return "gray"
    prev_open = df['open'].iloc[-2]
    prev_close = df['close'].iloc[-2]
    return "green" if prev_close > prev_open else "red"

def check_price_near_ema(
    df: pd.DataFrame,
    ema_period: int = 100,
    min_threshold: float = 0.5,
    max_threshold: float = 2.0
) -> Tuple[bool, Optional[bool]]:
    """Check if price is near EMA and whether it's below or above"""
    if df is None or len(df) < ema_period:
        return False, None

    df['EMA'] = ta.ema(df['close'], length=ema_period)
    prev_close = df['close'].iloc[-2]
    prev_ema = df['EMA'].iloc[-2]

    if pd.isna(prev_ema):
        return False, None

    percentage_diff = abs((prev_close - prev_ema) / prev_ema) * 100
    return (
        min_threshold <= percentage_diff <= max_threshold,
        prev_close < prev_ema
    )

def calculate_3day_high_low(df: pd.DataFrame) -> Tuple[Optional[float], Optional[float], Optional[float], Optional[float]]:
    """Calculate 3-day high and low percentages"""
    if df is None or len(df) < 72:
        return None, None, None, None

    three_day_df = df.iloc[-73:-1]  # Last 72 periods (3 days for 1h timeframe)
    three_day_high = three_day_df['high'].max()
    three_day_low = three_day_df['low'].min()
    prev_price = df['close'].iloc[-2]

    high_percentage = ((three_day_high - prev_price) / prev_price) * 100
    low_percentage = ((prev_price - three_day_low) / prev_price) * 100

    return three_day_high, three_day_low, high_percentage, low_percentage

def fetch_funding_rate(symbol: str) -> Optional[float]:
    """Fetch funding rate for a symbol"""
    try:
        funding_info = exchange.fetch_funding_rate(symbol)
        return float(funding_info.get('fundingRate', 0.0))
    except Exception:
        return None

def calculate_correlation(coin_df: pd.DataFrame, btc_df: pd.DataFrame) -> Optional[float]:
    """Calculate correlation with BTC"""
    if coin_df is None or btc_df is None:
        return None

    min_length = min(len(coin_df), len(btc_df))
    if min_length < 2:
        return None

    coin_close = coin_df['close'].iloc[-min_length:]
    btc_close = btc_df['close'].iloc[-min_length:]

    return coin_close.corr(btc_close)

def filter_coins() -> List[Dict[str, any]]:
    """Main filtering function"""
    symbols = get_perpetual_usdt_pairs()
    if not symbols:
        return []

    btc_df = fetch_ohlcv('BTC/USDT:USDT', '1h')
    if btc_df is None:
        return []

    filtered_symbols = []

    for symbol in symbols:  # Removed the [:50] limit to process all symbols
        try:
            # Skip BTC pair to avoid self-comparison
            if 'BTC/USDT' in symbol:
                continue

            df = fetch_ohlcv(symbol, '1h')
            if df is None:
                continue

            # Correlation check
            correlation = calculate_correlation(df, btc_df)
            if correlation is None or correlation > 0.59:
                continue

            # EMA proximity check
            near_ema, price_below_ema = check_price_near_ema(df)
            if not near_ema:
                continue

            # Candle color check
            candle_color = get_candle_color(df)
            volume = float(df['volume'].iloc[-2])

            # Validate candle color relative to EMA position
            if (price_below_ema and candle_color != "red") or (not price_below_ema and candle_color != "green"):
                continue

            # Volume filter
            if volume < 50000:
                continue

            # 3-day high/low check
            high, low, high_perc, low_perc = calculate_3day_high_low(df)
            if None in (high, low, high_perc, low_perc):
                continue

            if high_perc <= 3 and low_perc <= 3:
                continue

            # Funding rate
            funding_rate = fetch_funding_rate(symbol)
            if funding_rate is None:
                funding_rate = 0.0

            filtered_symbols.append({
                'symbol': symbol.replace(':USDT', ''),
                'candle': candle_color,
                'pema': "Below" if price_below_ema else "Above",
                'high3d': round(float(high_perc), 2),
                'low3d': round(float(low_perc), 2),
                'volume': int(volume),
                'funding': round(float(funding_rate), 6),
                'corrBtc': round(float(correlation), 2)
            })

        except Exception:
            continue

    return filtered_symbols

def on_button_click() -> str:
    """Main function called from Android app"""
    try:
        filtered_coins = filter_coins()
        if not filtered_coins:
            return json.dumps({'status': 'success', 'message': 'No coins met the filter conditions', 'data': []})
        return json.dumps({'status': 'success', 'data': filtered_coins})
    except Exception as e:
        return json.dumps({'status': 'error', 'message': str(e)})