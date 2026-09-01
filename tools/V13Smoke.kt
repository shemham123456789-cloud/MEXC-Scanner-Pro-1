import com.neuronis.jarvis.core.*

fun main() {
    val flow = OrderFlow(bidDepth=120.0, askDepth=80.0, depthImbalance=.20, tradeDelta=.15, tradeDeltaVelocity=.10, buyVolume=120.0, sellVolume=90.0, spreadPct=.0002)
    val candles = (0 until 80).map { i -> val p=100.0+i*.05; Candle(i*60L,p,p+.4,p-.2,p+.2,100.0+i) }
    val micro = MicrostructureEngine.read(flow,candles)
    check(micro.bias > 0)
    val raw = ProbabilityCalibrator.raw(82,.67,.61,micro.bias)
    check(raw in .50.. .94)
    check(LearningGovernor.approve(0.10,0.16,0.65,0.64,120).approved)
    check(!LearningGovernor.approve(0.10,0.11,0.65,0.64,120).approved)
    val fp = PatternFingerprint.create("BTC_USDT", MarketAnalysisEngine.analyzeGeneric("BTC_USDT",.1,candles,MarketSnapshot(AssetType.BTC,104.0,0.0,0.0,104.0,104.0,0.0,System.currentTimeMillis(),flow)),flow)
    check(fp.contains("BTC_USDT"))
    println("D5 V13 INTELLIGENCE SMOKE PASS")
}
