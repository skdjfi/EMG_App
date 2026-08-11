package com.knee.emgapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.knee.emgapp.model.RollingBuffer
import com.knee.emgapp.network.TcpClient
import com.knee.emgapp.protocol.EmgFrame
import com.knee.emgapp.protocol.EmgProtocol
import com.knee.emgapp.protocol.FrameListener
import com.knee.emgapp.protocol.FrameParser
import com.knee.emgapp.protocol.ImuFrame

class MainActivity : AppCompatActivity(), FrameListener {

    companion object {
        private const val MAX_POINTS = 500          // 每通道波形点数
        private const val UI_REFRESH_MS = 50L       // 界面刷新周期(20FPS)

        /* 增益档位(与 STM32 端 ads1194.h 一致) */
        private val GAINS = intArrayOf(1, 2, 3, 4, 6, 8, 12)
        /* 采样率档位(与 STM32 端 ads1194.h 一致) */
        private val SPS = intArrayOf(125, 250, 500, 1000, 2000, 4000, 8000)
    }

    private lateinit var editHost: EditText
    private lateinit var editPort: EditText
    private lateinit var btnConnect: Button
    private lateinit var txtStatus: TextView
    private lateinit var txtInfo: TextView
    private lateinit var txtImu: TextView
    private lateinit var txtDebug: TextView

    private lateinit var chartCh1: LineChart
    private lateinit var chartCh2: LineChart
    private lateinit var chartCh3: LineChart
    private lateinit var chartCh4: LineChart
    private lateinit var chartImu: LineChart

    private val emgBuf = Array(4) { RollingBuffer(MAX_POINTS) }
    private val imuAcc = RollingBuffer(MAX_POINTS)
    private val imuGyr = RollingBuffer(MAX_POINTS)

    private var tcp: TcpClient? = null
    private var parser: FrameParser? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    private var gainIdx = 2      // 默认 x6(索引 2)
    private var spsIdx = 2       // 默认 500SPS(索引 2)
    private var imuOn = true

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateCharts()
            uiHandler.postDelayed(this, UI_REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupCharts()
        setupButtons()

        // 周期刷新波形
        uiHandler.post(refreshRunnable)
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(refreshRunnable)
        tcp?.stop()
        super.onDestroy()
    }

    /* ============================ 视图绑定 ============================ */

    private fun bindViews() {
        editHost = findViewById(R.id.editHost)
        editPort = findViewById(R.id.editPort)
        btnConnect = findViewById(R.id.btnConnect)
        txtStatus = findViewById(R.id.txtStatus)
        txtInfo = findViewById(R.id.txtInfo)
        txtImu = findViewById(R.id.txtImu)
        txtDebug = findViewById(R.id.txtDebug)

        chartCh1 = findViewById(R.id.chartCh1)
        chartCh2 = findViewById(R.id.chartCh2)
        chartCh3 = findViewById(R.id.chartCh3)
        chartCh4 = findViewById(R.id.chartCh4)
        chartImu = findViewById(R.id.chartImu)
    }

    private fun setupButtons() {
        btnConnect.setOnClickListener { toggleConnect() }
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            sendCmd(EmgProtocol.CMD_START, 0)
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            sendCmd(EmgProtocol.CMD_STOP, 0)
        }
        findViewById<Button>(R.id.btnGainMinus).setOnClickListener { changeGain(-1) }
        findViewById<Button>(R.id.btnGainPlus).setOnClickListener { changeGain(1) }
        findViewById<Button>(R.id.btnSpsMinus).setOnClickListener { changeSps(-1) }
        findViewById<Button>(R.id.btnSpsPlus).setOnClickListener { changeSps(1) }
        findViewById<Button>(R.id.btnImuOn).setOnClickListener { setImu(true) }
        findViewById<Button>(R.id.btnImuOff).setOnClickListener { setImu(false) }
        findViewById<Button>(R.id.btnGetInfo).setOnClickListener {
            sendCmd(EmgProtocol.CMD_GET_INFO, 0)
        }
    }

    /* ============================ 波形配置 ============================ */

    private fun setupCharts() {
        configureChart(chartCh1, getString(R.string.ch1_label))
        configureChart(chartCh2, getString(R.string.ch2_label))
        configureChart(chartCh3, getString(R.string.ch3_label))
        configureChart(chartCh4, getString(R.string.ch4_label))
        configureChart(chartImu, "ACC/GYR")
    }

    private fun configureChart(chart: LineChart, label: String) {
        chart.setNoDataText("等待数据…")
        chart.description.isEnabled = false
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)
        chart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
        chart.legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
        chart.legend.orientation = Legend.LegendOrientation.HORIZONTAL
        chart.legend.isEnabled = true
        chart.legend.textColor = getColor(R.color.black)

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.isGranularityEnabled = true
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)

        val leftAxis = chart.axisLeft
        leftAxis.isGranularityEnabled = true
        chart.axisRight.isEnabled = false
    }

    private fun updateCharts() {
        val charts = arrayOf(chartCh1, chartCh2, chartCh3, chartCh4)
        val labels = arrayOf("CH1", "CH2", "CH3", "CH4")
        for (i in 0 until 4) {
            updateLineChart(charts[i], emgBuf[i], getColorById(i), labels[i])
        }
        /* IMU 图: 两条曲线, 按标签分别更新 */
        updateLineChart(chartImu, imuAcc, R.color.chart2, "ACC")
        updateLineChart(chartImu, imuGyr, R.color.chart3, "GYR")
    }

    private fun getColorById(idx: Int): Int = when (idx) {
        0 -> R.color.chart1
        1 -> R.color.chart2
        2 -> R.color.chart3
        else -> R.color.chart4
    }

    /** 向 chart 追加/更新一个数据序列(同名数据集存在则更新, 否则新建)。 */
    private fun updateLineChart(
        chart: LineChart,
        buffer: RollingBuffer,
        colorRes: Int,
        dataName: String
    ) {
        val color = getColor(colorRes)
        val entries = buffer.toList().mapIndexed { i, v -> Entry(i.toFloat(), v) }

        var data = chart.data
        if (data == null) {
            data = LineData()
            chart.data = data
        }

        var ds: LineDataSet? = null
        for (d in data.dataSets) {
            if ((d as LineDataSet).label == dataName) {
                ds = d
                break
            }
        }

        if (ds == null) {
            val newDs = LineDataSet(entries, dataName).apply {
                setColor(color)
                setCircleColor(color)
                setDrawCircles(false)
                setDrawValues(false)
                lineWidth = 1.5f
            }
            data.addDataSet(newDs)
        } else {
            // LineDataSet.values 需要 ArrayList<Entry>
            ds.values = entries.let { ArrayList(it) }
            ds.setColor(color)
        }
        chart.notifyDataSetChanged()
        chart.invalidate()
    }

    /* ============================ 连接管理 ============================ */

    private fun toggleConnect() {
        if (tcp != null) {
            tcp?.stop()
            tcp = null
            setStatus(false, "已断开")
            btnConnect.text = getString(R.string.connect)
            return
        }

        val host = editHost.text.toString().trim()
        val port = editPort.text.toString().trim().toIntOrNull() ?: 5000
        if (host.isEmpty()) {
            toast("请输入 IP 地址")
            return
        }

        setStatus(false, getString(R.string.status_connecting))
        btnConnect.isEnabled = false

        parser = FrameParser(this)
        val p = parser
        tcp = TcpClient(
            host = host,
            port = port,
            parser = p!!,
            onConnected = {
                uiHandler.post {
                    setStatus(true, "已连接 $host:$port")
                    btnConnect.text = getString(R.string.disconnect)
                }
            },
            onDisconnected = { msg ->
                uiHandler.post {
                    setStatus(false, "连接断开: $msg")
                    btnConnect.text = getString(R.string.connect)
                }
            }
        )
        tcp?.start()
        btnConnect.isEnabled = true
    }

    private fun setStatus(connected: Boolean, text: String) {
        txtStatus.text = text
        txtStatus.setTextColor(
            getColor(if (connected) R.color.chart3 else R.color.chart2)
        )
    }

    /* ============================ 命令发送 ============================ */

    private fun sendCmd(cmd: Int, arg: Int) {
        val frame = EmgProtocol.buildCmd(cmd, arg)
        if (tcp?.send(frame) != true) {
            toast("未连接, 命令未发送")
        }
    }

    private fun changeGain(delta: Int) {
        gainIdx = (gainIdx + delta).coerceIn(0, GAINS.size - 1)
        sendCmd(EmgProtocol.CMD_SET_GAIN, gainIdx)
        toast("增益: x${GAINS[gainIdx]}")
    }

    private fun changeSps(delta: Int) {
        spsIdx = (spsIdx + delta).coerceIn(0, SPS.size - 1)
        sendCmd(EmgProtocol.CMD_SET_SPS, spsIdx)
        toast("采样率: ${SPS[spsIdx]}SPS")
    }

    private fun setImu(on: Boolean) {
        imuOn = on
        sendCmd(EmgProtocol.CMD_SET_IMU, if (on) 1 else 0)
        toast(if (on) "IMU 已开启" else "IMU 已关闭")
    }

    /* ============================ 数据回调(后台线程) ============================ */

    override fun onEmg(frame: EmgFrame) {
        val values = frame.values()
        synchronized(emgBuf) {
            for (i in 0 until 4) emgBuf[i].push(values[i].toFloat())
        }
    }

    override fun onImu(frame: ImuFrame) {
        synchronized(imuAcc) {
            // 显示滚动中值展示
            imuAcc.push(frame.acc[0].toFloat())
            imuGyr.push(frame.gyr[0].toFloat())
            uiHandler.post {
                txtImu.text =
                    "ACC: ${frame.acc[0]},${frame.acc[1]},${frame.acc[2]}   " +
                    "GYR: ${frame.gyr[0]},${frame.gyr[1]},${frame.gyr[2]}"
            }
        }
    }

    override fun onInfo(text: String) {
        uiHandler.post { txtInfo.text = "设备信息: $text" }
    }

    override fun onParseError(msg: String) {
        uiHandler.post { txtDebug.text = "解析警告: $msg" }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}