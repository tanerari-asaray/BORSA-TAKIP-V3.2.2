from pathlib import Path
p = Path(__file__).resolve().parents[1] / 'app/src/main/java/tr/borsatakip/v3/MainActivityV4.kt'
s = p.read_text(encoding='utf-8')
s = s.replace(';padding(20)', ';setPadding(dp(20),dp(20),dp(20),dp(20))')
p.write_text(s, encoding='utf-8')
print('V4.0.6 compile cleanup applied')
