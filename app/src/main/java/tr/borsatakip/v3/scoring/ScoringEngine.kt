package tr.borsatakip.v3.scoring

import tr.borsatakip.v3.analysis.TechnicalAnalyzer
import tr.borsatakip.v3.model.Opportunity
import tr.borsatakip.v3.model.ScoreComponent
import tr.borsatakip.v3.model.Stock
import tr.borsatakip.v3.model.TechnicalSnapshot
import kotlin.math.abs

class ScoringEngine {
    fun score(stock: Stock): Opportunity? {
        if (stock.candles.size < 60) return null
        if (stock.isDemo) return null

        val t = TechnicalAnalyzer.analyze(stock.candles)
        val price = stock.candles.last().close
        if (!price.isFinite() || price <= 0.0) return null

        val longEval = evaluateSide(stock, t, true)
        val shortEval = evaluateSide(stock, t, false)
        val useLong = longEval.total >= shortEval.total
        val best = if (useLong) longEval else shortEval
        val opposite = if (useLong) shortEval else longEval

        val scoreGapPass = best.total - opposite.total >= 10
        val direction = when {
            !best.gatesPass -> "SİNYAL YOK"
            best.total < 70 -> "SİNYAL YOK"
            !scoreGapPass -> "SİNYAL YOK"
            useLong -> "LONG"
            else -> "SHORT"
        }

        val confidence = classify(best.total, best.gatesPass && scoreGapPass)
        val gateBreakdown = listOf(
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
        )

        return Opportunity(
            symbol = stock.symbol,
            companyName = stock.companyName,
            price = price,
            score = best.total,
            direction = direction,
            confidence = confidence,
            entry = if (direction == "SİNYAL YOK") null else price,
            target1 = if (direction == "SİNYAL YOK") null else best.target1,
            target2 = if (direction == "SİNYAL YOK") null else best.target2,
            stop = if (direction == "SİNYAL YOK") null else best.stop,
            riskReward = if (direction == "SİNYAL YOK") null else best.rr,
            technical = t,
            breakdown = best.components + gateBreakdown,
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
    ) {
        val gatesPass: Boolean
            get() = dataPass && liquidityPass && trendPass && structurePass && volumePass && rrPass &&
                !overextended && noCriticalLevel && regimePass
    }

    private fun evaluateSide(stock: Stock, t: TechnicalSnapshot, isLong: Boolean): SideEval {
        val candles = stock.candles
        val last = candles.last()
        val price = last.close
        val prev20 = candles.dropLast(1).takeLast(20)
        val prev50 = candles.dropLast(1).takeLast(50)
        val prev5 = candles.dropLast(6).takeLast(5)
        val recent5 = candles.dropLast(1).takeLast(5)
        val previousSnapshot = if (candles.size >= 65) TechnicalAnalyzer.analyze(candles.dropLast(5)) else t

        fun side(longCondition: Boolean, shortCondition: Boolean) = if (isLong) longCondition else shortCondition

        val dataPass = !stock.isDemo &&
            candles.size >= 60 &&
            stock.dataTimestamp > 0L &&
            candles.takeLast(20).all { c ->
                c.open.isFinite() && c.high.isFinite() && c.low.isFinite() && c.close.isFinite() && c.volume.isFinite() &&
                    c.open > 0.0 && c.high > 0.0 && c.low > 0.0 && c.close > 0.0 && c.high >= c.low
            }

        val avgVolume20 = prev20.map { it.volume }.filter { it > 0.0 }.takeIf { it.size >= 15 }?.average()
        val volumeRatio = if (avgVolume20 != null && avgVolume20 > 0.0) last.volume / avgVolume20 else null
        val liquidSessions = prev20.count { it.volume > 0.0 }
        val liquidityPass = avgVolume20 != null && liquidSessions >= 18 && last.volume > 0.0
        val volumePass = volumeRatio != null && volumeRatio >= 1.0

        val ema20 = t.ema20
        val ema50 = t.ema50
        val ema200 = t.ema200
        val prevEma20 = previousSnapshot.ema20
        val prevEma50 = previousSnapshot.ema50
        val emaSlopePass = side(
            ema20 != null && prevEma20 != null && ema50 != null && prevEma50 != null && ema20 >= prevEma20 && ema50 >= prevEma50,
            ema20 != null && prevEma20 != null && ema50 != null && prevEma50 != null && ema20 <= prevEma20 && ema50 <= prevEma50
        )
        val trendPass = side(
            ema20 != null && ema50 != null && price > ema20 && price > ema50 && ema20 > ema50 && emaSlopePass,
            ema20 != null && ema50 != null && price < ema20 && price < ema50 && ema20 < ema50 && emaSlopePass
        )

        val priorResistance20 = prev20.maxOfOrNull { it.high }
        val priorSupport20 = prev20.minOfOrNull { it.low }
        val breakout = isLong && priorResistance20 != null && price > priorResistance20 && volumeRatio != null && volumeRatio >= 1.5
        val breakdown = !isLong && priorSupport20 != null && price < priorSupport20 && volumeRatio != null && volumeRatio >= 1.5
        val candleRange = (last.high - last.low).takeIf { it > 0.0 }
        val closePosition = candleRange?.let { (last.close - last.low) / it }
        val closeQuality = side((closePosition ?: 0.5) >= 0.65, (closePosition ?: 0.5) <= 0.35)
        val confirmedBreak = side(breakout && closeQuality, breakdown && closeQuality)

        val higherStructure = if (prev5.isNotEmpty() && recent5.isNotEmpty()) {
            recent5.maxOf { it.high } > prev5.maxOf { it.high } && recent5.minOf { it.low } > prev5.minOf { it.low }
        } else false
        val lowerStructure = if (prev5.isNotEmpty() && recent5.isNotEmpty()) {
            recent5.maxOf { it.high } < prev5.maxOf { it.high } && recent5.minOf { it.low } < prev5.minOf { it.low }
        } else false
        val structurePass = side(confirmedBreak || higherStructure, confirmedBreak || lowerStructure)

        val rsi = t.rsi14
        val macdPositive = t.macd != null && t.macdSignal != null && t.macd > t.macdSignal
        val macdNegative = t.macd != null && t.macdSignal != null && t.macd < t.macdSignal
        val preferredMomentum = side(
            rsi != null && rsi in 55.0..68.0 && macdPositive,
            rsi != null && rsi in 32.0..45.0 && macdNegative
        )
        val acceptableMomentum = side(
            rsi != null && rsi > 50.0 && macdPositive,
            rsi != null && rsi < 50.0 && macdNegative
        )

        val atr = t.atr14
        val distanceAtr = if (atr != null && atr > 0.0 && ema20 != null) abs(price - ema20) / atr else null
        val rsiExtended = side((rsi ?: 50.0) >= 80.0, (rsi ?: 50.0) <= 20.0)
        val abnormalMove = if (atr != null && atr > 0.0) abs(last.close - last.open) > 2.5 * atr else false
        val overextended = (distanceAtr != null && distanceAtr >= 2.5) || rsiExtended || abnormalMove

        val recentLow = prev20.minOfOrNull { it.low }
        val recentHigh = prev20.maxOfOrNull { it.high }
        val stop = if (atr != null && atr > 0.0) {
            if (isLong) {
                val atrStop = price - 1.5 * atr
                val swingStop = recentLow?.minus(0.10 * atr)
                listOfNotNull(atrStop.takeIf { it > 0.0 }, swingStop?.takeIf { it > 0.0 && it < price }).maxOrNull()
            } else {
                val atrStop = price + 1.5 * atr
                val swingStop = recentHigh?.plus(0.10 * atr)
                listOfNotNull(atrStop, swingStop?.takeIf { it > price }).minOrNull()
            }
        } else null

        val risk = when {
            isLong && stop != null && stop < price -> price - stop
            !isLong && stop != null && stop > price -> stop - price
            else -> null
        }

        val resistance50 = prev50.map { it.high }.filter { it > price }.minOrNull()
        val support50 = prev50.map { it.low }.filter { it < price }.maxOrNull()
        val projectedTarget = if (atr != null && atr > 0.0) {
            if (isLong) price + 2.0 * atr else (price - 2.0 * atr).takeIf { it > 0.0 }
        } else null
        val target1 = if (isLong) resistance50 ?: projectedTarget else support50 ?: projectedTarget
        val rr = if (risk != null && risk > 0.0 && target1 != null) {
            val reward = if (isLong) target1 - price else price - target1
            if (reward > 0.0) reward / risk else null
        } else null
        val rrPass = rr != null && rr >= 1.75
        val noCriticalLevel = rrPass
        val target2 = if (risk != null && risk > 0.0) {
            if (isLong) price + 3.0 * risk else (price - 3.0 * risk).takeIf { it > 0.0 }
        } else null

        val stockReturn20 = return20d(candles)
        val relativeStrength = if (stockReturn20 != null && stock.benchmarkReturn20d != null) {
            stockReturn20 - stock.benchmarkReturn20d
        } else null
        val rsDirectional = if (relativeStrength == null) null else if (isLong) relativeStrength else -relativeStrength

        val regime = stock.marketRegime
        val regimePass = when {
            regime == null -> true
            regime == "NEUTRAL" -> true
            isLong && regime == "BULL" -> true
            !isLong && regime == "BEAR" -> true
            else -> (rsDirectional ?: Double.NEGATIVE_INFINITY) >= 4.0
        }

        val trendPoints = when {
            trendPass && ema200 != null && side(price > ema200, price < ema200) -> 20
            trendPass -> 16
            side(ema20 != null && price > ema20, ema20 != null && price < ema20) -> 8
            else -> 2
        }
        val momentumPoints = when {
            preferredMomentum -> 15
            acceptableMomentum -> 11
            side(rsi != null && rsi > 50.0, rsi != null && rsi < 50.0) -> 7
            else -> 2
        }
        val volumePoints = when {
            volumeRatio == null -> 0
            volumeRatio >= 2.0 -> 15
            volumeRatio >= 1.5 -> 13
            volumeRatio >= 1.2 -> 10
            volumeRatio >= 1.0 -> 7
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
            rsDirectional >= 8.0 -> 15
            rsDirectional >= 4.0 -> 12
            rsDirectional >= 2.0 -> 9
            rsDirectional >= 0.0 -> 6
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
            rr >= 2.0 -> 9
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
            total = components.sumOf { it.points }.coerceIn(0, 100),
            components = components,
            dataPass = dataPass,
            liquidityPass = liquidityPass,
            trendPass = trendPass,
            structurePass = structurePass,
            volumePass = volumePass,
            rrPass = rrPass,
            overextended = overextended,
            noCriticalLevel = noCriticalLevel,
            regimePass = regimePass,
            stop = stop,
            target1 = target1,
            target2 = target2,
            rr = rr
        )
    }

    private fun return20d(candles: List<tr.borsatakip.v3.model.Candle>): Double? {
        if (candles.size < 21) return null
        val start = candles[candles.lastIndex - 20].close
        val end = candles.last().close
        if (start <= 0.0) return null
        return (end / start - 1.0) * 100.0
    }

    private fun classify(score: Int, gatesPass: Boolean): String = when {
        !gatesPass -> "UYGUN DEĞİL"
        score >= 90 -> "A+ TEKNİK UYUM"
        score >= 85 -> "GÜÇLÜ FIRSAT"
        score >= 80 -> "FIRSAT ADAYI"
        score >= 70 -> "İZLE"
        else -> "UYGUN DEĞİL"
    }
}
