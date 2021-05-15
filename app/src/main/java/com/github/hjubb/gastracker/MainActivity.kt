package com.github.hjubb.gastracker

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.os.Bundle
import android.text.format.DateUtils
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import com.github.hjubb.gastracker.MainApplication.Companion.DEFAULT_GAS
import com.github.hjubb.gastracker.MainApplication.Companion.MAX_GAS
import com.github.hjubb.gastracker.MainApplication.Companion.gasPrice
import com.github.hjubb.gastracker.MainApplication.Companion.lastGas
import com.github.hjubb.gastracker.MainApplication.Companion.lastUpdate
import com.github.hjubb.gastracker.MainApplication.Companion.notificationsEnabled
import com.github.hjubb.gastracker.MainApplication.Companion.prefs
import com.github.hjubb.gastracker.MainApplication.Companion.recentGasValues
import com.github.hjubb.gastracker.MainApplication.Companion.setGasPrice
import com.github.hjubb.gastracker.MainApplication.Companion.setIsNotifEnabled
import com.github.hjubb.gastracker.ui.theme.GasTrackerTheme
import com.majorik.sparklinelibrary.SparkLineLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dataPrefs = prefs.data.map {
            AppState(
                notifsEnabled = it.notificationsEnabled(),
                gasPrice = it.gasPrice(),
                lastGas = it.lastGas(),
                lastUpdate = it.lastUpdate(),
                recentGasValues = it.recentGasValues()
            )
        }
        setContent {
            val timer by produceState(initialValue = System.currentTimeMillis()) {
                while (isActive) {
                    delay(10_000)
                    value = System.currentTimeMillis()
                }
            }
            AppScaffold(dataPrefs, timer)
        }
    }

}

data class AppState(
    val notifsEnabled: Boolean,
    val gasPrice: Int,
    val lastGas: Int,
    val lastUpdate: Long,
    val recentGasValues: List<Int>
)

@Composable
fun GasChart(recentGasValues: List<Int>) {

    AndroidView(modifier = Modifier
        .height(150.dp)
        .padding(bottom = 20.dp)
        .fillMaxWidth()
        .padding(horizontal = 50.dp), factory = { context ->
        val chart = SparkLineLayout(context)
        chart.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        chart.sparkLineThickness = 3f * Density(context).density
        chart.sparkLineBezier = 0.1f
        chart.sparkLineColor = context.getColor(R.color.primary)
        chart
    }, update = { chart ->
        chart.setData(arrayListOf(*recentGasValues.toTypedArray()))
        chart.invalidate()
    })
}

@Composable
fun AppScaffold(dataFlow: Flow<AppState>, ticker: Long) =
    GasTrackerTheme {

        val scope = rememberCoroutineScope()

        val isNotifOn by remember { dataFlow }
            .map { it.notifsEnabled }
            .collectAsState(initial = true)

        val gasPrice by remember { dataFlow }
            .map { it.gasPrice }
            .collectAsState(initial = DEFAULT_GAS)

        val lastGas by remember { dataFlow }
            .map { it.lastGas }
            .collectAsState(initial = 0)

        val lastUpdate by remember { dataFlow }
            .map { it.lastUpdate }
            .collectAsState(initial = 0)

        val recentGasValues by remember { dataFlow.map { it.recentGasValues } }
            .collectAsState(initial = List(50) { 0 })

        val context = LocalContext.current

        val lastUpdateTime = DateUtils.getRelativeDateTimeString(
            context,
            lastUpdate,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.HOUR_IN_MILLIS,
            0
        )

        fun setGasPrice(value: Int) {
            scope.launch {
                context.prefs.edit { prefs ->
                    prefs.setGasPrice(value)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GasChart(recentGasValues = recentGasValues)
            Text(
                color = MaterialTheme.colors.onSurface,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                text = "${lastGas}gw",
            )
            Text(
                color = MaterialTheme.colors.onSurface,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                text = "$lastUpdateTime",
                modifier = Modifier.padding(bottom = 32.dp)
            )
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .size(72.dp)
                    .background(
                        color = if (isNotifOn) MaterialTheme.colors.primaryVariant
                        else Color.Gray,
                        shape = RoundedCornerShape(50)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_dispenser),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(12.dp)
                        .clickable {
                            scope.launch {
                                context.prefs.edit { prefs ->
                                    prefs.setIsNotifEnabled(!isNotifOn)
                                }
                            }
                        }
                )
            }
            Slider(
                value = gasPrice.toFloat(),
                valueRange = 0f..MAX_GAS,
                steps = 100,
                onValueChange = { setGasPrice(it.toInt()) },
                modifier = Modifier.padding(horizontal = 64.dp)
            )
            Text(
                color = MaterialTheme.colors.onSurface,
                fontSize = 32.sp,
                text = "${gasPrice}gw"
            )
        }
    }

@Preview(showBackground = true, showSystemUi = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun DefaultPreview() {

    val timer by produceState(initialValue = System.currentTimeMillis()) {
        while (isActive) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }

    AppScaffold(
        flowOf(
            AppState(
                true,
                70,
                500,
                System.currentTimeMillis(),
                List(50) { 0 }
            )
        ),
        timer
    )
}
