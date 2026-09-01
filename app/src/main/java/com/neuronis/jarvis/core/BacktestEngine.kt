package com.neuronis.jarvis.core

import kotlin.math.max
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Causal validation lab. Every decision only sees candles strictly before the
 * decision index. OOS folds are separated by a purge gap to reduce boundary leakage.
 */
object BacktestEngine {
    fun run(asset: AssetType, candlesInput: List<Candle>, config: AnalysisConfig = AnalysisConfig()): BacktestResult {
        val candles = candlesInput.sortedBy { it.time }.distinctBy { it.time }.takeLast(60000)
        if (candles.size < 700) return empty(asset, candles.size)
        val folds = walkForward(candles, config)
        val oos = folds.flatMap { it.returns }
        val firstTrain = folds.firstOrNull()?.trainReturns.orEmpty()
        val all = firstTrain + oos
        val mc = monteCarlo(oos.ifEmpty { all }, 1000, 42)
        val stability = stabilityScore(folds)
        val overfit = folds.any { it.trainReturns.size >= 12 && it.oosAverage < it.trainAverage * .45 } || stability < .45
        val oosPf = profitFactor(oos)
        val anti = (stability * .55 + (1.0 - (if (overfit) 1.0 else 0.0)) * .20 + minOf(1.0, oos.size / 100.0) * .25).coerceIn(0.0, 1.0)
        return BacktestResult(asset, candles.size, all.size, all.count { it > 0 }, all.count { it <= 0 }, pctWin(all), avg(all), profitFactor(all), maxDrawdown(all), all.sum(),
            oos.size, pctWin(oos), avg(oos), sharpe(oos.ifEmpty { all }), mc.first, mc.second, mc.third, overfit, oosPf, folds.size, stability * 100.0, anti * 100.0, true)
    }

    private data class Fold(val trainReturns: List<Double>, val returns: List<Double>) {
        val trainAverage get() = avg(trainReturns)
        val oosAverage get() = avg(returns)
    }

    private fun walkForward(candles: List<Candle>, config: AnalysisConfig): List<Fold> {
        val out = mutableListOf<Fold>()
        val n = candles.size; val trainSize = (n * .48).toInt(); val testSize = (n * .12).toInt(); val purge = 240
        var start = 0
        while (start + trainSize + purge + testSize <= n && out.size < 5) {
            val trainEnd = start + trainSize; val testStart = trainEnd + purge; val testEnd = testStart + testSize
            val train = simulate(candles.subList(start, trainEnd), config)
            val oos = simulate(candles.subList(testStart, testEnd), config)
            if (oos.isNotEmpty()) out += Fold(train, oos)
            start += max(120, (testSize * .65).toInt())
        }
        if (out.isEmpty()) {
            val split = (n * .65).toInt(); val testStart = (split + purge).coerceAtMost(n - 100)
            out += Fold(simulate(candles.subList(0, split), config), simulate(candles.subList(testStart, n), config))
        }
        return out
    }

    private fun simulate(candles: List<Candle>, config: AnalysisConfig): List<Double> {
        val out = ArrayList<Double>(); var i = 240
        while (i < candles.size - 36) {
            val prefix = candles.subList(0, i)
            val p = prefix.last().close
            val fake = MarketSnapshot(AssetType.BTC, p, 0.0, 0.0, p, p, 0.0, prefix.last().time)
            val a = MarketAnalysisEngine.analyze(AssetType.BTC, prefix, fake, config)
            if (a.direction != SignalDirection.FLAT && a.quality >= config.minQuality && a.rr >= config.rrMinimum && a.state in setOf(SignalState.CONFIRMING, SignalState.READY)) out += resolve(a.entry, a, candles, i)
            i += 6
        }
        return out
    }

    private fun resolve(entry: Double, a: AnalysisSnapshot, future: List<Candle>, start: Int): Double {
        val risk = abs(entry - a.stop).coerceAtLeast(a.atr * .5).coerceAtLeast(1e-9)
        for (j in start until minOf(start + 36, future.size)) {
            val b = future[j]
            val sl = if (a.direction == SignalDirection.LONG) b.low <= a.stop else b.high >= a.stop
            val tp = if (a.direction == SignalDirection.LONG) b.high >= a.tp2 else b.low <= a.tp2
            if (sl && tp) return -1.0
            if (sl) return -1.0
            if (tp) return (abs(a.tp2 - entry) / risk).coerceAtMost(5.0)
        }
        val last = future[minOf(start + 35, future.lastIndex)].close
        return when (a.direction) { SignalDirection.LONG -> ((last - entry) / risk).coerceIn(-1.0, 1.0); SignalDirection.SHORT -> ((entry - last) / risk).coerceIn(-1.0, 1.0); else -> 0.0 }
    }

    private fun pctWin(r: List<Double>) = if (r.isEmpty()) 0.0 else r.count { it > 0 } * 100.0 / r.size
    private fun avg(r: List<Double>) = if (r.isEmpty()) 0.0 else r.average()
    private fun profitFactor(r: List<Double>): Double { val w = r.filter { it > 0 }.sum(); val l = -r.filter { it < 0 }.sum(); return when { l > 1e-9 -> w / l; w > 0 -> 99.0; else -> 0.0 } }
    private fun maxDrawdown(r: List<Double>): Double { var eq = 0.0; var peak = 0.0; var dd = 0.0; r.forEach { eq += it; peak = max(peak, eq); dd = max(dd, peak - eq) }; return dd }
    private fun sharpe(r: List<Double>): Double { if (r.size < 2) return 0.0; val m = r.average(); val sd = sqrt(r.map { (it - m) * (it - m) }.average()).coerceAtLeast(1e-9); return m / sd * sqrt(r.size.toDouble()) }
    private fun stabilityScore(folds: List<Fold>): Double {
        if (folds.isEmpty()) return 0.0
        val positive = folds.count { it.oosAverage > 0 }.toDouble() / folds.size
        val pfGood = folds.count { profitFactor(it.returns) >= 1.05 }.toDouble() / folds.size
        return (positive * .55 + pfGood * .45).coerceIn(0.0, 1.0)
    }
    private fun monteCarlo(r: List<Double>, runs: Int, seed: Int): Triple<Double, Double, Double> {
        if (r.isEmpty()) return Triple(0.0, 0.0, 0.0)
        val rnd = Random(seed); val finals = ArrayList<Double>(runs)
        repeat(runs) { var sum = 0.0; repeat(r.size) { sum += r[rnd.nextInt(r.size)] }; finals += sum }
        finals.sort(); return Triple(finals[(finals.size * .05).toInt().coerceAtMost(finals.lastIndex)], finals[(finals.size * .50).toInt().coerceAtMost(finals.lastIndex)], finals[(finals.size * .95).toInt().coerceAtMost(finals.lastIndex)])
    }
    private fun empty(asset: AssetType, candles: Int) = BacktestResult(asset, candles, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false, 0.0, 0, 0.0, 0.0, true)
}
