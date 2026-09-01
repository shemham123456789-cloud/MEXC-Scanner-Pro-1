package com.neuronis.jarvis.data

import android.content.Context
import com.neuronis.jarvis.core.Candle
import com.neuronis.jarvis.core.AssetType
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class HistoryStore(private val context: Context) {
    private fun dir(asset: AssetType, timeframe: String) = File(context.filesDir, "history/${asset.apiSymbol}/$timeframe").apply { mkdirs() }

    fun append(asset: AssetType, timeframe: String, candles: List<Candle>) {
        if (candles.isEmpty()) return
        val buckets = candles.groupBy { it.time / (7L * 24 * 3600) }
        buckets.forEach { (bucket, rows) ->
            val file = File(dir(asset, timeframe), "$bucket.csv.gz")
            val merged = ((if (file.exists()) readFile(file) else emptyList()) + rows).distinctBy { it.time }.sortedBy { it.time }
            GZIPOutputStream(FileOutputStream(file)).bufferedWriter().use { out -> merged.forEach { out.append("${it.time},${it.open},${it.high},${it.low},${it.close},${it.volume}\n") } }
        }
    }

    fun read(asset: AssetType, timeframe: String, limit: Int = 60000): List<Candle> {
        val files = dir(asset, timeframe).listFiles()?.filter { it.extension == "gz" }?.sortedBy { it.name }.orEmpty()
        if (files.isEmpty()) return emptyList()
        return files.flatMap { readFile(it) }.distinctBy { it.time }.sortedBy { it.time }.takeLast(limit)
    }

    fun count(asset: AssetType, timeframe: String): Long = read(asset, timeframe, 2_000_000).size.toLong()

    private fun readFile(file: File): List<Candle> = runCatching {
        GZIPInputStream(FileInputStream(file)).bufferedReader().useLines { lines ->
            lines.mapNotNull { row ->
                val p = row.split(',')
                if (p.size != 6) null else runCatching { Candle(p[0].toLong(), p[1].toDouble(), p[2].toDouble(), p[3].toDouble(), p[4].toDouble(), p[5].toDouble()) }.getOrNull()
            }.toList()
        }
    }.getOrDefault(emptyList())
}
