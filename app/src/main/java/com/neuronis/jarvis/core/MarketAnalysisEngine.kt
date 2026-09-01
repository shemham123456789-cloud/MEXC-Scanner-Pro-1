package com.neuronis.jarvis.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** D.5 IA decision engine: location first, regime second, adaptive ensemble third. */
object MarketAnalysisEngine {
    fun analyze(asset: AssetType, baseCandles: List<Candle>, snapshot: MarketSnapshot, config: AnalysisConfig = AnalysisConfig()): AnalysisSnapshot =
        analyzeInternal(asset.apiSymbol, baseCandles, snapshot, tick(asset), config)

    fun analyzeGeneric(symbol: String, tickSize: Double, baseCandles: List<Candle>, snapshot: MarketSnapshot, config: AnalysisConfig = AnalysisConfig()): AnalysisSnapshot =
        analyzeInternal(symbol, baseCandles, snapshot, tickSize, config)

    private fun analyzeInternal(symbol: String, baseCandles: List<Candle>, snapshot: MarketSnapshot, tickSize: Double, config: AnalysisConfig): AnalysisSnapshot {
        if (baseCandles.size < 80) return empty(snapshot)
        val c = baseCandles.sortedBy { it.time }.distinctBy { it.time }.takeLast(5000)
        val c5 = aggregate(c, 5); val c15 = aggregate(c, 15); val c60 = aggregate(c, 60); val c240 = aggregate(c, 240)
        if (c15.size < 30 || c60.size < 20) return empty(snapshot)
        val primary = c5.takeLast(360)
        val atr = Indicators.atr(primary, 14).coerceAtLeast(tickSize * 2)
        val rsi = Indicators.rsi(primary)
        val adx = Indicators.adx(primary)
        val vwap = Indicators.vwap(primary, 96)
        val regime = classifyRegime(primary, atr, adx)
        val zones = detectZones(primary, atr, tickSize)
        val tf5 = directionalTrend(c5); val tf15 = directionalTrend(c15); val tf60 = directionalTrend(c60); val tf240 = directionalTrend(c240)
        val enabled = config.strategyEnabled.filterValues { it }.keys
        if (enabled.isEmpty()) return empty(snapshot).copy(reasons = listOf("Todos los motores están apagados. Activa al menos uno."))

        val rawSignals = enabled.map { kind ->
            val raw = StrategyEngines.evaluate(kind, primary, atr, regime)
            val reliability = config.strategyReliability[raw.strategy] ?: 0.5
            val learned = config.learningAdjustments["STRATEGY:${raw.strategy.name}"] ?: 0.0
            raw.copy(score = (raw.score * (0.72 + reliability.coerceIn(.15, .85) * .56) + learned * 18.0).coerceIn(0.0, 100.0))
        }
        // The brain does not fire every engine. It selects the few that fit the current regime/location.
        val selected = AdaptiveIntelligence.selectTop(symbol, rawSignals, enabled, regime, snapshot.price, atr, zones, config)
        val micro = MicrostructureEngine.read(snapshot.flow, primary)
        val flowBias = orderFlowBias(snapshot)
        val longZone = AdaptiveIntelligence.zoneAffinity(SignalDirection.LONG, snapshot.price, atr, zones)
        val shortZone = AdaptiveIntelligence.zoneAffinity(SignalDirection.SHORT, snapshot.price, atr, zones)
        val longEngine = selected.filter { it.direction == SignalDirection.LONG }.sumOf { it.score * it.regimeFit / 100.0 }
        val shortEngine = selected.filter { it.direction == SignalDirection.SHORT }.sumOf { it.score * it.regimeFit / 100.0 }
        val longMtf = AdaptiveIntelligence.mtfSoftBias(tf5, tf15, tf60, tf240, SignalDirection.LONG)
        val shortMtf = AdaptiveIntelligence.mtfSoftBias(tf5, tf15, tf60, tf240, SignalDirection.SHORT)
        val longFlow = if (flowBias > 0) .08 else if (flowBias < 0) -.05 else 0.0
        val shortFlow = if (flowBias < 0) .08 else if (flowBias > 0) -.05 else 0.0
        val microLong = max(0.0, micro.bias) * config.microstructureWeight
        val microShort = max(0.0, -micro.bias) * config.microstructureWeight
        val longContext = longZone * 0.30 + max(0.0, longMtf) * 0.30 + longFlow + microLong
        val shortContext = shortZone * 0.30 + max(0.0, shortMtf) * 0.30 + shortFlow + microShort
        val longTotal = longEngine + longContext
        val shortTotal = shortEngine + shortContext
        val margin = AdaptiveIntelligence.decisionMargin(longTotal, shortTotal)

        // Flexible confirmation: at least one strong engine + meaningful location/MTF evidence.
        val strongestLong = selected.filter { it.direction == SignalDirection.LONG }.maxOfOrNull { it.score } ?: 0.0
        val strongestShort = selected.filter { it.direction == SignalDirection.SHORT }.maxOfOrNull { it.score } ?: 0.0
        val longEvidence = strongestLong >= 68 && (longZone >= .30 || longMtf >= .10 || flowBias > 0)
        val shortEvidence = strongestShort >= 68 && (shortZone >= .30 || shortMtf >= .10 || flowBias < 0)
        val direction = when {
            longEvidence && longTotal > shortTotal && margin >= .075 -> SignalDirection.LONG
            shortEvidence && shortTotal > longTotal && margin >= .075 -> SignalDirection.SHORT
            else -> SignalDirection.FLAT
        }

        val locationScore = if (direction == SignalDirection.LONG) longZone else if (direction == SignalDirection.SHORT) shortZone else max(longZone, shortZone)
        val mtfScore = if (direction == SignalDirection.LONG) max(0.0, longMtf) else if (direction == SignalDirection.SHORT) max(0.0, shortMtf) else 0.0
        val engineScore = if (direction == SignalDirection.LONG) strongestLong else if (direction == SignalDirection.SHORT) strongestShort else max(strongestLong, strongestShort)
        var quality = (engineScore * .54 + locationScore * 100.0 * .25 + mtfScore * 100.0 * .13 + (if (flowBias == 0) 0.0 else 8.0)).roundToInt().coerceIn(0, 99)
        if (direction == SignalDirection.FLAT) quality = min(quality, config.minQuality - 4)

        val riskMode = chooseRiskMode(regime, quality, adx, snapshot, locationScore)
        val entry = computeEntry(direction, primary, zones, atr, vwap, riskMode)
        val stopDist = stopDistance(primary, zones, atr, direction, riskMode)
        val stop = when (direction) { SignalDirection.LONG -> entry - stopDist; SignalDirection.SHORT -> entry + stopDist; SignalDirection.FLAT -> entry }
        val targetDist = targetDistance(primary, zones, atr, direction, riskMode)
        val tp1 = when (direction) { SignalDirection.LONG -> entry + targetDist * .55; SignalDirection.SHORT -> entry - targetDist * .55; SignalDirection.FLAT -> entry }
        val tp2 = when (direction) { SignalDirection.LONG -> entry + targetDist; SignalDirection.SHORT -> entry - targetDist; SignalDirection.FLAT -> entry }
        val rr = if (stopDist > 0) targetDist / stopDist else 0.0
        val finalDirection = if (direction != SignalDirection.FLAT && rr >= config.rrMinimum && quality >= config.minQuality) direction else SignalDirection.FLAT
        val state = when {
            finalDirection == SignalDirection.FLAT && direction != SignalDirection.FLAT -> SignalState.ABSTAIN
            finalDirection == SignalDirection.FLAT -> SignalState.WATCH
            quality >= 86 -> SignalState.READY
            quality >= config.minQuality -> SignalState.CONFIRMING
            else -> SignalState.WAITING_ENTRY
        }
        val zoneForLearning = nearestRelevantZone(finalDirection, snapshot.price, zones)
        val contextKey = AdaptiveIntelligence.contextKey(symbol, finalDirection, regime, riskMode, zoneForLearning?.type, quality)
        val contextAdjustment = config.learningAdjustments["CTX:$contextKey"] ?: 0.0
        quality = (quality + contextAdjustment * 20.0).roundToInt().coerceIn(0, 99)
        val probabilityRaw = empiricalProbability(quality, selected, config, contextAdjustment, micro.bias)
        val bucket = (probabilityRaw * 10.0).toInt()
        val probability = ProbabilityCalibrator.calibrated(probabilityRaw, config.calibrationAdjustments[bucket] ?: 0.0)
        val moveTicks = (targetDist / tickSize).roundToInt().coerceAtLeast(0)
        val reasons = buildReasons(primary, regime, selected, rawSignals, tf5, tf15, tf60, tf240, rsi, adx, vwap, flowBias, quality, rr, locationScore, margin, zoneForLearning, contextAdjustment).toMutableList().apply {
            add("Microestructura: ${micro.explanation} • sesgo ${"%.2f".format(micro.bias)} • calidad ${"%.0f".format(micro.quality * 100)}%")
            if (micro.absorption > .55) add("Posible absorción detectada; D.5 reduce la agresividad si contradice la dirección.")
            if (micro.exhaustion > .55) add("Posible agotamiento del flujo; se prioriza esperar confirmación de precio.")
        }
        val invalidation = when (finalDirection) {
            SignalDirection.LONG -> "Invalidar si el precio pierde la zona de demanda/OB relevante y confirma cierre 5m bajo el swing; también si 15m + 1h cambian de estructura."
            SignalDirection.SHORT -> "Invalidar si el precio recupera la zona de oferta/OB y confirma cierre 5m sobre el swing; también si 15m + 1h cambian de estructura."
            else -> "Esperar: falta ventaja suficiente en ubicación + estructura + motor seleccionado + riesgo. No forzar entrada."
        }
        return AnalysisSnapshot(
            finalDirection, state, quality, probability, regime, riskMode, targetDist, moveTicks,
            entry, stop, tp1, tp2, rr, zones, reasons, invalidation,
            selected.filter { it.direction == finalDirection }.joinToString(" + ") { it.strategy.title }.ifBlank { "Adaptive ensemble / espera" },
            selected, tf5, tf15, tf60, tf240, Indicators.volatilityPct(primary), atr, adx, vwap, rsi
        )
    }

    fun aggregate(bars: List<Candle>, minutes: Int): List<Candle> {
        if (bars.isEmpty() || minutes <= 1) return bars
        val periodSeconds = minutes * 60L
        return bars.sortedBy { it.time }.groupBy { it.time / periodSeconds }.toSortedMap().values.filter { it.isNotEmpty() }.map { g ->
            Candle(g.first().time, g.first().open, g.maxOf { it.high }, g.minOf { it.low }, g.last().close, g.sumOf { it.volume })
        }
    }

    fun tick(asset: AssetType): Double = when (asset) { AssetType.BTC -> .1; AssetType.ETH -> .01; AssetType.XAU -> .01 }

    private fun empty(s: MarketSnapshot) = AnalysisSnapshot(SignalDirection.FLAT, SignalState.WATCH, 0, Double.NaN, MarketRegime.UNKNOWN, RiskMode.PASSIVE, 0.0, 0,
        s.price, s.price, s.price, s.price, 0.0, emptyList(), listOf("Esperando histórico suficiente para evaluar D.5 IA."), "Datos insuficientes", "Sin señal", emptyList(),
        SignalDirection.FLAT, SignalDirection.FLAT, SignalDirection.FLAT, SignalDirection.FLAT, 0.0, 0.0, 0.0, s.price, 50.0)

    private fun directionalTrend(c: List<Candle>): SignalDirection {
        if (c.size < 30) return SignalDirection.FLAT
        val closes = c.map { it.close }; val e20 = Indicators.ema(closes, 20); val e50 = Indicators.ema(closes, 50.coerceAtMost(c.size)); val slope = Indicators.slope(c.takeLast(12).map { it.close })
        return when { e20 > e50 && slope > 0 -> SignalDirection.LONG; e20 < e50 && slope < 0 -> SignalDirection.SHORT; else -> SignalDirection.FLAT }
    }

    private fun classifyRegime(c: List<Candle>, atr: Double, adx: Double): MarketRegime {
        val vol = Indicators.volatilityPct(c); val (mid, upper, lower) = Indicators.bollinger(c, 20, 2.0); val width = if (mid > 0) (upper - lower) / mid * 100 else 0.0
        val closes = c.map { it.close }; val e20 = Indicators.ema(closes, 20); val e50 = Indicators.ema(closes, 50.coerceAtMost(c.size))
        return when { vol > .9 -> MarketRegime.HIGH_VOLATILITY; vol < .18 && width < .75 -> MarketRegime.LOW_VOLATILITY; adx > 25 && e20 > e50 -> MarketRegime.TREND_UP; adx > 25 && e20 < e50 -> MarketRegime.TREND_DOWN; adx < 16 && width < 1.6 -> MarketRegime.RANGE; adx < 20 -> MarketRegime.CHOPPY; else -> MarketRegime.UNKNOWN }
    }

    private fun detectZones(c: List<Candle>, atr: Double, tickSize: Double): List<Zone> {
        val out = mutableListOf<Zone>(); if (c.isEmpty()) return out
        val look = c.takeLast(120); val hi = look.maxOf { it.high }; val lo = look.minOf { it.low }; val pad = max(atr * .30, tickSize)
        out += Zone(ZoneType.LIQUIDITY_HIGH, hi - pad, hi, .92, label = "LIQUIDITY HIGH")
        out += Zone(ZoneType.LIQUIDITY_LOW, lo, lo + pad, .92, label = "LIQUIDITY LOW")
        val v = Indicators.vwap(c, 96); out += Zone(ZoneType.VWAP, v - atr * .10, v + atr * .10, .74, label = "VWAP")
        // Pivot supports/resistances from local swing clusters.
        for (i in 3 until look.lastIndex - 2) {
            val b = look[i]
            val swingHigh = b.high >= look[i-1].high && b.high >= look[i-2].high && b.high >= look[i+1].high && b.high >= look[i+2].high
            val swingLow = b.low <= look[i-1].low && b.low <= look[i-2].low && b.low <= look[i+1].low && b.low <= look[i+2].low
            if (swingHigh) out += Zone(ZoneType.PIVOT, b.high - pad * .35, b.high + pad * .35, .68, label = "RESISTANCE")
            if (swingLow) out += Zone(ZoneType.PIVOT, b.low - pad * .35, b.low + pad * .35, .68, label = "SUPPORT")
        }
        for (i in 2 until look.size) {
            val a = look[i - 2]; val b = look[i - 1]; val d = look[i]
            if (d.low > a.high + atr * .06) out += Zone(ZoneType.FVG, a.high, d.low, .72, label = "BULL FVG")
            if (d.high < a.low - atr * .06) out += Zone(ZoneType.FVG, d.high, a.low, .72, label = "BEAR FVG")
            if (b.close < b.open && d.close > d.open && d.close > b.high + atr * .15) out += Zone(ZoneType.ORDER_BLOCK, b.low, b.high, .84, label = "DEMAND OB")
            if (b.close > b.open && d.close < d.open && d.close < b.low - atr * .15) out += Zone(ZoneType.ORDER_BLOCK, b.low, b.high, .84, label = "SUPPLY OB")
        }
        // Explicit support/demand and resistance/supply clusters.
        val current = c.last().close
        val support = look.filter { it.low < current }.maxByOrNull { it.low }
        val resistance = look.filter { it.high > current }.minByOrNull { it.high }
        support?.let { out += Zone(ZoneType.DEMAND, it.low - pad * .5, it.low + pad * .5, .80, label = "DEMAND / SUPPORT") }
        resistance?.let { out += Zone(ZoneType.SUPPLY, it.high - pad * .5, it.high + pad * .5, .80, label = "SUPPLY / RESISTANCE") }
        return out.distinctBy { "${it.type}:${"%.8f".format(it.low)}:${"%.8f".format(it.high)}" }.sortedByDescending { it.strength }.take(28)
    }

    private fun nearestRelevantZone(dir: SignalDirection, price: Double, zones: List<Zone>): Zone? = zones.filter {
        when (dir) { SignalDirection.LONG -> it.type in setOf(ZoneType.DEMAND, ZoneType.ORDER_BLOCK, ZoneType.FVG, ZoneType.LIQUIDITY_LOW, ZoneType.PIVOT, ZoneType.VWAP); SignalDirection.SHORT -> it.type in setOf(ZoneType.SUPPLY, ZoneType.ORDER_BLOCK, ZoneType.FVG, ZoneType.LIQUIDITY_HIGH, ZoneType.PIVOT, ZoneType.VWAP); else -> true }
    }.minByOrNull { abs(price - it.mid) }

    private fun orderFlowBias(s: MarketSnapshot): Int {
        val depth = s.flow.bidDepth - s.flow.askDepth; val funding = s.flow.fundingRate
        return when { depth > 0 && funding < .001 -> 1; depth < 0 && funding > -.001 -> -1; else -> 0 }
    }

    private fun chooseRiskMode(regime: MarketRegime, quality: Int, adx: Double, s: MarketSnapshot, location: Double): RiskMode = when {
        regime == MarketRegime.HIGH_VOLATILITY -> RiskMode.PASSIVE
        location < .25 -> RiskMode.PASSIVE
        quality >= 88 && adx >= 30 && regime in setOf(MarketRegime.TREND_UP, MarketRegime.TREND_DOWN) -> RiskMode.AGGRESSIVE
        quality >= 78 && adx >= 22 -> RiskMode.BALANCED
        else -> RiskMode.PASSIVE
    }

    private fun computeEntry(dir: SignalDirection, c: List<Candle>, zones: List<Zone>, atr: Double, vwap: Double, mode: RiskMode): Double {
        val p = c.last().close; if (dir == SignalDirection.FLAT) return p
        val relevant = nearestRelevantZone(dir, p, zones)
        val ema9 = Indicators.ema(c.map { it.close }, 9)
        val candidate = relevant?.mid ?: ema9
        val pull = when (mode) { RiskMode.AGGRESSIVE -> .82; RiskMode.BALANCED -> .58; RiskMode.PASSIVE -> .38 }
        return (p + (candidate - p) * pull).coerceIn(p - atr * 1.0, p + atr * 1.0)
    }

    private fun stopDistance(c: List<Candle>, zones: List<Zone>, atr: Double, dir: SignalDirection, mode: RiskMode): Double {
        val low = c.takeLast(24).minOf { it.low }; val high = c.takeLast(24).maxOf { it.high }; val p = c.last().close
        val structure = if (dir == SignalDirection.LONG) abs(p - low) else abs(high - p)
        val factor = when (mode) { RiskMode.AGGRESSIVE -> .78; RiskMode.BALANCED -> .98; RiskMode.PASSIVE -> 1.18 }
        return max(atr * factor, structure * .50).coerceAtLeast(atr * .55)
    }

    private fun targetDistance(c: List<Candle>, zones: List<Zone>, atr: Double, dir: SignalDirection, mode: RiskMode): Double {
        val p = c.last().close
        val future = if (dir == SignalDirection.LONG) zones.filter { it.low > p }.minByOrNull { it.low } else zones.filter { it.high < p }.maxByOrNull { it.high }
        val base = when (mode) { RiskMode.AGGRESSIVE -> 3.0; RiskMode.BALANCED -> 2.6; RiskMode.PASSIVE -> 2.2 }
        return (future?.let { if (dir == SignalDirection.LONG) it.low - p else p - it.high }?.takeIf { it > atr * .9 } ?: atr * base).coerceAtLeast(atr * 1.7)
    }

    private fun empiricalProbability(quality: Int, signals: List<StrategySignal>, config: AnalysisConfig, contextAdjustment: Double, microBias: Double): Double {
        val active = signals.filter { it.direction != SignalDirection.FLAT }
        val consensus = if (active.isEmpty()) 0.0 else active.count { it.score >= 70 } / active.size.toDouble()
        val learnedRel = if (active.isEmpty()) .5 else active.map { config.strategyReliability[it.strategy] ?: .5 }.average()
        return ProbabilityCalibrator.raw(quality, consensus, learnedRel, microBias) + contextAdjustment * .08
    }

    private fun buildReasons(c: List<Candle>, regime: MarketRegime, selected: List<StrategySignal>, raw: List<StrategySignal>, t5: SignalDirection, t15: SignalDirection, t60: SignalDirection, t240: SignalDirection, rsi: Double, adx: Double, vwap: Double, flow: Int, quality: Int, rr: Double, location: Double, margin: Double, zone: Zone?, learned: Double): List<String> = buildList {
        add("MTF suave: 4H ${t240.name} • 1H ${t60.name} • 15M ${t15.name} • 5M ${t5.name} (el desacuerdo no cancela por sí solo)")
        add("Régimen ${regime.name} • ATR ${"%.5f".format(Indicators.atr(c))} • vol ${"%.2f".format(Indicators.volatilityPct(c))}% • ADX ${"%.1f".format(adx)}")
        add("Motores elegidos: ${selected.joinToString(" + ") { it.strategy.title }}")
        add("Ubicación ${"%.0f".format(location * 100)}% • margen de decisión ${"%.1f".format(margin * 100)}% • zona ${zone?.label ?: "ninguna clara"}")
        add("RSI ${"%.1f".format(rsi)} • VWAP ${"%.5f".format(vwap)} • flujo ${if (flow > 0) "comprador" else if (flow < 0) "vendedor" else "neutro"}")
        add("Calidad ${quality}/100 • R:R ${"%.2f".format(rr)} • confirmaciones ponderadas, no checklist rígido")
        if (learned != 0.0) add("Memoria contextual aplicada: ${if (learned > 0) "+" else ""}${"%.1f".format(learned * 100)} puntos de evidencia")
        selected.filter { it.direction != SignalDirection.FLAT }.forEach { add("${it.strategy.title}: ${it.reason}") }
        val unused = raw.filterNot { r -> selected.any { it.strategy == r.strategy } }.map { it.strategy.title }
        if (unused.isNotEmpty()) add("Motores descartados por contexto: ${unused.joinToString(", ")}")
    }
}
