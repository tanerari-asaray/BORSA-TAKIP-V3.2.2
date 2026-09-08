package tr.borsatakip.v3.market

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tr.borsatakip.v3.analysis.TechnicalAnalyzer
import tr.borsatakip.v3.model.Candle
import tr.borsatakip.v3.model.Stock
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** BIST için gerçek günlük OHLCV verisi. Demo/sahte veri üretmez. */
class YahooBistMarketDataProvider(private val context: Context) {
    private val timeoutMs = 8_000

    private val bist30 = listOf(
        "AEFES", "AKBNK", "ASELS", "ASTOR", "BIMAS", "DSTKF", "EKGYO", "ENKAI", "EREGL", "FROTO",
        "GARAN", "GUBRF", "ISCTR", "KCHOL", "KRDMD", "MGROS", "PETKM", "PGSUS", "SAHOL", "SASA",
        "SISE", "TAVHL", "TCELL", "THYAO", "TOASO", "TRALT", "TTKOM", "TUPRS", "VAKBN", "YKBNK"
    )

    suspend fun load(): Result<MarketDataResult> = withContext(Dispatchers.IO) {
        runCatching {
            val watchlist = context.getSharedPreferences("watchlist", Context.MODE_PRIVATE)
                .getStringSet("symbols", emptySet())
                ?.map { it.trim().uppercase() }
                ?.filter { it.matches(Regex("[A-Z0-9]{3,6}")) }
                ?: emptyList()
            val symbols = (bist30 + watchlist).distinct().take(50)

            // BIST100 benchmark verisi bulunursa göreceli güç ve piyasa rejimi gerçek veriden hesaplanır.
            // Alınamazsa alanlar null kalır; kesinlikle değer uydurulmaz.
            val benchmark = fetchYahooStock("XU100.IS", "XU100")
            val benchmarkReturn20d = benchmark?.let { return20d(it.candles) }
            val marketRegime = benchmark?.let { determineMarketRegime(it) }

            val stocks = coroutineScope {
                symbols.chunked(6).flatMap { batch ->
                    batch.map { symbol -> async { fetchStock(symbol) } }.awaitAll().filterNotNull()
                }
            }.map { stock ->
                stock.copy(
                    benchmarkReturn20d = benchmarkReturn20d,
                    marketRegime = marketRegime
                )
            }

            require(stocks.isNotEmpty()) { "Yahoo Finance BIST için geçerli veri döndürmedi." }
            MarketDataResult(
                items = stocks,
                sourceName = "Yahoo Finance • BIST 30 + Takip Listesi",
                dataTimestamp = stocks.maxOf { it.dataTimestamp },
                isDemo = false
            )
        }
    }

    private fun fetchStock(symbol: String): Stock? = fetchYahooStock("${symbol}.IS", symbol)

    private fun fetchYahooStock(yahooSymbol: String, fallbackSymbol: String): Stock? {
        val encoded = URLEncoder.encode(yahooSymbol, "UTF-8")
        val url = URL("https://query1.finance.yahoo.com/v8/finance/chart/$encoded?range=1y&interval=1d&events=history")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) BorsaTakipV3/3.2.6")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            parseStock(connection.inputStream.bufferedReader().use { it.readText() }, fallbackSymbol)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parseStock(json: String, fallbackSymbol: String): Stock? {
        val root = JSONObject(json)
        val chart = root.optJSONObject("chart") ?: return null
        val result = chart.optJSONArray("result")?.optJSONObject(0) ?: return null
        val timestamps = result.optJSONArray("timestamp") ?: return null
        val quote = result.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0) ?: return null
        val opens = quote.optJSONArray("open") ?: return null
        val highs = quote.optJSONArray("high") ?: return null
        val lows = quote.optJSONArray("low") ?: return null
        val closes = quote.optJSONArray("close") ?: return null
        val volumes = quote.optJSONArray("volume") ?: return null

        val candles = ArrayList<Candle>(timestamps.length())
        for (i in 0 until timestamps.length()) {
            if (opens.isNull(i) || highs.isNull(i) || lows.isNull(i) || closes.isNull(i) || volumes.isNull(i)) continue
            val open = opens.optDouble(i, Double.NaN)
            val high = highs.optDouble(i, Double.NaN)
            val low = lows.optDouble(i, Double.NaN)
            val close = closes.optDouble(i, Double.NaN)
            val volume = volumes.optDouble(i, Double.NaN)
            if (listOf(open, high, low, close, volume).any { it.isNaN() || it.isInfinite() }) continue
            candles.add(Candle(timestamps.getLong(i) * 1000L, open, high, low, close, volume))
        }
        if (candles.size < 30) return null

        val meta = result.optJSONObject("meta")
        val displaySymbol = meta?.optString("symbol")
            ?.removeSuffix(".IS")
            ?.takeIf { it.isNotBlank() }
            ?: fallbackSymbol
        val companyName = meta?.optString("longName")?.takeIf { it.isNotBlank() }
        return Stock(
            symbol = displaySymbol,
            companyName = companyName,
            candles = candles.sortedBy { it.timestamp },
            dataTimestamp = candles.maxOf { it.timestamp },
            bedelsizPercent = null,
            isDemo = false
        )
    }

    private fun return20d(candles: List<Candle>): Double? {
        if (candles.size < 21) return null
        val start = candles[candles.lastIndex - 20].close
        val end = candles.last().close
        if (start <= 0.0) return null
        return (end / start - 1.0) * 100.0
    }

    private fun determineMarketRegime(stock: Stock): String {
        val t = TechnicalAnalyzer.analyze(stock.candles)
        val price = stock.candles.last().close
        return when {
            t.ema20 != null && t.ema50 != null && price > t.ema20 && t.ema20 > t.ema50 -> "BULL"
            t.ema20 != null && t.ema50 != null && price < t.ema20 && t.ema20 < t.ema50 -> "BEAR"
            else -> "NEUTRAL"
        }
    }
}
