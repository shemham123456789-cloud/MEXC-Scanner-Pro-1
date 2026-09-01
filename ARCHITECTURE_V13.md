# D.5 IA v13 Architecture

```text
MEXC REST + Futures WebSocket
        |
        +--> candles / ticker / depth / trades
        |
        v
Microstructure Engine
        |
        v
Market Context -> Regime -> Zones -> MTF Structure
        |
        v
Adaptive Strategy Selector
        |
        +--> Trend
        +--> Breakout
        +--> Sweep
        +--> SMC
        +--> Mean Reversion
        +--> Momentum
        |
        v
Decision Engine -> LONG / SHORT / WAIT
        |
        +--> Entry
        +--> SL / TP1 / TP2
        +--> Risk Mode
        |
        v
Paper Result
        |
        +--> Error Analyzer
        +--> Pattern Fingerprint
        +--> Calibration Store
        +--> Strategy/Context Reliability
        |
        v
Learning Governor
        |
        +--> approve only evidence-backed stable changes
        +--> reject weak OOS improvements
```

## Important design rule
The system learns from completed paper outcomes and historical OOS data. It does not train itself on the current candle or modify parameters after one isolated loss.
