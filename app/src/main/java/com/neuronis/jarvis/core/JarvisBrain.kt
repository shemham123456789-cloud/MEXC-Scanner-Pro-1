package com.neuronis.jarvis.core

object JarvisBrain {
    fun answer(question: String, asset: AssetType, analysis: AnalysisSnapshot, paper: String = "sin posición"): String {
        val q = question.lowercase()
        return when {
            q.contains("entrada") || q.contains("entrar") -> "${asset.label}: ${analysis.direction.name}. Estado ${analysis.state.name}. Entrada ${fmt(analysis.entry)}, SL ${fmt(analysis.stop)}, TP2 ${fmt(analysis.tp2)}, R:R ${"%.2f".format(analysis.rr)}. No fuerces la entrada si el precio se aleja del trigger."
            q.contains("por qué") || q.contains("porque") || q.contains("razon") -> analysis.reasons.take(5).joinToString("\n")
            q.contains("riesgo") -> "Modo ${analysis.riskMode.name}: el motor reduce agresividad cuando la volatilidad o la calidad no justifican el riesgo. El paper activo es $paper."
            q.contains("qué ves") || q.contains("que ves") -> "Veo régimen ${analysis.regime.name}, MTF 4H ${analysis.trend240.name}, 1H ${analysis.trend60.name}, 15M ${analysis.trend15.name}, 5M ${analysis.trend5.name}. Calidad ${analysis.quality}/100."
            q.contains("invalida") || q.contains("invalid") -> analysis.invalidation
            else -> "Estoy observando ${asset.label}. Decisión actual: ${analysis.direction.name}; estado ${analysis.state.name}; calidad ${analysis.quality}/100. Pregunta por entrada, riesgo, razones o invalidación."
        }
    }
    private fun fmt(v: Double) = "%.6f".format(v)
}
