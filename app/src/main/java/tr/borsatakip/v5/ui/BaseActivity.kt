package tr.borsatakip.v5.ui

import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {
    protected fun setupBottomNav() {
        // V5 ekranlarının bazıları kendi navigasyonunu kullanır.
        // Layout'ta bottom navigation bulunmayan ekranlarda derleme/runtime hatası üretme.
    }
}
