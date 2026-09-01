package com.neuronis.jarvis.data

import android.content.Context
import org.json.JSONObject
import com.neuronis.jarvis.core.AdaptiveIntelligence
import com.neuronis.jarvis.core.ErrorAnalyzer
import com.neuronis.jarvis.core.AnalysisSnapshot
import com.neuronis.jarvis.core.MarketRegime
import com.neuronis.jarvis.core.RiskMode
import com.neuronis.jarvis.core.SignalDirection
import com.neuronis.jarvis.core.StrategyKind
import com.neuronis.jarvis.core.ZoneType
import kotlin.math.max

/** Evidence-bounded memory. Small samples have almost no influence. */
class LearningStore(context: Context) {
    private val prefs = context.getSharedPreferences("d5_learning_v12", Context.MODE_PRIVATE)
    private val lock = Any()

    fun record(strategy: StrategyKind, win: Boolean) {
        synchronized(lock) {
            val w = prefs.getInt("${strategy.name}_w", 0) + if (win) 1 else 0
            val l = prefs.getInt("${strategy.name}_l", 0) + if (win) 0 else 1
            prefs.edit().putInt("${strategy.name}_w", w).putInt("${strategy.name}_l", l).apply()
        }
    }

    fun recordOutcome(symbol: String, direction: SignalDirection, regime: MarketRegime, risk: RiskMode,
                     zone: ZoneType?, quality: Int, strategies: List<StrategyKind>, win: Boolean, r: Double,
                     analysis: AnalysisSnapshot? = null, predictionProbability: Double? = null) {
        synchronized(lock) {
            strategies.forEach { strategy ->
                record(strategy, win)
                val key = AdaptiveIntelligence.strategyContextKey(symbol, strategy, direction, regime, zone)
                updateContext(key, win, r)
            }
            val base = AdaptiveIntelligence.contextKey(symbol, direction, regime, risk, zone, quality)
            updateContext("CTX:$base", win, r)
            analysis?.let { a ->
                val fp = com.neuronis.jarvis.core.PatternFingerprint.create(symbol, a, com.neuronis.jarvis.core.OrderFlow())
                updateContext("PAT:$fp", win, r)
            }
            predictionProbability?.let { recordPrediction(it, win) }
            if (!win && analysis != null) {
                val error = ErrorAnalyzer.classify(analysis, false)
                val ek = "ERROR:${error.name}"
                val count = prefs.getInt(ek, 0) + 1
                prefs.edit().putInt(ek, count).apply()
            }
        }
    }

    private fun updateContext(key: String, win: Boolean, r: Double) {
        val w = prefs.getInt("$key:w", 0) + if (win) 1 else 0
        val l = prefs.getInt("$key:l", 0) + if (win) 0 else 1
        val sumR = prefs.getFloat("$key:r", 0f).toDouble() + r
        prefs.edit().putInt("$key:w", w).putInt("$key:l", l).putFloat("$key:r", sumR.toFloat()).apply()
    }

    fun recordPrediction(probability: Double, win: Boolean) {
        synchronized(lock) {
            val bucket = (probability.coerceIn(.5, .96) * 10.0).toInt()
            val w = prefs.getInt("CAL:$bucket:w", 0) + if (win) 1 else 0
            val l = prefs.getInt("CAL:$bucket:l", 0) + if (win) 0 else 1
            prefs.edit().putInt("CAL:$bucket:w", w).putInt("CAL:$bucket:l", l).apply()
        }
    }

    fun calibrationAdjustment(probability: Double): Double {
        val bucket = (probability.coerceIn(.5, .96) * 10.0).toInt()
        val w = prefs.getInt("CAL:$bucket:w", 0); val l = prefs.getInt("CAL:$bucket:l", 0); val n = w + l
        if (n < 25) return 0.0
        val observed = (w + 2.0) / (n + 4.0)
        val target = bucket / 10.0
        return ((observed - target) * ((n - 25).toDouble() / 175.0).coerceIn(0.0, 1.0) * .35).coerceIn(-.08, .08)
    }

    fun patternSummary(key: String): String {
        val w = prefs.getInt("PAT:$key:w", 0); val l = prefs.getInt("PAT:$key:l", 0); val n = w + l
        if (n == 0) return "sin evidencia"
        return "${n} muestras • ${"%.1f".format(w * 100.0 / n)}% wins"
    }

    fun reliability(strategy: StrategyKind): Double? {
        val w = prefs.getInt("${strategy.name}_w", 0); val l = prefs.getInt("${strategy.name}_l", 0); val n = w + l
        if (n < 20) return null
        // Jeffreys-like smoothing; bounded so learning can never dominate the model.
        return ((w + 3.0) / (n + 6.0)).coerceIn(.15, .85)
    }

    fun contextAdjustment(key: String): Double {
        val w = prefs.getInt("$key:w", 0); val l = prefs.getInt("$key:l", 0); val n = w + l
        if (n < 12) return 0.0
        val wr = (w + 2.0) / (n + 4.0)
        val edge = wr - 0.5
        val confidence = ((n - 12).toDouble() / 88.0).coerceIn(0.0, 1.0)
        return (edge * confidence * 0.30).coerceIn(-0.15, 0.15)
    }

    fun contextSamples(key: String): Int = prefs.getInt("$key:w", 0) + prefs.getInt("$key:l", 0)

    fun errorSummary(): String = ErrorAnalyzer.ErrorClass.entries
        .mapNotNull { e -> prefs.getInt("ERROR:${e.name}", 0).takeIf { it > 0 }?.let { "${e.name}:$it" } }
        .joinToString(" • ").ifBlank { "Sin errores suficientes para clasificar." }

    fun exportSummary(): String {
        val obj = JSONObject()
        StrategyKind.entries.forEach { s ->
            obj.put(s.name, JSONObject().put("wins", prefs.getInt("${s.name}_w", 0)).put("losses", prefs.getInt("${s.name}_l", 0)))
        }
        return obj.toString()
    }

    fun summary(): String {
        val parts = StrategyKind.entries.mapNotNull { s -> reliability(s)?.let { "${s.title}: ${"%.0f".format(it * 100)}%" } }
        return if (parts.isEmpty()) "Aprendizaje: esperando evidencia suficiente." else parts.joinToString(" • ")
    }
}
