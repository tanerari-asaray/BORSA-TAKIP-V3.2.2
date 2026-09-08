from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/tr/borsatakip/v3"

# 1) Correct RSI to Wilder-style smoothing and add VWAP to the technical snapshot.
model = SRC / "model/Opportunity.kt"
s = model.read_text(encoding="utf-8")
old = "val bollingerLower: Double?, val volumeRatio: Double?, val support: Double?, val resistance: Double?"
new = "val bollingerLower: Double?, val volumeRatio: Double?, val support: Double?, val resistance: Double?, val vwap: Double? = null"
if old in s:
    s = s.replace(old, new)
model.write_text(s, encoding="utf-8")

an = SRC / "analysis/TechnicalAnalyzer.kt"
s = an.read_text(encoding="utf-8")
start = s.index("object TechnicalAnalyzer {")
new_analyzer = '''package tr.borsatakip.v3.analysis

import tr.borsatakip.v3.model.Candle
import tr.borsatakip.v3.model.TechnicalSnapshot
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** Production technical indicators: Wilder RSI, EMA, MACD, ATR, Bollinger and VWAP. */
object TechnicalAnalyzer {
    fun analyze(candles: List<Candle>): TechnicalSnapshot {
        val closes = candles.map { it.close }
        val volumes = candles.map { it.volume }
        val ema20 = ema(closes, 20)
        val ema50 = ema(closes, 50)
        val ema200 = ema(closes, 200)
        val rsi14 = rsiWilder(closes, 14)
        val macdSeries = emaSeries(closes, 12).zip(emaSeries(closes, 26)) { a, b -> a - b }
        val macd = macdSeries.lastOrNull()
        val macdSignal = ema(macdSeries, 9)
        val atr14 = atr(candles, 14)
        val last20 = closes.takeLast(20)
        val mean20 = last20.takeIf { it.size == 20 }?.average()
        val sd20 = if (last20.size == 20 && mean20 != null) sqrt(last20.sumOf { (it - mean20).pow(2) } / last20.size) else null
        val avgVol20 = volumes.takeLast(20).takeIf { it.size == 20 }?.average()
        val volumeRatio = if (avgVol20 != null && avgVol20 > 0) volumes.lastOrNull()?.div(avgVol20) else null
        val vwap = vwap(candles.takeLast(20))
        return TechnicalSnapshot(
            ema20, ema50, ema200, rsi14, macd, macdSignal, atr14,
            mean20?.let { m -> sd20?.let { m + 2 * it } },
            mean20?.let { m -> sd20?.let { m - 2 * it } },
            volumeRatio, candles.takeLast(20).minOfOrNull { it.low },
            candles.takeLast(20).maxOfOrNull { it.high }, vwap
        )
    }

    private fun ema(values: List<Double>, period: Int): Double? = emaSeries(values, period).lastOrNull()

    private fun emaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.size < period) return emptyList()
        val out = mutableListOf<Double>()
        var current = values.take(period).average()
        out += current
        val k = 2.0 / (period + 1.0)
        values.drop(period).forEach { v -> current = v * k + current * (1 - k); out += current }
        return out
    }

    private fun rsiWilder(values: List<Double>, period: Int): Double? {
        if (values.size <= period) return null
        val changes = values.zipWithNext { a, b -> b - a }
        var avgGain = changes.take(period).sumOf { if (it > 0) it else 0.0 } / period
        var avgLoss = changes.take(period).sumOf { if (it < 0) -it else 0.0 } / period
        for (change in changes.drop(period)) {
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) -change else 0.0
            avgGain = ((avgGain * (period - 1)) + gain) / period
            avgLoss = ((avgLoss * (period - 1)) + loss) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - 100.0 / (1.0 + rs)
    }

    private fun atr(candles: List<Candle>, period: Int): Double? {
        if (candles.size <= period) return null
        val trs = candles.zipWithNext { prev, cur -> maxOf(cur.high - cur.low, abs(cur.high - prev.close), abs(cur.low - prev.close)) }
        return trs.takeLast(period).average()
    }

    private fun vwap(candles: List<Candle>): Double? {
        if (candles.isEmpty()) return null
        var pv = 0.0
        var volume = 0.0
        for (c in candles) {
            if (c.volume > 0 && c.high.isFinite() && c.low.isFinite() && c.close.isFinite()) {
                pv += ((c.high + c.low + c.close) / 3.0) * c.volume
                volume += c.volume
            }
        }
        return if (volume > 0) pv / volume else null
    }
}
'''
an.write_text(new_analyzer, encoding="utf-8")

# 2) Keep market identity in the stock model so BIST/VIOP records cannot be mixed.
stock = SRC / "model/Stock.kt"
s = stock.read_text(encoding="utf-8")
needle = "val marketType: String? = null"
if needle not in s:
    s = s.replace("val marketRegime: String? = null", "val marketRegime: String? = null,\n    val marketType: String? = null")
stock.write_text(s, encoding="utf-8")

# 3) Prevent API secret from being sent to the device-facing backend. API key remains the only client credential.
live = SRC / "market/LiveBackendMarketDataProvider.kt"
s = live.read_text(encoding="utf-8")
s = s.replace('            val apiSecret = prefs.getString("api_secret", "")?.trim().orEmpty()\n', '')
s = s.replace('                if (apiSecret.isNotBlank()) setRequestProperty("X-API-Secret", apiSecret)\n', '')
s = s.replace('                setRequestProperty("Cache-Control", "no-cache")\n', '                setRequestProperty("Cache-Control", "no-cache")\n', 1)
# Remove duplicate Cache-Control lines left by earlier patch, retaining one.
lines = s.splitlines()
out = []
seen_cache = False
for line in lines:
    if 'setRequestProperty("Cache-Control", "no-cache")' in line:
        if seen_cache: continue
        seen_cache = True
    out.append(line)
live.write_text("\n".join(out) + "\n", encoding="utf-8")

# 4) Make Yahoo cache source-aware and explicitly delayed; never label it as live.
yahoo = SRC / "market/YahooBistMarketDataProvider.kt"
s = yahoo.read_text(encoding="utf-8")
s = s.replace('User-Agent", "Mozilla/5.0 (Android) BorsaTakipV4/4.0.2"', 'User-Agent", "Mozilla/5.0 (Android) BorsaTakipV4/4.0.3"')
s = s.replace('sourceName = "Yahoo Finance • BIST genis evren + Takip Listesi"', 'sourceName = "Yahoo Finance • GECİKMELİ VERİ • BIST genis evren + Takip Listesi"')
yahoo.write_text(s, encoding="utf-8")

# 5) Build-time verification markers.
print("V4.0.3 production fixes applied: Wilder RSI, VWAP, market identity, client secret removal, freshness label")
