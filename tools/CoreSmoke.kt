import com.neuronis.jarvis.core.*
import kotlin.math.sin

fun main() {
    val candles = (0 until 1800).map { i ->
        val base = 100.0 + i * 0.035 + sin(i / 9.0) * 1.8
        val open = base - 0.18
        val close = base + 0.22
        Candle(i.toLong() * 60, open, maxOf(open, close) + 0.30, minOf(open, close) - 0.30, close, 1000.0 + (i % 17) * 50.0)
    }
    val snap = MarketSnapshot(AssetType.BTC, candles.last().close, 1.2, 1_000_000.0, candles.last().close, candles.last().close, 0.0001, candles.last().time)
    val a = MarketAnalysisEngine.analyze(AssetType.BTC, candles, snap)
    check(a.quality in 0..99)
    check(a.rr >= 0.0)
    check(a.zones.size <= 28)
    check(a.reasons.isNotEmpty())
    val bt = BacktestEngine.run(AssetType.BTC, candles)
    check(bt.candles == candles.size)
    println("CORE SMOKE PASS")
    println("quality=${a.quality} direction=${a.direction} state=${a.state} rr=${"%.2f".format(a.rr)}")
    println("backtest trades=${bt.trades} oos=${bt.oosTrades} pf=${"%.2f".format(bt.profitFactor)} dd=${"%.2f".format(bt.maxDrawdownR)}")
}
