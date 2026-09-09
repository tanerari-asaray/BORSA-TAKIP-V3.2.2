from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'app/src/main/java/tr/borsatakip/v3/MainActivityV4.kt'
GRADLE = ROOT / 'app/build.gradle.kts'

s = MAIN.read_text(encoding='utf-8')
if 'import android.graphics.drawable.GradientDrawable' not in s:
    s = s.replace('import android.graphics.Typeface', 'import android.graphics.Typeface\nimport android.graphics.drawable.GradientDrawable')

# Rounded, high-contrast reference panels.
anchor = '    private fun badge(text:String, color:Int=blue): TextView = TextView(this).apply {'
helper = '''    private fun panel(fill:Int, stroke:Int=border, radius:Float=18f): GradientDrawable = GradientDrawable().apply {\n        setColor(fill)\n        cornerRadius = dp(radius.toInt()).toFloat()\n        setStroke(dp(1), stroke)\n    }\n\n'''
if helper not in s:
    s = s.replace(anchor, helper + anchor)

s = s.replace('setBackgroundColor(Color.rgb(8,36,58))', 'background = panel(Color.rgb(8,36,58), color, 14f)')
s = s.replace('setBackgroundColor(surface)}', 'background = panel(surface, border, 18f)}')
s = s.replace('c.setBackgroundColor(Color.rgb(5,35,55));', 'c.background = panel(Color.rgb(5,35,55), color, 18f);')
s = s.replace('c.setBackgroundColor(Color.rgb(5,35,55))', 'c.background = panel(Color.rgb(5,35,55), color, 18f)')
s = s.replace('setBackgroundColor(Color.rgb(2,18,31))', 'background = panel(Color.rgb(2,18,31), Color.rgb(15,50,72), 0f)')
s = s.replace('setBackgroundColor(bg)', 'setBackgroundColor(bg)')

# Rounded CTA buttons while retaining the existing reference colors.
s = s.replace('setBackgroundColor(if(viop)purple else blue);setOnClickListener', 'background = panel(if(viop)purple else blue, if(viop)purple else blue, 14f);setOnClickListener')

# More polished home header/status wording.
s = s.replace('header("BORSA TAKİP V4","BIST • VİOP • FIRSAT",blue,false)', 'header("BORSA TAKİP V4.0.7","BIST  •  VİOP  •  FIRSAT",blue,false)')
s = s.replace('"VERİ KAYNAĞI DURUMU"', '"VERİ KAYNAĞI DURUMU"')

# Use the current release identity.
s = s.replace('V4.0.6', 'V4.0.7')
MAIN.write_text(s, encoding='utf-8')

g = GRADLE.read_text(encoding='utf-8')
g = g.replace('versionCode = 45', 'versionCode = 47')
g = g.replace('versionName = "4.0.5"', 'versionName = "4.0.7"')
GRADLE.write_text(g, encoding='utf-8')
print('V4.0.7 UI polish applied')
