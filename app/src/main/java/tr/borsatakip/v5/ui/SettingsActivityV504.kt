package tr.borsatakip.v5.ui

import android.os.Bundle
import android.widget.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import tr.borsatakip.v5.R
import tr.borsatakip.v5.data.BackendProvider
import tr.borsatakip.v5.data.SettingsStore

class SettingsActivityV504 : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setupBottomNav()
        val s = SettingsStore(this)
        val viopUrl = findViewById<EditText>(R.id.viopBaseUrl)
        val apiKey = findViewById<EditText>(R.id.apiKey)
        val status = findViewById<TextView>(R.id.viopStatus)
        findViewById<Button>(R.id.saveSettings).setOnClickListener {
            s.viopBaseUrl = viopUrl.text.toString()
            s.apiKey = apiKey.text.toString()
            status.text = "Ayarlar kaydedildi."
        }
        findViewById<Button>(R.id.testViop).setOnClickListener {
            s.viopBaseUrl = viopUrl.text.toString()
            s.apiKey = apiKey.text.toString()
            status.text = "Bağlantı test ediliyor..."
            lifecycleScope.launch {
                BackendProvider(this@SettingsActivityV504).testViopConnection()
                    .onSuccess { status.text = "VİOP bağlantısı başarılı." }
                    .onFailure { status.text = "VİOP bağlantı hatası: ${it.message ?: "Bilinmeyen hata"}" }
            }
        }
    }
}
