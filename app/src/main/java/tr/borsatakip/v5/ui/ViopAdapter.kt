package tr.borsatakip.v5.ui
import android.view.*
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import tr.borsatakip.v5.R
import tr.borsatakip.v5.model.ViopContract
class ViopAdapter(private val a:List<ViopContract>):RecyclerView.Adapter<ViopAdapter.H>(){class H(v:View):RecyclerView.ViewHolder(v){val t=v.findViewById<TextView>(R.id.text)};override fun onCreateViewHolder(p:ViewGroup,t:Int)=H(LayoutInflater.from(p.context).inflate(R.layout.item_viop,p,false));override fun getItemCount()=a.size;override fun onBindViewHolder(h:H,i:Int){val x=a[i];h.t.text="${x.symbol}   ${"%.2f".format(x.lastPrice)} (${"%+.2f".format(x.dailyChangePct)}%)\nDayanak: ${x.underlying} • Vade: ${x.expiry}\nFiyat adımı: ${x.tickSize?:"Veri yok"} • Çarpan: ${x.multiplier?:"Veri yok"}\nAçık pozisyon: ${x.openInterest?:"Veri yok"} • Likidite: ${x.liquidity?:"Veri yok"}\nRollover: ${x.rollover?:"Veri yok"}"}}