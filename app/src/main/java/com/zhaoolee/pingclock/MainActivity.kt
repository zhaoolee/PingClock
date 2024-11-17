package com.zhaoolee.pingclock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.io.IOException
import java.net.InetAddress
import androidx.compose.foundation.Canvas  // 添加这行
import androidx.compose.ui.graphics.Path   // 添加这行
import androidx.compose.ui.graphics.Color  // 添加这行
import androidx.compose.ui.graphics.drawscope.Stroke  // 添加这行

import android.graphics.Paint

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.PathEffect

data class PingRecord(
    val pingTime: Long?,
    val timestamp: Long
)

class PingViewModel : ViewModel() {
    private var pingJob: Job? = null
    private var _pingResult = mutableStateOf<Long?>(null)
    val pingResult: State<Long?> = _pingResult

    private val _pingHistory = mutableStateListOf<PingRecord>()
    val pingHistory: List<PingRecord> = _pingHistory

    private var _isRunning = mutableStateOf(false)
    val isRunning: State<Boolean> = _isRunning

    fun startPinging(host: String, interval: Long) {
        if (pingJob?.isActive == true) return

        _isRunning.value = true
//        val pingTime = System.currentTimeMillis() - startTime
//        _pingResult.value = if (reachable) pingTime else null
//        // 添加这两行
//        _pingHistory.add(PingRecord(if (reachable) pingTime else null, System.currentTimeMillis()))
//        if (_pingHistory.size > 100) _pingHistory.removeAt(0)

        pingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val startTime = System.currentTimeMillis()
                    val reachable = withContext(Dispatchers.IO) {
                        InetAddress.getByName(host).isReachable(5000)
                    }
                    val pingTime = System.currentTimeMillis() - startTime
                    val currentPingTime = if (reachable) pingTime else null
                    _pingResult.value = currentPingTime
                    // 添加这两行到正确的位置
                    _pingHistory.add(PingRecord(currentPingTime, System.currentTimeMillis()))
                    if (_pingHistory.size > 100) _pingHistory.removeAt(0)
                } catch (e: IOException) {
                    _pingResult.value = null
                    // 添加这一行
                    _pingHistory.add(PingRecord(null, System.currentTimeMillis()))
                }
                delay(interval)
            }
        }
    }

    fun stopPinging() {
        pingJob?.cancel()
        _isRunning.value = false
        _pingResult.value = null
        _pingHistory.clear()
    }

    override fun onCleared() {
        super.onCleared()
        pingJob?.cancel()
    }
}

@Composable
fun PingHistoryChart(
    history: List<PingRecord>,
    modifier: Modifier = Modifier
) {
    val textPaint = Paint().apply {
        textSize = 35f
        color = android.graphics.Color.GRAY
        textAlign = Paint.Align.RIGHT
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(start = 40.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)
    ) {
        if (history.isEmpty()) return@Canvas

        val validPings = history.mapNotNull { it.pingTime }
        if (validPings.isEmpty()) return@Canvas

        val maxPing = (validPings.maxOrNull() ?: 0L).coerceAtLeast(100L)  // 至少显示到100ms
        val width = size.width
        val height = size.height
        val xStep = width / (history.size - 1)

        // 绘制网格线和Y轴刻度
        val yAxisSteps = 5
        val msStep = maxPing / yAxisSteps
        (0..yAxisSteps).forEach { i ->
            val y = height * (1 - i.toFloat() / yAxisSteps)

            // 绘制水平网格线
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )

            // 绘制Y轴刻度值
            drawContext.canvas.nativeCanvas.drawText(
                "${(maxPing * i / yAxisSteps).toInt()}ms",
                -8f,
                y + textPaint.textSize/3,
                textPaint
            )
        }

        // 创建渐变填充
        val gradientPath = Path()
        history.mapNotNull { it.pingTime }.forEachIndexed { index, ping ->
            val x = index * xStep
            val y = height * (1 - ping.toFloat() / maxPing)
            if (index == 0) {
                gradientPath.moveTo(x, y)
            } else {
                gradientPath.lineTo(x, y)
            }
        }
        // 封闭路径以便填充
        gradientPath.lineTo(width, height)
        gradientPath.lineTo(0f, height)
        gradientPath.close()

        // 绘制渐变填充
        drawPath(
            path = gradientPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF2196F3).copy(alpha = 0.3f),
                    Color(0xFF2196F3).copy(alpha = 0.1f)
                ),
                startY = 0f,
                endY = height
            )
        )

        // 绘制数据点
        history.mapNotNull { it.pingTime }.forEachIndexed { index, ping ->
            val x = index * xStep
            val y = height * (1 - ping.toFloat() / maxPing)

            // 绘制点
            drawCircle(
                color = Color(0xFF2196F3),
                radius = 4f,
                center = Offset(x, y)
            )
        }

        // 绘制折线
        val strokePath = Path()
        history.mapNotNull { it.pingTime }.forEachIndexed { index, ping ->
            val x = index * xStep
            val y = height * (1 - ping.toFloat() / maxPing)
            if (index == 0) {
                strokePath.moveTo(x, y)
            } else {
                strokePath.lineTo(x, y)
            }
        }

        drawPath(
            path = strokePath,
            color = Color(0xFF2196F3),
            style = Stroke(
                width = 3f,
                pathEffect = PathEffect.cornerPathEffect(10f)
            )
        )
    }
}

//@Composable
//fun PingHistoryChart(
//    history: List<PingRecord>,
//    modifier: Modifier = Modifier
//) {
//    Canvas(
//        modifier = modifier
//            .fillMaxWidth()
//            .height(200.dp)
//    ) {
//        if (history.isEmpty()) return@Canvas
//
//        val validPings = history.mapNotNull { it.pingTime }
//        if (validPings.isEmpty()) return@Canvas
//
//        val maxPing = validPings.maxOrNull() ?: return@Canvas
//        val width = size.width
//        val height = size.height
//        val xStep = width / (history.size - 1)
//
//        // 绘制网格线
//        val strokePath = Path()
//        history.mapNotNull { it.pingTime }.forEachIndexed { index, ping ->
//            val x = index * xStep
//            val y = height * (1 - ping.toFloat() / maxPing)
//            if (index == 0) {
//                strokePath.moveTo(x, y)
//            } else {
//                strokePath.lineTo(x, y)
//            }
//        }
//
//        // 绘制折线
//        drawPath(
//            path = strokePath,
//            color = Color.Blue,
//            style = Stroke(width = 3f)
//        )
//    }
//}


@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PingClockApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingClockApp(viewModel: PingViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    var host by remember { mutableStateOf("www.baidu.com") }
    var interval by remember { mutableStateOf("1000") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Host input
        TextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("域名") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Interval input
        TextField(
            value = interval,
            onValueChange = { interval = it },
            label = { Text("Ping间隔 (毫秒)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Start/Stop button
        Button(
            onClick = {
                if (viewModel.isRunning.value) {
                    viewModel.stopPinging()
                } else {
                    viewModel.startPinging(host, interval.toLongOrNull() ?: 1000)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (viewModel.isRunning.value) "停止" else "开始")
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Ping result display with animation
        val pingResult = viewModel.pingResult.value
        AnimatedContent(
            targetState = pingResult,
            label = "ping result"
        ) { result ->
            when (result) {
                null -> Text("等待ping响应...", style = MaterialTheme.typography.headlineMedium)
                else -> Text(
                    "$result ms",
                    style = MaterialTheme.typography.headlineMedium,
                    color = when {
                        result < 100 -> MaterialTheme.colorScheme.primary
                        result < 300 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }
        }

        // 显示ping历史图表
        PingHistoryChart(
            history = viewModel.pingHistory,
            modifier = Modifier.fillMaxWidth()
        )
    }
}