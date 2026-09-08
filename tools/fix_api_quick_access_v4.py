from pathlib import Path

p = Path("app/src/main/java/tr/borsatakip/v3/MainActivityV2.kt")
text = p.read_text(encoding="utf-8")
listener = '        findViewById<View>(R.id.apiQuickCard).setOnClickListener { showSettings() }\n'
if 'R.id.apiQuickCard).setOnClickListener' not in text:
    anchor = '        findViewById<View>(R.id.navSettings).setOnClickListener { showSettings() }\n'
    if anchor not in text:
        raise SystemExit("navSettings listener anchor not found in MainActivityV2.kt")
    text = text.replace(anchor, anchor + listener, 1)
p.write_text(text, encoding="utf-8")
print("V4 API quick-access listener applied to MainActivityV2")
