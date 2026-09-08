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

# The release workflow promotes MainActivityV2 to MainActivityV4 after this step.
# Patch both possible activity sources so the API card remains clickable in V4.
for activity_path in [
    Path("app/src/main/java/tr/borsatakip/v3/MainActivity.kt"),
    Path("app/src/main/java/tr/borsatakip/v3/MainActivityV2.kt"),
]:
    if not activity_path.exists():
        continue
    text = activity_path.read_text(encoding="utf-8")
    listener = '        findViewById<View>(R.id.apiQuickCard).setOnClickListener { showSettings() }\n'
    if 'apiQuickCard).setOnClickListener' not in text:
        anchor = '        findViewById<View>(R.id.navSettings).setOnClickListener { showSettings() }\n'
        if anchor in text:
            text = text.replace(anchor, anchor + listener, 1)
        elif 'binding.navSettings.setOnClickListener { showSettings() }' in text:
            text = text.replace('        binding.navSettings.setOnClickListener { showSettings() }\n', '        binding.navSettings.setOnClickListener { showSettings() }\n        binding.apiQuickCard.setOnClickListener { showSettings() }\n', 1)
        else:
            raise SystemExit(f"navSettings listener anchor not found in {activity_path}")

    # Synchronize the visible home status with saved API settings when this activity has the card.
    if 'apiQuickStatus' in text and 'apiQuickStatus.text' not in text:
        needle = '    private fun showHome() {'
        start = text.find(needle)
        if start != -1:
            end = text.find('\n', start)
            if end != -1:
                suffix = ' val apiPrefs=getSharedPreferences("market_api_settings",MODE_PRIVATE); val apiKey=apiPrefs.getString("api_key","")?.trim().orEmpty(); findViewById<TextView>(R.id.apiQuickStatus).text=if(apiKey.isBlank()) "🔴 Veri sağlayıcı bağlı değil • API Key girilmedi" else "🟡 API bilgileri kayıtlı • Bağlantıyı test edin";'
                text = text[:end] + suffix + text[end:]
    activity_path.write_text(text, encoding="utf-8")

print("API quick access applied to all activity sources")
