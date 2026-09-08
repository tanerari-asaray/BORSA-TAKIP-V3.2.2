package tr.borsatakip.v3

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tr.borsatakip.v3.databinding.ActivityMainBinding
import tr.borsatakip.v3.market.AppMarketDataProvider
import tr.borsatakip.v3.market.Market
import tr.borsatakip.v3.market.MarketDataProvider
import tr.borsatakip.v3.model.Opportunity
import tr.borsatakip.v3.scoring.ScoringEngine
import tr.borsatakip.v3.ui.OpportunityAdapter
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {