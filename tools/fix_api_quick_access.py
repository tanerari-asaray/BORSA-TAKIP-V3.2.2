from pathlib import Path

p = Path("app/src/main/java/tr/borsatakip/v3/MainActivity.kt")
text = p.read_text(encoding="utf-8")
marker = ' val apiPrefs=getSharedPreferences("market_api_settings",MODE_PRIVATE); val apiKey=apiPrefs.getString("api_key","")?.trim().orEmpty(); binding.apiQuickStatus.text=if(apiKey.isBlank()) "🔴 Veri sağlayıcı bağlı değil • API Key girilmedi" else "🟡 API bilgileri kayıtlı • Bağlantıyı test edin";'
if marker in text:
    text = text.replace(marker, '', 1)
    target = 'selectNav(0) }'
    replacement = 'selectNav(0); val apiPrefs=getSharedPreferences("market_api_settings",MODE_PRIVATE); val apiKey=apiPrefs.getString("api_key","")?.trim().orEmpty(); binding.apiQuickStatus.text=if(apiKey.isBlank()) "🔴 Veri sağlayıcı bağlı değil • API Key girilmedi" else "🟡 API bilgileri kayıtlı • Bağlantıyı test edin" }'
    if target not in text:
        raise SystemExit("showHome insertion point not found")
    text = text.replace(target, replacement, 1)
p.write_text(text, encoding="utf-8")
print("API quick access syntax normalized")
