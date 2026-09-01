package com.neuronis.jarvis.core

enum class AssetType(val apiSymbol: String, val label: String) {
    BTC("BTC_USDT", "BTC/USDT"),
    ETH("ETH_USDT", "ETH/USDT"),
    XAU("XAU_USDT", "XAU/USDT")
}

enum class SignalDirection { LONG, SHORT, FLAT }
enum class SignalState { WATCH, WAITING_ENTRY, CONFIRMING, READY, ABSTAIN, INVALIDATED }
enum class MarketRegime { TREND_UP, TREND_DOWN, RANGE, HIGH_VOLATILITY, LOW_VOLATILITY, CHOPPY, UNKNOWN }

enum class RiskMode { PASSIVE, BALANCED, AGGRESSIVE }

enum class ZoneType { DEMAND, SUPPLY, ORDER_BLOCK, FVG, LIQUIDITY_HIGH, LIQUIDITY_LOW, VWAP, PIVOT }

enum class StrategyKind(val title: String) {
    TREND("Trend + EMA/ADX"),
    BREAKOUT("Volatility Breakout"),
    SWEEP("Liquidity Sweep"),
    SMC("SMC Structure"),
    MEAN_REVERSION("Mean Reversion"),
    MOMENTUM("Momentum + RSI/MACD")
}

data class Candle(val time: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Double)

data class OrderFlow(
    val delta: Double = 0.0,
    val cvd: Double = 0.0,
    val openInterest: Double = 0.0,
    val fundingRate: Double = 0.0,
    val bidDepth: Double = 0.0,
    val askDepth: Double = 0.0,
    val spreadPct: Double = 0.0,
    val depthImbalance: Double = 0.0,
    val tradeDelta: Double = 0.0,
    val tradeDeltaVelocity: Double = 0.0,
    val buyVolume: Double = 0.0,
    val sellVolume: Double = 0.0,
    val tradeCount: Int = 0,
    val flowUpdatedAt: Long = 0L
)

data class MarketSnapshot(
    val asset: AssetType,
    val price: Double,
    val changePct: Double,
    val volume24h: Double,
    val indexPrice: Double,
    val fairPrice: Double,
    val fundingRate: Double,
    val updatedAt: Long,
    val flow: OrderFlow = OrderFlow()
)

data class ContractInfo(
    val symbol: String,
    val displayName: String,
    val quoteCoin: String,
    val settleCoin: String,
    val state: Int,
    val contractSize: Double,
    val minVol: Double,
    val maxVol: Double
)

data class Zone(val type: ZoneType, val low: Double, val high: Double, val strength: Double, val active: Boolean = true, val label: String = type.name) {
    val mid: Double get() = (low + high) / 2.0
}

data class StrategySignal(
    val strategy: StrategyKind,
    val direction: SignalDirection,
    val score: Double,
    val reason: String,
    val regimeFit: Double
)

data class AnalysisConfig(
    val minQuality: Int = 75,
    val aggressiveThreshold: Int = 84,
    val passiveThreshold: Int = 88,
    val rrMinimum: Double = 1.25,
    val maxRiskPct: Double = 1.0,
    val maxLeverage: Int = 20,
    val strategyEnabled: Map<StrategyKind, Boolean> = StrategyKind.entries.associateWith { true },
    val strategyReliability: Map<StrategyKind, Double> = emptyMap(),
    /** Context-specific empirical adjustments learned from paper/OOS outcomes. */
    val learningAdjustments: Map<String, Double> = emptyMap(),
    val calibrationAdjustments: Map<Int, Double> = emptyMap(),
    val adaptiveEngineCount: Int = 3,
    val zoneProximityAtr: Double = 1.20,
    val microstructureWeight: Double = 0.18,
    val maxSignalAgeSeconds: Long = 45L
)

data class AnalysisSnapshot(
    val direction: SignalDirection,
    val state: SignalState,
    val quality: Int,
    val calibratedProbability: Double,
    val regime: MarketRegime,
    val riskMode: RiskMode,
    val expectedMove: Double,
    val expectedMoveTicks: Int,
    val entry: Double,
    val stop: Double,
    val tp1: Double,
    val tp2: Double,
    val rr: Double,
    val zones: List<Zone>,
    val reasons: List<String>,
    val invalidation: String,
    val strategy: String,
    val strategySignals: List<StrategySignal>,
    val trend5: SignalDirection,
    val trend15: SignalDirection,
    val trend60: SignalDirection,
    val trend240: SignalDirection,
    val volatilityPct: Double,
    val atr: Double,
    val adx: Double,
    val vwap: Double,
    val rsi: Double
)

data class ScannerSignal(
    val symbol: String,
    val direction: SignalDirection,
    val state: SignalState,
    val quality: Int,
    val entry: Double,
    val stop: Double,
    val tp2: Double,
    val rr: Double,
    val expectedMovePct: Double,
    val regime: MarketRegime
)

data class SignalCard(
    val asset: AssetType,
    val direction: SignalDirection,
    val state: SignalState,
    val quality: Int,
    val calibratedProbability: Double,
    val entry: Double,
    val stop: Double,
    val tp1: Double,
    val tp2: Double,
    val expectedMoveTicks: Int,
    val rr: Double,
    val regime: MarketRegime,
    val riskMode: RiskMode,
    val strategy: String
)

data class BacktestResult(
    val asset: AssetType,
    val candles: Int,
    val trades: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val averageR: Double,
    val profitFactor: Double,
    val maxDrawdownR: Double,
    val netR: Double,
    val oosTrades: Int,
    val oosWinRate: Double,
    val oosAverageR: Double,
    val sharpeApprox: Double,
    val monteCarloP5: Double,
    val monteCarloP50: Double,
    val monteCarloP95: Double,
    val overfitFlag: Boolean,
    val oosProfitFactor: Double = 0.0,
    val walkForwardWindows: Int = 0,
    val stabilityScore: Double = 0.0,
    val antiOverfitScore: Double = 0.0,
    val leakageGuard: Boolean = true
)
