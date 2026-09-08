package tr.borsatakip.v3.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import tr.borsatakip.v3.R
import tr.borsatakip.v3.databinding.ItemOpportunityBinding
import tr.borsatakip.v3.model.Opportunity
import java.util.Locale

class OpportunityAdapter(private val onClick:(Opportunity)->Unit={}) : ListAdapter<Opportunity,OpportunityAdapter.Holder>(DiffCallback){
 override fun onCreateViewHolder(parent:ViewGroup,viewType:Int)=Holder(ItemOpportunityBinding.inflate(LayoutInflater.from(parent.context),parent,false),onClick)
 override fun onBindViewHolder(holder:Holder,position:Int)=holder.bind(getItem(position))
 class Holder(private val binding:ItemOpportunityBinding,private val onClick:(Opportunity)->Unit):RecyclerView.ViewHolder(binding.root){
  fun bind(item:Opportunity){binding.root.setOnClickListener{onClick(item)};binding.symbolText.text=item.symbol;binding.scoreText.text="${item.score}/100";binding.signalText.text="${item.direction} • ${item.confidence}";binding.signalText.setTextColor(ContextCompat.getColor(binding.root.context,when(item.direction){"LONG"->R.color.green;"SHORT"->R.color.red;else->R.color.yellow}));binding.priceText.text="Fiyat ${fmt(item.price)} TL";binding.buyText.text=item.entry?.let{"Giriş ${fmt(it)} TL"}?:"Giriş: Veri yetersiz";binding.targetText.text=item.target1?.let{t1->item.target2?.let{t2->"Hedef ${fmt(t1)} / ${fmt(t2)} TL"}?:"Hedef ${fmt(t1)} TL"}?:"Hedef: Veri yetersiz";binding.stopText.text=item.stop?.let{"Stop ${fmt(it)} TL"}?:"Stop: Veri yetersiz";binding.bedelsizText.text=item.riskReward?.let{"Risk/Getiri 1:${fmt(it)}"}?:"Risk/Getiri: Veri yetersiz"}
  private fun fmt(v:Double)=String.format(Locale("tr","TR"),"%.2f",v)
 }
 private object DiffCallback:DiffUtil.ItemCallback<Opportunity>(){override fun areItemsTheSame(a:Opportunity,b:Opportunity)=a.symbol==b.symbol;override fun areContentsTheSame(a:Opportunity,b:Opportunity)=a==b}
}
