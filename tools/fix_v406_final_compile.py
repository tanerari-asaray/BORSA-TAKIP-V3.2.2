from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/java/tr/borsatakip/v3/MainActivityV4.kt'
s = MAIN.read_text(encoding='utf-8')

# Kotlin parser is stricter around inline if expressions inside apply blocks.
s = re.sub(r'text=if\(viop\)("[^"]*") else ("[^"]*")', r'text=(if (viop) \1 else \2)', s)
s = re.sub(r'setBackgroundColor\(if\(viop\)([^ ]+) else ([^)]+)\)', r'setBackgroundColor(if (viop) \1 else \2)', s)

# Generated source accidentally used Compose-style padding in one TextView.
s = re.sub(r'(?<!set)padding\(20\)', 'setPadding(dp(20),dp(20),dp(20),dp(20))', s)

MAIN.write_text(s, encoding='utf-8')
print('V4.0.6 final compile fixes applied')
