from pathlib import Path

layout = Path("app/src/main/res/layout/activity_main.xml")
text = layout.read_text(encoding="utf-8")
anchor = '                <Button android:id="@+id/btnBist"'
if 'android:id="@+id/apiQuickCard"' not in text:
    card = '''                <LinearLayout android:id="@+id/apiQuickCard" android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical" android:background="@drawable/bg_surface" android:padding="14dp" android:layout_marginBottom="8dp" android:clickable="true" android:focusable="true">
                    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:gravity="center_vertical">
                        <TextView android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:text="⚙  API AYARLARI" android:textStyle="bold" android:textSize="16sp" android:textColor="@color/text_primary"/>
                        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="AÇ ›" android:textStyle="bold" android:textSize="12sp" android:textColor="@color/primary"/>
                    </LinearLayout>
                    <TextView android:id="@+id/apiQuickStatus" android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="4dp" android:text="🔴 Veri sağlayıcı bağlı değil • API Key girilmedi" android:textColor="@color/text_secondary" android:textSize="12sp"/>
                    <TextView android:layout_width="match_parent" android:layout_height="wrap_content" android:layout_marginTop="3dp" android:text="BIST + VİOP gerçek veri bağlantısını buradan yapılandırın." android:textColor="@color/text_muted" android:textSize="11sp"/>
                </LinearLayout>
'''
    if anchor not in text:
        raise SystemExit("BIST button anchor not found")
    text = text.replace(anchor, card + anchor, 1)
layout.write_text(text, encoding="utf-8")

main = Path("app/src/main/java/tr/borsatakip/v3/MainActivity.kt")
text = main.read_text(encoding="utf-8")
listener = '        binding.apiQuickCard.setOnClickListener { showSettings() }\n'
if 'binding.apiQuickCard.setOnClickListener' not in text:
    anchor = '        binding.navSettings.setOnClickListener { showSettings() }\n'
    if anchor not in text:
        raise SystemExit("navSettings listener anchor not found")
    text = text.replace(anchor, anchor + listener, 1)
main.write_text(text, encoding="utf-8")

# Synchronize the visible home status with saved API settings.
needle = '    private fun showHome() {'
if 'binding.apiQuickStatus.text' not in text:
    start = text.find(needle)
    if start == -1:
        raise SystemExit("showHome not found")
    end = text.find('\n', start)
    if end == -1:
        raise SystemExit("showHome line not found")
    suffix = ' val apiPrefs=getSharedPreferences("market_api_settings",MODE_PRIVATE); val apiKey=apiPrefs.getString("api_key","")?.trim().orEmpty(); binding.apiQuickStatus.text=if(apiKey.isBlank()) "🔴 Veri sağlayıcı bağlı değil • API Key girilmedi" else "🟡 API bilgileri kayıtlı • Bağlantıyı test edin";'
    text = text[:end] + suffix + text[end:]
main.write_text(text, encoding="utf-8")

print("API quick access applied")
