from pathlib import Path

p = Path('app/src/main/java/tr/borsatakip/v3/MainActivity.kt')
s = p.read_text()

s = s.replace(
    'binding.watchCard.setOnClickListener { showInfo("Takip Listem", "Henüz takip edilen hisse bulunmuyor.") }',
    'binding.watchCard.setOnClickListener { showWatchlist() }'
)

s = s.replace(
    'private fun showContent(){binding.homeScroll.visibility=View.GONE;binding.settingsPanel.visibility=View.GONE;binding.contentPanel.visibility=View.VISIBLE;binding.backButton.visibility=View.VISIBLE}',
    'private fun showContent(){binding.homeScroll.visibility=View.GONE;binding.settingsPanel.visibility=View.GONE;binding.contentPanel.visibility=View.VISIBLE;binding.backButton.visibility=View.VISIBLE;binding.watchActionRow.visibility=View.GONE;binding.watchListContainer.visibility=View.GONE;binding.recyclerView.visibility=View.VISIBLE}'
)

marker = 'private fun showInfo(title:String,body:String){'
if 'private fun showWatchlist()' not in s:
    methods = r'''private fun showWatchlist(){
scanJob?.cancel();scanJob=null;restoreButtons();showContent();binding.titleText.text="Takip Listem";binding.subtitleText.text="İzlemek istediğiniz hisseleri burada yönetin";binding.progressBar.visibility=View.GONE;binding.recyclerView.visibility=View.GONE;binding.statusText.text="Hisseleri sembol ile ekleyin. Liste cihazda saklanır.";binding.watchActionRow.visibility=View.VISIBLE;binding.watchListContainer.visibility=View.VISIBLE;binding.addWatchButton.setOnClickListener{addWatchSymbol()};binding.clearWatchButton.setOnClickListener{clearWatchlist()};renderWatchlist();selectNav(3)
}
private fun renderWatchlist(){
val prefs=getSharedPreferences("watchlist",MODE_PRIVATE);val symbols=prefs.getStringSet("symbols",emptySet())?.toList()?.sorted()?:emptyList();binding.watchListContainer.removeAllViews();if(symbols.isEmpty()){val empty=android.widget.TextView(this);empty.text="Henüz takip edilen hisse bulunmuyor.\n\nÖrneğin: THYAO, ASELS, TUPRS";empty.setTextColor(Color.parseColor("#91A3B9"));empty.textSize=16f;empty.setPadding(16,24,16,24);binding.watchListContainer.addView(empty);return};symbols.forEach{symbol->val row=android.widget.LinearLayout(this);row.orientation=android.widget.LinearLayout.HORIZONTAL;row.gravity=android.view.Gravity.CENTER_VERTICAL;row.setPadding(12,10,8,10);val tv=android.widget.TextView(this);tv.text="★  $symbol";tv.textSize=18f;tv.setTextColor(Color.parseColor("#EAF3FF"));row.addView(tv,android.widget.LinearLayout.LayoutParams(0,android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,1f));val del=android.widget.Button(this);del.text="SİL";del.setOnClickListener{removeWatchSymbol(symbol)};row.addView(del);binding.watchListContainer.addView(row)}}
private fun addWatchSymbol(){val input=android.widget.EditText(this);input.hint="Hisse kodu (örn. THYAO)";input.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;android.app.AlertDialog.Builder(this).setTitle("Takip Listesine Hisse Ekle").setView(input).setNegativeButton("İPTAL",null).setPositiveButton("EKLE"){_,_->val symbol=input.text.toString().trim().uppercase(Locale("tr","TR"));if(symbol.isBlank())return@setPositiveButton;val prefs=getSharedPreferences("watchlist",MODE_PRIVATE);val set=prefs.getStringSet("symbols",emptySet())?.toMutableSet()?:mutableSetOf();set.add(symbol);prefs.edit().putStringSet("symbols",set).apply();renderWatchlist()}.show()}
private fun removeWatchSymbol(symbol:String){val prefs=getSharedPreferences("watchlist",MODE_PRIVATE);val set=prefs.getStringSet("symbols",emptySet())?.toMutableSet()?:mutableSetOf();set.remove(symbol);prefs.edit().putStringSet("symbols",set).apply();renderWatchlist()}
private fun clearWatchlist(){getSharedPreferences("watchlist",MODE_PRIVATE).edit().clear().apply();renderWatchlist()}
'''
    methods = methods.replace(chr(92) + '"', '"')
    if marker not in s:
        raise SystemExit('showInfo marker not found')
    s = s.replace(marker, methods + marker)

p.write_text(s)
