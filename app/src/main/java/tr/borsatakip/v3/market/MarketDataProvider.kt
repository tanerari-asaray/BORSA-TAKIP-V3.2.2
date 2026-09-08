package tr.borsatakip.v3.market

import tr.borsatakip.v3.model.Stock

data class MarketDataResult(
    val items: List<Stock>,
    val sourceName: String,
    val dataTimestamp: Long,
    val isDemo: Boolean
)

interface MarketDataProvider {
    suspend fun loadMarket(market: Market): Result<MarketDataResult>
}
