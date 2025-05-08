package com.example.cryptofilterapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.cryptofilterapp.ui.theme.CryptoFilterAppTheme
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

enum class SortState {
    ASCENDING,
    DESCENDING,
    NONE
}

data class ColumnSort(
    val column: String,
    val state: SortState
)

class MainActivity : ComponentActivity() {
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        setContent {
            CryptoFilterAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FilterCoinScreen(coroutineScope)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}

data class CoinData(
    val symbol: String,
    val candle: String,
    val high3d: String,
    val low3d: String,
    val volume: String,
    val corrBtc: String
)

@Composable
fun FilterCoinScreen(
    coroutineScope: CoroutineScope
) {
    var isLoading by remember { mutableStateOf(false) }
    var coinData by remember { mutableStateOf<List<CoinData>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var buttonText by remember { mutableStateOf("Run Filter Coins") }
    var currentSort by remember { mutableStateOf<ColumnSort?>(null) }

    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("CryptoFilterPrefs", Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        val savedData = sharedPref.getString("saved_coins", null)
        if (savedData != null) {
            coinData = parseJsonResult(savedData)

            val savedTime = sharedPref.getString("last_update_time", null)
            if (savedTime != null) {
                buttonText = "Last run: $savedTime"
            }
        }
    }

    val sortedCoinData = remember(coinData, currentSort) {
        currentSort?.let { sort ->
            when (sort.state) {
                SortState.ASCENDING -> applySort(coinData, sort.column, ascending = true)
                SortState.DESCENDING -> applySort(coinData, sort.column, ascending = false)
                SortState.NONE -> coinData
            }
        } ?: coinData
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp))
            {
                Button(
                    onClick = {
                        if (isLoading) return@Button

                        isLoading = true
                        errorMessage = null

                        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        val currentTime = timeFormat.format(Date())
                        buttonText = "Running at $currentTime"

                        // Launch the Python execution in a background thread
                        coroutineScope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    try {
                                        val python = Python.getInstance()
                                        val module = python.getModule("filter_coins")
                                        module.callAttr("on_button_click").toString()
                                    } catch (e: Exception) {
                                        throw e
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    val json = JSONObject(result)
                                    when (json.getString("status")) {
                                        "success" -> {
                                            if (json.has("message")) {
                                                errorMessage = json.getString("message")
                                            } else {
                                                coinData = parseJsonResult(result)

                                                with(sharedPref.edit()) {
                                                    putString("saved_coins", result)
                                                    putString("last_update_time", currentTime)
                                                    apply()
                                                }

                                                buttonText = "Last run: $currentTime"
                                            }
                                        }
                                        "error" -> {
                                            errorMessage = json.getString("message")
                                            buttonText = "Run Filter Coins"
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    errorMessage = "Error: ${e.message}"
                                    buttonText = "Run Filter Coins"
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLoading) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.primary
                    ),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Processing...")
                    } else {
                        Text(buttonText)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (coinData.isNotEmpty()) {
                    val totalCoins = coinData.size
                    val greenCoins = coinData.count { it.candle.lowercase() == "green" }
                    val redCoins = totalCoins - greenCoins

                    // BTC Summary Row - Clickable
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable {
                                openTradingView(context, "BTC/USDT")
                            },
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "BTC ",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Total: $totalCoins  ",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Red: $redCoins  ",
                            color = Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Green: $greenCoins",
                            color = Color.Green,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    errorMessage != null -> {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    coinData.isNotEmpty() -> {
                        CoinTable(
                            coinData = sortedCoinData,
                            currentSort = currentSort,
                            onSortClicked = { column ->
                                currentSort = when {
                                    currentSort?.column == column -> {
                                        when (currentSort?.state) {
                                            SortState.ASCENDING -> ColumnSort(column, SortState.DESCENDING)
                                            SortState.DESCENDING -> null
                                            else -> ColumnSort(column, SortState.ASCENDING)
                                        }
                                    }
                                    else -> ColumnSort(column, SortState.ASCENDING)
                                }
                            },
                            onCoinClick = { symbol ->
                                openTradingView(context, symbol)
                            }
                        )
                    }

                    else -> {
                        Text(
                            text = "Click the button to fetch coins",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
}

private fun openTradingView(context: Context, symbol: String) {
    try {
        // Special case for BTC/USDT to open BITGET:BTCUSDT.P
        val tradingViewSymbol = if (symbol.equals("BTC/USDT", ignoreCase = true)) {
            "BITGET:BTCUSDT.P"
        } else {
            // Convert other symbols to TradingView format
            "BITGET:${symbol.replace("/", "")}.P"
        }

        // First try to open the TradingView app
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("tradingview://chart?symbol=$tradingViewSymbol")
                `package` = "com.tradingview.tradingviewapp"
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // If app not installed, open in browser
            val webIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.tradingview.com/chart/?symbol=$tradingViewSymbol")
            }
            context.startActivity(webIntent)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error opening TradingView", Toast.LENGTH_SHORT).show()
    }
}

private fun applySort(data: List<CoinData>, column: String, ascending: Boolean): List<CoinData> {
    return when (column) {
        "Symbol" -> data.sortedBy { it.symbol }
        "Candle" -> data.sortedBy { it.candle }
        "3D High%" -> data.sortedBy { it.high3d.toFloatOrNull() ?: 0f }
        "3D Low%" -> data.sortedBy { it.low3d.toFloatOrNull() ?: 0f }
        "Volume" -> data.sortedBy { it.volume.toIntOrNull() ?: 0 }
        "BTC Corr" -> data.sortedBy { it.corrBtc.toFloatOrNull() ?: 0f }
        else -> data
    }.let { if (ascending) it else it.reversed() }
}

fun parseJsonResult(result: String): List<CoinData> {
    val json = JSONObject(result)
    val dataArray = json.optJSONArray("data") ?: return emptyList()
    val coins = mutableListOf<CoinData>()

    for (i in 0 until dataArray.length()) {
        val item = dataArray.getJSONObject(i)
        coins.add(
            CoinData(
                symbol = item.getString("symbol"),
                candle = item.getString("candle"),
                high3d = item.getDouble("high3d").toString(),
                low3d = item.getDouble("low3d").toString(),
                volume = item.getInt("volume").toString(),
                corrBtc = item.getDouble("corrBtc").toString()
            )
        )
    }
    return coins
}

@Composable
fun CoinTable(
    coinData: List<CoinData>,
    currentSort: ColumnSort?,
    onSortClicked: (String) -> Unit,
    onCoinClick: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(8.dp)
        ) {
            listOf(
                "Symbol" to 2f,
                "Candle" to 1f,
                "3D High%" to 1f,
                "3D Low%" to 1f,
                "Volume" to 1f,
                "BTC Corr" to 1f
            ).forEach { (column, weight) ->
                val isCurrentColumn = currentSort?.column == column
                val sortState = if (isCurrentColumn) currentSort.state else SortState.NONE

                Box(
                    modifier = Modifier
                        .weight(weight)
                        .clickable { onSortClicked(column) }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = column,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isCurrentColumn) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropUp,
                                contentDescription = "Sort ascending",
                                modifier = Modifier.size(14.dp),
                                tint = if (sortState == SortState.ASCENDING) activeColor else inactiveColor
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Sort descending",
                                modifier = Modifier.size(14.dp),
                                tint = if (sortState == SortState.DESCENDING) activeColor else inactiveColor
                            )
                        }
                    }
                }
            }
        }

        // Data Rows
        coinData.forEach { coin ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, Color.LightGray)
            ) {
                // Clickable Symbol Cell
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .clickable { onCoinClick(coin.symbol) }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = coin.symbol,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                // Other cells
                TableCell(text = coin.candle, weight = 1f,
                    color = if (coin.candle.lowercase() == "green") Color.Green else Color.Red)
                TableCell(text = coin.high3d, weight = 1f)
                TableCell(text = coin.low3d, weight = 1f)
                TableCell(text = coin.volume, weight = 1f)
                TableCell(text = coin.corrBtc, weight = 1f)
            }
        }
    }
}

@Composable
fun RowScope.TableCell(
    text: String,
    weight: Float,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = text,
        modifier = Modifier
            .weight(weight)
            .padding(8.dp),
        fontSize = 12.sp,
        color = color,
        textAlign = TextAlign.Center
    )
}