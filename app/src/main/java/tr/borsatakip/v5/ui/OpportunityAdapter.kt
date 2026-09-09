package tr.borsatakip.v5.ui

import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import tr.borsatakip.v5.R
import tr.borsatakip.v5.model.Opportunity

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
    override fun onBindViewHolder(h:H,i:Int){
        val x=items[i]
        h.symbol.text=x.symbol; h.score.text="${x.score}/100"
        h.company.text=x.companyName?:""
        h.meta.text="${x.direction} • Teknik ${x.technicalLabel} • Hacim ${x.volumeLabel} • KAP ${x.kapLabel} • Likidite ${x.liquidityLabel}"
        h.risk.text="Risk ${x.riskScore}/100 • Destek ${x.support?.let{"%.2f".format(it)}?:"-"} • Direnç ${x.resistance?.let{"%.2f".format(it)}?:"-"}"
        val p=h.itemView.context.getSharedPreferences("favorites",android.content.Context.MODE_PRIVATE)
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