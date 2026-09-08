from pathlib import Path
import re
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
out = ROOT / "app/src/main/java/tr/borsatakip/v3/market/BistUniverse.kt"
url = "https://www.getmidas.com/canli-borsa/tum-hisseler"

req = urllib.request.Request(url, headers={"User-Agent":"Mozilla/5.0 BorsaTakipV4/4.0.2"})
html = urllib.request.urlopen(req, timeout=30).read().decode("utf-8", "ignore")

# Midas'ın tüm hisseler tablosundaki bağlantı metinlerinden sembolleri çıkar.
# Yalnızca 3-7 karakterlik büyük harf/rakam sembollerini kabul et.
symbols = []
seen = set()
for m in re.finditer(r">([A-Z0-9]{3,7})</a>", html):
    x = m.group(1)
    if x not in seen:
        seen.add(x); symbols.append(x)

# Aşırı geniş eşleşmeleri dışarıda bırak; gerçek BIST sembolleri çoğunlukla 3-6 karakterdir.
symbols = [x for x in symbols if 3 <= len(x) <= 6]
if len(symbols) < 300:
    raise RuntimeError(f"BIST evreni alınamadı; yalnızca {len(symbols)} sembol bulundu")

body = "package tr.borsatakip.v3.market\n\n/** Build-time BIST equity universe. Refreshed from the current listed-equity table. */\nobject BistUniverse {\n    val symbols: List<String> = listOf(\n        " + ", ".join('"'+x+'"' for x in symbols) + "\n    )\n}\n"
out.write_text(body, encoding="utf-8")
print(f"Generated BIST universe: {len(symbols)} symbols")
