package tr.borsatakip.v3

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tr.borsatakip.v3.market.AppMarketDataProvider
import tr.borsatakip.v3.market.Market
import tr.borsatakip.v3.market.MarketDataProvider
import tr.borsatakip.v3.model.Opportunity
import tr.borsatakip.v3.model.Stock
import tr.borsatakip.v3.scoring.ScoringEngine
import tr.borsatakip.v3.ui.OpportunityAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivityV2 : AppCompatActivity() {
    private lateinit var root: View
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var home: View
    private lateinit var content: View
    private lateinit var settings: View
    private lateinit var status: TextView
    private lateinit var progress: android.widget.ProgressBar
    private lateinit var recycler: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: OpportunityAdapter
    private lateinit var watchContainer: LinearLayout
    private lateinit var watchActions: View
    private lateinit var provider: MarketDataProvider
    private val scoring = ScoringEngine()
    private var job: Job? = null
    private var liveJob: Job? = null
    private var lastBist: List<Stock> = emptyList()
    private var lastViop: List<Stock> = emptyList()
    private var lastOpportunities: List<Opportunity> = emptyList()
    private var filter = "TÜMÜ"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        root = findViewById(android.R.id.content)
        title = findViewById(R.id.titleText); subtitle = findViewById(R.id.subtitleText)
        home = findViewById(R.id.homeScroll); content = findViewById(R.id.contentPanel); settings = findViewById(R.id.settingsPanel)
        status = findViewById(R.id.statusText); progress = findViewById(R.id.progressBar); recycler = findViewById(R.id.recyclerView)
        watchContainer = findViewById(R.id.watchListContainer); watchActions = findViewById(R.id.watchActionRow)
        provider = AppMarketDataProvider(this)
        adapter = OpportunityAdapter { showDetail(it) }
        recycler.layoutManager = LinearLayoutManager(this); recycler.adapter = adapter
        bindClicks(); showHome(); startLiveRefresh()
    }

    private fun bindClicks() {
        findViewById<View>(R.id.btnBist).setOnClickListener { scan(Market.BIST) }
        findViewById<View>(R.id.btnViop).setOnClickListener { scan(Market.VIOP) }
        findViewById<View>(R.id.btnOpportunity).setOnClickListener { scanOpportunity() }
        findViewById<View>(R.id.watchCard).setOnClickListener { showWatchlist() }
        findViewById<View>(R.id.navHome).setOnClickListener { showHome() }
        findViewById<View>(R.id.navStocks).setOnClickListener { scan(Market.BIST) }
        findViewById<View>(R.id.navViop).setOnClickListener { scan(Market.VIOP) }
        findViewById<View>(R.id.navFavorites).setOnClickListener { showWatchlist() }
        findViewById<View>(R.id.navSettings).setOnClickListener { showSettings() }
        findViewById<View>(R.id.backButton).setOnClickListener { showHome() }
        findViewById<View>(R.id.addWatchButton).setOnClickListener { addWatch() }
        findViewById<View>(R.id.clearWatchButton).setOnClickListener { clearWatch() }
        findViewById<View>(R.id.settingsSaveButton).setOnClickListener { saveSettings() }
        findViewById<View>(R.id.settingsClearButton).setOnClickListener { clearSettings() }
        findViewById<View>(R.id.settingsTestButton).setOnClickListener { testSettings() }
        findViewById<View>(R.id.summaryCard).setOnClickListener { scanOpportunity() }
        findViewById<View>(R.id.noticeCard).setOnClickListener { info("Bildirimler", "Bildirim altyapısı hazır. İzin verilmeden bildirim gönderilmez.") }
        findViewById<View>(R.id.newsCard).setOnClickListener { info("Haberler", "Haber sağlayıcısı bağlı değil. Sahte haber gösterilmez.") }
    }

    private fun showHome() {
        job?.cancel(); home.visibility=View.VISIBLE; content.visibility=View.GONE; settings.visibility=View.GONE; findViewById<View>(R.id.backButton).visibility=View.GONE
        title.text="BORSA TAKİP V3"; subtitle.text="BIST • VİOP • FIRSAT"; selectNav(0); updateHome()
    }

    private fun scan(market: Market) {
        if (job?.isActive == true) return
        showContent(); title.text=if(market==Market.BIST) "BIST TARAMA" else "VİOP TARAMA"; subtitle.text="Gerçek veri kaynağından tarama"; progress.visibility=View.VISIBLE; progress.progress=10
        job=lifecycleScope.launch {
            status.text="${if(market==Market.BIST) "BIST" else "VİOP"} VERİLERİ ALINIYOR..."
            provider.loadMarket(market).onSuccess { result ->
                if(market==Market.BIST) lastBist=result.items else lastViop=result.items
                val rows=result.items.mapNotNull(scoring::score).sortedByDescending{it.score}
                val visible=rows.filter{it.direction!="SİNYAL YOK"}
                progress.progress=100; adapter.submitList(visible); status.text="${result.items.size} kayıt • ${visible.size} sinyalli sonuç\nVeri: ${fmtTime(result.dataTimestamp)} • ${result.sourceName}\nSkor ve sinyal yalnızca taramada güncellenir."
                if(market==Market.BIST) lastOpportunities=visible.filter{it.score>=60}
                updateHome()
            }.onFailure { status.text="VERİ ALINAMADI\n${it.message ?: "Bağlantı hatası"}\nEski veya demo sonuç gösterilmedi."; progress.progress=0 }
            job=null
        }
    }

    private fun scanOpportunity() {
        if(job?.isActive==true) return
        showContent(); title.text="FIRSAT KONTROLÜ"; subtitle.text="BIST + VİOP birlikte taranıyor"; progress.visibility=View.VISIBLE; progress.progress=5
        job=lifecycleScope.launch {
            try {
                coroutineScope {
                    val b=async { provider.loadMarket(Market.BIST) }
                    val v=async { provider.loadMarket(Market.VIOP) }
                    progress.progress=25
                    val br=b.await(); progress.progress=55
                    val vr=v.await(); progress.progress=75
                    br.onSuccess{lastBist=it.items}; vr.onSuccess{lastViop=it.items}
                    val all=(br.getOrNull()?.items.orEmpty()+vr.getOrNull()?.items.orEmpty()).mapNotNull(scoring::score)
                    lastOpportunities=all.filter{it.direction!="SİNYAL YOK"&&it.score>=60}.sortedByDescending{it.score}
                    adapter.submitList(lastOpportunities.take(50)); progress.progress=100
                    val strong=lastOpportunities.count{it.score>=80}; val watch=lastOpportunities.count{it.score in 60..79}; val risky=all.count{it.direction!="SİNYAL YOK"&&it.score<60}
                    status.text="Tarama tamamlandı\nGÜÇLÜ FIRSAT: $strong   •   İZLE: $watch   •   RİSKLİ: $risky\nBIST: ${br.getOrNull()?.items?.size ?: 0} kayıt   VİOP: ${vr.getOrNull()?.items?.size ?: 0} kayıt\n${if(vr.isFailure) "VİOP veri kaynağı başarısız: ${vr.exceptionOrNull()?.message}" else "VİOP veri alındı."}"
                    updateHome()
                }
            } catch(e:Throwable){ status.text="FIRSAT KONTROLÜ BAŞARISIZ\n${e.message ?: "Veri alınamadı"}"; progress.progress=0 }
            job=null
        }
    }

    private fun updateHome() {
        val summary=findViewById<TextView>(R.id.summaryCard)
        val b=lastBist.lastOrNull()?.let{latest(it)}; val v=lastViop.lastOrNull()?.let{latest(it)}
        summary.text="BIST 100   ${b?.let{fmt(it.first)} ?: "—"}   ${b?.let{fmtPct(it.second)} ?: "—"}\nVİOP   ${v?.let{fmt(it.first)} ?: "—"}   ${v?.let{fmtPct(it.second)} ?: "—"}\nPiyasa durumu: ${if(lastBist.isNotEmpty()||lastViop.isNotEmpty()) "VERİ ALINDI" else "VERİ BEKLENİYOR"}"
        findViewById<TextView>(R.id.opportunitySummary).text="${lastOpportunities.size} fırsat • ${if(lastOpportunities.isNotEmpty()) "Son tarama hazır" else "Henüz tarama yapılmadı"}"
        findViewById<TextView>(R.id.opportunityDistribution).text="GÜÇLÜ FIRSAT   ${lastOpportunities.count{it.score>=80}}    |    İZLE   ${lastOpportunities.count{it.score in 60..79}}    |    RİSKLİ   0"
        val favs=getWatchSymbols(); findViewById<TextView>(R.id.favoritesPreview).text=if(favs.isEmpty()) "Henüz favori eklenmedi.\nHisse eklemek için Takip Listem'e dokunun." else favs.take(5).joinToString("\n"){symbol -> favoriteLine(symbol)}
    }

    private fun startLiveRefresh() {
        liveJob?.cancel(); liveJob=lifecycleScope.launch {
            while(true){ delay(15000); if(getWatchSymbols().isNotEmpty()||lastBist.isNotEmpty()){ provider.loadMarket(Market.BIST).onSuccess{lastBist=it.items; updateHome(); if(content.visibility==View.VISIBLE&&watchContainer.visibility==View.VISIBLE) renderWatchlist()} } }
        }
    }

    private fun showWatchlist() {
        showContent(); findViewById<View>(R.id.recyclerView).visibility=View.GONE; watchActions.visibility=View.VISIBLE; watchContainer.visibility=View.VISIBLE; title.text="Takip Listem"; subtitle.text="Canlı fiyat • değişim • yön"; status.text="Fiyatlar veri kaynağından periyodik yenilenir. Skor/sinyal otomatik olarak yeniden yazılmaz."; renderWatchlist(); selectNav(3)
    }

    private fun renderWatchlist() {
        watchContainer.removeAllViews();
        val filters=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setPadding(0,4,0,8)}
        listOf("TÜMÜ","YÜKSELEN","DÜŞEN","LONG","SHORT","BEKLE").forEach{f->Button(this).apply{text=f;textSize=9f;setOnClickListener{filter=f;renderWatchlist()};filters.addView(this,LinearLayout.LayoutParams(0,42,1f))}}
        watchContainer.addView(filters)
        val list=getWatchSymbols().filter{matchesFilter(it)}
        if(list.isEmpty()){TextView(this).apply{text="Filtreye uygun hisse bulunamadı.";setTextColor(Color.parseColor("#5D6B78"));setPadding(12,24,12,24);watchContainer.addView(this)};return}
        list.forEach{symbol->
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(12,10,8,10);setBackgroundResource(R.drawable.bg_surface)}
            val info=TextView(this).apply{text=favoriteLine(symbol);textSize=15f;setTextColor(Color.parseColor("#17212B"));setPadding(4,0,4,0)}
            row.addView(info,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
            Button(this).apply{text="SİL";textSize=10f;setOnClickListener{removeWatch(symbol)};row.addView(this)}
            watchContainer.addView(row)
        }
    }

    private fun favoriteLine(symbol:String):String {
        val s=lastBist.firstOrNull{it.symbol.equals(symbol,true)} ?: return "★ $symbol   •   Veri bekleniyor"
        val q=latest(s) ?: return "★ $symbol   •   Veri bekleniyor"
        val dir=if(q.second>0.05) "LONG" else if(q.second < -0.05) "SHORT" else "BEKLE"
        return "★ $symbol   ${fmt(q.first)}   ${fmtPct(q.second)}   $dir"
    }

    private fun matchesFilter(symbol:String):Boolean {
        if(filter=="TÜMÜ") return true
        val q=lastBist.firstOrNull{it.symbol.equals(symbol,true)}?.let(::latest) ?: return false
        return when(filter){"YÜKSELEN"->q.second>0.05;"DÜŞEN"->q.second<-0.05;"LONG"->q.second>0.05;"SHORT"->q.second<-0.05;"BEKLE"->q.second in -0.05..0.05;else->true}
    }

    private fun addWatch(){
        val input=EditText(this).apply{hint="Örn. THYAO";inputType=1}
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Takip Listesine Hisse Ekle").setView(input).setNegativeButton("İPTAL",null).setPositiveButton("EKLE"){_,_->val s=input.text.toString().trim().uppercase(Locale("tr","TR"));if(s.isNotBlank()){val set=getWatchSymbols().toMutableSet();set.add(s);saveWatchSymbols(set);renderWatchlist();updateHome()}}.show()
    }
    private fun removeWatch(s:String){val set=getWatchSymbols().toMutableSet();set.remove(s);saveWatchSymbols(set);renderWatchlist();updateHome()}
    private fun clearWatch(){getSharedPreferences("watchlist",0).edit().clear().apply();renderWatchlist();updateHome()}
    private fun getWatchSymbols()=getSharedPreferences("watchlist",0).getStringSet("symbols",emptySet())?.toList()?.sorted()?:emptyList()
    private fun saveWatchSymbols(s:Set<String>){getSharedPreferences("watchlist",0).edit().putStringSet("symbols",s).apply()}

    private fun showSettings(){home.visibility=View.GONE;content.visibility=View.GONE;settings.visibility=View.VISIBLE;findViewById<View>(R.id.backButton).visibility=View.VISIBLE;title.text="API AYARLARI";subtitle.text="Gerçek piyasa verisi bağlantısı";val p=getSharedPreferences("market_api_settings",0);findViewById<EditText>(R.id.apiBaseUrlInput).setText(p.getString("base_url",BuildConfig.MARKET_API_BASE_URL));findViewById<EditText>(R.id.apiKeyInput).setText(p.getString("api_key",""));findViewById<EditText>(R.id.apiSecretInput).setText(p.getString("api_secret",""));selectNav(4)}
    private fun saveSettings(){val p=getSharedPreferences("market_api_settings",0);p.edit().putString("base_url",findViewById<EditText>(R.id.apiBaseUrlInput).text.toString().trim()).putString("api_key",findViewById<EditText>(R.id.apiKeyInput).text.toString().trim()).putString("api_secret",findViewById<EditText>(R.id.apiSecretInput).text.toString().trim()).apply();findViewById<TextView>(R.id.settingsStatus).text="API bilgileri kaydedildi. Bağlantıyı test edin."}
    private fun clearSettings(){getSharedPreferences("market_api_settings",0).edit().clear().apply();findViewById<EditText>(R.id.apiBaseUrlInput).text.clear();findViewById<EditText>(R.id.apiKeyInput).text.clear();findViewById<EditText>(R.id.apiSecretInput).text.clear();findViewById<TextView>(R.id.settingsStatus).text="API bilgileri temizlendi."}
    private fun testSettings(){lifecycleScope.launch{findViewById<TextView>(R.id.settingsStatus).text="BAĞLANTI TEST EDİLİYOR...";provider.loadMarket(Market.BIST).onSuccess{findViewById<TextView>(R.id.settingsStatus).text="BAĞLANTI BAŞARILI\n${it.items.size} BIST kaydı alındı. Kaynak: ${it.sourceName}"}.onFailure{findViewById<TextView>(R.id.settingsStatus).text="BAĞLANTI BAŞARISIZ\n${it.message ?: "Bilinmeyen hata"}"}}}

    private fun showDetail(item:Opportunity){showContent();title.text=item.symbol;subtitle.text="${item.direction} • ${item.confidence} • ${item.score}/100";status.text="Fiyat: ${fmt(item.price)}\nVeri zamanı: ${fmtTime(item.dataTimestamp)}\n\nRSI14: ${item.technical.rsi14?.let(::fmt)?:"Veri yetersiz"}\nMACD: ${item.technical.macd?.let(::fmt)?:"Veri yetersiz"}\nEMA20: ${item.technical.ema20?.let(::fmt)?:"Veri yetersiz"}\nEMA50: ${item.technical.ema50?.let(::fmt)?:"Veri yetersiz"}\nEMA200: ${item.technical.ema200?.let(::fmt)?:"Veri yetersiz"}\nATR14: ${item.technical.atr14?.let(::fmt)?:"Veri yetersiz"}\n\nGiriş: ${item.entry?.let(::fmt)?:"Veri yetersiz"}\nStop: ${item.stop?.let(::fmt)?:"Veri yetersiz"}\nHedef 1: ${item.target1?.let(::fmt)?:"Veri yetersiz"}\nHedef 2: ${item.target2?.let(::fmt)?:"Veri yetersiz"}\nRisk/Getiri: ${item.riskReward?.let{String.format(Locale("tr","TR"),"1:%.2f",it)}?:"Veri yetersiz"}\n\nBu analiz yatırım tavsiyesi değildir.";adapter.submitList(emptyList())}
    private fun info(t:String,b:String){showContent();title.text=t;subtitle.text="BORSA TAKİP V3";status.text=b;adapter.submitList(emptyList())}
    private fun showContent(){home.visibility=View.GONE;settings.visibility=View.GONE;content.visibility=View.VISIBLE;findViewById<View>(R.id.backButton).visibility=View.VISIBLE;findViewById<View>(R.id.watchActionRow).visibility=View.GONE;findViewById<View>(R.id.watchListContainer).visibility=View.GONE;findViewById<View>(R.id.recyclerView).visibility=View.VISIBLE;progress.visibility=View.GONE}
    private fun selectNav(i:Int){val ids=listOf(R.id.navHome,R.id.navStocks,R.id.navViop,R.id.navFavorites,R.id.navSettings);ids.forEachIndexed{n,id->findViewById<TextView>(id).alpha=if(n==i)1f else .62f}}
    private fun latest(s:Stock):Pair<Double,Double>?{val c=s.candles; if(c.isEmpty()) return null; val last=c.last(); val prev=c.dropLast(1).lastOrNull()?:return Pair(last.close,0.0); return Pair(last.close,(last.close/prev.close-1.0)*100.0)}
    private fun fmt(v:Double)=String.format(Locale("tr","TR"),"%.2f",v)
    private fun fmtPct(v:Double)=String.format(Locale("tr","TR"),"%+.2f%%",v)
    private fun fmtTime(t:Long)=SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale("tr","TR")).format(Date(t))
    override fun onDestroy(){liveJob?.cancel();job?.cancel();super.onDestroy()}
}
