package com.neuronis.jarvis.core

import kotlin.math.exp

/** Converts setup evidence to a bounded probability estimate; calibration adjustments come only from past outcomes. */
object ProbabilityCalibrator {
    fun raw(quality: Int, consensus: Double, reliability: Double, microBias: Double): Double {
        val x = -2.2 + quality / 100.0 * 3.1 + consensus * .75 + (reliability - .5) * .9 + kotlin.math.abs(microBias) * .35
        return (1.0 / (1.0 + exp(-x))).coerceIn(.50, .94)
    }

    fun calibrated(raw: Double, bucketAdjustment: Double): Double = (raw + bucketAdjustment).coerceIn(.50, .96)
}
