from pathlib import Path
import re

p = Path("app/src/main/java/tr/borsatakip/v3/MainActivityV2.kt")
text = p.read_text(encoding="utf-8")

# Yahoo fallback must not be polled every 15 seconds across the entire BIST universe.
# Keep periodic refresh only for a configured live API backend.
pattern = re.compile(r'    private fun startLiveRefresh\(\) \{.*?\n    \}\n\n    private fun showWatchlist', re.S)
replacement = '''    private fun startLiveRefresh() {
        liveJob?.cancel()
        liveJob = lifecycleScope.launch {
            while (true) {
                delay(30_000)
                val prefs = getSharedPreferences("market_api_settings", MODE_PRIVATE)
                val apiKey = prefs.getString("api_key", "")?.trim().orEmpty()
                val baseUrl = prefs.getString("base_url", BuildConfig.MARKET_API_BASE_URL)?.trim().orEmpty()
                // Yahoo fallback is scanned only when the user explicitly requests it.
                // Automatic refresh is reserved for a configured live backend.
                if (apiKey.isNotBlank() && baseUrl.startsWith("https://")) {
                    provider.loadMarket(Market.BIST).onSuccess {
                        lastBist = it.items
                        updateHome()
                    }
                }
            }
        }
    }

    private fun showWatchlist'''
new_text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise SystemExit("startLiveRefresh block not found in MainActivityV2.kt")
p.write_text(new_text, encoding="utf-8")
print("Controlled market refresh applied")
