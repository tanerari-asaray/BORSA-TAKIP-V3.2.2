package tr.borsatakip.v3.market

import android.content.Context
import tr.borsatakip.v3.BuildConfig

/** Gerçek veri sağlayıcısı. BIST için backend yoksa doğrudan canlı piyasa verisi kullanılır. */
class AppMarketDataProvider(context: Context) : MarketDataProvider {
    private val live = LiveBackendMarketDataProvider(context)
    private val yahooBist = YahooBistMarketDataProvider(context)
    private val prefs = context.getSharedPreferences("market_api_settings", Context.MODE_PRIVATE)

    override suspend fun loadMarket(market: Market): Result<MarketDataResult> {
        if (market == Market.BIST) {
            val configuredBase = prefs.getString("base_url", BuildConfig.MARKET_API_BASE_URL)?.trim().orEmpty()
            if (configuredBase.isBlank()) return yahooBist.load()
            return live.loadMarket(market).recoverCatching { yahooBist.load().getOrThrow() }
        }
        return live.loadMarket(market)
    }
}
