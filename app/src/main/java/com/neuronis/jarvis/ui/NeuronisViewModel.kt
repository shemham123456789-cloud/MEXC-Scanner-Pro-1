package com.neuronis.jarvis.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neuronis.jarvis.core.*
import com.neuronis.jarvis.data.HistoryStore
import com.neuronis.jarvis.data.LearningStore
import com.neuronis.jarvis.data.MexcMarketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

class NeuronisViewModel(app: Application) : AndroidViewModel(app) {
    private val client = MexcMarketClient(); private val history = HistoryStore(app); private val learning = LearningStore(app)
    private var ws: com.neuronis.jarvis.data.MexcFuturesWebSocket? = null
    private val liveFlow = java.util.concurrent.ConcurrentHashMap<String, com.neuronis.jarvis.core.OrderFlow>()
    private val livePrice = java.util.concurrent.ConcurrentHashMap<String, Double>()
    private val assets = AssetType.entries
    private val _snapshots = MutableStateFlow<List<MarketSnapshot>>(emptyList()); val snapshots: StateFlow<List<MarketSnapshot>> = _snapshots.asStateFlow()
    private val _candles = MutableStateFlow<List<Candle>>(emptyList()); val candles: StateFlow<List<Candle>> = _candles.asStateFlow()
    private val _analysis = MutableStateFlow(MarketAnalysisEngine.analyze(AssetType.BTC, emptyList(), bootstrap(AssetType.BTC))); val analysis: StateFlow<AnalysisSnapshot> = _analysis.asStateFlow()
    private val _signals = MutableStateFlow<List<SignalCard>>(emptyList()); val signals: StateFlow<List<SignalCard>> = _signals.asStateFlow()
    private val _scanner = MutableStateFlow<List<ScannerSignal>>(emptyList()); val scanner: StateFlow<List<ScannerSignal>> = _scanner.asStateFlow()
    private val _selected = MutableStateFlow(AssetType.BTC); val selected: StateFlow<AssetType> = _selected.asStateFlow()
    private val _selectedSymbol = MutableStateFlow(AssetType.BTC.apiSymbol); val selectedSymbol: StateFlow<String> = _selectedSymbol.asStateFlow()
    private val _selectedTitle = MutableStateFlow(AssetType.BTC.label); val selectedTitle: StateFlow<String> = _selectedTitle.asStateFlow()
    private val _status = MutableStateFlow("D.5 IA • INICIANDO") ; val status: StateFlow<String> = _status.asStateFlow()
    private val _backtest = MutableStateFlow<BacktestResult?>(null); val backtest: StateFlow<BacktestResult?> = _backtest.asStateFlow()
    private val _labBusy = MutableStateFlow(false); val labBusy: StateFlow<Boolean> = _labBusy.asStateFlow()
    private val _config = MutableStateFlow(AnalysisConfig()); val config: StateFlow<AnalysisConfig> = _config.asStateFlow()
    private val _paper = MutableStateFlow<PaperPosition?>(null); val paper: StateFlow<PaperPosition?> = _paper.asStateFlow()

    data class PaperPosition(
        val asset: AssetType, val symbol: String, val direction: SignalDirection,
        val entry: Double, val stop: Double, val target: Double, val openedAt: Long,
        val strategies: List<StrategyKind>, val regime: MarketRegime, val riskMode: RiskMode,
        val zone: ZoneType?, val quality: Int, val predictionProbability: Double, val status: String = "ABIERTA", val counted: Boolean = false
    )

    init { startWebSocket(AssetType.BTC.apiSymbol); loadSelected(); startLiveLoop(); refreshAll(); }

    fun select(asset: AssetType) { _selected.value = asset; _selectedSymbol.value = asset.apiSymbol; _selectedTitle.value = asset.label; startWebSocket(asset.apiSymbol); loadSelected() }

    fun selectSymbol(symbol: String) {
        startWebSocket(symbol)
        viewModelScope.launch(Dispatchers.IO) {
            _selectedSymbol.value = symbol; _selectedTitle.value = symbol; _status.value = "D.5 IA • CARGANDO $symbol"
            runCatching {
                val snap0 = client.fetchSnapshotSymbol(symbol)
                val snap = snap0.copy(flow = liveFlow[symbol] ?: snap0.flow)
                val bars = client.fetchCandlesSymbol(symbol, "Min1").takeLast(7000)
                val tick = when { snap.price >= 10000 -> .1; snap.price >= 100 -> .01; snap.price >= 1 -> .0001; snap.price >= .1 -> .00001; else -> .000001 }
                _candles.value = bars
                _analysis.value = MarketAnalysisEngine.analyzeGeneric(symbol, tick, bars, snap, effectiveConfig())
                _status.value = "MEXC • $symbol • ANALIZADO"
            }.onFailure { _status.value = "MEXC • NO SE PUDO CARGAR $symbol" }
        }
    }

    private fun startWebSocket(symbol: String) {
        ws?.stop()
        ws = com.neuronis.jarvis.data.MexcFuturesWebSocket(object : com.neuronis.jarvis.data.MexcFuturesWebSocket.Listener {
            override fun onTicker(symbol: String, last: Double, funding: Double, index: Double, fair: Double, volume24: Double, ts: Long) {
                livePrice[symbol] = last
                _status.value = "MEXC WS • $symbol • ${"%.6f".format(last)}"
            }
            override fun onTrade(symbol: String, price: Double, volume: Double, buy: Boolean, ts: Long) {
                val old = liveFlow[symbol] ?: com.neuronis.jarvis.core.OrderFlow()
                val signed = if (buy) volume else -volume
                val total = old.buyVolume + old.sellVolume + volume
                val newBuy = old.buyVolume + if (buy) volume else 0.0
                val newSell = old.sellVolume + if (buy) 0.0 else volume
                val delta = if (newBuy + newSell > 0) (newBuy - newSell) / (newBuy + newSell) else 0.0
                liveFlow[symbol] = old.copy(tradeDelta = signed, tradeDeltaVelocity = (delta - old.tradeDelta).coerceIn(-1.0, 1.0), buyVolume = newBuy, sellVolume = newSell, tradeCount = old.tradeCount + 1, flowUpdatedAt = ts)
            }
            override fun onDepth(symbol: String, bids: List<Pair<Double, Double>>, asks: List<Pair<Double, Double>>, version: Long, ts: Long) {
                val bid = bids.sumOf { it.second }; val ask = asks.sumOf { it.second }; val total = bid + ask
                val bestBid = bids.maxByOrNull { it.first }?.first ?: 0.0; val bestAsk = asks.minByOrNull { it.first }?.first ?: 0.0
                val spread = if (bestBid > 0 && bestAsk > 0) ((bestAsk - bestBid) / ((bestAsk + bestBid) / 2.0)).coerceAtLeast(0.0) else 0.0
                val old = liveFlow[symbol] ?: com.neuronis.jarvis.core.OrderFlow()
                liveFlow[symbol] = old.copy(bidDepth = bid, askDepth = ask, depthImbalance = if (total > 0) (bid - ask) / total else 0.0, spreadPct = spread, flowUpdatedAt = ts)
            }
            override fun onKline(symbol: String, candle: Candle) {
                if (_selectedSymbol.value == symbol) {
                    val merged = (_candles.value.filterNot { it.time == candle.time } + candle).sortedBy { it.time }.takeLast(6000)
                    _candles.value = merged
                    val snap = _snapshots.value.firstOrNull { it.asset.apiSymbol == symbol } ?: client.fetchSnapshotSymbol(symbol)
                    _analysis.value = MarketAnalysisEngine.analyzeGeneric(symbol, tickFor(snap.price), merged, snap.copy(flow = liveFlow[symbol] ?: snap.flow), effectiveConfig())
                }
            }
        }).also { it.start(symbol) }
    }

    fun toggleStrategy(kind: StrategyKind) {
        val next = _config.value.strategyEnabled.toMutableMap(); next[kind] = !(next[kind] ?: true); _config.value = _config.value.copy(strategyEnabled = next)
        recompute(_selected.value)
    }

    fun setMinQuality(value: Int) { _config.value = _config.value.copy(minQuality = value.coerceIn(60, 95)); recompute(_selected.value) }

    fun refreshAll() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val list = assets.mapNotNull { runCatching { client.fetchSnapshot(it) }.getOrNull() }
                _snapshots.value = list
                loadSelected()
                _status.value = "MEXC • LIVE • ${list.size}/${assets.size} ACTIVO(S)"
            }.onFailure { _status.value = "MEXC • ERROR DE DATOS" }
        }
    }

    fun scanMarketUniverse(limit: Int = 16) {
        viewModelScope.launch(Dispatchers.IO) {
            _status.value = "D.5 IA SCANNER • BUSCANDO OPORTUNIDADES…"
            val symbols = runCatching { client.discoverTopUsdtMarkets(limit.coerceIn(3, 40)) }.getOrDefault(emptyList())
            val results = symbols.mapNotNull { symbol ->
                runCatching {
                    val snap = client.fetchSnapshotSymbol(symbol)
                    val bars = client.fetchCandlesSymbol(symbol, "Min5", null, null).takeLast(700)
                    ScannerEngine.scan(symbol, bars, snap, _config.value)
                }.getOrNull()
            }.sortedByDescending { it.quality }
            _scanner.value = results
            _status.value = "SCANNER • ${results.size} MERCADOS • MODO ${_config.value.minQuality}+"
        }
    }

    fun downloadHistory(days: Int) {
        val asset = _selected.value
        viewModelScope.launch(Dispatchers.IO) {
            _status.value = "DESCARGANDO ${days}D • ${asset.label}"
            val end = System.currentTimeMillis() / 1000; val start = end - days.coerceIn(1, 3650) * 86400L
            var cursor = start; var total = 0
            while (cursor < end) {
                val page = runCatching { client.fetchCandles(asset, "Min1", cursor, minOf(end, cursor + 1000L * 60)) }.getOrDefault(emptyList())
                if (page.isEmpty()) break
                history.append(asset, "1m", page); total += page.size
                val next = page.maxOf { it.time } + 60; if (next <= cursor) break; cursor = next
            }
            loadSelected(); _status.value = "HISTÓRICO LISTO • $total VELAS"
        }
    }

    fun runBacktest() {
        val asset = _selected.value
        viewModelScope.launch(Dispatchers.Default) {
            _labBusy.value = true; _status.value = "QUANT LAB • WALK-FORWARD + OOS + MONTE CARLO"
            val data = history.read(asset, "1m", 30000)
            val result = BacktestEngine.run(asset, data, _config.value)
            _backtest.value = result
            _labBusy.value = false; _status.value = "QUANT LAB • BACKTEST COMPLETO"
        }
    }

    fun openPaper() {
        val a = _analysis.value
        if (a.direction == SignalDirection.FLAT || a.state !in setOf(SignalState.CONFIRMING, SignalState.READY)) return
        _paper.value = PaperPosition(
            asset = _selected.value, symbol = _selectedSymbol.value, direction = a.direction,
            entry = a.entry, stop = a.stop, target = a.tp2, openedAt = System.currentTimeMillis(),
            strategies = a.strategySignals.filter { it.direction == a.direction }.map { it.strategy },
            regime = a.regime, riskMode = a.riskMode,
            zone = a.zones.minByOrNull { z -> kotlin.math.abs((_snapshots.value.firstOrNull { ss -> ss.asset == _selected.value }?.price ?: a.entry) - z.mid) }?.type,
            quality = a.quality, predictionProbability = a.calibratedProbability
        )
        _status.value = "PAPER • ${a.direction.name} ABIERTO"
    }

    fun closePaper() { _paper.value = null; _status.value = "PAPER • POSICIÓN CERRADA" }

    private fun loadSelected() {
        viewModelScope.launch(Dispatchers.IO) {
            val a = _selected.value; val b = history.read(a, "1m", 60000)
            if (b.isNotEmpty()) _candles.value = b
            recompute(a)
        }
    }

    private fun startLiveLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                assets.forEach { asset ->
                    runCatching {
                        val s0 = client.fetchSnapshot(asset)
                        val s = s0.copy(flow = liveFlow[asset.apiSymbol] ?: s0.flow)
                        _snapshots.value = (_snapshots.value.filterNot { it.asset == asset } + s).sortedBy { it.asset.ordinal }
                        val k = client.fetchCandles(asset, "Min1").takeLast(240)
                        history.append(asset, "1m", k)
                        if (asset == _selected.value) { _candles.value = history.read(asset, "1m", 60000); recompute(asset) }
                    }.onFailure { if (asset == _selected.value) _status.value = "MEXC • REINTENTANDO" }
                }
                // The selected scanner contract stays live even when it is not one of the three preset assets.
                val activeSymbol = _selectedSymbol.value
                if (activeSymbol != _selected.value.apiSymbol) {
                    runCatching {
                        val snap0 = client.fetchSnapshotSymbol(activeSymbol)
                        val snap = snap0.copy(flow = liveFlow[activeSymbol] ?: snap0.flow)
                        val k = client.fetchCandlesSymbol(activeSymbol, "Min1").takeLast(1200)
                        if (k.isNotEmpty()) { _candles.value = k; _analysis.value = MarketAnalysisEngine.analyzeGeneric(activeSymbol, tickFor(snap.price), k, snap, effectiveConfig()) }
                    }.onFailure { _status.value = "D.5 IA • LIVE $activeSymbol • REINTENTANDO" }
                }
                delay(5000)
            }
        }
    }

    private fun recompute(asset: AssetType) {
        val s = _snapshots.value.firstOrNull { it.asset == asset } ?: bootstrap(asset)
        var cfg = effectiveConfig()
        var a = MarketAnalysisEngine.analyze(asset, _candles.value, s, cfg)
        // Second pass: once regime/direction/zone are known, apply only the bounded
        // context-memory adjustment. This prevents the current candle from teaching itself.
        val nearZone = a.zones.minByOrNull { z -> kotlin.math.abs(s.price - z.mid) }
        val contextKey = AdaptiveIntelligence.contextKey(_selectedSymbol.value, a.direction, a.regime, a.riskMode, nearZone?.type, a.quality)
        val learned = learning.contextAdjustment("CTX:$contextKey")
        val strategyContext = a.strategySignals.associate { signal ->
            val z = a.zones.minByOrNull { zz -> kotlin.math.abs(s.price - zz.mid) }
            "STRATCTX:${AdaptiveIntelligence.strategyContextKey(_selectedSymbol.value, signal.strategy, signal.direction, a.regime, z?.type)}" to learning.contextAdjustment("${"STRATCTX:"}${AdaptiveIntelligence.strategyContextKey(_selectedSymbol.value, signal.strategy, signal.direction, a.regime, z?.type)}")
        }
        cfg = cfg.copy(learningAdjustments = cfg.learningAdjustments + ("CTX:$contextKey" to learned) + strategyContext)
        a = MarketAnalysisEngine.analyze(asset, _candles.value, s, cfg)
        _analysis.value = a
        _signals.value = assets.map { other ->
            val snap = _snapshots.value.firstOrNull { it.asset == other } ?: bootstrap(other)
            val data = if (other == asset) _candles.value else history.read(other, "1m", 5000)
            val x = MarketAnalysisEngine.analyze(other, data, snap, cfg)
            SignalCard(other, x.direction, x.state, x.quality, x.calibratedProbability, x.entry, x.stop, x.tp1, x.tp2, x.expectedMoveTicks, x.rr, x.regime, x.riskMode, x.strategy)
        }.sortedByDescending { it.quality }
        updatePaper(a)
    }

    private fun updatePaper(a: AnalysisSnapshot) {
        val p = _paper.value ?: return; val current = livePrice[p.symbol] ?: _snapshots.value.firstOrNull { it.asset == p.asset }?.price ?: return
        val hitStop = if (p.direction == SignalDirection.LONG) current <= p.stop else current >= p.stop
        val hitTarget = if (p.direction == SignalDirection.LONG) current >= p.target else current <= p.target
        if ((hitStop || hitTarget) && !p.counted) {
            learning.recordOutcome(
                symbol = p.symbol,
                direction = p.direction,
                regime = p.regime,
                risk = p.riskMode,
                zone = p.zone,
                quality = p.quality,
                strategies = p.strategies,
                win = hitTarget,
                r = if (hitTarget) 1.0 else -1.0,
                analysis = a, predictionProbability = p.predictionProbability
            )
            _paper.value = p.copy(status = if (hitTarget) "TP HIT" else "SL HIT", counted = true)
        }
    }

    private fun effectiveConfig(): AnalysisConfig {
        val rel = StrategyKind.entries.mapNotNull { learning.reliability(it)?.let { r -> it to r } }.toMap()
        val adjustments = buildMap {
            StrategyKind.entries.forEach { strategy ->
                val global = learning.reliability(strategy)
                if (global != null) put("STRATEGY:${strategy.name}", global - 0.5)
            }
            // Context keys are generated by the core after direction/regime/zone are known;
            // the generic store remains bounded and is consulted when available.
        }
        val calibration = buildMap<Int, Double> {
            for (bucket in 5..9) {
                val p = bucket / 10.0
                put(bucket, learning.calibrationAdjustment(p))
            }
        }
        return _config.value.copy(strategyReliability = rel, learningAdjustments = adjustments, calibrationAdjustments = calibration)
    }

    private fun tickFor(price: Double): Double = when { price >= 10000 -> .1; price >= 100 -> .01; price >= 1 -> .0001; price >= .1 -> .00001; else -> .000001 }

    private fun bootstrap(asset: AssetType) = MarketSnapshot(asset, when (asset) { AssetType.BTC -> 65000.0; AssetType.ETH -> 3200.0; AssetType.XAU -> 2400.0 }, 0.0, 0.0, 0.0, 0.0, 0.0, System.currentTimeMillis())
    override fun onCleared() {
        ws?.stop()
        super.onCleared()
    }
}

