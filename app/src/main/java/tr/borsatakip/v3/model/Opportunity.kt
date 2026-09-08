package tr.borsatakip.v3.model

data class ScoreComponent(val name: String, val points: Int, val maxPoints: Int)

data class TechnicalSnapshot(
    val ema20: Double?, val ema50: Double?, val ema200: Double?, val rsi14: Double?,
    val macd: Double?, val macdSignal: Double?, val atr14: Double?, val bollingerUpper: Double?,
    val bollingerLower: Double?, val volumeRatio: Double?, val support: Double?, val resistance: Double?
)

data class Opportunity(
    val symbol: String, val companyName: String?, val price: Double, val score: Int,
    val direction: String, val confidence: String, val entry: Double?, val target1: Double?,
    val target2: Double?, val stop: Double?, val riskReward: Double?, val technical: TechnicalSnapshot,
    val breakdown: List<ScoreComponent>, val dataTimestamp: Long, val isDemo: Boolean
)
