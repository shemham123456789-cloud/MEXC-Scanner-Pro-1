package com.neuronis.jarvis.core

import kotlin.math.abs

/** Deterministic order-flow layer. It consumes live depth/trades when available and degrades gracefully to neutral. */
object MicrostructureEngine {
    data class Read(
        val bias: Double, val quality: Double, val spreadPenalty: Double,
        val absorption: Double, val exhaustion: Double, val explanation: String
    )

    fun read(flow: OrderFlow, candles: List<Candle>): Read {
        val depth = flow.depthImbalance.coerceIn(-1.0, 1.0)
        val delta = if (flow.buyVolume + flow.sellVolume > 0) flow.tradeDelta / (flow.buyVolume + flow.sellVolume) else flow.tradeDelta.coerceIn(-1.0, 1.0)
        val velocity = flow.tradeDeltaVelocity.coerceIn(-1.0, 1.0)
        val spreadPenalty = (flow.spreadPct * 100.0).coerceIn(0.0, 1.0)
        val bias = (depth * .42 + delta * .40 + velocity * .18).coerceIn(-1.0, 1.0)
        val candlePressure = if (candles.size >= 6) {
            val recent = candles.takeLast(6)
            val up = recent.count { it.close > it.open }
            (up - 3) / 3.0
        } else 0.0
        val absorption = (abs(depth) * (1.0 - abs(delta)) * .8).coerceIn(0.0, 1.0)
        val exhaustion = (abs(delta) * (1.0 - abs(velocity)) * .8).coerceIn(0.0, 1.0)
        val quality = ((abs(bias) * .7 + abs(candlePressure) * .3) * (1.0 - spreadPenalty)).coerceIn(0.0, 1.0)
        val text = when {
            abs(bias) < .15 -> "microestructura neutra"
            bias > .15 -> "presión compradora / profundidad favorable"
            else -> "presión vendedora / profundidad favorable"
        }
        return Read(bias, quality, spreadPenalty, absorption, exhaustion, text)
    }
}
