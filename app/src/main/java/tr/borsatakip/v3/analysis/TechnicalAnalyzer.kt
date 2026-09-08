package tr.borsatakip.v3.analysis

import tr.borsatakip.v3.model.Candle
import tr.borsatakip.v3.model.TechnicalSnapshot
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

object TechnicalAnalyzer {
    fun analyze(candles: List<Candle>): TechnicalSnapshot {
        val closes=candles.map{it.close}; val volumes=candles.map{it.volume}
        val ema20=ema(closes,20); val ema50=ema(closes,50); val ema200=ema(closes,200); val rsi14=rsi(closes,14)
        val macdSeries=emaSeries(closes,12).zip(emaSeries(closes,26)){a,b->a-b}; val macd=macdSeries.lastOrNull(); val macdSignal=ema(macdSeries,9); val atr14=atr(candles,14)
        val last20=closes.takeLast(20); val mean20=last20.takeIf{it.size==20}?.average(); val sd20=if(last20.size==20&&mean20!=null)sqrt(last20.sumOf{(it-mean20).pow(2)}/last20.size)else null
        val avgVol20=volumes.takeLast(20).takeIf{it.size==20}?.average(); val volumeRatio=if(avgVol20!=null&&avgVol20>0)volumes.lastOrNull()?.div(avgVol20)else null
        return TechnicalSnapshot(ema20,ema50,ema200,rsi14,macd,macdSignal,atr14,mean20?.let{m->sd20?.let{m+2*it}},mean20?.let{m->sd20?.let{m-2*it}},volumeRatio,candles.takeLast(20).minOfOrNull{it.low},candles.takeLast(20).maxOfOrNull{it.high})
    }
    private fun ema(values:List<Double>,period:Int):Double?=emaSeries(values,period).lastOrNull()
    private fun emaSeries(values:List<Double>,period:Int):List<Double>{if(values.size<period)return emptyList();val out=mutableListOf<Double>();var current=values.take(period).average();out+=current;val k=2.0/(period+1.0);values.drop(period).forEach{v->current=v*k+current*(1-k);out+=current};return out}
    private fun rsi(values:List<Double>,period:Int):Double?{if(values.size<=period)return null;val changes=values.zipWithNext{a,b->b-a};val window=changes.takeLast(period);val gains=window.filter{it>0}.sum();val losses=window.filter{it<0}.sumOf{abs(it)};if(losses==0.0)return 100.0;val rs=(gains/period)/(losses/period);return 100.0-100.0/(1.0+rs)}
    private fun atr(candles:List<Candle>,period:Int):Double?{if(candles.size<=period)return null;val trs=candles.zipWithNext{prev,cur->maxOf(cur.high-cur.low,abs(cur.high-prev.close),abs(cur.low-prev.close))};return trs.takeLast(period).average()}
}
