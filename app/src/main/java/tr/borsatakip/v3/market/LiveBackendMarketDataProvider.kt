package tr.borsatakip.v3.market

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tr.borsatakip.v3.BuildConfig
import tr.borsatakip.v3.model.Candle
import tr.borsatakip.v3.model.Stock
import java.net.HttpURLConnection
import java.net.URL

class LiveBackendMarketDataProvider(private val context: Context) : MarketDataProvider {
    private val prefs = context.getSharedPreferences("market_api_settings", Context.MODE_PRIVATE)

    override suspend fun loadMarket(market: Market): Result<MarketDataResult> = withContext(Dispatchers.IO) {
        runCatching {
            val base = prefs.getString("base_url", BuildConfig.MARKET_API_BASE_URL)?.trim()?.trimEnd('/') .orEmpty()
            require(base.isNotBlank()) { "Canlı veri backend adresi yapılandırılmamış. Ayarlar > API Ayarları bölümünden girin." }
            require(base.startsWith("https://")) { "Canlı veri backend adresi HTTPS olmalıdır." }
            val apiKey = prefs.getString("api_key", "")?.trim().orEmpty()
            val apiSecret = prefs.getString("api_secret", "")?.trim().orEmpty()
            val path = if (market == Market.BIST) "/api/v1/market/bist" else "/api/v1/market/viop"
            val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Cache-Control", "no-cache")
                if (apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("X-API-Key", apiKey)
                }
                if (apiSecret.isNotBlank()) setRequestProperty("X-API-Secret", apiSecret)
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) error("Veri sunucusu HTTP $code döndürdü.")
                parse(connection.inputStream.bufferedReader().use { it.readText() })
            } finally { connection.disconnect() }
        }
    }

    private fun parse(json: String): MarketDataResult {
        val root = JSONObject(json)
        val source = root.optString("source", "Backend")
        val topTimestamp = root.optLong("dataTimestamp", System.currentTimeMillis())
        val instruments = root.getJSONArray("instruments")
        val items = buildList {
            for (i in 0 until instruments.length()) {
                val obj = instruments.getJSONObject(i)
                val candlesJson = obj.getJSONArray("candles")
                val candles = buildList {
                    for (j in 0 until candlesJson.length()) {
                        val c = candlesJson.getJSONObject(j)
                        add(Candle(c.getLong("timestamp"), c.getDouble("open"), c.getDouble("high"), c.getDouble("low"), c.getDouble("close"), c.getDouble("volume")))
                    }
                }.sortedBy { it.timestamp }
                if (candles.isNotEmpty()) add(Stock(obj.getString("symbol"), obj.optString("companyName").takeIf { it.isNotBlank() }, candles, obj.optLong("dataTimestamp", topTimestamp), if (obj.has("bedelsizPercent") && !obj.isNull("bedelsizPercent")) obj.getDouble("bedelsizPercent") else null, false))
            }
        }
        require(items.isNotEmpty()) { "Backend geçerli piyasa kaydı döndürmedi." }
        return MarketDataResult(items, source, topTimestamp, false)
    }
}
