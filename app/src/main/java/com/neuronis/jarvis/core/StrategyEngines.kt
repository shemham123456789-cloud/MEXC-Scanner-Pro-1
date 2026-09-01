package com.neuronis.jarvis.core

import kotlin.math.abs
import kotlin.math.max

object StrategyEngines {
    fun evaluate(kind: StrategyKind, candles: List<Candle>, atr: Double, regime: MarketRegime): StrategySignal {
        if (candles.size < 40) return StrategySignal(kind, SignalDirection.FLAT, 0.0, "Histórico insuficiente", 0.0)
        return when (kind) {
            StrategyKind.TREND -> trend(candles, atr, regime)
            StrategyKind.BREAKOUT -> breakout(candles, atr, regime)
            StrategyKind.SWEEP -> sweep(candles, atr, regime)
            StrategyKind.SMC -> smc(candles, atr, regime)
            StrategyKind.MEAN_REVERSION -> meanReversion(candles, atr, regime)
            StrategyKind.MOMENTUM -> momentum(candles, atr, regime)
        }
    }

    private fun trend(c: List<Candle>, atr: Double, regime: MarketRegime): StrategySignal {
        val closes = c.map { it.close }; val e9 = Indicators.ema(closes, 9); val e21 = Indicators.ema(closes, 21); val e50 = Indicators.ema(closes, 50)
        val adx = Indicators.adx(c); val dist = (e9 - e21) / atr.coerceAtLeast(1e-9)
        val dir = when { e9 > e21 && e21 > e50 -> SignalDirection.LONG; e9 < e21 && e21 < e50 -> SignalDirection.SHORT; else -> SignalDirection.FLAT }
        val fit = if (regime == MarketRegime.TREND_UP || regime == MarketRegime.TREND_DOWN) 1.0 else .55
        val score = (abs(dist).coerceAtMost(2.0) / 2.0 * 45 + (adx / 50).coerceAtMost(1.0) * 35 + fit * 20).coerceAtMost(100.0)
        return StrategySignal(StrategyKind.TREND, dir, score, "EMA 9/21/50 + ADX ${"%.1f".format(adx)}", fit)
    }

    private fun breakout(c: List<Candle>, atr: Double, regime: MarketRegime): StrategySignal {
        val last = c.last(); val prior = c.dropLast(1).takeLast(24)
        val hi = prior.maxOf { it.high }; val lo = prior.minOf { it.low }; val range = hi - lo
        val closeLoc = if (range <= 0) .5 else (last.close - lo) / range
        val volBase = prior.map { it.volume }.average().coerceAtLeast(1e-9)
        val volBoost = (last.volume / volBase).coerceAtMost(3.0)
        val dir = when { last.close > hi + atr * .08 && closeLoc > .9 -> SignalDirection.LONG; last.close < lo - atr * .08 && closeLoc < .1 -> SignalDirection.SHORT; else -> SignalDirection.FLAT }
        val fit = if (regime == MarketRegime.HIGH_VOLATILITY || regime == MarketRegime.TREND_UP || regime == MarketRegime.TREND_DOWN) .95 else .6
        val score = (abs(closeLoc - .5) * 100 * .45 + (volBoost / 3.0) * 35 + fit * 20).coerceAtMost(100.0)
        return StrategySignal(StrategyKind.BREAKOUT, dir, score, "Rango 24 + expansión de volumen ${"%.1f".format(volBoost)}x", fit)
    }

    private fun sweep(c: List<Candle>, atr: Double, regime: MarketRegime): StrategySignal {
        val last = c.last(); val prior = c.dropLast(1).takeLast(18); val hi = prior.maxOf { it.high }; val lo = prior.minOf { it.low }
        val wickUp = last.high - max(last.open, last.close); val wickDown = minOf(last.open, last.close) - last.low
        val bull = last.low < lo && wickDown > atr * .25 && last.close > lo
        val bear = last.high > hi && wickUp > atr * .25 && last.close < hi
        val dir = when { bull -> SignalDirection.LONG; bear -> SignalDirection.SHORT; else -> SignalDirection.FLAT }
        val fit = if (regime == MarketRegime.RANGE || regime == MarketRegime.CHOPPY) .95 else .75
        val score = when { bull || bear -> 76.0 + (max(wickDown, wickUp) / atr.coerceAtLeast(1e-9)).coerceAtMost(1.5) * 14.0; else -> 0.0 }
        return StrategySignal(StrategyKind.SWEEP, dir, score.coerceAtMost(100.0), "Barrido de liquidez + rechazo", fit)
    }

    private fun smc(c: List<Candle>, atr: Double, regime: MarketRegime): StrategySignal {
        val n = 16; val recent = c.takeLast(n); val prev = c.dropLast(n).takeLast(n)
        val rHi = recent.maxOf { it.high }; val rLo = recent.minOf { it.low }; val pHi = prev.maxOf { it.high }; val pLo = prev.minOf { it.low }
        val bullishBos = rHi > pHi + atr * .1 && recent.last().close > pHi
        val bearishBos = rLo < pLo - atr * .1 && recent.last().close < pLo
        val dir = when { bullishBos -> SignalDirection.LONG; bearishBos -> SignalDirection.SHORT; else -> SignalDirection.FLAT }
        val fit = if (regime == MarketRegime.TREND_UP || regime == MarketRegime.TREND_DOWN) .95 else .7
        val score = if (dir == SignalDirection.FLAT) 0.0 else (78.0 + if (fit > .9) 12 else 8).coerceAtMost(100.0)
        return StrategySignal(StrategyKind.SMC, dir, score, "BOS/estructura + desplazamiento", fit)
    }

    private fun meanReversion(c: List<Candle>, atr: Double, regime: MarketRegime): StrategySignal {
        val (mid, upper, lower) = Indicators.bollinger(c, 20, 2.0); val rsi = Indicators.rsi(c); val p = c.last().close
        val dir = when { p < lower && rsi < 35 -> SignalDirection.LONG; p > upper && rsi > 65 -> SignalDirection.SHORT; else -> SignalDirection.FLAT }
        val fit = if (regime == MarketRegime.RANGE || regime == MarketRegime.LOW_VOLATILITY) .95 else .45
        val distance = abs(p - mid) / atr.coerceAtLeast(1e-9)
        val score = if (dir == SignalDirection.FLAT) 0.0 else (70 + (distance.coerceAtMost(2.0) / 2.0) * 18 + (fit * 12)).coerceAtMost(100.0)
        return StrategySignal(StrategyKind.MEAN_REVERSION, dir, score, "Bandas de Bollinger + RSI ${"%.1f".format(rsi)}", fit)
    }

    private fun momentum(c: List<Candle>, atr: Double, regime: MarketRegime): StrategySignal {
        val rsi = Indicators.rsi(c); val (macd, hist) = Indicators.macd(c); val p = c.last().close; val vwap = Indicators.vwap(c)
        val dir = when { p > vwap && rsi > 55 && hist > 0 && macd > 0 -> SignalDirection.LONG; p < vwap && rsi < 45 && hist < 0 && macd < 0 -> SignalDirection.SHORT; else -> SignalDirection.FLAT }
        val fit = if (regime == MarketRegime.TREND_UP || regime == MarketRegime.TREND_DOWN) .9 else .7
        val score = if (dir == SignalDirection.FLAT) 0.0 else (68 + (abs(rsi - 50) / 50) * 20 + fit * 12).coerceAtMost(100.0)
        return StrategySignal(StrategyKind.MOMENTUM, dir, score, "VWAP + RSI + MACD", fit)
    }
}
