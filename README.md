# CryptoFilter App
![Image](https://github.com/user-attachments/assets/c402430a-cc32-46be-84cb-c7d50aaa30be)

A mobile application that filters cryptocurrency perpetual trading pairs based on technical indicators and market data. Built with Python for data processing and Android (Kotlin/Jetpack Compose) for UI.

## Features

**Python Backend (CCXT Integration):**
- Fetches USDT perpetual pairs from Bitget exchange
- Technical analysis using:
  - EMA (100-period) proximity checks
  - Price correlation with BTC
  - 3-day high/low percentage calculations
  - Candle color detection (green/red)
  - Funding rate checks
  - Volume filtering (>50k)

**Android Frontend:**
- Modern Jetpack Compose UI
- Interactive table display of filtered coins
- Real-time data refresh capability
- Error handling and loading states
- Color-coded candle status indicators

## Installation

1. **Python Requirements** (src/main/python):
```bash
pip install ccxt pandas pandas_ta
