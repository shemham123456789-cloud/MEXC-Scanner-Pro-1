package com.neuronis.jarvis.data

import com.neuronis.jarvis.core.Candle
import com.neuronis.jarvis.core.OrderFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.max

/** Live MEXC Futures stream: ticker, deals, depth and 1m kline. Reconnects automatically. */
class MexcFuturesWebSocket(private val listener: Listener) {
    interface Listener {
        fun onTicker(symbol: String, last: Double, funding: Double, index: Double, fair: Double, volume24: Double, ts: Long)
        fun onTrade(symbol: String, price: Double, volume: Double, buy: Boolean, ts: Long)
        fun onDepth(symbol: String, bids: List<Pair<Double, Double>>, asks: List<Pair<Double, Double>>, version: Long, ts: Long)
        fun onKline(symbol: String, candle: Candle)
        fun onStatus(text: String) {}
    }
    private val client = OkHttpClient.Builder().pingInterval(15, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
    private var ws: WebSocket? = null
    private var symbol: String? = null
    private var stopped = false

    @Synchronized fun start(symbol: String) {
        stop(false); this.symbol = symbol.uppercase(); stopped = false
        connect()
    }
    @Synchronized fun stop(closeClient: Boolean = true) {
        stopped = true; ws?.close(1000, "D5 stop"); ws = null
        if (closeClient) client.dispatcher.executorService.shutdown()
    }
    private fun connect() {
        val s = symbol ?: return
        val req = Request.Builder().url("wss://contract.mexc.com/edge").build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onStatus("WS LIVE • $s")
                listOf(
                    JSONObject().put("method", "sub.ticker").put("param", JSONObject().put("symbol", s)),
                    JSONObject().put("method", "sub.deal").put("param", JSONObject().put("symbol", s)),
                    JSONObject().put("method", "sub.depth").put("param", JSONObject().put("symbol", s).put("compress", true)),
                    JSONObject().put("method", "sub.kline").put("param", JSONObject().put("symbol", s).put("interval", "Min1"))
                ).forEach { webSocket.send(it.toString()) }
            }
            override fun onMessage(webSocket: WebSocket, text: String) { runCatching { handle(JSONObject(text)) } }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onStatus("WS RECONNECTING • ${t.message ?: "error"}")
                if (!stopped) { Thread.sleep(1000); connect() }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (!stopped) { Thread.sleep(500); connect() } }
        })
    }
    private fun handle(x: JSONObject) {
        val ch = x.optString("channel"); val data = x.opt("data"); val s = x.optString("symbol", symbol ?: ""); val ts = x.optLong("ts", System.currentTimeMillis())
        when (ch) {
            "push.ticker" -> { val d = data as? JSONObject ?: return; listener.onTicker(s, d.num("lastPrice"), d.num("fundingRate"), d.num("indexPrice"), d.num("fairPrice"), d.num("volume24"), d.optLong("timestamp", ts)) }
            "push.deal" -> { val d = data as? JSONObject ?: return; listener.onTrade(s, d.num("p"), d.num("v"), d.optInt("T", 2) == 1, d.optLong("t", ts)) }
            "push.depth" -> { val d = data as? JSONObject ?: return; listener.onDepth(s, levels(d.optJSONArray("bids")), levels(d.optJSONArray("asks")), d.optLong("version"), ts) }
            "push.kline" -> { val d = data as? JSONObject ?: return; listener.onKline(s, Candle(d.optLong("t") * 1000L, d.num("o"), d.num("h"), d.num("l"), d.num("c"), max(0.0, d.num("q")))) }
        }
    }
    private fun levels(a: org.json.JSONArray?): List<Pair<Double, Double>> {
        if (a == null) return emptyList(); return buildList(a.length()) { for (i in 0 until a.length()) { val r = a.optJSONArray(i) ?: continue; add(r.optDouble(0) to r.optDouble(1)) } }
    }
    private fun JSONObject.num(k: String): Double = optDouble(k, optString(k).toDoubleOrNull() ?: 0.0)
}
