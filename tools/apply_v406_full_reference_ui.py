from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/tr/borsatakip/v3"
MAIN = SRC / "MainActivityV4.kt"

code = r'''package tr.borsatakip.v3

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class MainActivityV4 : AppCompatActivity() {
    private val bg = Color.rgb(1,25,45)
    private val surface = Color.rgb(6,27,45)
    private val small = Color.rgb(9,32,51)
    private val border = Color.rgb(23,70,98)
    private val blue = Color.rgb(24,165,255)
    private val green = Color.rgb(16,217,119)
    private val yellow = Color.rgb(255,197,61)
    private val orange = Color.rgb(255,138,31)
    private val red = Color.rgb(255,64,85)
    private val purple = Color.rgb(116,82,255)
    private val white = Color.rgb(244,247,251)
    private val secondary = Color.rgb(167,181,200)
    private val muted = Color.rgb(130,148,170)

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var nav: LinearLayout
    private var job: Job? = null
    private lateinit var provider: MarketDataProvider
    private val scoring = ScoringEngine()
    private var lastBist: List<Stock> = emptyList()
    private var lastViop: List<Stock> = emptyList()
    private var lastOpp: List<Opportunity> = emptyList()
    private var selectedMarket = Market.BIST
    private var scanStartedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = Color.rgb(0,19,31)
        provider = AppMarketDataProvider(this)
        buildShell()
        showHome()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg) }
        content = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(10),dp(12),0) }
        root.addView(content, LinearLayout.LayoutParams(-1,0,1f))
        nav = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setBackgroundColor(Color.rgb(2,18,31)); setPadding(4,4,4,4) }
        listOf("⌂\nAna Sayfa","▥\nHisseler","▥\nVİOP","★\nFavoriler","⚙\nAyarlar").forEachIndexed { i,t ->
            val v = navItem(t)
            v.setOnClickListener { when(i){0->showHome();1->showBistScan();2->showViopScan();3->showFavorites();4->showSettings()} }
            nav.addView(v, LinearLayout.LayoutParams(0,dp(68),1f))
        }
        root.addView(nav)
        setContentView(root)
    }

    private fun navItem(text:String) = TextView(this).apply {
        this.text=text; gravity=Gravity.CENTER; textSize=11f; setTextColor(muted); typeface=Typeface.DEFAULT
        setPadding(0,4,0,0)
    }

    private fun header(title:String, subtitle:String, accent:Int=blue, back:Boolean=true, action:String?=null): LinearLayout {
        val h=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,4,0,8)}
        if(back){ h.addView(TextView(this).apply{text="‹";textSize=38f;setTextColor(accent);gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(38),dp(58))) }
        val col=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        col.addView(TextView(this).apply{text=title;textSize=22f;setTextColor(white);typeface=Typeface.DEFAULT_BOLD})
        col.addView(TextView(this).apply{text=subtitle;textSize=12f;setTextColor(secondary)})
        h.addView(col,LinearLayout.LayoutParams(0,-2,1f))
        if(action!=null) h.addView(TextView(this).apply{text=action;textSize=12f;setTextColor(accent);gravity=Gravity.CENTER})
        return h
    }

    private fun badge(text:String, color:Int=blue): TextView = TextView(this).apply {
        this.text=text; textSize=9f; setTextColor(color); gravity=Gravity.CENTER; setPadding(dp(8),dp(4),dp(8),dp(4));
        setBackgroundColor(Color.rgb(8,36,58))
    }

    private fun card(title:String?=null, accent:Int=border): LinearLayout {
        val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));setBackgroundColor(surface)}
        if(title!=null)c.addView(TextView(this).apply{text=title;textSize=14f;setTextColor(white);typeface=Typeface.DEFAULT_BOLD})
        val lp=LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,0);c.layoutParams=lp
        return c
    }

    private fun row(label:String,value:String,valueColor:Int=secondary): LinearLayout {
        val r=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(5),0,dp(5))}
        r.addView(TextView(this).apply{text=label;textSize=12f;setTextColor(secondary)},LinearLayout.LayoutParams(0,-2,1f))
        r.addView(TextView(this).apply{text=value;textSize=12f;setTextColor(valueColor);gravity=Gravity.RIGHT})
        return r
    }

    private fun showHome(){
        job?.cancel(); content.removeAllViews();
        val scroll=ScrollView(this); val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        body.addView(header("BORSA TAKİP V4","BIST • VİOP • FIRSAT",blue,false))
        val demo=badge(if(lastBist.isEmpty()&&lastViop.isEmpty())"VERİ BEKLENİYOR" else "VERİ KAYNAĞI DURUMU")
        body.addView(demo,LinearLayout.LayoutParams(-2,dp(28)))
        body.addView(actionCard("⌕","BIST TARA","BIST hisselerini tara ve fırsatları bul",blue){showBistScan()})
        body.addView(actionCard("★","FIRSAT KONTROL ET","Bedelsiz, bedelli, temettü, sermaye ve teknik sinyalleri analiz et",green){scanOpportunity()})
        body.addView(actionCard("▥","VİOP TARA","VİOP sözleşmelerini ayrı olarak analiz et",purple){showViopScan()})
        val grid=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        grid.addView(smallAction("◷","Takip Listem","İzlediğin hisseler"){showFavorites()},LinearLayout.LayoutParams(0,-2,1f))
        grid.addView(smallAction("♧","Bildirimler","Fırsat kaçırma"){info("Bildirimler","Bildirimler yalnızca gerçek altyapı ve izin mevcutsa etkinleşir.")},LinearLayout.LayoutParams(0,-2,1f))
        body.addView(grid)
        val grid2=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        grid2.addView(smallAction("▤","Haberler","Piyasalardan son gelişmeler"){info("Haberler","Haber sağlayıcısı bağlı değilse haber uydurulmaz.")},LinearLayout.LayoutParams(0,-2,1f))
        grid2.addView(smallAction("▥","Piyasa Özeti","BIST ve VİOP görünümü"){showSummary()},LinearLayout.LayoutParams(0,-2,1f))
        body.addView(grid2)
        val opp=card("💡  Günün Fırsatları",yellow)
        opp.addView(TextView(this).apply{text=if(lastOpp.isEmpty())"Henüz tarama yapılmadı. Gerçek zamanlı veri olmadan fırsat sonucu üretilmez." else "${lastOpp.size} doğrulanmış fırsat bulundu.";textSize=12f;setTextColor(secondary);setPadding(0,dp(6),0,0)})
        opp.setOnClickListener{scanOpportunity()};body.addView(opp)
        scroll.addView(body);content.addView(scroll)
        selectNav(0)
    }

    private fun actionCard(icon:String,title:String,sub:String,color:Int,click:()->Unit):View{
        val c=card(); c.setBackgroundColor(Color.rgb(5,35,55));
        val r=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        r.addView(TextView(this).apply{text=icon;textSize=30f;setTextColor(Color.WHITE);gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(50),dp(58)))
        val col=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        col.addView(TextView(this).apply{text=title;textSize=17f;setTextColor(white);typeface=Typeface.DEFAULT_BOLD})
        col.addView(TextView(this).apply{text=sub;textSize=11f;setTextColor(secondary);maxLines=2})
        r.addView(col,LinearLayout.LayoutParams(0,-2,1f));r.addView(TextView(this).apply{text="›";textSize=28f;setTextColor(color)})
        c.addView(r);c.setOnClickListener{click()};return c
    }

    private fun smallAction(icon:String,title:String,sub:String,click:()->Unit):View{
        val c=card();c.layoutParams=LinearLayout.LayoutParams(0,-2,1f).also{it.setMargins(dp(3),dp(3),dp(3),0)}
        c.setOnClickListener{click()};c.addView(TextView(this).apply{text="$icon  $title";textSize=13f;setTextColor(white);typeface=Typeface.DEFAULT_BOLD})
        c.addView(TextView(this).apply{text=sub;textSize=10f;setTextColor(muted)})
        return c
    }

    private fun showBistScan(){ selectedMarket=Market.BIST; showScanScreen(false) }
    private fun showViopScan(){ selectedMarket=Market.VIOP; showScanScreen(true) }

    private fun showScanScreen(viop:Boolean){
        content.removeAllViews();val scroll=ScrollView(this);val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        body.addView(header(if(viop)"VİOP Tarama" else "BIST Tarama",if(viop)"Vadeli sözleşmeler analiz ediliyor":"BIST hisseleri analiz ediliyor",if(viop)purple else blue,true,"⚙"))
        body.addView(badge(if(viop)"VİOP • AYRI TARAMA" else "BIST • TARAMA"))
        val prog=card();val title=TextView(this).apply{text=if(viop)"Hazır" else "Tarama başlatılmaya hazır";textSize=20f;setTextColor(white);typeface=Typeface.DEFAULT_BOLD};prog.addView(title)
        val pb=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pb.max=100;pb.progress=0;prog.addView(pb,LinearLayout.LayoutParams(-1,dp(10)))
        val pct=TextView(this).apply{text="0%";textSize=11f;setTextColor(secondary);gravity=Gravity.RIGHT};prog.addView(pct)
        body.addView(prog)
        val steps=card(if(viop)"Tarama Adımları" else "Tarama Adımları")
        val stepTexts=if(viop)listOf("Fiyat verileri alınıyor","Açık pozisyon kontrolü","Teknik analiz hesaplanıyor","Risk seviyesi oluşturuluyor") else listOf("Fiyat verileri alınıyor","Teknik göstergeler hesaplanıyor","KAP duyuruları kontrol ediliyor","Fırsat skoru hazırlanıyor")
        val stepViews=stepTexts.map{TextView(this).apply{text="○  $it   Beklemede";textSize=12f;setTextColor(secondary);setPadding(0,dp(8),0,dp(8));steps.addView(this)}}
        body.addView(steps)
        if(!viop){
            val filters=card("⚙  Tarama Kriterleri")
            filters.addView(TextView(this).apply{text="Seçilebilir kriterler: Bedelsiz • Bedelli • Temettü • Sermaye Artırımı • Teknik Sinyal • Hacim";textSize=11f;setTextColor(secondary)})
            body.addView(filters)
        }
        val scan=Button(this).apply{text=if(viop)"VİOP TARAMASINI BAŞLAT" else "BIST TARAMASINI BAŞLAT";setTextColor(Color.WHITE);setBackgroundColor(if(viop)purple else blue);setOnClickListener{runScan(viop,title,pct,pb,stepViews)}}
        body.addView(scan,LinearLayout.LayoutParams(-1,dp(50)))
        val info=card("💡 Veri Durumu")
        info.addView(TextView(this).apply{text="Sağlayıcı bağlı değilse canlı sonuç gösterilmez. Demo veriler gerçek veri gibi sunulmaz.";textSize=11f;setTextColor(secondary)})
        body.addView(info)
        scroll.addView(body);content.addView(scroll);selectNav(if(viop)2 else 1)
    }

    private fun runScan(viop:Boolean,title:TextView,pct:TextView,pb:ProgressBar,steps:List<TextView>){
        if(job?.isActive==true)return
        scanStartedAt=System.currentTimeMillis();val market=if(viop)Market.VIOP else Market.BIST
        job=lifecycleScope.launch{
            title.text="${if(viop)"VİOP" else "BIST"} taranıyor…"
            steps.forEachIndexed{i,v->v.text="◌  ${v.text.substringAfter("  ").substringBefore("   ")}   Çalışıyor";v.setTextColor(blue)}
            try{
                pb.progress=10;pct.text="10%";delay(120)
                val result=provider.loadMarket(market)
                pb.progress=70;pct.text="70%"
                result.onSuccess{r->
                    if(viop)lastViop=r.items else lastBist=r.items
                    val opp=r.items.mapNotNull(scoring::score).sortedByDescending{it.score}
                    if(viop) lastOpp=opp else lastOpp=opp.filter{it.score>=60}
                    steps.forEach{it.text=it.text.substringBefore("   ")+"   Tamamlandı";it.setTextColor(green)}
                    pb.progress=100;pct.text="100%";title.text="${r.items.size} kayıt tarandı"
                    lifecycleScope.launch{delay(250);showResults(viop,r.items,opp,r.sourceName,r.dataTimestamp)}
                }.onFailure{e->
                    steps.last().text="✕  Veri alınamadı   Sağlayıcı hatası";steps.last().setTextColor(red);title.text="Tarama tamamlanamadı";pct.text="Veri yok";pb.progress=0
                    info("Veri alınamadı",e.message?:"Sağlayıcı bağlantısı kurulamadı.")
                }
            }finally{job=null}
        }
    }

    private fun showResults(viop:Boolean,items:List<Stock>,opp:List<Opportunity>,source:String,ts:Long){
        content.removeAllViews();val scroll=ScrollView(this);val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        body.addView(header(if(viop)"VİOP Sonuçları" else "BIST Tarama Sonuçları",if(viop)"Vadeli sözleşmeler tarandı":"BIST hisseleri tarandı",if(viop)purple else blue,true,"⚱"))
        body.addView(badge(if(source.contains("DEMO",true))"DEMO VERİ" else source.uppercase(Locale("tr"))))
        if(!viop){
            val tabs=TextView(this).apply{text="Tümü (${opp.size})     Bedelsiz     Bedelli     Temettü     Sermaye Art.";textSize=11f;setTextColor(secondary);setPadding(0,dp(10),0,dp(10))};body.addView(tabs)
        }
        if(items.isEmpty()) body.addView(card().apply{addView(TextView(this@MainActivityV4).apply{text="Veri yetersiz\nSağlayıcıdan geçerli veri alınamadı.";textSize=15f;setTextColor(yellow);gravity=Gravity.CENTER;padding(20)})})
        val rows=if(viop)opp else opp.filter{it.direction!="SİNYAL YOK"}
        rows.take(100).forEach{op->body.addView(resultRow(op,viop))}
        val foot=card();foot.addView(TextView(this).apply{text="Son tarama: ${fmt(scanStartedAt)}\nVeri zamanı: ${fmt(ts)}\nKaynak: $source";textSize=11f;setTextColor(secondary)})
        val again=Button(this).apply{text="↻  Yeniden Tara";setOnClickListener{if(viop)showViopScan() else showBistScan()}};foot.addView(again)
        body.addView(foot);scroll.addView(body);content.addView(scroll);selectNav(if(viop)2 else 1)
    }

    private fun resultRow(op:Opportunity,viop:Boolean):View{
        val c=card();c.setOnClickListener{showDetail(op)}
        val r=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val col=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        col.addView(TextView(this).apply{text=op.symbol;textSize=15f;setTextColor(white);typeface=Typeface.DEFAULT_BOLD})
        col.addView(TextView(this).apply{text=(op.companyName?:if(viop)"Vadeli sözleşme" else "BIST hissesi");textSize=10f;setTextColor(muted)})
        r.addView(col,LinearLayout.LayoutParams(0,-2,1f))
        r.addView(TextView(this).apply{text=fmt(op.price);textSize=12f;setTextColor(secondary);gravity=Gravity.RIGHT},LinearLayout.LayoutParams(dp(70),-2))
        val score=TextView(this).apply{text="${op.score}";textSize=15f;setTextColor(if(op.score>=80)green else if(op.score>=60)blue else red);gravity=Gravity.CENTER;typeface=Typeface.DEFAULT_BOLD}
        r.addView(score,LinearLayout.LayoutParams(dp(48),dp(40)));r.addView(TextView(this).apply{text="›";textSize=24f;setTextColor(blue)})
        c.addView(r)
        c.addView(TextView(this).apply{text="${op.direction}   •   ${op.confidence}   •   Risk: ${op.riskReward?.let{String.format(Locale.US,"%.2f",it)}?:"Veri yetersiz"}";textSize=10f;setTextColor(secondary);setPadding(0,dp(6),0,0)})
        return c
    }

    private fun showDetail(op:Opportunity){
        content.removeAllViews();val scroll=ScrollView(this);val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        body.addView(header(op.symbol,op.companyName?:"Hisse Detayı",blue,true))
        val top=card();val tr=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        tr.addView(TextView(this).apply{text="Son Fiyat\n${fmt(op.price)}\n${op.direction}";textSize=20f;setTextColor(white);typeface=Typeface.DEFAULT_BOLD},LinearLayout.LayoutParams(0,-2,1f))
        tr.addView(TextView(this).apply{text="Fırsat Skoru\n${op.score}/100\n${op.confidence}";textSize=17f;setTextColor(green);gravity=Gravity.RIGHT;typeface=Typeface.DEFAULT_BOLD})
        top.addView(tr);body.addView(top)
        val sig=card("▥  Sinyaller")
        op.breakdown.take(6).forEach{sig.addView(row(it.name,"${it.points}/${it.maxPoints}",if(it.points*2>=it.maxPoints)green else secondary))}
        body.addView(sig)
        val tech=card("▥  Teknik Analiz")
        tech.addView(row("RSI (14)",num(op.technical.rsi14)))
        tech.addView(row("MACD",num(op.technical.macd)))
        tech.addView(row("EMA 20 / 50 / 200","${num(op.technical.ema20)} / ${num(op.technical.ema50)} / ${num(op.technical.ema200)}"))
        tech.addView(row("Hacim oranı",num(op.technical.volumeRatio)))
        tech.addView(row("Destek",num(op.technical.support)))
        tech.addView(row("Direnç",num(op.technical.resistance)))
        body.addView(tech)
        val risk=card("⚠  Risk / Getiri")
        risk.addView(row("Giriş",num(op.entry)));risk.addView(row("Stop",num(op.stop),red));risk.addView(row("Hedef 1",num(op.target1),green));risk.addView(row("Hedef 2",num(op.target2),green));risk.addView(row("Risk / Getiri",num(op.riskReward),yellow))
        body.addView(risk)
        val note=card("💡  Önemli Uyarı");note.addView(TextView(this).apply{text="Bu ekran yatırım tavsiyesi değildir. Eksik hesaplar Veri yetersiz olarak değerlendirilir.";textSize=11f;setTextColor(secondary)});body.addView(note)
        scroll.addView(body);content.addView(scroll);selectNav(1)
    }

    private fun showFavorites(){
        content.removeAllViews();val scroll=ScrollView(this);val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        body.addView(header("Favoriler","İzlediğin hisseler",blue,true))
        val prefs=getSharedPreferences("borsa_watch",Context.MODE_PRIVATE);val syms=prefs.getStringSet("symbols",emptySet()).orEmpty().toList().sorted()
        if(syms.isEmpty())body.addView(card().apply{addView(TextView(this@MainActivityV4).apply{text="Henüz favori eklenmedi.\nHisse detayından favoriye ekleme kullanılabilir.";textSize=14f;setTextColor(secondary);gravity=Gravity.CENTER;padding(24)})})
        syms.forEach{s->
            val c=card();c.addView(TextView(this).apply{text=s;textSize=15f;setTextColor(white);typeface=Typeface.DEFAULT_BOLD});c.addView(TextView(this).apply{text="Favori • canlı veri yalnızca sağlayıcı bağlıysa gösterilir";textSize=10f;setTextColor(muted)})
            c.setOnLongClickListener{prefs.edit().putStringSet("symbols",syms.filter{it!=s}.toSet()).apply();showFavorites();true};body.addView(c)
        }
        val add=Button(this).apply{text="＋ FAVORİ EKLE";setOnClickListener{addFavoriteDialog()}};body.addView(add)
        scroll.addView(body);content.addView(scroll);selectNav(3)
    }

    private fun addFavoriteDialog(){
        val input=EditText(this).apply{hint="Örn. ASELS";setTextColor(white)}
        AlertDialog.Builder(this).setTitle("Favori ekle").setView(input).setNegativeButton("İptal",null).setPositiveButton("Ekle"){_,_->
            val s=input.text.toString().trim().uppercase(Locale("tr"));if(s.isNotEmpty()){val p=getSharedPreferences("borsa_watch",0);val set=p.getStringSet("symbols",emptySet()).orEmpty().toMutableSet();set.add(s);p.edit().putStringSet("symbols",set).apply()};showFavorites()
        }.show()
    }

    private fun showSettings(){
        content.removeAllViews();val scroll=ScrollView(this);val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        body.addView(header("Ayarlar","Uygulama tercihleri",blue,true))
        body.addView(badge("DEMO VERİ yalnızca açıkça işaretlenirse kullanılabilir"))
        body.addView(settingsSection("Görünüm",listOf("Tema" to "Koyu")))
        val data=card("Veri ve Analiz")
        val demo=Switch(this).apply{text="Demo Veri Göster";setTextColor(white);isChecked=getSharedPreferences("settings",0).getBoolean("demo",false);setOnCheckedChangeListener{_,v->getSharedPreferences("settings",0).edit().putBoolean("demo",v).apply()}}
        data.addView(demo);data.addView(TextView(this).apply{text="Gerçek zamanlı veri olmadığında demo veriyi açıkça etiketlemeden gösterme.";textSize=10f;setTextColor(muted)})
        val notif=Switch(this).apply{text="Bildirimleri Aç";setTextColor(white)};data.addView(notif)
        val auto=Switch(this).apply{text="Otomatik Tarama";setTextColor(white);isChecked=false};data.addView(auto)
        data.addView(row("Veri Sağlayıcısı","BIST / VİOP sağlayıcısı"));body.addView(data)
        val score=card("Minimum Fırsat Skoru");score.addView(TextView(this).apply{text="Gösterilecek fırsatların minimum skoru: 60+ / 70+ / 80+";textSize=11f;setTextColor(secondary)});body.addView(score)
        body.addView(settingsSection("Hesap ve Senkronizasyon",listOf("Favoriler Senkronizasyonu" to "Hazır değil")))
        body.addView(settingsSection("Diğer",listOf("Yatırım Uyarısı Metni" to "Açıkla","Hakkında" to "BORSA TAKİP V4")))
        val info=card("ⓘ  Veri Hakkında");info.addView(TextView(this).apply{text="Canlı sağlayıcı bağlı değilken uygulama canlı veri varmış gibi davranmaz. API/sağlayıcı ayarları yapılandırılmadan sonuçlar tamamlanmış sayılmaz.";textSize=11f;setTextColor(secondary)});body.addView(info)
        scroll.addView(body);content.addView(scroll);selectNav(4)
    }

    private fun settingsSection(title:String,items:List<Pair<String,String>>):View{val c=card(title);items.forEach{c.addView(row(it.first,it.second))};return c}

    private fun showSummary(){
        content.removeAllViews();val scroll=ScrollView(this);val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};body.addView(header("Piyasa Özeti","BIST ve VİOP görünümü",green,true))
        val b=card("BIST");b.addView(row("Kayıt",lastBist.size.toString()));b.addView(row("Veri durumu",if(lastBist.isEmpty())"Veri bekleniyor" else "Veri alındı",if(lastBist.isEmpty())yellow else green));body.addView(b)
        val v=card("VİOP");v.addView(row("Kayıt",lastViop.size.toString()));v.addView(row("Veri durumu",if(lastViop.isEmpty())"Veri bekleniyor" else "Veri alındı",if(lastViop.isEmpty())yellow else green));body.addView(v)
        scroll.addView(body);content.addView(scroll)
    }

    private fun scanOpportunity(){
        content.removeAllViews();val scroll=ScrollView(this);val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};body.addView(header("Fırsat Kontrol","BIST + VİOP fırsat analizi",green,true,"⌕"))
        val summary=card();val counts=TextView(this).apply{text="Yüksek 0     Orta 0     Düşük 0     Riskli 0";textSize=15f;setTextColor(secondary);gravity=Gravity.CENTER};summary.addView(counts);body.addView(summary)
        val status=TextView(this).apply{text="Tarama başlatılmadı. Gerçek veri alınmadan fırsat sonucu üretilmez.";textSize=12f;setTextColor(secondary);setPadding(0,dp(8),0,dp(8))};body.addView(status)
        val go=Button(this).apply{text="⚡ FIRSAT KONTROL ET";setOnClickListener{
            status.text="BIST ve VİOP verileri alınıyor…";lifecycleScope.launch{coroutineScope{val b=async{provider.loadMarket(Market.BIST)};val v=async{provider.loadMarket(Market.VIOP)};val br=b.await();val vr=v.await();val all=(br.getOrNull()?.items.orEmpty()+vr.getOrNull()?.items.orEmpty()).mapNotNull(scoring::score);lastOpp=all.filter{it.direction!="SİNYAL YOK"&&it.score>=60}.sortedByDescending{it.score};counts.text="Yüksek ${lastOpp.count{it.score>=80}}     Orta ${lastOpp.count{it.score in 60..79}}     Düşük 0     Riskli ${all.count{it.direction!="SİNYAL YOK"&&it.score<60}}";status.text="${lastOpp.size} fırsat bulundu • Kaynak: ${br.getOrNull()?.sourceName?:"BIST yok"} / ${vr.getOrNull()?.sourceName?:"VİOP yok"}";lastOpp.take(50).forEach{body.addView(resultRow(it,false))}}}}
        }};body.addView(go);scroll.addView(body);content.addView(scroll);selectNav(0)
    }

    private fun info(title:String,msg:String){AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("Tamam",null).show()}
    private fun selectNav(active:Int){for(i in 0 until nav.childCount)(nav.getChildAt(i) as TextView).setTextColor(if(i==active)blue else muted)}
    private fun fmt(v:Double?)=v?.let{String.format(Locale("tr"),"%,.2f",it)}?:"Veri yetersiz"
    private fun num(v:Double?)=v?.let{String.format(Locale("tr"),"%.2f",it)}?:"Veri yetersiz"
    private fun fmt(t:Long)=if(t<=0)"—" else SimpleDateFormat("dd.MM.yyyy HH:mm",Locale("tr")).format(Date(t))
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
}
'''
MAIN.write_text(code, encoding='utf-8')

# Ensure version is V4.0.6 with a higher versionCode.
gradle = ROOT / 'app/build.gradle.kts'
if gradle.exists():
    s = gradle.read_text(encoding='utf-8')
    import re
    s = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 46', s)
    s = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "4.0.6"', s)
    gradle.write_text(s, encoding='utf-8')

manifest = ROOT / 'app/src/main/AndroidManifest.xml'
if manifest.exists():
    s=manifest.read_text(encoding='utf-8').replace('android:label="Borsa Takip V4"','android:label="BORSA TAKİP V4.0.6"')
    s=s.replace('.MainActivityV2','.MainActivityV4')
    manifest.write_text(s,encoding='utf-8')

print('V4.0.6 full 8-screen reference UI applied')
