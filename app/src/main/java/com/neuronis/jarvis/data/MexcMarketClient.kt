package com.neuronis.jarvis.data

import com.neuronis.jarvis.core.AssetType
import com.neuronis.jarvis.core.Candle
import com.neuronis.jarvis.core.ContractInfo
import com.neuronis.jarvis.core.MarketSnapshot
import com.neuronis.jarvis.core.OrderFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class MexcMarketClient {
    companion object { private const val BASE = "https://api.mexc.com" }
    private val lastJson = ConcurrentHashMap<String, Pair<Long, JSONObject>>()

    fun fetchSnapshot(asset: AssetType): MarketSnapshot = fetchSnapshotSymbol(asset.apiSymbol, asset)

    fun fetchSnapshotSymbol(symbol: String, asset: AssetType = alias(symbol)): MarketSnapshot {
        val ticker = get("$BASE/api/v1/contract/ticker?symbol=${URLEncoder.encode(symbol, "UTF-8")}").optJSONObject("data") ?: JSONObject()
        val depth = runCatching { get("$BASE/api/v1/contract/depth/$symbol?limit=20").optJSONObject("data") }.getOrNull()
        val bids = sumDepth(depth?.optJSONArray("bids")); val asks = sumDepth(depth?.optJSONArray("asks"))
        val price = ticker.optDouble("lastPrice", ticker.optDouble("last", 0.0))
        return MarketSnapshot(
            asset = asset,
            price = price,
            changePct = firstDouble(ticker, "riseFallRate", "changeRate", "change") * 100.0,
            volume24h = firstDouble(ticker, "volume24", "amount24", "volume24h", "volume"),
            indexPrice = firstDouble(ticker, "indexPrice", "index", "fairPrice").takeIf { it > 0 } ?: price,
            fairPrice = firstDouble(ticker, "fairPrice", "markPrice").takeIf { it > 0 } ?: price,
            fundingRate = firstDouble(ticker, "fundingRate", "funding"),
            updatedAt = ticker.optLong("timestamp", System.currentTimeMillis()),
            flow = OrderFlow(
                bidDepth = bids, askDepth = asks, fundingRate = firstDouble(ticker, "fundingRate", "funding"),
                depthImbalance = if (bids + asks > 0) (bids - asks) / (bids + asks) else 0.0,
                spreadPct = if (price > 0 && depth != null) {
                    val bb = depth.optJSONArray("bids")?.optJSONArray(0)?.optDouble(0, price) ?: price
                    val ba = depth.optJSONArray("asks")?.optJSONArray(0)?.optDouble(0, price) ?: price
                    ((ba - bb) / price).coerceAtLeast(0.0)
                } else 0.0,
                flowUpdatedAt = System.currentTimeMillis()
            )
        )
    }

    fun fetchCandles(asset: AssetType, interval: String = "Min1", startSec: Long? = null, endSec: Long? = null): List<Candle> = fetchCandlesSymbol(asset.apiSymbol, interval, startSec, endSec)

    fun fetchCandlesSymbol(symbol: String, interval: String = "Min1", startSec: Long? = null, endSec: Long? = null): List<Candle> {
        val q = buildString {
            append("interval=").append(URLEncoder.encode(interval, "UTF-8"))
            startSec?.let { append("&start=").append(it) }
            endSec?.let { append("&end=").append(it) }
        }
        val data = get("$BASE/api/v1/contract/kline/${URLEncoder.encode(symbol, "UTF-8")}?$q").optJSONObject("data") ?: return emptyList()
        val t = data.optJSONArray("time") ?: JSONArray(); val o = data.optJSONArray("open") ?: JSONArray(); val h = data.optJSONArray("high") ?: JSONArray(); val l = data.optJSONArray("low") ?: JSONArray(); val c = data.optJSONArray("close") ?: JSONArray(); val v = data.optJSONArray("vol") ?: JSONArray()
        return buildList(t.length()) {
            for (i in 0 until t.length()) {
                runCatching { add(Candle(t.getLong(i), o.getDouble(i), h.getDouble(i), l.getDouble(i), c.getDouble(i), v.optDouble(i, 0.0))) }
            }
        }.sortedBy { it.time }
    }

    fun discoverContracts(): List<ContractInfo> {
        val data = get("$BASE/api/v1/contract/detail").optJSONArray("data") ?: return emptyList()
        return buildList(data.length()) {
            for (i in 0 until data.length()) runCatching {
                val x = data.getJSONObject(i)
                add(ContractInfo(
                    symbol = x.optString("symbol"),
                    displayName = x.optString("displayNameEn", x.optString("displayName")),
                    quoteCoin = x.optString("quoteCoin", x.optString("settleCoin")),
                    settleCoin = x.optString("settleCoin", x.optString("quoteCoin")),
                    state = x.optInt("state", 0),
                    contractSize = x.optDouble("contractSize", 1.0),
                    minVol = x.optDouble("minVol", 0.0),
                    maxVol = x.optDouble("maxVol", 0.0)
                ))
            }
        }.filter { it.symbol.isNotBlank() && (it.quoteCoin.equals("USDT", true) || it.settleCoin.equals("USDT", true)) && it.state == 0 }
    }

    fun discoverTopUsdtMarkets(limit: Int): List<String> {
        val contracts = discoverContracts().associateBy { it.symbol }
        val data = runCatching { get("$BASE/api/v1/contract/ticker").optJSONArray("data") }.getOrNull()
        if (data == null) return contracts.keys.take(limit)
        val scored = buildList {
            for (i in 0 until data.length()) {
                val x = data.optJSONObject(i) ?: continue
                val symbol = x.optString("symbol")
                if (!contracts.containsKey(symbol)) continue
                val amount = firstDouble(x, "amount24", "volume24", "volume", "turnover")
                if (amount.isFinite()) add(symbol to amount)
            }
        }.sortedByDescending { it.second }
        if (scored.isEmpty()) return contracts.keys.take(limit)
        // Diversified universe: leaders + liquid middle + a deterministic sample of the tail.
        val n = limit.coerceIn(3, 80)
        val top = scored.take((n * .45).toInt().coerceAtLeast(1))
        val midStart = (scored.size * .25).toInt().coerceAtMost(scored.lastIndex)
        val mid = scored.drop(midStart).take((n * .30).toInt().coerceAtLeast(1))
        val tailPool = scored.drop((scored.size * .70).toInt().coerceAtMost(scored.size))
        val tail = tailPool.filterIndexed { i, _ -> i % maxOf(1, tailPool.size / (n * .25).toInt().coerceAtLeast(1)) == 0 }.take((n * .25).toInt().coerceAtLeast(1))
        return (top + mid + tail).map { it.first }.distinct().take(n)
    }

    private fun alias(symbol: String): AssetType = when {
        symbol.equals("ETH_USDT", true) -> AssetType.ETH
        symbol.equals("XAU_USDT", true) -> AssetType.XAU
        else -> AssetType.BTC
    }

    private fun firstDouble(x: JSONObject, vararg keys: String): Double {
        for (k in keys) if (x.has(k) && !x.isNull(k)) {
            val v = x.optDouble(k, Double.NaN)
            if (!v.isNaN()) return v
            val s = x.optString(k)
            s.toDoubleOrNull()?.let { return it }
        }
        return 0.0
    }

    private fun sumDepth(a: JSONArray?): Double {
        if (a == null) return 0.0
        var sum = 0.0
        for (i in 0 until a.length()) {
            val row = a.optJSONArray(i) ?: continue
            sum += row.optDouble(1, 0.0)
        }
        return sum
    }

    private fun get(url: String): JSONObject {
        val now = System.currentTimeMillis(); lastJson[url]?.let { if (now - it.first < 1500) return it.second }
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = "GET"; c.connectTimeout = 7000; c.readTimeout = 9000; c.setRequestProperty("Accept", "application/json")
                try {
                    if (c.responseCode !in 200..299) error("MEXC HTTP ${c.responseCode}")
                    val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    lastJson[url] = System.currentTimeMillis() to json
                    return json
                } finally { c.disconnect() }
            } catch (e: Exception) { lastError = e; Thread.sleep((200L * (attempt + 1))) }
        }
        throw lastError ?: IllegalStateException("MEXC request failed")
    }
}
