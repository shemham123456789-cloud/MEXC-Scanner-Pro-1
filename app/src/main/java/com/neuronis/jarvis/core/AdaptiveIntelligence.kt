package com.neuronis.jarvis.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Context-aware, bounded adaptation layer. It never rewrites indicators or
 * optimizes against the current candle; it only changes weights by small,
 * evidence-bounded amounts supplied by LearningStore.
 */
object AdaptiveIntelligence {
    fun contextKey(symbol: String, direction: SignalDirection, regime: MarketRegime, risk: RiskMode, zone: ZoneType?, quality: Int): String {
        val q = (quality / 10) * 10
        return listOf(symbol.uppercase(), direction.name, regime.name, risk.name, zone?.name ?: "NONE", q).joinToString("|")
    }

    fun strategyContextKey(symbol: String, strategy: StrategyKind, direction: SignalDirection, regime: MarketRegime, zone: ZoneType?): String =
        listOf(symbol.uppercase(), strategy.name, direction.name, regime.name, zone?.name ?: "NONE").joinToString("|")

    fun selectTop(symbol: String, signals: List<StrategySignal>, enabled: Set<StrategyKind>, regime: MarketRegime, price: Double, atr: Double, zones: List<Zone>, config: AnalysisConfig): List<StrategySignal> {
        if (signals.isEmpty()) return emptyList()
        val cap = config.adaptiveEngineCount.coerceIn(1, enabled.size.coerceAtLeast(1))
        return signals
            .filter { it.strategy in enabled }
            .sortedByDescending { signal ->
                val rel = config.strategyReliability[signal.strategy] ?: 0.5
                val zoneBoost = zoneAffinity(signal.direction, price, atr, zones)
                val learned = config.learningAdjustments["STRATEGY:${signal.strategy.name}"] ?: 0.0
                val zone = nearestZone(signal.direction, price, zones)
                val contextLearned = config.learningAdjustments["STRATCTX:${strategyContextKey(symbol, signal.strategy, signal.direction, regime, zone?.type)}"] ?: 0.0
                signal.score * (0.72 + rel * 0.56) * (0.78 + signal.regimeFit * 0.22) * (0.85 + zoneBoost * 0.30) + learned * 18.0 + contextLearned * 22.0
            }
            .take(cap)
    }

    private fun nearestZone(direction: SignalDirection, price: Double, zones: List<Zone>): Zone? = zones.filter {
        when (direction) {
            SignalDirection.LONG -> it.type in setOf(ZoneType.DEMAND, ZoneType.ORDER_BLOCK, ZoneType.FVG, ZoneType.LIQUIDITY_LOW, ZoneType.PIVOT, ZoneType.VWAP)
            SignalDirection.SHORT -> it.type in setOf(ZoneType.SUPPLY, ZoneType.ORDER_BLOCK, ZoneType.FVG, ZoneType.LIQUIDITY_HIGH, ZoneType.PIVOT, ZoneType.VWAP)
            else -> true
        }
    }.minByOrNull { abs(price - it.mid) }

    fun zoneAffinity(direction: SignalDirection, price: Double, atr: Double, zones: List<Zone>): Double {
        if (direction == SignalDirection.FLAT || atr <= 0.0) return 0.0
        val relevant = when (direction) {
            SignalDirection.LONG -> zones.filter { it.type in setOf(ZoneType.DEMAND, ZoneType.ORDER_BLOCK, ZoneType.FVG, ZoneType.LIQUIDITY_LOW, ZoneType.VWAP) }
            SignalDirection.SHORT -> zones.filter { it.type in setOf(ZoneType.SUPPLY, ZoneType.ORDER_BLOCK, ZoneType.FVG, ZoneType.LIQUIDITY_HIGH, ZoneType.VWAP) }
            else -> emptyList()
        }
        if (relevant.isEmpty()) return 0.0
        val best = relevant.minOf { distanceToZone(price, it) / atr }
        return (1.0 - best / 1.8).coerceIn(0.0, 1.0)
    }

    private fun distanceToZone(price: Double, z: Zone): Double = when {
        price in z.low..z.high -> 0.0
        price < z.low -> z.low - price
        else -> price - z.high
    }

    fun mtfSoftBias(tf5: SignalDirection, tf15: SignalDirection, tf60: SignalDirection, tf240: SignalDirection, side: SignalDirection): Double {
        val weights = listOf(tf5 to 1.0, tf15 to 2.0, tf60 to 3.0, tf240 to 4.0)
        val total = weights.sumOf { it.second }
        val score = weights.sumOf { (d, w) -> if (d == side) w else if (d == opposite(side)) -w * 0.55 else 0.0 }
        return score / total
    }

    private fun opposite(d: SignalDirection) = when (d) { SignalDirection.LONG -> SignalDirection.SHORT; SignalDirection.SHORT -> SignalDirection.LONG; else -> SignalDirection.FLAT }

    fun decisionMargin(longScore: Double, shortScore: Double): Double {
        val hi = max(longScore, shortScore); val lo = min(longScore, shortScore)
        return if (hi <= 1e-9) 0.0 else (hi - lo) / hi
    }
}
