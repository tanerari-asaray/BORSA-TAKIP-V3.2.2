package tr.borsatakip.v3.scoring

import tr.borsatakip.v3.analysis.TechnicalAnalyzer
import tr.borsatakip.v3.model.Opportunity
import tr.borsatakip.v3.model.ScoreComponent
import tr.borsatakip.v3.model.Stock
import kotlin.math.abs

class ScoringEngine {
    fun score(stock: Stock): Opportunity? {
        if (stock.candles.size < 50) return null
        val t = TechnicalAnalyzer.analyze(stock.candles)
        val price = stock.candles.last().close
        if (price <= 0.0) return null
        val longScore = scoreSide(price, t, true)
        val shortScore = scoreSide(price, t, false)
        val useLong = longScore.total >= shortScore.total
        val best = if (useLong) longScore else shortScore
        val opposite = if (useLong) shortScore else longScore
        val direction = when {
            best.total < 70 -> "SİNYAL YOK"
            best.total - opposite.total < 10 -> "SİNYAL YOK"
            useLong -> "LONG"
            else -> "SHORT"
        }
        val atr = t.atr14
        val stop = when {
            atr == null -> null
            direction == "LONG" -> (price - 1.5 * atr).takeIf { it > 0 }
            direction == "SHORT" -> price + 1.5 * atr
            else -> null
        }
        val target1 = when {
            atr == null -> null
            direction == "LONG" -> t.resistance?.takeIf { it > price } ?: price + 2 * atr
            direction == "SHORT" -> t.support?.takeIf { it < price && it > 0 } ?: (price - 2 * atr).takeIf { it > 0 }
            else -> null
        }
        val target2 = when {
            atr == null || target1 == null -> null
            direction == "LONG" -> target1 + atr
            direction == "SHORT" -> (target1 - atr).takeIf { it > 0 }
            else -> null
        }
        val rr = when {
            direction == "LONG" && stop != null && target1 != null && price > stop -> (target1 - price) / (price - stop)
            direction == "SHORT" && stop != null && target1 != null && stop > price -> (price - target1) / (stop - price)
            else -> null
        }
        return Opportunity(stock.symbol, stock.companyName, price, best.total, direction, classify(best.total), if (direction == "SİNYAL YOK") null else price, target1, target2, stop, rr, t, best.components + ScoreComponent("Karşı yön skoru", opposite.total, 100), stock.dataTimestamp, false)
    }

    private data class SideScore(val total: Int, val components: List<ScoreComponent>)

    private fun scoreSide(price: Double, t: tr.borsatakip.v3.model.TechnicalSnapshot, isLong: Boolean): SideScore {
        fun side(a: Boolean, b: Boolean) = if (isLong) a else b
        val c = mutableListOf<ScoreComponent>()
        val trend = when {
            t.ema20 != null && t.ema50 != null && t.ema200 != null && side(price > t.ema20 && t.ema20 > t.ema50 && t.ema50 > t.ema200, price < t.ema20 && t.ema20 < t.ema50 && t.ema50 < t.ema200) -> 20
            t.ema20 != null && t.ema50 != null && side(price > t.ema20 && t.ema20 > t.ema50, price < t.ema20 && t.ema20 < t.ema50) -> 15
            t.ema20 != null && side(price > t.ema20, price < t.ema20) -> 10
            else -> 2
        }
        c += ScoreComponent("Trend", trend, 20)
        val momentum = when {
            t.macd != null && t.macdSignal != null && t.rsi14 != null && side(t.macd > t.macdSignal && t.rsi14 >= 52, t.macd < t.macdSignal && t.rsi14 <= 48) -> 20
            t.macd != null && t.macdSignal != null && side(t.macd > t.macdSignal, t.macd < t.macdSignal) -> 14
            t.rsi14 != null && side(t.rsi14 >= 50, t.rsi14 <= 50) -> 9
            else -> 3
        }
        c += ScoreComponent("Momentum", momentum, 20)
        val vr = t.volumeRatio ?: 0.0
        c += ScoreComponent("Hacim", when { vr >= 2 -> 15; vr >= 1.5 -> 12; vr >= 1.2 -> 9; vr >= 1 -> 6; else -> 2 }, 15)
        val r = t.rsi14
        c += ScoreComponent("RSI", when { r == null -> 0; isLong && r in 52.0..68.0 -> 15; !isLong && r in 32.0..48.0 -> 15; isLong && r in 48.0..72.0 -> 10; !isLong && r in 28.0..52.0 -> 10; else -> 3 }, 15)
        c += ScoreComponent("Hareketli Ort.", when { t.ema20 != null && t.ema50 != null && side(price > t.ema20 && t.ema20 > t.ema50, price < t.ema20 && t.ema20 < t.ema50) -> 10; t.ema20 != null && side(price > t.ema20, price < t.ema20) -> 6; else -> 2 }, 10)
        val ar = t.atr14?.let { abs(it / price) }
        c += ScoreComponent("Volatilite", when { ar == null -> 0; ar in 0.008..0.045 -> 10; ar in 0.004..0.065 -> 7; ar <= 0.10 -> 4; else -> 1 }, 10)
        c += ScoreComponent("Risk", if (t.support != null && t.resistance != null && t.support < price && t.resistance > price) 10 else if (t.support != null || t.resistance != null) 6 else 2, 10)
        return SideScore(c.sumOf { it.points }.coerceIn(0, 100), c)
    }

    private fun classify(score: Int) = when { score >= 90 -> "ÇOK GÜÇLÜ FIRSAT"; score >= 80 -> "GÜÇLÜ FIRSAT"; score >= 70 -> "İZLE"; else -> "ZAYIF" }
}
