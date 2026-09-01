# D.5 IA v12 — Adaptive Intelligence Upgrade

## Goal
Turn the previous deterministic ensemble into a context-aware decision system that can learn from paper/OOS outcomes without allowing one bad trade to rewrite the strategy.

## Implemented
- Adaptive engine selector: maximum 3 engines per decision, selected by regime fit, location and learned reliability.
- Flexible confirmation: disagreement on one timeframe does not automatically cancel a setup.
- Location-first decision layer: demand/supply, support/resistance, order blocks, FVG, liquidity and VWAP are scored before entry quality is finalized.
- Regime classification: trend up/down, range, choppy, high/low volatility.
- Dynamic risk mode: PASSIVE/BALANCED/AGGRESSIVE with volatility/location guards.
- Contextual memory: symbol + direction + regime + risk + zone + quality band.
- Strategy-context memory: symbol + strategy + direction + regime + zone.
- Bounded learning: minimum samples, smoothing and hard adjustment caps.
- Error memory: losing trades are classified into BAD_LOCATION, WEAK_STRUCTURE, REGIME_MISMATCH, EXCESS_VOLATILITY or LOW_EDGE.
- Learning is applied to future decisions; the current candle never teaches itself.
- Causal walk-forward validation with purge gap, multiple folds, OOS profit factor, stability and anti-overfit score.
- Conservative same-candle SL/TP resolution.
- Monte Carlo stress distribution.
- Correct MACD line/signal/histogram calculation.
- Volatility function now respects its requested period.
- Diversified MEXC universe: leaders + middle + deterministic tail sample, instead of only the largest contracts.
- Selected scanner symbol remains live and is refreshed independently of the preset assets.
- App label/version changed to D.5 IA.
- Quant UI exposes WF, OOS PF, stability and anti-overfit metrics.

## Important limitation
This is adaptive statistical memory, not a magical self-training neural network. The app must accumulate sufficient paper/OOS evidence before learning can have meaningful influence. No 90% win-rate guarantee is claimed.
