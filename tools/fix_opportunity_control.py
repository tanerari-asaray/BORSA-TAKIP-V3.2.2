from pathlib import Path
p=Path('app/src/main/java/tr/borsatakip/v3/MainActivity.kt')
s=p.read_text(encoding='utf-8')
old='''    private fun startCombinedOpportunityScan() {
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
'''
new='''    private fun startCombinedOpportunityScan() {
        if (scanJob?.isActive == true) return
        beginScanUi(ScanButton.OPPORTUNITY,"FIRSAT KONTROLÜ"); selectNav(1)
        scanJob=lifecycleScope.launch {
            binding.statusText.text="BIST VERİLERİ ALINIYOR..."; binding.progressBar.progress=15
            try {
                val b=provider.loadMarket(Market.BIST).getOrElse{throw it}
                binding.statusText.text="BIST FIRSATLARI TARANIYOR..."; binding.progressBar.progress=50
                val rows=b.items.mapNotNull(scoring::score); binding.progressBar.progress=80
                val visible=rows.filter{it.direction!="SİNYAL YOK"&&it.score>=70}.sortedByDescending{it.score}
                finishSuccess(visible,b.dataTimestamp,"${b.sourceName} • Fırsat Kontrolü",false)
            } catch(t:Throwable){finishFailure(t)}
        }
    }
'''
if old not in s: raise SystemExit('combined opportunity block not found')
p.write_text(s.replace(old,new),encoding='utf-8')
