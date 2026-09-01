package com.neuronis.jarvis.core

import kotlin.math.roundToInt

object PatternFingerprint {
    fun create(symbol: String, a: AnalysisSnapshot, flow: OrderFlow): String {
        val zone = a.zones.minByOrNull { kotlin.math.abs(a.entry - it.mid) }?.type?.name ?: "NONE"
        val q = (a.quality / 10) * 10
        val vol = (a.volatilityPct * 10).roundToInt()
        val flowBin = (flow.depthImbalance * 4).roundToInt()
        return listOf(symbol.uppercase(), a.direction.name, a.regime.name, zone, q, vol, flowBin, a.riskMode.name).joinToString("|")
    }
}
