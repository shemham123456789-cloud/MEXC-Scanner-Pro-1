package com.neuronis.jarvis.ui

import androidx.compose.ui.graphics.Color
import com.neuronis.jarvis.core.*

val BG = Color(0xFF050914); val PANEL = Color(0xFF0B1324); val PANEL2 = Color(0xFF101C31)
val CYAN = Color(0xFF00E5FF); val BLUE = Color(0xFF4A93FF); val GREEN = Color(0xFF00E676); val RED = Color(0xFFFF4668); val GOLD = Color(0xFFFFBD4A)
val PURPLE = Color(0xFFB86BFF); val MUTED = Color(0xFF8FA0B7); val GRID = Color(0xFF22314C); val TEXT = Color(0xFFF4F7FE)
fun signalColor(d: SignalDirection) = when (d) { SignalDirection.LONG -> GREEN; SignalDirection.SHORT -> RED; SignalDirection.FLAT -> GOLD }
fun zoneColor(t: ZoneType) = when (t) { ZoneType.DEMAND -> GREEN; ZoneType.SUPPLY -> RED; ZoneType.ORDER_BLOCK -> BLUE; ZoneType.FVG -> PURPLE; ZoneType.LIQUIDITY_HIGH -> RED; ZoneType.LIQUIDITY_LOW -> GREEN; ZoneType.VWAP -> CYAN; ZoneType.PIVOT -> GOLD }
fun priceText(v: Double): String = when { v >= 10000 -> "%.2f".format(v); v >= 100 -> "%.3f".format(v); v >= 1 -> "%.5f".format(v); else -> "%.8f".format(v) }
