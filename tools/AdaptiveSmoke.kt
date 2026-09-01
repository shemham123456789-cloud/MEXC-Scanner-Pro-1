import com.neuronis.jarvis.core.*

fun main() {
    val zones = listOf(
        Zone(ZoneType.DEMAND, 99.0, 100.5, .9),
        Zone(ZoneType.SUPPLY, 104.0, 105.5, .9)
    )
    val cfg = AnalysisConfig(strategyReliability = mapOf(StrategyKind.SWEEP to .72))
    val signals = StrategyKind.entries.map { StrategySignal(it, if (it == StrategyKind.SWEEP) SignalDirection.LONG else SignalDirection.FLAT, if (it == StrategyKind.SWEEP) 82.0 else 0.0, "test", .95) }
    val selected = AdaptiveIntelligence.selectTop("BTC_USDT", signals, StrategyKind.entries.toSet(), MarketRegime.RANGE, 100.0, 1.0, zones, cfg)
    check(selected.size <= 3)
    check(selected.any { it.strategy == StrategyKind.SWEEP })
    val key = AdaptiveIntelligence.contextKey("BTC_USDT", SignalDirection.LONG, MarketRegime.RANGE, RiskMode.PASSIVE, ZoneType.DEMAND, 80)
    check(key.contains("BTC_USDT") && key.contains("DEMAND"))
    println("ADAPTIVE MEMORY SMOKE PASS")
}
