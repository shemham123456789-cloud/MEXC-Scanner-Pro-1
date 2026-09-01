package com.neuronis.jarvis.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

object Indicators {
    fun ema(values: List<Double>, period: Int): Double {
        if (values.isEmpty()) return 0.0
        val p = period.coerceAtLeast(2)
        val k = 2.0 / (p + 1.0)
        var e = values.first()
        for (i in 1 until values.size) e = values[i] * k + e * (1.0 - k)
        return e
    }

    fun emaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val p = period.coerceAtLeast(2)
        val k = 2.0 / (p + 1.0)
        var e = values.first()
        return buildList(values.size) {
            add(e)
            for (i in 1 until values.size) { e = values[i] * k + e * (1.0 - k); add(e) }
        }
    }

    fun trueRanges(candles: List<Candle>): List<Double> {
        if (candles.isEmpty()) return emptyList()
        return buildList(candles.size) {
            add(candles.first().high - candles.first().low)
            for (i in 1 until candles.size) {
                val c = candles[i]; val p = candles[i - 1]
                add(max(c.high - c.low, max(abs(c.high - p.close), abs(c.low - p.close))))
            }
        }
    }

    fun atr(candles: List<Candle>, period: Int = 14): Double = ema(trueRanges(candles), period).coerceAtLeast(0.0)

    fun rsi(candles: List<Candle>, period: Int = 14): Double {
        if (candles.size <= period) return 50.0
        var gain = 0.0; var loss = 0.0
        val start = candles.size - period
        for (i in start until candles.size) {
            val d = candles[i].close - candles[i - 1].close
            if (d >= 0) gain += d else loss -= d
        }
        val ag = gain / period; val al = loss / period
        if (al <= 1e-12) return 100.0
        val rs = ag / al
        return (100.0 - 100.0 / (1.0 + rs)).coerceIn(0.0, 100.0)
    }

    fun macd(candles: List<Candle>): Pair<Double, Double> {
        val closes = candles.map { it.close }
        if (closes.size < 26) return 0.0 to 0.0
        val e12 = emaSeries(closes, 12); val e26 = emaSeries(closes, 26)
        val line = e12.indices.map { e12[it] - e26[it] }
        val signal = ema(line, 9)
        val m = line.last(); return m to (m - signal)
    }

    fun adx(candles: List<Candle>, period: Int = 14): Double {
        if (candles.size < period + 2) return 0.0
        val trs = ArrayList<Double>(); val plus = ArrayList<Double>(); val minus = ArrayList<Double>()
        for (i in 1 until candles.size) {
            val c = candles[i]; val p = candles[i - 1]
            trs += max(c.high - c.low, max(abs(c.high - p.close), abs(c.low - p.close)))
            val up = c.high - p.high; val dn = p.low - c.low
            plus += if (up > dn && up > 0) up else 0.0
            minus += if (dn > up && dn > 0) dn else 0.0
        }
        if (trs.isEmpty()) return 0.0
        val tr = ema(trs, period).coerceAtLeast(1e-9)
        val pdi = 100.0 * ema(plus, period) / tr
        val mdi = 100.0 * ema(minus, period) / tr
        val dx = 100.0 * abs(pdi - mdi) / (pdi + mdi).coerceAtLeast(1e-9)
        return dx.coerceIn(0.0, 100.0)
    }

    fun vwap(candles: List<Candle>, period: Int = 96): Double {
        val slice = candles.takeLast(period.coerceAtMost(candles.size))
        if (slice.isEmpty()) return 0.0
        var pv = 0.0; var vol = 0.0
        slice.forEach { val typical = (it.high + it.low + it.close) / 3.0; pv += typical * it.volume; vol += it.volume }
        return if (vol > 0) pv / vol else slice.last().close
    }

    fun bollinger(candles: List<Candle>, period: Int = 20, mult: Double = 2.0): Triple<Double, Double, Double> {
        val values = candles.takeLast(period.coerceAtMost(candles.size)).map { it.close }
        if (values.isEmpty()) return Triple(0.0, 0.0, 0.0)
        val mean = values.average(); val sd = sqrt(values.map { (it - mean).pow(2) }.average())
        return Triple(mean, mean + mult * sd, mean - mult * sd)
    }

    fun volatilityPct(candles: List<Candle>, period: Int = 20): Double {
        val atr = atr(candles, period.coerceAtLeast(2)); val price = candles.lastOrNull()?.close ?: 0.0
        return if (price > 0) atr / price * 100.0 else 0.0
    }

    fun slope(values: List<Double>): Double {
        if (values.size < 3) return 0.0
        val n = values.size.toDouble(); val sx = (0 until values.size).sum().toDouble(); val sy = values.sum()
        var sxy = 0.0; var sx2 = 0.0
        values.indices.forEach { i -> sxy += i * values[i]; sx2 += i * i }
        val denom = n * sx2 - sx * sx
        return if (abs(denom) < 1e-12) 0.0 else (n * sxy - sx * sy) / denom
    }

    fun normalized(value: Double, low: Double, high: Double): Double {
        if (high <= low) return 0.5
        return ((value - low) / (high - low)).coerceIn(0.0, 1.0)
    }
}
