package com.neuronis.jarvis.core

/** Turns a losing trade into a repeatable, bounded lesson rather than a one-off score change. */
object ErrorAnalyzer {
    enum class ErrorClass { BAD_LOCATION, WEAK_STRUCTURE, REGIME_MISMATCH, EXCESS_VOLATILITY, LOW_EDGE, UNKNOWN }

    fun classify(a: AnalysisSnapshot, wasWin: Boolean): ErrorClass {
        if (wasWin) return ErrorClass.UNKNOWN
        val location = a.zones.minByOrNull { kotlin.math.abs(a.entry - it.mid) }
        return when {
            a.regime == MarketRegime.HIGH_VOLATILITY -> ErrorClass.EXCESS_VOLATILITY
            location == null || kotlin.math.abs(a.entry - location.mid) > a.atr * 1.5 -> ErrorClass.BAD_LOCATION
            a.trend15 != a.direction && a.trend60 != a.direction -> ErrorClass.WEAK_STRUCTURE
            a.calibratedProbability < .58 || a.quality < 72 -> ErrorClass.LOW_EDGE
            a.riskMode == RiskMode.AGGRESSIVE && a.regime in setOf(MarketRegime.RANGE, MarketRegime.CHOPPY) -> ErrorClass.REGIME_MISMATCH
            else -> ErrorClass.REGIME_MISMATCH
        }
    }
}
