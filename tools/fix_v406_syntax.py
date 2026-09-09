from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/java/tr/borsatakip/v3/MainActivityV4.kt'
s = MAIN.read_text(encoding='utf-8')

if 'import androidx.appcompat.app.AlertDialog' not in s:
    s = s.replace('import androidx.appcompat.app.AppCompatActivity', 'import androidx.appcompat.app.AppCompatActivity\nimport androidx.appcompat.app.AlertDialog')

# Explicitly repair common generated Kotlin parser traps.
s = s.replace('gravity=Gravity.CENTER;padding(20)', 'gravity=Gravity.CENTER;setPadding(dp(20),dp(20),dp(20),dp(20))')
s = s.replace(';padding(20)', ';setPadding(dp(20),dp(20),dp(20),dp(20))')
s = s.replace('padding(20)', 'setPadding(dp(20),dp(20),dp(20),dp(20))')

# Make the home opportunity text an ordinary local value.
s = s.replace('val opp=card("💡  Günün Fırsatları",yellow)\n        opp.addView(TextView(this).apply{text=if(lastOpp.isEmpty())"Henüz tarama yapılmadı. Gerçek zamanlı veri olmadan fırsat sonucu üretilmez." else "${lastOpp.size} doğrulanmış fırsat bulundu.";textSize=12f;setTextColor(secondary);setPadding(0,dp(6),0,0)})', 'val opp=card("💡  Günün Fırsatları",yellow)\n        val oppText = if (lastOpp.isEmpty()) "Henüz tarama yapılmadı. Gerçek zamanlı veri olmadan fırsat sonucu üretilmez." else "${lastOpp.size} doğrulanmış fırsat bulundu."\n        opp.addView(TextView(this).apply { text=oppText; textSize=12f; setTextColor(secondary); setPadding(0,dp(6),0,0) })')

# Make scan-step manipulation parser-safe.
s = s.replace('steps.forEachIndexed{i,v->v.text="◌  ${v.text.substringAfter("  ").substringBefore("   ")}   Çalışıyor";v.setTextColor(blue)}', 'steps.forEachIndexed { _, v ->\n                val base = v.text.toString().substringBefore("   ").removePrefix("○  ")\n                v.text = "◌  $base   Çalışıyor"\n                v.setTextColor(blue)\n            }')
s = s.replace('steps.forEach{it.text=it.text.substringBefore("   ")+"   Tamamlandı";it.setTextColor(green)}', 'steps.forEach { it ->\n                        it.text = it.text.toString().substringBefore("   ") + "   Tamamlandı"\n                        it.setTextColor(green)\n                    }')

# Replace the result empty-state one-liner if present.
needle = 'body.addView(TextView(this@MainActivityV4).apply{text="Veri yetersiz\\nSağlayıcıdan geçerli veri alınamadı.";textSize=15f;setTextColor(yellow);gravity=Gravity.CENTER;padding(20)})'
if needle in s:
    s = s.replace(needle, 'body.addView(TextView(this@MainActivityV4).apply { text="Veri yetersiz\\nSağlayıcıdan geçerli veri alınamadı."; textSize=15f; setTextColor(yellow); gravity=Gravity.CENTER; setPadding(dp(20),dp(20),dp(20),dp(20)) })')

MAIN.write_text(s, encoding='utf-8')
print('V4.0.6 syntax fixes applied')
