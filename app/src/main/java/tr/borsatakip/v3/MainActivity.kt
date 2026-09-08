package tr.borsatakip.v3

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tr.borsatakip.v3.databinding.ActivityMainBinding
import tr.borsatakip.v3.market.AppMarketDataProvider
import tr.borsatakip.v3.market.Market
import tr.borsatakip.v3.market.MarketDataProvider
import tr.borsatakip.v3.model.Opportunity
import tr.borsatakip.v3.scoring.ScoringEngine
import tr.borsatakip.v3.ui.OpportunityAdapter
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val provider: MarketDataProvider by lazy { AppMarketDataProvider(this) }
    private val scoring = ScoringEngine()
    private val adapter = OpportunityAdapter(::showDetail)
    private var scanJob: Job? = null

    private enum class ScanButton { BIST, OPPORTUNITY, VIOP }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.btnBist.setOnClickListener { startSingleScan(Market.BIST, ScanButton.BIST) }
        binding.btnViop.setOnClickListener { startSingleScan(Market.VIOP, ScanButton.VIOP) }
        binding.btnOpportunity.setOnClickListener { startCombinedOpportunityScan() }
        binding.backButton.setOnClickListener { showHome() }
        binding.navHome.setOnClickListener { showHome() }
        binding.navStocks.setOnClickListener { startSingleScan(Market.BIST, ScanButton.BIST) }
        binding.navViop.setOnClickListener { startSingleScan(Market.VIOP, ScanButton.VIOP) }
        binding.navFavorites.setOnClickListener { showInfo("Favoriler", "Henüz favori eklenmedi.") }
        binding.navSettings.setOnClickListener { showSettings() }
        binding.settingsSaveButton.setOnClickListener { saveApiSettings() }
        binding.settingsClearButton.setOnClickListener { clearApiSettings() }
        binding.settingsTestButton.setOnClickListener { testApiSettings() }
        binding.watchCard.setOnClickListener { showWatchlist() }
        binding.noticeCard.setOnClickListener { showInfo("Bildirimler", "Bildirim altyapısı hazır tutulmuştur; kullanıcı izni olmadan bildirim gönderilmez.") }
        binding.newsCard.setOnClickListener { showInfo("Haberler", "Haber sağlayıcısı bağlı değil. Sahte haber gösterilmez.") }
        binding.summaryCard.setOnClickListener { showInfo("Piyasa Özeti", "Piyasa özeti gerçek backend verisi mevcut olduğunda üretilecektir.") }
        showHome()
    }

    private fun showHome() { scanJob?.cancel(); scanJob = null; restoreButtons(); binding.homeScroll.visibility=View.VISIBLE; binding.contentPanel.visibility=View.GONE; binding.settingsPanel.visibility=View.GONE; binding.backButton.visibility=View.GONE; binding.titleText.text="BORSA TAKİP V3"; binding.subtitleText.text="Borsa fırsatlarını bir adım önce keşfet"; binding.lastScanHome.text="Henüz tarama yapılmadı."; adapter.submitList(emptyList()); selectNav(0) }

    private fun startSingleScan(market: Market, button: ScanButton) {
        if (scanJob?.isActive == true) return
        beginScanUi(button, if (market == Market.BIST) "BIST TARAMA" else "VİOP TARAMA")
        selectNav(if (market == Market.BIST) 1 else 2)
        scanJob = lifecycleScope.launch {
            binding.statusText.text="VERİ ALINIYOR..."; binding.progressBar.progress=15
            provider.loadMarket(market).onSuccess { result ->
                binding.statusText.text="${result.items.size} kayıt alındı. TARANIYOR..."; binding.progressBar.progress=45
                val all=result.items.mapNotNull(scoring::score); binding.statusText.text="ANALİZ EDİLİYOR..."; binding.progressBar.progress=75
                finishSuccess(all.filter{it.direction!="SİNYAL YOK"&&it.score>=70}.sortedByDescending{it.score},result.dataTimestamp,result.sourceName)
            }.onFailure(::finishFailure)
        }
    }

    private fun startCombinedOpportunityScan() {
        if (scanJob?.isActive == true) return
        beginScanUi(ScanButton.OPPORTUNITY,"FIRSAT KONTROLÜ"); selectNav(1)
        scanJob=lifecycleScope.launch {
            binding.statusText.text="BIST + VİOP VERİLERİ ALINIYOR..."; binding.progressBar.progress=15
            try {
                val (br,vr)=coroutineScope { async{provider.loadMarket(Market.BIST)} to async{provider.loadMarket(Market.VIOP)} }
                val b=br.await().getOrElse{throw it}; val v=vr.await().getOrElse{throw it}
                binding.statusText.text="BIST + VİOP TARANIYOR..."; binding.progressBar.progress=50
                val rows=(b.items+v.items).mapNotNull(scoring::score); binding.progressBar.progress=80
                val visible=rows.filter{it.direction!="SİNYAL YOK"&&it.score>=70}.sortedByDescending{it.score}
                finishSuccess(visible,maxOf(b.dataTimestamp,v.dataTimestamp),listOf(b.sourceName,v.sourceName).distinct().joinToString(" + "),true)
            } catch(t:Throwable){finishFailure(t)}
        }
    }

    private fun beginScanUi(button:ScanButton,title:String){adapter.submitList(emptyList());showContent();binding.titleText.text=title;binding.subtitleText.text="Yeni tarama başlatıldı";binding.progressBar.visibility=View.VISIBLE;binding.progressBar.progress=5;binding.statusText.text="VERİ ALINIYOR...";setButtonsEnabled(false);when(button){ScanButton.BIST->binding.btnBistTitle.text="⟳ TARANIYOR...";ScanButton.OPPORTUNITY->binding.btnOpportunityTitle.text="⟳ TARANIYOR...";ScanButton.VIOP->binding.btnViopTitle.text="⟳ TARANIYOR..."}}

    private fun finishSuccess(visible:List<Opportunity>,dataTimestamp:Long,sourceName:String,combined:Boolean=false){binding.progressBar.progress=100;adapter.submitList(visible);val now=System.currentTimeMillis();binding.subtitleText.text="${freshnessLabel(dataTimestamp)} • $sourceName";binding.statusText.text=buildString{append("Tarama tamamlandı\nSon güncelleme: ${formatTime(now)}\nVeri zamanı: ${formatTime(dataTimestamp)}\n${if(combined)"BIST + VİOP" else "Piyasa"} güçlü fırsat sayısı: ${visible.size}");if(visible.isEmpty())append("\n\nŞu anda 70 puan üzeri güçlü LONG/SHORT fırsatı bulunamadı.")};binding.lastScanHome.text="Son tarama: ${formatTime(now)}";restoreButtons();scanJob=null}

    private fun finishFailure(error:Throwable){adapter.submitList(emptyList());binding.progressBar.progress=0;binding.subtitleText.text="VERİ ALINAMADI";binding.statusText.text=userFacingError(error);restoreButtons();scanJob=null}
    private fun userFacingError(error:Throwable):String{val root=generateSequence(error){it.cause}.last();return when(root){is UnknownHostException->"İnternet bağlantısı bulunamadı.";is SocketTimeoutException->"Veri sağlayıcısından yanıt alınamadı.";else->when{root.message?.contains("backend adresi",true)==true->"Gerçek piyasa veri kaynağı yapılandırılmamış.";root.message?.contains("geçerli piyasa",true)==true->"Tarama için kullanılabilir güncel veri bulunamadı.";else->"Piyasa verisi alınamadı."}}+"\n\nEski veya demo sonuç gösterilmedi."}

    private fun showDetail(item:Opportunity){showContent();binding.titleText.text=item.symbol;binding.subtitleText.text="Hisse/Sözleşme Detayı • ${freshnessLabel(item.dataTimestamp)}";binding.progressBar.visibility=View.GONE;adapter.submitList(emptyList());binding.statusText.text=buildString{append("Son Fiyat   ${fmt(item.price)}     Skor   ${item.score}/100\nYön: ${item.direction}    Sınıf: ${item.confidence}\nVeri zamanı: ${formatTime(item.dataTimestamp)}\n\nTeknik Analiz\nRSI14: ${fmtOrNA(item.technical.rsi14)}\nMACD: ${fmtOrNA(item.technical.macd)}   Sinyal: ${fmtOrNA(item.technical.macdSignal)}\nEMA20: ${fmtOrNA(item.technical.ema20)}   EMA50: ${fmtOrNA(item.technical.ema50)}   EMA200: ${fmtOrNA(item.technical.ema200)}\nATR14: ${fmtOrNA(item.technical.atr14)}   Hacim: ${item.technical.volumeRatio?.let{fmt(it)+"x"}?:"Veri yetersiz"}\nDestek: ${fmtOrNA(item.technical.support)}   Direnç: ${fmtOrNA(item.technical.resistance)}\n\nGiriş: ${item.entry?.let(::fmt)?:"Veri yetersiz"}\nStop: ${item.stop?.let(::fmt)?:"Veri yetersiz"}\nHedef 1: ${item.target1?.let(::fmt)?:"Veri yetersiz"}\nHedef 2: ${item.target2?.let(::fmt)?:"Veri yetersiz"}\nRisk/Getiri: ${item.riskReward?.let{"1:${fmt(it)}"}?:"Veri yetersiz"}\n\nSkor bileşenleri\n");item.breakdown.forEach{append("${it.name}: ${it.points}/${it.maxPoints}\n")};append("\nBu analiz yatırım tavsiyesi değildir.")}}
    private fun freshnessLabel(t:Long)=when{System.currentTimeMillis()-t<=15*60_000L->"GÜNCEL VERİ";System.currentTimeMillis()-t<=24*60*60_000L->"GECİKMELİ VERİ";else->"ESKİ VERİ"}
    private fun restoreButtons(){binding.btnBistTitle.text="BIST TARA";binding.btnOpportunityTitle.text="FIRSAT KONTROL ET";binding.btnViopTitle.text="VİOP TARA";setButtonsEnabled(true)}
    private fun setButtonsEnabled(e:Boolean){listOf(binding.btnBist,binding.btnOpportunity,binding.btnViop).forEach{it.isEnabled=e;it.alpha=if(e)1f else .72f}}
    private fun formatTime(t:Long)=SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale("tr","TR")).format(Date(t))
    private fun fmt(v:Double)=String.format(Locale("tr","TR"),"%.2f",v); private fun fmtOrNA(v:Double?)=v?.let(::fmt)?:"Veri yetersiz"
    private fun showWatchlist(){
scanJob?.cancel();scanJob=null;restoreButtons();showContent();binding.titleText.text="Takip Listem";binding.subtitleText.text="İzlemek istediğiniz hisseleri burada yönetin";binding.progressBar.visibility=View.GONE;binding.recyclerView.visibility=View.GONE;binding.statusText.text="Hisseleri sembol ile ekleyin. Liste cihazda saklanır.";binding.watchActionRow.visibility=View.VISIBLE;binding.watchListContainer.visibility=View.VISIBLE;binding.addWatchButton.setOnClickListener{addWatchSymbol()};binding.clearWatchButton.setOnClickListener{clearWatchlist()};renderWatchlist();selectNav(3)
}
private fun renderWatchlist(){
val prefs=getSharedPreferences("watchlist",MODE_PRIVATE);val symbols=prefs.getStringSet("symbols",emptySet())?.toList()?.sorted()?:emptyList();binding.watchListContainer.removeAllViews();if(symbols.isEmpty()){val empty=android.widget.TextView(this);empty.text="Henüz takip edilen hisse bulunmuyor.\n\nÖrneğin: THYAO, ASELS, TUPRS";empty.setTextColor(Color.parseColor("#91A3B9"));empty.textSize=16f;empty.setPadding(16,24,16,24);binding.watchListContainer.addView(empty);return};symbols.forEach{symbol->val row=android.widget.LinearLayout(this);row.orientation=android.widget.LinearLayout.HORIZONTAL;row.gravity=android.view.Gravity.CENTER_VERTICAL;row.setPadding(12,10,8,10);val tv=android.widget.TextView(this);tv.text="★  $symbol";tv.textSize=18f;tv.setTextColor(Color.parseColor("#EAF3FF"));row.addView(tv,android.widget.LinearLayout.LayoutParams(0,android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,1f));val del=android.widget.Button(this);del.text="SİL";del.setOnClickListener{removeWatchSymbol(symbol)};row.addView(del);binding.watchListContainer.addView(row)}}
private fun addWatchSymbol(){val input=android.widget.EditText(this);input.hint="Hisse kodu (örn. THYAO)";input.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;android.app.AlertDialog.Builder(this).setTitle("Takip Listesine Hisse Ekle").setView(input).setNegativeButton("İPTAL",null).setPositiveButton("EKLE"){_,_->val symbol=input.text.toString().trim().uppercase(Locale("tr","TR"));if(symbol.isBlank())return@setPositiveButton;val prefs=getSharedPreferences("watchlist",MODE_PRIVATE);val set=prefs.getStringSet("symbols",emptySet())?.toMutableSet()?:mutableSetOf();set.add(symbol);prefs.edit().putStringSet("symbols",set).apply();renderWatchlist()}.show()}
private fun removeWatchSymbol(symbol:String){val prefs=getSharedPreferences("watchlist",MODE_PRIVATE);val set=prefs.getStringSet("symbols",emptySet())?.toMutableSet()?:mutableSetOf();set.remove(symbol);prefs.edit().putStringSet("symbols",set).apply();renderWatchlist()}
private fun clearWatchlist(){getSharedPreferences("watchlist",MODE_PRIVATE).edit().clear().apply();renderWatchlist()}
private fun showInfo(title:String,body:String){scanJob?.cancel();scanJob=null;restoreButtons();showContent();binding.titleText.text=title;binding.subtitleText.text="Borsa Takip V3.2.2";binding.progressBar.visibility=View.GONE;adapter.submitList(emptyList());binding.statusText.text=body;selectNav(if(title=="Ayarlar")4 else -1)}
    private fun showContent(){binding.homeScroll.visibility=View.GONE;binding.settingsPanel.visibility=View.GONE;binding.contentPanel.visibility=View.VISIBLE;binding.backButton.visibility=View.VISIBLE;binding.watchActionRow.visibility=View.GONE;binding.watchListContainer.visibility=View.GONE;binding.recyclerView.visibility=View.VISIBLE}
    private fun showSettings(){scanJob?.cancel();scanJob=null;restoreButtons();binding.homeScroll.visibility=View.GONE;binding.contentPanel.visibility=View.GONE;binding.settingsPanel.visibility=View.VISIBLE;binding.backButton.visibility=View.VISIBLE;binding.titleText.text="API AYARLARI";binding.subtitleText.text="Gerçek piyasa verisi bağlantısı";val p=getSharedPreferences("market_api_settings",MODE_PRIVATE);binding.apiBaseUrlInput.setText(p.getString("base_url",BuildConfig.MARKET_API_BASE_URL));binding.apiKeyInput.setText(p.getString("api_key",""));binding.apiSecretInput.setText(p.getString("api_secret",""));binding.settingsStatus.text=if(p.getString("base_url","").isNullOrBlank())"API bilgileri girilmedi. Kaydettikten sonra bağlantıyı test edebilirsiniz." else "API ayarları kayıtlı.";selectNav(4)}
    private fun saveApiSettings(){val base=binding.apiBaseUrlInput.text?.toString()?.trim()?.trimEnd('/').orEmpty();val key=binding.apiKeyInput.text?.toString()?.trim().orEmpty();val secret=binding.apiSecretInput.text?.toString()?.trim().orEmpty();if(base.isNotBlank()&&!base.startsWith("https://")){binding.settingsStatus.text="HATA: Base URL HTTPS ile başlamalıdır.";return};getSharedPreferences("market_api_settings",MODE_PRIVATE).edit().putString("base_url",base).putString("api_key",key).putString("api_secret",secret).apply();binding.settingsStatus.text="API ayarları kaydedildi."
    }
    private fun clearApiSettings(){getSharedPreferences("market_api_settings",MODE_PRIVATE).edit().clear().apply();binding.apiBaseUrlInput.setText(BuildConfig.MARKET_API_BASE_URL);binding.apiKeyInput.text?.clear();binding.apiSecretInput.text?.clear();binding.settingsStatus.text="API bilgileri cihazdan temizlendi."}
    private fun testApiSettings(){saveApiSettings();val base=binding.apiBaseUrlInput.text?.toString()?.trim().orEmpty();if(base.isBlank())return;binding.settingsStatus.text="BIST bağlantısı test ediliyor...";lifecycleScope.launch{provider.loadMarket(Market.BIST).onSuccess{r->binding.settingsStatus.text="BAĞLANTI BAŞARILI ✓\n${r.items.size} gerçek kayıt alındı. Kaynak: ${r.sourceName}"}.onFailure{e->binding.settingsStatus.text="BAĞLANTI BAŞARISIZ ✕\n${userFacingError(e)}"}}}
    private fun selectNav(selected:Int){listOf(binding.navHome,binding.navStocks,binding.navViop,binding.navFavorites,binding.navSettings).forEachIndexed{index,v->v.setTextColor(Color.parseColor(if(index==selected)"#18A5FF" else "#91A3B9"))}}
}
