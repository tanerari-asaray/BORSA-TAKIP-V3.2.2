package tr.borsatakip.v3.scoring

import tr.borsatakip.v3.analysis.TechnicalAnalyzer
import tr.borsatakip.v3.model.Opportunity
import tr.borsatakip.v3.model.ScoreComponent
import tr.borsatakip.v3.model.Stock
import kotlin.math.abs

/** V3.2.7 FIRSAT KONTROLÜ - seçici teknik fırsat motoru. */
class ScoringEngine {
    fun score(stock: Stock): Opportunity? {
        if (stock.isDemo || stock.candles.size < 50) return null
        if (stock.candles.any { !it.open.isFinite() || !it.high.isFinite() || !it.low.isFinite() || !it.close.isFinite() || !it.volume.isFinite() }) return null

        val t = TechnicalAnalyzer.analyze(stock.candles)
        val price = stock.candles.last().close
        if (!price.isFinite() || price <= 0.0) return null

        val longEval = evaluateSide(stock, t, isLong = true)
        val shortEval = evaluateSide(stock, t, isLong = false)
        val useLong = longEval.total >= shortEval.total
        val best = if (useLong) longEval else shortEval
        val opposite = if (useLong) shortEval else longEval
        val scoreGapPass = best.total - opposite.total >= 10
        val direction = when {
            !best.gatesPass -> "SİNYAL YOK"
            best.total < 70 -> "SİNYAL YOK"
            !scoreGapPass -> "SİNYAL YOK"
            else -> if (useLong) "LONG" else "SHORT"
        }

        return Opportunity(
            symbol = stock.symbol,
            companyName = stock.companyName,
            price = price,
            score = best.total,
            direction = direction,
            confidence = classify(best.total, best.gatesPass && scoreGapPass),
            entry = if (direction == "SİNYAL YOK") null else price,
            target1 = if (direction == "SİNYAL YOK") null else best.target1,
            target2 = if (direction == "SİNYAL YOK") null else best.target2,
            stop = if (direction == "SİNYAL YOK") null else best.stop,
            riskReward = if (direction == "SİNYAL YOK") null else best.rr,
            technical = t,
            breakdown = best.components + listOf(
                ScoreComponent("KAPI • Veri", if (best.dataPass) 1 else 0, 1),
                ScoreComponent("KAPI • Likidite", if (best.liquidityPass) 1 else 0, 1),
                ScoreComponent("KAPI • Trend", if (best.trendPass) 1 else 0, 1),
                ScoreComponent("KAPI • Yapı", if (best.structurePass) 1 else 0, 1),
                ScoreComponent("KAPI • Hacim", if (best.volumePass) 1 else 0, 1),
                ScoreComponent("KAPI • R/R", if (best.rrPass) 1 else 0, 1),
                ScoreComponent("KAPI • Aşırı Uzama Yok", if (!best.overextended) 1 else 0, 1),
                ScoreComponent("KAPI • Kritik Seviye Yok", if (best.noCriticalLevel) 1 else 0, 1),
                ScoreComponent("KAPI • Piyasa Rejimi", if (best.regimePass) 1 else 0, 1),
                ScoreComponent("Karşı yön skoru", opposite.total, 100)
            ),
            dataTimestamp = stock.dataTimestamp,
            isDemo = false
        )
    }

    private data class SideEval(
        val total: Int,
        val components: List<ScoreComponent>,
        val dataPass: Boolean,
        val liquidityPass: Boolean,
        val trendPass: Boolean,
        val structurePass: Boolean,
        val volumePass: Boolean,
        val rrPass: Boolean,
        val overextended: Boolean,
        val noCriticalLevel: Boolean,
        val regimePass: Boolean,
        val stop: Double?,
        val target1: Double?,
        val target2: Double?,
        val rr: Double?
    ) { val gatesPass get() = dataPass && liquidityPass && trendPass && structurePass && volumePass && rrPass && !overextended && noCriticalLevel && regimePass }

    private fun evaluateSide(stock: Stock, t: tr.borsatakip.v3.model.TechnicalSnapshot, isLong: Boolean): SideEval {
        val candles = stock.candles
        val last = candles.last()
        val price = last.close
        val prev20 = candles.dropLast(1).takeLast(20)
        val prev50 = candles.dropLast(1).takeLast(50)
        val prev5 = candles.dropLast(6).takeLast(5)
        val recent5 = candles.dropLast(1).takeLast(5)
        val previous = if (candles.size >= 65) TechnicalAnalyzer.analyze(candles.dropLast(5)) else t

        val dataPass = !stock.isDemo && candles.size >= 50 && stock.dataTimestamp > 0L && candles.takeLast(20).all { c ->
            c.open > 0 && c.high > 0 && c.low > 0 && c.close > 0 && c.high >= c.low && c.volume.isFinite()
        }
        val avgVol = prev20.map { it.volume }.filter { it > 0 }.takeIf { it.size >= 15 }?.average()
        val volumeRatio = if (avgVol != null && avgVol > 0) last.volume / avgVol else null
        val liquidityPass = avgVol != null && prev20.count { it.volume > 0 } >= 18 && last.volume > 0
        val volumePass = volumeRatio != null && volumeRatio >= 1.0

        val ema20 = t.ema20
        val ema50 = t.ema50
        val ema200 = t.ema200
        val prevEma20 = previous.ema20
        val prevEma50 = previous.ema50
        val slopeLong = ema20 != null && prevEma20 != null && ema50 != null && prevEma50 != null && ema20 >= prevEma20 && ema50 >= prevEma50
        val slopeShort = ema20 != null && prevEma20 != null && ema50 != null && prevEma50 != null && ema20 <= prevEma20 && ema50 <= prevEma50
        val slopePass = if (isLong) slopeLong else slopeShort
        val trendPass = if (isLong) {
            ema20 != null && ema50 != null && price > ema20 && price > ema50 && ema20 > ema50 && slopePass
        } else {
            ema20 != null && ema50 != null && price < ema20 && price < ema50 && ema20 < ema50 && slopePass
        }

        val priorResistance20 = prev20.maxOfOrNull { it.high }
        val priorSupport20 = prev20.minOfOrNull { it.low }
        val breakout = isLong && priorResistance20 != null && price > priorResistance20 && volumeRatio != null && volumeRatio >= 1.5
        val breakdown = !isLong && priorSupport20 != null && price < priorSupport20 && volumeRatio != null && volumeRatio >= 1.5
        val range = (last.high - last.low).takeIf { it > 0 }
        val closePos = range?.let { (last.close - last.low) / it } ?: 0.5
        val closeQuality = if (isLong) closePos >= 0.65 else closePos <= 0.35
        val confirmedBreak = if (isLong) breakout && closeQuality else breakdown && closeQuality
        val higherStructure = recent5.size == 5 && prev5.size == 5 && recent5.maxOf { it.high } > prev5.maxOf { it.high } && recent5.minOf { it.low } > prev5.minOf { it.low }
        val lowerStructure = recent5.size == 5 && prev5.size == 5 && recent5.maxOf { it.high } < prev5.maxOf { it.high } && recent5.minOf { it.low } < prev5.minOf { it.low }
        val structurePass = if (isLong) confirmedBreak || higherStructure else confirmedBreak || lowerStructure

        val rsi = t.rsi14
        val macdLong = t.macd != null && t.macdSignal != null && t.macd > t.macdSignal
        val macdShort = t.macd != null && t.macdSignal != null && t.macd < t.macdSignal
        val preferredMomentum = if (isLong) rsi != null && rsi in 55.0..68.0 && macdLong else rsi != null && rsi in 32.0..45.0 && macdShort
        val acceptableMomentum = if (isLong) rsi != null && rsi > 50 && macdLong else rsi != null && rsi < 50 && macdShort

        val atr = t.atr14
        val distanceAtr = if (atr != null && atr > 0 && t.ema20 != null) abs(price - t.ema20) / atr else null
        val rsiExtended = if (isLong) (rsi ?: 50.0) >= 80 else (rsi ?: 50.0) <= 20
        val abnormalMove = atr != null && atr > 0 && abs(last.close - last.open) > 2.5 * atr
        val overextended = (distanceAtr != null && distanceAtr >= 2.5) || rsiExtended || abnormalMove

        val recentLow = prev20.minOfOrNull { it.low }
        val recentHigh = prev20.maxOfOrNull { it.high }
        val stop = if (atr != null && atr > 0) {
            if (isLong) {
                val a = price - 1.5 * atr
                val s = recentLow?.minus(0.10 * atr)
                listOfNotNull(a.takeIf { it > 0 }, s?.takeIf { it > 0 && it < price }).maxOrNull()
            } else {
                val a = price + 1.5 * atr
                val s = recentHigh?.plus(0.10 * atr)
                listOfNotNull(a, s?.takeIf { it > price }).minOrNull()
            }
        } else null
        val risk = if (isLong) stop?.takeIf { it < price }?.let { price - it } else stop?.takeIf { it > price }?.let { it - price }
        val resistance50 = prev50.map { it.high }.filter { it > price }.minOrNull()
        val support50 = prev50.map { it.low }.filter { it < price }.maxOrNull()
        val target1 = if (isLong) resistance50 ?: atr?.takeIf { it > 0 }?.let { price + 2 * it } else support50 ?: atr?.takeIf { it > 0 }?.let { (price - 2 * it).takeIf { x -> x > 0 } }
        val rr = if (risk != null && risk > 0 && target1 != null) {
            val reward = if (isLong) target1 - price else price - target1
            if (reward > 0) reward / risk else null
        } else null
        val rrPass = rr != null && rr >= 1.75
        val noCriticalLevel = rrPass
        val target2 = risk?.let { if (isLong) price + 3 * it else (price - 3 * it).takeIf { x -> x > 0 } }

        val rs = stock.benchmarkReturn20d?.let { benchmark ->
            if (candles.size >= 21) (price / candles[candles.lastIndex - 20].close - 1) * 100 - benchmark else null
        }
        val rsDirectional = if (rs == null) null else if (isLong) rs else -rs
        val regime = stock.marketRegime
        val regimePass = when {
            regime == null || regime == "NEUTRAL" -> true
            isLong && regime == "BULL" -> true
            !isLong && regime == "BEAR" -> true
            else -> (rsDirectional ?: Double.NEGATIVE_INFINITY) >= 4.0
        }

        val trendPoints = when {
            trendPass && ema200 != null && if (isLong) price > ema200 else price < ema200 -> 20
            trendPass -> 16
            if (isLong) ema20 != null && price > ema20 else ema20 != null && price < ema20 -> 8
            else -> 2
        }
        val momentumPoints = when {
            preferredMomentum -> 15
            acceptableMomentum -> 11
            if (isLong) rsi != null && rsi > 50 else rsi != null && rsi < 50 -> 7
            else -> 2
        }
        val volumePoints = when {
            volumeRatio == null -> 0
            volumeRatio >= 2 -> 15
            volumeRatio >= 1.5 -> 13
            volumeRatio >= 1.2 -> 10
            volumeRatio >= 1 -> 7
            else -> 2
        }
        val structurePoints = when {
            confirmedBreak -> 15
            structurePass -> 11
            closeQuality -> 6
            else -> 2
        }
        val rsPoints = when {
            rsDirectional == null -> 0
            rsDirectional >= 8 -> 15
            rsDirectional >= 4 -> 12
            rsDirectional >= 2 -> 9
            rsDirectional >= 0 -> 6
            else -> 1
        }
        val regimePoints = when {
            regime == null -> 0
            regime == "NEUTRAL" -> 6
            isLong && regime == "BULL" -> 10
            !isLong && regime == "BEAR" -> 10
            regimePass -> 4
            else -> 0
        }
        val rrPoints = when {
            rr == null -> 0
            rr >= 2.5 -> 10
            rr >= 2 -> 9
            rr >= 1.75 -> 7
            rr >= 1.25 -> 3
            else -> 0
        }
        val components = listOf(
            ScoreComponent("Trend", trendPoints, 20),
            ScoreComponent("Momentum", momentumPoints, 15),
            ScoreComponent("Hacim", volumePoints, 15),
            ScoreComponent("Kırılım / Yapı", structurePoints, 15),
            ScoreComponent("Göreceli Güç", rsPoints, 15),
            ScoreComponent("Piyasa Rejimi", regimePoints, 10),
            ScoreComponent("Risk / Getiri", rrPoints, 10)
        )

        return SideEval(
            components.sumOf { it.points }.coerceIn(0, 100),
            components,
            dataPass,
            liquidityPass,
            trendPass,
            structurePass,
            volumePass,
            rrPass,
            overextended,
            noCriticalLevel,
            regimePass,
            stop,
            target1,
            target2,
            rr
        )
    }

    private fun classify(score: Int, gatesPass: Boolean) = when {
        !gatesPass -> "UYGUN DEĞİL"
        score >= 90 -> "A+ TEKNİK UYUM"
        score >= 85 -> "GÜÇLÜ FIRSAT"
        score >= 80 -> "FIRSAT ADAYI"
        score >= 70 -> "İZLE"
        else -> "ZAYIF"
    }
}
