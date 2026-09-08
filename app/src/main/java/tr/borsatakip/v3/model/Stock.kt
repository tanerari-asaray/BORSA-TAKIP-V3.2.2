package tr.borsatakip.v3.model

data class Candle(val timestamp: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Double)

data class Stock(
    val symbol: String, val companyName: String? = null, val candles: List<Candle>,
    val dataTimestamp: Long, val bedelsizPercent: Double? = null, val isDemo: Boolean = false
)
