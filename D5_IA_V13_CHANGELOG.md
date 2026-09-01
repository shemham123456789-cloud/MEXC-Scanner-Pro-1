# D.5 IA v13 — Adaptive Market Intelligence

## Added
- Live MEXC Futures WebSocket adapter on `wss://contract.mexc.com/edge`.
- Live ticker, trades, incremental depth and 1m kline subscriptions.
- Automatic reconnect and 15s ping interval.
- Microstructure engine using depth imbalance, trade delta, delta velocity and spread.
- Order-flow bias now participates in entry selection and risk behavior.
- Probability calibration layer using historical prediction buckets.
- Prediction is recorded at the time the paper signal is opened, not at the time it closes.
- Pattern fingerprints for contextual learning.
- Learning Governor that rejects weak/unstable self-modifications.
- Stronger error classification for bad location, weak structure, regime mismatch, excess volatility and low edge.
- Live price cache so generic scanner symbols can close Paper positions correctly.
- REST depth now calculates spread and depth imbalance when WebSocket is unavailable.

## Safety / anti-overfit
- Learning remains bounded and sample-gated.
- One loss cannot disable a strategy.
- Probability calibration needs a minimum sample count.
- No live order execution or API keys are embedded.

## MEXC protocol
The Futures WebSocket implementation follows the documented public contract stream at `wss://contract.mexc.com/edge` with `sub.ticker`, `sub.deal`, `sub.depth` and `sub.kline` subscriptions.
