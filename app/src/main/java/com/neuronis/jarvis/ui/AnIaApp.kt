package com.neuronis.jarvis.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neuronis.jarvis.core.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun AnIaApp(vm: NeuronisViewModel = viewModel()) {
    val snaps by vm.snapshots.collectAsState(); val candles by vm.candles.collectAsState(); val a by vm.analysis.collectAsState(); val signals by vm.signals.collectAsState(); val scan by vm.scanner.collectAsState(); val sel by vm.selected.collectAsState(); val selectedTitle by vm.selectedTitle.collectAsState(); val status by vm.status.collectAsState(); val bt by vm.backtest.collectAsState(); val busy by vm.labBusy.collectAsState(); val cfg by vm.config.collectAsState(); val paper by vm.paper.collectAsState()
    var tab by remember { mutableStateOf("INICIO") }
    Surface(Modifier.fillMaxSize(), color = BG) {
        Column(Modifier.fillMaxSize()) {
            TopBar(status, a, paper?.status ?: "PAPER OFF")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) { assetsChips(snaps, sel) { vm.select(it) } }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("INICIO", "GRAFICO", "SCANNER", "ESTRATEGIAS", "QUANT", "D.5 IA").forEach { t -> NavChip(t, tab == t) { tab = t } }
            }
            when (tab) {
                "INICIO" -> Dashboard(a, signals, sel, paper, { vm.openPaper() }, { vm.closePaper() }) { vm.select(it) }
                "GRAFICO" -> ChartScreen(selectedTitle, snaps.firstOrNull { it.asset == sel }, candles, a)
                "SCANNER" -> ScannerScreen(scan, { vm.scanMarketUniverse() }) { vm.selectSymbol(it) }
                "ESTRATEGIAS" -> StrategiesScreen(cfg) { vm.toggleStrategy(it) }
                "QUANT" -> QuantLab(bt, busy, status, { vm.downloadHistory(it) }, { vm.runBacktest() })
                "D.5 IA" -> JarvisScreen(vm, sel, a, paper?.status ?: "sin posición")
            }
        }
    }
}

@Composable private fun TopBar(status: String, a: AnalysisSnapshot, paper: String) {
    Row(Modifier.fillMaxWidth().background(PANEL).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(PANEL2).border(1.dp, CYAN, CircleShape), contentAlignment = Alignment.Center) { Text("AI", color = CYAN, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text("D.5 IA • ADAPTIVE TRADING INTELLIGENCE", color = TEXT, fontWeight = FontWeight.Black, fontSize = 13.sp); Text("${status} • ${a.riskMode.name} • PAPER ${paper}", color = MUTED, fontSize = 8.sp) }
        Text("${a.quality}/100", color = signalColor(a.direction), fontWeight = FontWeight.Black, fontSize = 16.sp)
    }
}

@Composable private fun assetsChips(snaps: List<MarketSnapshot>, selected: AssetType, onClick: (AssetType) -> Unit) {
    AssetType.entries.forEach { a ->
        val s = snaps.firstOrNull { it.asset == a }; Surface(onClick = { onClick(a) }, color = if (selected == a) PANEL2 else PANEL, shape = RoundedCornerShape(11.dp), modifier = Modifier.border(1.dp, if (selected == a) CYAN else GRID, RoundedCornerShape(11.dp))) {
            Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text(a.label, color = TEXT, fontWeight = FontWeight.Bold, fontSize = 10.sp); Spacer(Modifier.width(5.dp)); Text(if (s == null) "--" else "${priceText(s.price)}", color = GREEN, fontSize = 9.sp) }
        }
    }
}

@Composable private fun NavChip(t: String, active: Boolean, click: () -> Unit) = Surface(onClick = click, color = if (active) PANEL2 else PANEL, shape = RoundedCornerShape(9.dp), modifier = Modifier.border(1.dp, if (active) CYAN else GRID, RoundedCornerShape(9.dp))) { Text(t, color = if (active) CYAN else MUTED, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) }

@Composable private fun Dashboard(a: AnalysisSnapshot, signals: List<SignalCard>, sel: AssetType, paper: NeuronisViewModel.PaperPosition?, openPaper: () -> Unit, closePaper: () -> Unit, select: (AssetType) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Card(colors = CardDefaults.cardColors(PANEL), shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(14.dp)) { Text("AI CORE", color = CYAN, fontWeight = FontWeight.Black); Text("Ensemble multi-estrategia + régimen + MTF + riesgo dinámico", color = MUTED, fontSize = 9.sp); Spacer(Modifier.height(6.dp)); Text("${a.regime.name} • 4H ${a.trend240.name} • 1H ${a.trend60.name} • 15M ${a.trend15.name} • 5M ${a.trend5.name}", color = TEXT, fontSize = 10.sp) } } }
        item { SignalHero(a, sel, paper, openPaper, closePaper) }
        item { Text("OPORTUNIDADES", color = CYAN, fontWeight = FontWeight.Black, fontSize = 12.sp) }
        items(signals) { SignalRow(it, select) }
        item { Text("Lectura: calidad ≠ probabilidad garantizada. La app exige evidencia y R:R antes de elevar una señal.", color = GOLD, fontSize = 8.sp) }
    }
}

@Composable private fun SignalHero(a: AnalysisSnapshot, sel: AssetType, paper: NeuronisViewModel.PaperPosition?, openPaper: () -> Unit, closePaper: () -> Unit) {
    val c = signalColor(a.direction)
    Card(colors = CardDefaults.cardColors(PANEL), shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(sel.label, color = MUTED, fontSize = 9.sp); Text(if (a.direction == SignalDirection.LONG) "▲ LONG" else if (a.direction == SignalDirection.SHORT) "▼ SHORT" else "• ESPERAR", color = c, fontSize = 30.sp, fontWeight = FontWeight.Black); Text(a.state.name, color = c, fontSize = 10.sp, fontWeight = FontWeight.Bold) }; Column(horizontalAlignment = Alignment.End) { Text("CALIDAD", color = MUTED, fontSize = 8.sp); Text("${a.quality}/100", color = c, fontSize = 24.sp, fontWeight = FontWeight.Black) } }
        Spacer(Modifier.height(8.dp)); MetricRow("ENTRY", priceText(a.entry), TEXT); MetricRow("SL", priceText(a.stop), RED); MetricRow("TP1", priceText(a.tp1), GREEN); MetricRow("TP2", priceText(a.tp2), GOLD); MetricRow("R:R", "${"%.2f".format(a.rr)}R", CYAN); MetricRow("MOVE", "${a.expectedMoveTicks} ticks • ${"%.2f".format(a.volatilityPct)}% vol", CYAN)
        Spacer(Modifier.height(6.dp)); Text("Riesgo ${a.riskMode.name} • ADX ${"%.1f".format(a.adx)} • RSI ${"%.1f".format(a.rsi)}", color = MUTED, fontSize = 8.sp)
        Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = openPaper, enabled = paper == null && a.direction != SignalDirection.FLAT && a.state in setOf(SignalState.CONFIRMING, SignalState.READY), modifier = Modifier.weight(1f)) { Text("PAPER ENTRY", fontSize = 10.sp) }; OutlinedButton(onClick = closePaper, enabled = paper != null, modifier = Modifier.weight(1f)) { Text("CERRAR PAPER", fontSize = 10.sp) } }
    } }
}

@Composable private fun MetricRow(k: String, v: String, c: Color) { Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(k, color = MUTED, fontSize = 8.sp); Text(v, color = c, fontWeight = FontWeight.Bold, fontSize = 10.sp) } }

@Composable private fun SignalRow(s: SignalCard, onSelect: (AssetType) -> Unit) { val c = signalColor(s.direction); Card(colors = CardDefaults.cardColors(PANEL), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().clickable { onSelect(s.asset) }) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(s.asset.label, color = TEXT, fontWeight = FontWeight.Black, fontSize = 12.sp); Text(s.strategy, color = MUTED, fontSize = 8.sp); Text("${s.state.name} • ${s.riskMode.name}", color = c, fontSize = 8.sp) }; Column(horizontalAlignment = Alignment.End) { Text(if (s.direction == SignalDirection.LONG) "▲ LONG" else if (s.direction == SignalDirection.SHORT) "▼ SHORT" else "ESPERAR", color = c, fontWeight = FontWeight.Black, fontSize = 14.sp); Text("${s.quality}/100 • ${"%.2f".format(s.rr)}R", color = CYAN, fontSize = 8.sp) } } } }

@Composable private fun ChartScreen(title: String, snapshot: MarketSnapshot?, candles: List<Candle>, a: AnalysisSnapshot) {
    var tf by remember { mutableStateOf("5m") }; var zoom by remember { mutableStateOf(1f) }; var offset by remember { mutableStateOf(0f) }; var zonesOn by remember { mutableStateOf(true) }
    val mins = when (tf) { "1m" -> 1; "5m" -> 5; "15m" -> 15; "1h" -> 60; "4h" -> 240; else -> 1 }; val display = MarketAnalysisEngine.aggregate(candles, mins); val visible = (100f / zoom).roundToInt().coerceIn(30, max(30, display.size)); val end = (display.size + offset.roundToInt()).coerceIn(visible, display.size); val start = (end - visible).coerceAtLeast(0); val slice = if (start < end) display.subList(start, end) else emptyList()
    Column(Modifier.fillMaxSize().padding(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("${title} • MEXC FUTURES", color = TEXT, fontSize = 18.sp, fontWeight = FontWeight.Black); Text(snapshot?.let { "${priceText(it.price)} • ${"%.2f".format(it.changePct)}%" } ?: "--", color = GREEN, fontSize = 11.sp) }; Text(if (a.direction == SignalDirection.LONG) "▲ LONG ${a.quality}" else if (a.direction == SignalDirection.SHORT) "▼ SHORT ${a.quality}" else "• WAIT", color = signalColor(a.direction), fontWeight = FontWeight.Black) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("1m", "5m", "15m", "1h", "4h").forEach { t -> NavChip(t, tf == t) { tf = t; zoom = 1f; offset = 0f } }; NavChip("ZONAS", zonesOn) { zonesOn = !zonesOn }; NavChip("-", false) { zoom = (zoom / .82f).coerceIn(.5f, 6f) }; NavChip("+", false) { zoom = (zoom * 1.18f).coerceIn(.5f, 6f) } }
        Spacer(Modifier.height(6.dp)); Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(BG).pointerInput(tf, display.size, visible) { detectTransformGestures { _, pan, scale, _ -> offset += -pan.x / 900f * visible; zoom = (zoom / scale).coerceIn(.5f, 6f) } }) { Canvas(Modifier.fillMaxSize()) { drawAdvancedChart(slice, a, zonesOn) } }
        Text("IA dibuja ENTRY/SL/TP y zonas • pellizca para zoom • arrastra para histórico", color = MUTED, fontSize = 8.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

private fun DrawScope.drawAdvancedChart(
    slice: List<Candle>,
    a: AnalysisSnapshot,
    zonesOn: Boolean
) {
    if (slice.isEmpty()) return

    val prices = mutableListOf<Double>()

    slice.forEach { candle ->
        prices.add(candle.high)
        prices.add(candle.low)
    }

    prices.addAll(
        listOf(
            a.entry,
            a.stop,
            a.tp1,
            a.tp2,
            a.vwap
        )
    )

    val hi = prices.maxOrNull() ?: 1.0
    val lo = prices.minOrNull() ?: 0.0
    val range = max(hi - lo, 1e-9)

    fun y(value: Double): Float {
        return size.height -
            ((value - lo) / range * size.height).toFloat()
    }

    for (i in 1..6) {
        drawLine(
            color = GRID,
            start = Offset(
                0f,
                size.height * i / 7f
            ),
            end = Offset(
                size.width,
                size.height * i / 7f
            ),
            strokeWidth = 1f
        )
    }

    if (zonesOn) {
        a.zones
            .take(10)
            .forEach { zone ->
                val top = y(zone.high)
                val bottom = y(zone.low)

                if (bottom >= -60f && top <= size.height + 60f) {
                    drawRect(
                        color = zoneColor(zone.type).copy(alpha = 0.08f),
                        topLeft = Offset(
                            0f,
                            min(top, bottom)
                        ),
                        size = Size(
                            size.width,
                            max(2f, abs(bottom - top))
                        )
                    )
                }
            }
    }

    val candleWidth = size.width / slice.size.toFloat()

    slice.forEachIndexed { index, candle ->
        val x = index * candleWidth + candleWidth / 2f
        val candleColor =
            if (candle.close >= candle.open) GREEN else RED

        drawLine(
            color = candleColor,
            start = Offset(x, y(candle.high)),
            end = Offset(x, y(candle.low)),
            strokeWidth = 1.4f
        )

        val bodyTop = y(max(candle.open, candle.close))
        val bodyBottom = y(min(candle.open, candle.close))

        drawRect(
            color = candleColor,
            topLeft = Offset(
                x - candleWidth * 0.28f,
                bodyTop
            ),
            size = Size(
                max(2f, candleWidth * 0.56f),
                max(2f, abs(bodyBottom - bodyTop))
            )
        )
    }

    val levels = listOf(
        "ENTRY" to a.entry,
        "SL" to a.stop,
        "TP1" to a.tp1,
        "TP2" to a.tp2,
        "VWAP" to a.vwap
    )

    levels.forEach { (label, value) ->
        if (value in lo..hi) {
            val yy = y(value)

            val lineColor = when (label) {
                "SL" -> RED
                "TP1" -> GREEN
                "TP2" -> GOLD
                "VWAP" -> CYAN
                else -> TEXT
            }

            drawLine(
                color = lineColor.copy(alpha = 0.7f),
                start = Offset(0f, yy),
                end = Offset(size.width, yy),
                strokeWidth = if (label == "ENTRY") 2f else 1f
            )
        }
    }

    if (a.direction != SignalDirection.FLAT) {
        val x = size.width * 0.82f
        val yy = y(a.entry)
        val signal = signalColor(a.direction)

        drawCircle(
            color = signal,
            radius = 8f,
            center = Offset(x, yy)
        )

        drawLine(
            color = signal,
            start = Offset(x, yy),
            end = Offset(
                x,
                if (a.direction == SignalDirection.LONG) {
                    yy - 46f
                } else {
                    yy + 46f
                }
            ),
            strokeWidth = 2.2f
        )
    }
}

@Composable private fun ScannerScreen(rows: List<ScannerSignal>, scan: () -> Unit, select: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(10.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("MARKET SCANNER", color = TEXT, fontSize = 20.sp, fontWeight = FontWeight.Black); Text("MEXC USDT Futures • top volume • multi-engine", color = MUTED, fontSize = 9.sp) }; Button(onClick = scan) { Text("ESCANEAR") } }; Spacer(Modifier.height(8.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(rows) { r -> Card(colors = CardDefaults.cardColors(PANEL), shape = RoundedCornerShape(17.dp), modifier = Modifier.clickable { select(r.symbol) }) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(r.symbol, color = TEXT, fontWeight = FontWeight.Black); Text("${r.regime.name} • ${r.state.name}", color = MUTED, fontSize = 8.sp) }; Column(horizontalAlignment = Alignment.End) { val c = signalColor(r.direction); Text(if (r.direction == SignalDirection.LONG) "▲ LONG" else if (r.direction == SignalDirection.SHORT) "▼ SHORT" else "WAIT", color = c, fontWeight = FontWeight.Black); Text("${r.quality}/100 • ${"%.2f".format(r.rr)}R", color = CYAN, fontSize = 9.sp) } } } } } }
}

@Composable private fun StrategiesScreen(cfg: AnalysisConfig, toggle: (StrategyKind) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { item { Text("ESTRATEGIAS / MOTORES", color = TEXT, fontSize = 20.sp, fontWeight = FontWeight.Black); Text("Todos se pueden prender/apagar. El ensemble solo usa motores habilitados.", color = MUTED, fontSize = 9.sp) }; items(StrategyKind.entries.toList()) { s -> val on = cfg.strategyEnabled[s] == true; Card(colors = CardDefaults.cardColors(if (on) PANEL else PANEL.copy(alpha = .55f)), shape = RoundedCornerShape(18.dp), modifier = Modifier.clickable { toggle(s) }) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(s.title, color = TEXT, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(when (s) { StrategyKind.TREND -> "EMA 9/21/50 + ADX"; StrategyKind.BREAKOUT -> "Rango + volumen + expansión"; StrategyKind.SWEEP -> "Barrido + rechazo"; StrategyKind.SMC -> "BOS + estructura"; StrategyKind.MEAN_REVERSION -> "Bollinger + RSI"; StrategyKind.MOMENTUM -> "VWAP + RSI + MACD" }, color = MUTED, fontSize = 8.sp) }; Switch(checked = on, onCheckedChange = { toggle(s) }) } } }; item { Spacer(Modifier.height(8.dp)); Text("Umbral de calidad actual: ${cfg.minQuality}/100", color = CYAN, fontWeight = FontWeight.Black) } }
}

@Composable private fun QuantLab(bt: BacktestResult?, busy: Boolean, status: String, download: (Int) -> Unit, run: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { item { Text("QUANT LAB", color = TEXT, fontSize = 21.sp, fontWeight = FontWeight.Black); Text("Walk-forward • OOS • Monte Carlo • detector de sobreajuste", color = CYAN, fontSize = 9.sp) }; item { Card(colors = CardDefaults.cardColors(PANEL), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(12.dp)) { Text("HISTÓRICO MEXC", color = CYAN, fontWeight = FontWeight.Black); Text("Descarga 7, 30 o 180 días para el activo seleccionado.", color = MUTED, fontSize = 8.sp); Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) { listOf(7, 30, 180).forEach { d -> Button(onClick = { download(d) }, enabled = !busy) { Text("${d}D", fontSize = 9.sp) } } } } } }; item { Button(onClick = run, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "EJECUTANDO…" else "EJECUTAR LABORATORIO") } }; bt?.let { r -> item { Card(colors = CardDefaults.cardColors(PANEL), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(12.dp)) { Text("${r.asset.label} • RESULTADOS", color = CYAN, fontWeight = FontWeight.Black); MetricRow("TRADES", r.trades.toString(), TEXT); MetricRow("WIN RATE", "${"%.2f".format(r.winRate)}%", GREEN); MetricRow("OOS WIN RATE", "${"%.2f".format(r.oosWinRate)}%", GREEN); MetricRow("AVG R", "${"%.2f".format(r.averageR)}", CYAN); MetricRow("OOS AVG R", "${"%.2f".format(r.oosAverageR)}", CYAN); MetricRow("PROFIT FACTOR", "${"%.2f".format(r.profitFactor)}", GOLD); MetricRow("MAX DD", "-${"%.2f".format(r.maxDrawdownR)}R", RED); MetricRow("SHARPE*", "${"%.2f".format(r.sharpeApprox)}", TEXT); MetricRow("MC P5/P50/P95", "${"%.1f".format(r.monteCarloP5)} / ${"%.1f".format(r.monteCarloP50)} / ${"%.1f".format(r.monteCarloP95)}R", TEXT); MetricRow("OOS PF", "${"%.2f".format(r.oosProfitFactor)}", GOLD); MetricRow("WF", r.walkForwardWindows.toString(), TEXT); MetricRow("ESTABILIDAD", "${"%.0f".format(r.stabilityScore)}%", CYAN); MetricRow("ANTI-OVERFIT", "${"%.0f".format(r.antiOverfitScore)}%", CYAN); Text(if (r.overfitFlag) "ALERTA: posible sobreajuste" else "Sin alerta estadística simple de sobreajuste", color = if (r.overfitFlag) RED else GREEN, fontSize = 9.sp, fontWeight = FontWeight.Bold) } } } }; item { Text(status, color = MUTED, fontSize = 8.sp) } }
}

@Composable private fun JarvisScreen(vm: NeuronisViewModel, sel: AssetType, a: AnalysisSnapshot, paper: String) {
    var input by remember { mutableStateOf("") }; var answer by remember { mutableStateOf("Pregúntame: ¿dónde entrar?, ¿por qué?, ¿qué invalida?, ¿qué ves?, ¿qué aprendió? o riesgo") }
    Column(Modifier.fillMaxSize().padding(10.dp)) { Text("D.5 IA", color = TEXT, fontSize = 24.sp, fontWeight = FontWeight.Black); Text("Cerebro local explicable • sin inventar noticias ni resultados", color = MUTED, fontSize = 9.sp); Spacer(Modifier.height(9.dp)); Card(colors = CardDefaults.cardColors(PANEL), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().weight(1f)) { Column(Modifier.padding(12.dp)) { Text(answer, color = TEXT, fontSize = 12.sp); Spacer(Modifier.height(8.dp)); Text("Estado ${a.state.name} • ${a.direction.name} • calidad ${a.quality}", color = CYAN, fontSize = 9.sp) } }; Spacer(Modifier.height(8.dp)); OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Habla con D.5 IA…") }); Button(onClick = { answer = JarvisBrain.answer(input, sel, a, paper); input = "" }, modifier = Modifier.fillMaxWidth()) { Text("PREGUNTAR") } }
}
