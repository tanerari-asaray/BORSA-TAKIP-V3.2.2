package tr.borsatakip.v3.market

import android.content.Context

/** Gerçek backend sağlayıcısı; demo veriye geri dönüş yoktur. */
class AppMarketDataProvider(context: Context) : MarketDataProvider {
    private val live = LiveBackendMarketDataProvider(context)
    override suspend fun loadMarket(market: Market): Result<MarketDataResult> = live.loadMarket(market)
}
