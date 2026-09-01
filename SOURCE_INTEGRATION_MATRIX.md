# JARVIS PRO - Integration Matrix

## Implemented in this build
- MEXC Futures base domain: `https://api.mexc.com`.
- Dynamic USDT Futures universe via contract catalog + all-ticker ranking.
- MTF: 4H -> 1H -> 15M -> 5M, with 1M live data.
- Six independent strategy engines: Trend, Breakout, Liquidity Sweep, SMC, Mean Reversion, Momentum.
- Strategy toggles and ensemble voting.
- Regime classifier + adaptive PASSIVE/BALANCED/AGGRESSIVE risk mode.
- Dynamic Entry/SL/TP1/TP2/R:R.
- Liquidity/depth/funding context where public fields are available.
- Touch chart with candles, VWAP, zones, ENTRY/SL/TP overlays.
- Scanner for smaller/larger USDT Futures markets by ranked opportunity.
- Paper trading loop and local outcome memory.
- Walk-forward style backtest split, OOS metrics and Monte Carlo distribution.
- Simple overfit warning based on train/OOS degradation.
- Jarvis local explainable text interface.
- Compressed historical store.
- No real order execution in this build.

## Intentionally not faked
- No random confidence values.
- No claim that 90/100 quality means a 90% win rate.
- No invented news, order-flow trades, or backtest performance.
- No sandbox assumption for MEXC.

## Future extension points
- True WebSocket streaming for tick/depth/trades.
- External LLM provider adapter with local fallback.
- Vision module for screenshot/chart-image interpretation.
- Event/news/economic calendar connector.
- Protected live order router with explicit user confirmation and hard risk limits.
- Rich persistent experiment/version database.
