from pathlib import Path
import re

p = Path('app/src/main/java/tr/borsatakip/v3/MainActivity.kt')
s = p.read_text()

new_method = r'''private fun addWatchSymbol(){
val symbols=listOf("AEFES","AKBNK","AKSEN","ALARK","ALBRK","ALFAS","ARCLK","ASELS","ASTOR","AYDEM","BIMAS","BRSAN","CCOLA","CIMSA","DOAS","DOHOL","ECILC","ECZYT","EKGYO","ENKAI","EREGL","EUPWR","FROTO","GARAN","GESAN","GUBRF","HALKB","HEKTS","ISCTR","ISDMR","ISMEN","KARSN","KCHOL","KONTR","KOZAA","KOZAL","KRDMD","MAVI","MGROS","MIATK","ODAS","OYAKC","PASEU","PETKM","PGSUS","PETUN","SAHOL","SASA","SISE","SKBNK","SMRTG","TAVHL","TCELL","THYAO","TKFEN","TOASO","TRALT","TTKOM","TTRAK","TUPRS","TURSG","ULKER","VAKBN","VESBE","VESTL","YKBNK","YYLGD")
val input=android.widget.AutoCompleteTextView(this)
input.hint="Hisse kodunu yazın veya listeden seçin"
input.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
input.threshold=0
input.setAdapter(android.widget.ArrayAdapter(this,android.R.layout.simple_dropdown_item_1line,symbols))
input.setOnFocusChangeListener{_,hasFocus->if(hasFocus){input.post{input.showDropDown()}}}
input.setOnClickListener{input.showDropDown()}
input.setOnItemClickListener{_,_,position,_->input.setText(input.adapter.getItem(position).toString());input.setSelection(input.text.length);input.dismissDropDown()}
val dialog=android.app.AlertDialog.Builder(this).setTitle("Takip Listesine Hisse Ekle").setMessage("Hisse kutusuna dokunduğunuzda kod listesi otomatik açılır. Yazdıkça liste anında filtrelenir.").setView(input).setNegativeButton("İPTAL",null).setPositiveButton("EKLE"){_,_->val symbol=input.text.toString().trim().uppercase(Locale("tr","TR"));if(symbol.isBlank())return@setPositiveButton;val prefs=getSharedPreferences("watchlist",MODE_PRIVATE);val set=prefs.getStringSet("symbols",emptySet())?.toMutableSet()?:mutableSetOf();set.add(symbol);prefs.edit().putStringSet("symbols",set).apply();renderWatchlist()}.create()
dialog.setOnShowListener{input.requestFocus();input.post{input.showDropDown()};dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)}
dialog.show()
}
'''
new_method = new_method.replace('\\"','"')
s = re.sub(r'private fun addWatchSymbol\(\)\{.*?\nprivate fun removeWatchSymbol', new_method + 'private fun removeWatchSymbol', s, count=1, flags=re.S)
if 'input.threshold=0' not in s:
    raise SystemExit('stock search replacement failed')
p.write_text(s)
