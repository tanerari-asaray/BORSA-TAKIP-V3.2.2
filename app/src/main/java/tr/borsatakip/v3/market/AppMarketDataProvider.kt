package tr.borsatakip.v3.market

import android.content.Context
import tr.borsatakip.v3.BuildConfig

/**
 * Veri kaynağı seçimi:
 * - API Key girilmişse gerçek veri backend'i kullanılır.
 * - API Key boşsa BIST için Yahoo Finance otomatik yedek kaynak olarak kullanılır.
 *
 * Yahoo Finance verisi gerçek zamanlı kabul edilmez; uygulama kaynak bilgisini
 * "Yahoo Finance" olarak gösterir. VİOP için Yahoo fallback'i kullanılmaz.
 */
class AppMarketDataProvider(context: Context) : MarketDataProvider {
    private val live = LiveBackendMarketDataProvider(context)
    private val yahoo = YahooBistMarketDataProvider(context)
    private val prefs = context.getSharedPreferences("market_api_settings", Context.MODE_PRIVATE)

    override suspend fun loadMarket(market: Market): Result<MarketDataResult> {
        val apiKey = prefs.getString("api_key", "")?.trim().orEmpty()
        val configuredBase = prefs
            .getString("base_url", BuildConfig.MARKET_API_BASE_URL)
            ?.trim()
            .orEmpty()

        // API Key girilmemişse kullanıcıyı API kurulumu bekletmeden BIST verisini Yahoo'dan al.
        if (apiKey.isBlank()) {
            return if (market == Market.BIST) {
                yahoo.load().map { result ->
                    result.copy(sourceName = "Yahoo Finance • API Key girilmedi")
                }
            } else {
                Result.failure(
                    IllegalStateException(
                        "API Key girilmedi. Yahoo Finance üzerinden VİOP verisi desteklenmiyor. " +
                            "VİOP için Ayarlar > API Ayarları bölümünden gerçek veri API'sini yapılandırın."
                    )
                )
            }
        }

        if (configuredBase.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "API Key girildi ancak gerçek piyasa veri backend adresi yapılandırılmamış."
                )
            )
        }

        return live.loadMarket(market)
    }
}
