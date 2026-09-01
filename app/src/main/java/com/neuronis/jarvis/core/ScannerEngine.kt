package com.neuronis.jarvis.core

object ScannerEngine {
    fun scan(symbol: String, candles: List<Candle>, snapshot: MarketSnapshot, config: AnalysisConfig): ScannerSignal? {
        if (candles.size < 100 || snapshot.price <= 0) return null
        val tick = when {
            snapshot.price >= 10000 -> 0.1
            snapshot.price >= 100 -> 0.01
            snapshot.price >= 1 -> 0.0001
            snapshot.price >= 0.1 -> 0.00001
            else -> 0.000001
        }
        val a = MarketAnalysisEngine.analyzeGeneric(symbol, tick, candles, snapshot, config)
        return ScannerSignal(symbol, a.direction, a.state, a.quality, a.entry, a.stop, a.tp2, a.rr, if (snapshot.price > 0) a.expectedMove / snapshot.price * 100.0 else 0.0, a.regime)
    }
}
