package com.neuronis.jarvis.core

/** Rejects self-modifications that are unsupported or unstable. */
object LearningGovernor {
    data class Decision(val approved: Boolean, val reason: String)
    fun approve(oosBefore: Double, oosAfter: Double, stabilityBefore: Double, stabilityAfter: Double, samples: Int): Decision {
        if (samples < 80) return Decision(false, "evidencia insuficiente")
        if (!oosAfter.isFinite() || !stabilityAfter.isFinite()) return Decision(false, "métricas inválidas")
        val gain = oosAfter - oosBefore
        val stabilityDrop = stabilityBefore - stabilityAfter
        return when {
            gain < 0.03 -> Decision(false, "mejora OOS demasiado pequeña")
            stabilityDrop > 0.08 -> Decision(false, "la mejora reduce estabilidad")
            else -> Decision(true, "mejora OOS estable y con evidencia suficiente")
        }
    }
}
