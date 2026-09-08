from pathlib import Path
import re

main = Path('app/src/main/java/tr/borsatakip/v3/MainActivity.kt')
s = main.read_text(encoding='utf-8')

# V4: keep previous results visible while a new request is running.
s = s.replace('private fun beginScanUi(button:ScanButton,title:String){adapter.submitList(emptyList());showContent();', 'private fun beginScanUi(button:ScanButton,title:String){showContent();')
# V4: never blank the result list on a transient network failure.
s = s.replace('private fun finishFailure(error:Throwable){adapter.submitList(emptyList());binding.progressBar.progress=0;', 'private fun finishFailure(error:Throwable){binding.progressBar.progress=0;')
# V4: score on Default dispatcher so JSON/network work is not followed by heavy UI-thread analysis.
s = s.replace('import kotlinx.coroutines.Job\n', 'import kotlinx.coroutines.Job\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.withContext\n')
s = s.replace('val all=result.items.mapNotNull(scoring::score); binding.statusText.text="ANALİZ EDİLİYOR...";', 'val all=withContext(Dispatchers.Default){result.items.mapNotNull(scoring::score)}; binding.statusText.text="ANALİZ EDİLİYOR...";')
s = s.replace('val rows=b.items.mapNotNull(scoring::score); binding.progressBar.progress=80', 'val rows=withContext(Dispatchers.Default){b.items.mapNotNull(scoring::score)}; binding.progressBar.progress=80')
# V4: BIST and VIOP requests run concurrently for the combined scan.
start='    private fun startCombinedOpportunityScan() {'
end='    private fun beginScanUi(button:ScanButton,title:String)'
i=s.find(start); j=s.find(end,i)
if i!=-1 and j!=-1:
    block='''    private fun startCombinedOpportunityScan() {\n        if (scanJob?.isActive == true) return\n        beginScanUi(ScanButton.OPPORTUNITY,"FIRSAT KONTROLÜ"); selectNav(1)\n        scanJob=lifecycleScope.launch {\n            binding.statusText.text="BIST + VİOP VERİLERİ AYNI ANDA ALINIYOR..."; binding.progressBar.progress=10\n            try {\n                val (br,vr)=coroutineScope { async { provider.loadMarket(Market.BIST) } to async { provider.loadMarket(Market.VIOP) } }\n                val b=br.await().getOrElse{throw it}; binding.progressBar.progress=50\n                val v=vr.await().getOrElse{throw it}; binding.progressBar.progress=65\n                binding.statusText.text="BIST + VİOP ANALİZ EDİLİYOR..."\n                val rows=withContext(Dispatchers.Default){(b.items+v.items).mapNotNull(scoring::score)}\n                val visible=rows.filter{it.direction!="SİNYAL YOK"&&it.score>=70}.sortedByDescending{it.score}\n                finishSuccess(visible,maxOf(b.dataTimestamp,v.dataTimestamp),listOf(b.sourceName,v.sourceName).distinct().joinToString(" + "),true)\n            } catch(t:Throwable){finishFailure(t)}\n        }\n    }\n\n'''
    s=s[:i]+block+s[j:]
else:
    raise SystemExit('combined scan anchors not found')

main.write_text(s,encoding='utf-8')

provider=Path('app/src/main/java/tr/borsatakip/v3/market/LiveBackendMarketDataProvider.kt')
p=provider.read_text(encoding='utf-8')
p=p.replace('connectTimeout = 10_000','connectTimeout = 5_000')
p=p.replace('readTimeout = 20_000','readTimeout = 8_000')
p=p.replace('setRequestProperty("Accept", "application/json")','setRequestProperty("Accept", "application/json")\n                setRequestProperty("Cache-Control", "no-cache")')
provider.write_text(p,encoding='utf-8')

# V4 version number.
gradle=Path('app/build.gradle.kts')
g=gradle.read_text(encoding='utf-8')
g=g.replace('versionCode = 10','versionCode = 40').replace('versionName = "3.2.10"','versionName = "4.0.0"')
gradle.write_text(g,encoding='utf-8')

readme=Path('README.md')
r=readme.read_text(encoding='utf-8')
r=r.replace('# Borsa Takip V3.2.8','# Borsa Takip V4.0.0').replace('API ayarları ve seçici Fırsat Kontrolü teknik motoru bulunan Android uygulaması.','Hızlı veri, paralel BIST/VİOP taraması ve performans odaklı Android uygulaması.')
readme.write_text(r,encoding='utf-8')
