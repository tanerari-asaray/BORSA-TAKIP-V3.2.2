package tr.borsatakip.v3.market

import android.content.Context
import tr.borsatakip.v3.BuildConfig

/**
 * Tek gerçek veri yolu.
 *
 * V3.2.7 itibarıyla Yahoo gecikmeli veri fallback'i kaldırılmıştır.
 * Kullanıcı API AYARLARI sekmesinden gerçek veri backend'ini yapılandırmadan
 * BIST veya VİOP taraması sahte/gecikmeli veri ile devam etmez.
 */
class AppMarketDataProvider(context: Context) : MarketDataProvider {
    private val live = LiveBackendMarketDataProvider(context)
    private val prefs = context.getSharedPreferences("market_api_settings", Context.MODE_PRIVATE)

    override suspend fun loadMarket(market: Market): Result<MarketDataResult> {
        val configuredBase = prefs
            .getString("base_url", BuildConfig.MARKET_API_BASE_URL)
            ?.trim()
            .orEmpty()

        if (configuredBase.isBlank()) {
            return Result.failure(
                IllegalStateException("Gerçek piyasa veri backend adresi yapılandırılmamış.")
            )
        }

        return live.loadMarket(market)
    }
}
