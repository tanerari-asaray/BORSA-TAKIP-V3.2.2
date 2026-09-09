package tr.borsatakip.v5.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import tr.borsatakip.v5.R
import tr.borsatakip.v5.model.Opportunity
import kotlin.math.abs
import kotlin.math.min

class OpportunityAdapter(private var items:List<Opportunity>,private val click:(Opportunity)->Unit):RecyclerView.Adapter<OpportunityAdapter.H>(){
    class H(v:View):RecyclerView.ViewHolder(v){
        val symbol=v.findViewById<TextView>(R.id.symbol)
        val score=v.findViewById<TextView>(R.id.score)
        val favorite=v.findViewById<TextView>(R.id.favorite)
        val company=v.findViewById<TextView>(R.id.company)
        val meta=v.findViewById<TextView>(R.id.meta)
        val risk=v.findViewById<TextView>(R.id.risk)
    }

    override fun onCreateViewHolder(p:ViewGroup,t:Int)=H(LayoutInflater.from(p.context).inflate(R.layout.item_opportunity,p,false))
    override fun getItemCount()=items.size

    private fun trendInfo(x:Opportunity):Pair<String,Int>{
        val t=x.technical
        val price=x.price
        val e20=t.ema20
        val e50=t.ema50
        val e200=t.ema200
        if(e20==null || e50==null || e200==null || price<=0.0) return "NEUTRAL" to 0

        val bullish=price>e20 && e20>e50 && e50>e200
        val bearish=price<e20 && e20<e50 && e50<e200
        if(!bullish && !bearish) return "NEUTRAL" to 0

        val pGap=min(abs(price/e20-1.0)*100.0,8.0)
        val eGap=min(abs(e20/e50-1.0)*100.0,8.0)
        val longGap=min(abs(e50/e200-1.0)*100.0,8.0)
        val strength=(55.0 + (pGap+eGap+longGap)*1.9).coerceIn(0.0,100.0).toInt()
        return if(bullish) "UP" to strength else "DOWN" to strength
    }

    private fun neonBackground(direction:String,strength:Int):GradientDrawable{
        val s=(strength.coerceIn(0,100))/100f
        val base=when(direction){
            "UP"->Color.rgb(2,32,22)
            "DOWN"->Color.rgb(40,8,12)
            else->Color.rgb(10,35,50)
        }
        val neon=when(direction){
            "UP"->Color.rgb(0,255,102)
            "DOWN"->Color.rgb(255,23,68)
            else->Color.rgb(20,110,150)
        }
        val fill=Color.argb((70 + 95*s).toInt(),Color.red(neon),Color.green(neon),Color.blue(neon))
        val stroke=Color.argb((110 + 145*s).toInt(),Color.red(neon),Color.green(neon),Color.blue(neon))
        val drawable=GradientDrawable()
        drawable.cornerRadius=22f
        drawable.setColor(blend(base,fill,s*0.85f))
        drawable.setStroke((1 + s*2.5f).toInt(),stroke)
        return drawable
    }

    private fun blend(a:Int,b:Int,amount:Float):Int{
        val t=amount.coerceIn(0f,1f)
        return Color.rgb(
            (Color.red(a)+(Color.red(b)-Color.red(a))*t).toInt(),
            (Color.green(a)+(Color.green(b)-Color.green(a))*t).toInt(),
            (Color.blue(a)+(Color.blue(b)-Color.blue(a))*t).toInt()
        )
    }

    override fun onBindViewHolder(h:H,i:Int){
        val x=items[i]
        h.symbol.text=x.symbol
        h.score.text="${x.score}/100"
        h.company.text=x.companyName?:""
        val (trend,trendStrength)=trendInfo(x)
        val trendLabel=when(trend){"UP"->"▲ TREND YUKARI";"DOWN"->"▼ TREND AŞAĞI";else->"● TREND NÖTR"}
        h.meta.text="${x.direction} • $trendLabel • ${trendStrength}/100 • Teknik ${x.technicalLabel} • Hacim ${x.volumeLabel} • KAP ${x.kapLabel} • Likidite ${x.liquidityLabel}"
        h.risk.text="Risk ${x.riskScore}/100 • Destek ${x.support?.let{"%.2f".format(it)}?:"-"} • Direnç ${x.resistance?.let{"%.2f".format(it)}?:"-"}"

        h.itemView.background=neonBackground(trend,trendStrength)
        val accent=when(trend){
            "UP"->Color.rgb(0,255,102)
            "DOWN"->Color.rgb(255,23,68)
            else->Color.rgb(150,190,205)
        }
        h.score.setTextColor(accent)
        h.score.alpha=(0.55f+0.45f*(trendStrength/100f)).coerceIn(0f,1f)

        val p=h.itemView.context.getSharedPreferences("favorites",Context.MODE_PRIVATE)
        fun refreshStar(){val fav=p.getStringSet("bist",emptySet())?.contains(x.symbol)==true;h.favorite.text=if(fav)"★" else "☆";h.favorite.contentDescription=if(fav)"Favorilerden çıkar" else "Favoriye ekle"}
        refreshStar()
        h.favorite.setOnClickListener{
            val set=p.getStringSet("bist",emptySet())?.toMutableSet()?:mutableSetOf()
            if(set.contains(x.symbol)) set.remove(x.symbol) else set.add(x.symbol)
            p.edit().putStringSet("bist",set).apply(); refreshStar()
        }
        h.itemView.setOnClickListener{click(x)}
    }
}