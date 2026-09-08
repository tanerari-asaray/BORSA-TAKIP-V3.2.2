from pathlib import Path
import re

p = Path('app/src/main/java/tr/borsatakip/v3/MainActivity.kt')
s = p.read_text(encoding='utf-8')

# Keep exactly one coroutine import for each symbol after all patch scripts have run.
s = re.sub(r'(?m)^import kotlinx\.coroutines\.Dispatchers\r?\n', '', s)
s = re.sub(r'(?m)^import kotlinx\.coroutines\.withContext\r?\n', '', s)
s = s.replace('import kotlinx.coroutines.Job\n', 'import kotlinx.coroutines.Job\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.withContext\n', 1)
p.write_text(s, encoding='utf-8')
