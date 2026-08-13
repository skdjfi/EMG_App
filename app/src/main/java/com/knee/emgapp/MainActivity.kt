package com.knee.emgapp

import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.knee.emgapp.protocol.EmgProtocol
import com.knee.emgapp.theme.Theme
import com.knee.emgapp.view.HorizonView
import com.knee.emgapp.view.KneeView
import com.knee.emgapp.view.MiniWaveView
import com.knee.emgapp.view.WaveformView
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), AppState.UiBridge {

    companion object {
        private const val PREFS = "emg_prefs"
        private const val KEY_DARK = "dark"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val UI_REFRESH_MS = 50L
        private const val INTENTS = 10
    }

    /* ==================== 页面 ==================== */
    private lateinit var pageWave: View
    private lateinit var pagePredict: View
    private lateinit var pageWaveDetail: View
    private lateinit var pageImuDetail: View

    /* 底部栏 */
    private lateinit var tabWave: TextView
    private lateinit var tabPredict: TextView
    private lateinit var fab: ImageButton

    /* 主页 */
    private lateinit var ledStatus: View
    private lateinit var stMode: TextView
    private lateinit var stHost: TextView
    private lateinit var bGain: TextView
    private lateinit var bSps: TextView
    private lateinit var bImu: TextView
    private lateinit var btnConn: TextView
    private lateinit var minis: Array<MiniWaveView>
    private lateinit var chVals: Array<TextView>
    private lateinit var chCards: Array<View>
    private lateinit var imuTxt: TextView
    private lateinit var barR: View
    private lateinit var barP: View
    private lateinit var barY: View

    /* 波形详情 */
    private lateinit var dtName: TextView
    private lateinit var dtLive: View
    private lateinit var waveView: WaveformView
    private lateinit var stRms: TextView
    private lateinit var dtPeak: TextView
    private lateinit var stMdf: TextView
    private lateinit var stSnr: TextView
    private lateinit var stMdfM: View
    private lateinit var stSnrM: View
    private lateinit var dots: Array<TextView>
    private lateinit var chipGain: TextView
    private lateinit var chipSps: TextView
    private lateinit var waveTag: TextView

    /* IMU 详情 */
    private lateinit var horizon: HorizonView
    private lateinit var ivR: TextView
    private lateinit var ivP: TextView
    private lateinit var ivY: TextView
    private lateinit var kAccX: TextView
    private lateinit var kAccY: TextView
    private lateinit var kAccZ: TextView
    private lateinit var kGyrX: TextView
    private lateinit var kGyrY: TextView
    private lateinit var kGyrZ: TextView
    private lateinit var kAccXM: View
    private lateinit var kAccYM: View
    private lateinit var kAccZM: View
    private lateinit var kGyrXM: View
    private lateinit var kGyrYM: View
    private lateinit var kGyrZM: View

    /* 预测页 */
    private lateinit var knee: KneeView
    private lateinit var flexBig: TextView
    private lateinit var phaseTag: TextView
    private lateinit var aX: TextView
    private lateinit var aY: TextView
    private lateinit var aZ: TextView
    private lateinit var mX: View
    private lateinit var mY: View
    private lateinit var mZ: View
    private lateinit var winFill: View
    private lateinit var confTxt: TextView
    private lateinit var modelDesc: TextView
    private lateinit var intentRows: LinearLayout

    private val intentBars = arrayOfNulls<View>(INTENTS)
    private val intentPcts = arrayOfNulls<TextView>(INTENTS)
    private val intentItems = arrayOfNulls<LinearLayout>(INTENTS)
    private var curIntent = 2
    private var flexV = 62f
    private var flexDir = 1f
    private var winTick = 0

    private var detailIdx = 0

    private val uiHandler = Handler(Looper.getMainLooper())
    private val random = java.util.Random()

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateHome()
            updateWaveDetail()
            updateImuDetail()
            updatePredict()
            uiHandler.postDelayed(this, UI_REFRESH_MS)
        }
    }

    /* ==================== 生命周期 ==================== */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        refreshThemeIcon()
        setupListeners()
        buildIntentRows()
        AppState.ui = this
        reflectState()
        uiHandler.post(refreshRunnable)
        startPredictTimers()
    }

    override fun onResume() {
        super.onResume()
        AppState.ui = this
        reflectState()
    }

    override fun onDestroy() {
        if (AppState.ui === this) AppState.ui = null
        uiHandler.removeCallbacks(refreshRunnable)
        super.onDestroy()
    }

    /* ==================== 视图绑定 ==================== */

    private fun bindViews() {
        pageWave = findViewById(R.id.pageWave)
        pagePredict = findViewById(R.id.pagePredict)
        pageWaveDetail = findViewById(R.id.pageWaveDetail)
        pageImuDetail = findViewById(R.id.pageImuDetail)

        tabWave = findViewById(R.id.tabWave)
        tabPredict = findViewById(R.id.tabPredict)
        fab = findViewById(R.id.fab)

        ledStatus = findViewById(R.id.ledStatus)
        stMode = findViewById(R.id.stMode)
        stHost = findViewById(R.id.stHost)
        bGain = findViewById(R.id.bGain)
        bSps = findViewById(R.id.bSps)
        bImu = findViewById(R.id.bImu)
        btnConn = findViewById(R.id.btnConn)
        minis = Array(4) { findViewById(R.id.miniW0 + it) as MiniWaveView }
        chVals = Array(4) { findViewById(R.id.chVal0 + it) as TextView }
        chCards = Array(4) { findViewById(R.id.chCard0 + it) }
        imuTxt = findViewById(R.id.imuTxt)
        barR = findViewById(R.id.barR)
        barP = findViewById(R.id.barP)
        barY = findViewById(R.id.barY)

        dtName = findViewById(R.id.dtName)
        dtLive = findViewById(R.id.dtLive)
        waveView = findViewById(R.id.waveView)
        stRms = findViewById(R.id.stRms)
        dtPeak = findViewById(R.id.dtPeak)
        stMdf = findViewById(R.id.stMdf)
        stSnr = findViewById(R.id.stSnr)
        stMdfM = findViewById(R.id.stMdfM)
        stSnrM = findViewById(R.id.stSnrM)
        dots = Array(4) { findViewById(R.id.dot0 + it) as TextView }
        chipGain = findViewById(R.id.chipGain)
        chipSps = findViewById(R.id.chipSps)
        waveTag = findViewById(R.id.waveTag)

        horizon = findViewById(R.id.horizon)
        ivR = findViewById(R.id.ivR)
        ivP = findViewById(R.id.ivP)
        ivY = findViewById(R.id.ivY)
        kAccX = findViewById(R.id.accX)
        kAccY = findViewById(R.id.accY)
        kAccZ = findViewById(R.id.accZ)
        kGyrX = findViewById(R.id.gyrX)
        kGyrY = findViewById(R.id.gyrY)
        kGyrZ = findViewById(R.id.gyrZ)
        kAccXM = findViewById(R.id.accXM)
        kAccYM = findViewById(R.id.accYM)
        kAccZM = findViewById(R.id.accZM)
        kGyrXM = findViewById(R.id.gyrXM)
        kGyrYM = findViewById(R.id.gyrYM)
        kGyrZM = findViewById(R.id.gyrZM)

        knee = findViewById(R.id.knee)
        flexBig = findViewById(R.id.flexBig)
        phaseTag = findViewById(R.id.phaseTag)
        aX = findViewById(R.id.aX)
        aY = findViewById(R.id.aY)
        aZ = findViewById(R.id.aZ)
        mX = findViewById(R.id.mX)
        mY = findViewById(R.id.mY)
        mZ = findViewById(R.id.mZ)
        winFill = findViewById(R.id.winFill)
        confTxt = findViewById(R.id.confTxt)
        modelDesc = findViewById(R.id.modelDesc)
        intentRows = findViewById(R.id.intentRows)

        val chColors = intArrayOf(
            Theme.color(this, R.attr.ch1),
            Theme.color(this, R.attr.ch2),
            Theme.color(this, R.attr.ch3),
            Theme.color(this, R.attr.ch4)
        )
        repeat(4) { minis[it].setColor(chColors[it]) }
    }

    /* ==================== 监听 ==================== */

    private fun setupListeners() {
        tabWave.setOnClickListener { go(0) }
        tabPredict.setOnClickListener { go(1) }

        fab.setOnClickListener { onFabClick() }
        btnConn.setOnClickListener { onConnectClick() }

        repeat(4) {
            val idx = it
            chCards[idx].setOnClickListener { openWaveDetail(idx) }
        }
        findViewById<View>(R.id.imuCard).setOnClickListener {
            pageImuDetail.visibility = View.VISIBLE
        }

        findViewById<View>(R.id.btnBackD).setOnClickListener { closeWaveDetail() }
        findViewById<View>(R.id.btnImuBack).setOnClickListener { pageImuDetail.visibility = View.GONE }

        repeat(4) {
            val idx = it
            dots[idx].setOnClickListener { switchDetailChannel(idx) }
        }
        chipGain.setOnClickListener { cycleGain() }
        chipSps.setOnClickListener { cycleSps() }

        val themeBtns = intArrayOf(R.id.btnTheme, R.id.btnThemeD, R.id.btnThemeImu, R.id.btnThemeP)
        themeBtns.forEach { id ->
            findViewById<View>(id).setOnClickListener { toggleTheme() }
        }
        findViewById<View>(R.id.btnSettings).setOnClickListener { toast("设置: 参数调节在波形详情页底部") }
        findViewById<View>(R.id.btnSettingsD).setOnClickListener { toast("设置: 参数调节在波形详情页底部") }
        findViewById<View>(R.id.btnSettingsImu).setOnClickListener { toast("设置: 参数调节在波形详情页底部") }
        findViewById<View>(R.id.btnInfoP).setOnClickListener { showInfoDialog() }
    }

    /* ==================== 页面切换 ==================== */

    private fun go(idx: Int) {
        pageWave.visibility = if (idx == 0) View.VISIBLE else View.GONE
        pagePredict.visibility = if (idx == 1) View.VISIBLE else View.GONE
        val accent = Theme.color(this, R.attr.accent)
        val tertiary = Theme.color(this, R.attr.textTertiary)
        tabWave.setTextColor(if (idx == 0) accent else tertiary)
        tabPredict.setTextColor(if (idx == 1) accent else tertiary)
        tabWave.setCompoundDrawableTintList(Theme.tint(if (idx == 0) accent else tertiary))
        tabPredict.setCompoundDrawableTintList(Theme.tint(if (idx == 1) accent else tertiary))
    }

    private fun openWaveDetail(idx: Int) {
        switchDetailChannel(idx)
        pageWaveDetail.visibility = View.VISIBLE
    }

    private fun closeWaveDetail() {
        pageWaveDetail.visibility = View.GONE
        updateWaveDetail()
    }

    private fun switchDetailChannel(idx: Int) {
        detailIdx = idx
        dtName.text = getString(R.string.wave_detail_title, idx + 1, chName(idx))
        val onSel = Theme.color(this, R.attr.textSecondary)
        repeat(4) {
            val d = dots[it]
            if (it == idx) {
                d.setTextColor(Color.WHITE)
                d.setBackgroundResource(R.drawable.bg_pill_on)
            } else {
                d.setTextColor(onSel)
                d.setBackgroundResource(0)
            }
        }
    }

    private fun chName(idx: Int): String = getString(
        intArrayOf(
            R.string.ch_name_0, R.string.ch_name_1,
            R.string.ch_name_2, R.string.ch_name_3
        )[idx]
    )

    /* ==================== 连接 ==================== */

    private fun onConnectClick() {
        if (AppState.connected) {
            AppState.disconnect()
            return
        }
        showConnectDialog()
    }

    private fun showConnectDialog() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val pad = 24
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val inputHost = EditText(this).apply {
            hint = "ESP32 IP (AP 模式 192.168.4.1)"
            setText(prefs.getString(KEY_HOST, "192.168.4.1"))
            singleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        val inputPort = EditText(this).apply {
            hint = "端口 (默认 5000)"
            setText(prefs.getString(KEY_PORT, "5000"))
            singleLine = true
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(inputHost, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        layout.addView(inputPort, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        AlertDialog.Builder(this)
            .setTitle("连接 ESP32 网关")
            .setView(layout)
            .setNegativeButton("取消", null)
            .setPositiveButton("连接") { _, _ ->
                val host = inputHost.text.toString().trim()
                val port = inputPort.text.toString().toIntOrNull() ?: 5000
                if (host.isEmpty()) {
                    toast("请输入 IP")
                    return@setPositiveButton
                }
                prefs.edit().putString(KEY_HOST, host).putString(KEY_PORT, port.toString()).apply()
                AppState.connect(host, port)
            }
            .show()
    }

    override fun onConnChanged(connected: Boolean) {
        uiHandler.post {
            reflectState()
            toast(if (connected) "已连接" else "连接断开")
        }
    }

    override fun onInfo(text: String) {
        uiHandler.post { toast("设备信息: $text") }
    }

    private fun reflectState() {
        val connected = AppState.connected
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        ledStatus.backgroundTintList = if (connected) Theme.tint(Theme.color(this, R.attr.accent)) else null
        dtLive.backgroundTintList = if (connected) Theme.tint(Theme.color(this, R.attr.accent)) else null
        stMode.text = getString(if (connected) R.string.status_ap else R.string.status_offline)
        stMode.setTextColor(Theme.color(this, if (connected) R.attr.accent else R.attr.textTertiary))
        val host = prefs.getString(KEY_HOST, "192.168.4.1") ?: "192.168.4.1"
        val port = prefs.getString(KEY_PORT, "5000") ?: "5000"
        stHost.text = "$host:$port · " + getString(if (connected) R.string.connect_ok else R.string.connect_off)
        bGain.text = "×${AppState.currentGain}"
        bSps.text = "${AppState.currentSps} SPS"
        chipGain.text = "×${AppState.currentGain}"
        chipSps.text = "${AppState.currentSps} Hz"
        waveTag.text = getString(R.string.wave_tag, AppState.currentGain)
        reflectFab()
    }

    private fun reflectFab() {
        if (AppState.running) {
            fab.setBackgroundResource(R.drawable.bg_btn_red)
            fab.setImageResource(R.drawable.ic_stop)
        } else {
            fab.setBackgroundResource(R.drawable.bg_btn_accent)
            fab.setImageResource(R.drawable.ic_play)
        }
    }

    private fun onFabClick() {
        if (!AppState.connected) {
            toast(getString(R.string.not_connected))
            return
        }
        val started = !AppState.running
        val ok = AppState.sendCmd(
            if (started) EmgProtocol.CMD_START else EmgProtocol.CMD_STOP, 0
        )
        if (ok) {
            toast(getString(if (started) R.string.toast_start else R.string.toast_stop))
            reflectFab()
        }
    }

    private fun cycleGain() {
        AppState.changeGain(1)
        reflectState()
        toast(getString(R.string.toast_gain, AppState.currentGain))
    }

    private fun cycleSps() {
        AppState.changeSps(1)
        reflectState()
        toast(getString(R.string.toast_sps, AppState.currentSps))
    }

    private fun showInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("模型: best.pt (CNN-GRU)")
            .setMessage(
                "3 s 窗口 @ 200 Hz · 85 通道肌电\n" +
                    "输出: 意图 10 类 + 右膝 3 分量角度\n" +
                    "测试精度 ≈79% · 角度 MAE 0.8~1.5°\n\n" +
                    "设备信息: ${AppState.lastInfo.ifEmpty { "未获取" }}"
            )
            .setNegativeButton("获取设备信息", null)
            .setPositiveButton("关闭", null)
            .show()
    }

    /* ==================== 主题 ==================== */

    private fun isDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun toggleTheme() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, !isDark()).apply()
        AppCompatDelegate.setDefaultNightMode(
            if (!isDark()) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        recreate()
    }

    private fun refreshThemeIcon() {
        val icon = if (isDark()) R.drawable.ic_moon else R.drawable.ic_sun
        intArrayOf(R.id.btnTheme, R.id.btnThemeD, R.id.btnThemeImu, R.id.btnThemeP).forEach { id ->
            findViewById<ImageView>(id).setImageResource(icon)
        }
    }

    /* ==================== 数据刷新 ==================== */

    private fun updateHome() {
        val running = AppState.running
        repeat(4) { i ->
            minis[i].setData(AppState.emgBuf[i].toList())
            val rms = calcRms(AppState.emgBuf[i].toList())
            chVals[i].text = rms.toInt().toString()
        }
        val acc = AppState.lastAcc
        val gyr = AppState.lastGyr
        imuTxt.text = String.format(
            "ACC %.2f · %.2f · %.2f g　GYR %d · %d · %d °/s",
            acc[0] / 1024f, acc[1] / 1024f, acc[2] / 1024f,
            gyr[0], gyr[1], gyr[2]
        )
        setBarPct(barR, abs(AppState.roll) / 30f)
        setBarPct(barP, abs(AppState.pitch) / 20f)
        setBarPct(barY, abs(AppState.yaw) / 90f)
    }

    private fun calcRms(list: List<Float>): Float {
        if (list.isEmpty()) return 0f
        var sum = 0.0
        for (v in list) sum += v * v
        return sqrt(sum / list.size).toFloat()
    }

    private fun setBarPct(bar: View, pct: Float) {
        val parent = bar.parent as? View ?: return
        val w = parent.width
        if (w > 0) {
            bar.layoutParams.width = (w * pct.coerceIn(0f, 1f)).toInt().coerceAtLeast(2)
            bar.requestLayout()
        }
    }

    private fun updateWaveDetail() {
        if (pageWaveDetail.visibility != View.VISIBLE) return
        val list = AppState.emgBuf[detailIdx].toList()
        waveView.setData(list)
        if (list.isEmpty()) return

        val rms = calcRms(list)
        val peak = list.maxOfOrNull { abs(it) } ?: 0f
        stRms.text = "${rms.toInt()}"
        dtPeak.text = getString(R.string.peak, peak.toInt())

        // 中位频率: 过零率估算
        val n = list.size.coerceAtMost(200)
        var zc = 0
        for (k in 1 until n) {
            if ((list[list.size - k] >= 0f) != (list[list.size - k - 1] >= 0f)) zc++
        }
        val freq = if (n > 1) zc * AppState.currentSps / (2f * (n - 1)) else 0f
        stMdf.text = "${freq.roundToInt()} Hz"
        setBarPct(stMdfM, (freq / 160f).coerceIn(0f, 1f))

        val quality = if (AppState.connected && AppState.running) 95 else 0
        stSnr.text = "$quality%"
        setBarPct(stSnrM, quality / 100f)
    }

    private fun updateImuDetail() {
        if (pageImuDetail.visibility != View.VISIBLE) return
        horizon.setAttitude(AppState.roll, AppState.pitch, AppState.yaw)
        ivR.text = String.format("%.1f°", AppState.roll)
        ivP.text = String.format("%.1f°", AppState.pitch)
        ivY.text = String.format("%.1f°", AppState.yaw)
        val acc = AppState.lastAcc
        val gyr = AppState.lastGyr
        kAccX.text = String.format("%.2f g", acc[0] / 1024f)
        kAccY.text = String.format("%.2f g", acc[1] / 1024f)
        kAccZ.text = String.format("%.2f g", acc[2] / 1024f)
        kGyrX.text = String.format("%.1f °/s", gyr[0])
        kGyrY.text = String.format("%.1f °/s", gyr[1])
        kGyrZ.text = String.format("%.1f °/s", gyr[2])
        setBarPct(kAccXM, abs(acc[0]) / 6144f)
        setBarPct(kAccYM, abs(acc[1]) / 6144f)
        setBarPct(kAccZM, abs(acc[2]) / 6144f)
        setBarPct(kGyrXM, abs(gyr[0]) / 2000f)
        setBarPct(kGyrYM, abs(gyr[1]) / 2000f)
        setBarPct(kGyrZM, abs(gyr[2]) / 2000f)
    }

    private fun updatePredict() {
        if (pagePredict.visibility != View.VISIBLE) return
        // 窗口缓冲: 采集时 3s 填满
        winTick = if (AppState.running) winTick + 1 else 0
        val pct = (winTick / 60f).coerceIn(0f, 1f)
        setBarPct(winFill, pct)
        modelDesc.text = getString(R.string.model_desc, 85, Math.round(pct * 1000) / 10f)
        confTxt.text = getString(R.string.conf, Math.round(92 * pct))
        val ab = 4.2f + AppState.roll / 10f
        val rot = -2.8f + (AppState.yaw - 180f) / 60f
        flexBig.text = "${flexV.roundToInt()}°"
        aX.text = "${flexV.roundToInt()}°"
        setBarPct(mX, flexV / 120f)
        aY.text = String.format("%.1f°", ab)
        aZ.text = String.format("%.1f°", rot)
        setBarPct(mY, abs(ab) / 20f)
        setBarPct(mZ, abs(rot) / 40f)
    }

    /* ==================== 预测动画 ==================== */

    private fun startPredictTimers() {
        uiHandler.post(object : Runnable {
            override fun run() {
                flexV += flexDir * (1 + random.nextFloat() * 2)
                if (flexV > 88) { flexV = 88f; flexDir = -1f }
                if (flexV < 30) { flexV = 30f; flexDir = 1f }
                knee.setFlex(flexV)
                phaseTag.text = getString(if (flexDir > 0) R.string.phase_flex else R.string.phase_ext)
                uiHandler.postDelayed(this, 700)
            }
        })
        uiHandler.post(object : Runnable {
            override fun run() {
                curIntent = (curIntent + 1) % INTENTS
                paintIntents()
                uiHandler.postDelayed(this, 2600)
            }
        })
    }

    private fun buildIntentRows() {
        val names = arrayOf("坐", "站", "走", "上楼梯", "下楼梯", "上坡", "下坡", "不平地", "斜步碎步", "躺下")
        repeat(INTENTS) { rowIdx ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 7, 0, 7)
            }

            val tag = TextView(this).apply {
                text = names[rowIdx].take(1)
                gravity = Gravity.CENTER
                setTextColor(Theme.color(this@MainActivity, R.attr.textSecondary))
                setBackgroundResource(R.drawable.bg_chip)
                textSize = 12f
            }
            row.addView(tag, dp(34), dp(34))

            val name = TextView(this).apply {
                text = names[rowIdx]
                setTextColor(Theme.color(this@MainActivity, R.attr.textPrimary))
                textSize = 12f
                gravity = Gravity.CENTER_VERTICAL
            }
            val nameLp = LinearLayout.LayoutParams(dp(62), LinearLayout.LayoutParams.MATCH_PARENT)
            nameLp.leftMargin = dp(11)
            row.addView(name, nameLp)

            val bar = FrameLayout(this).apply { setBackgroundResource(R.drawable.bg_track) }
            val barLp = LinearLayout.LayoutParams(0, dp(8), 1f)
            barLp.leftMargin = dp(8)
            row.addView(bar, barLp)
            val fill = View(this).apply { setBackgroundResource(R.drawable.bg_bar_gradient) }
            bar.addView(fill, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT))

            val pct = TextView(this).apply {
                gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
                text = "0%"
                setTextColor(Theme.color(this@MainActivity, R.attr.textSecondary))
                textSize = 11f
            }
            val pctLp = LinearLayout.LayoutParams(dp(38), LinearLayout.LayoutParams.MATCH_PARENT)
            pctLp.leftMargin = dp(6)
            row.addView(pct, pctLp)

            intentItems[rowIdx] = row
            intentBars[rowIdx] = fill
            intentPcts[rowIdx] = pct
            intentRows.addView(row, LinearLayout.LayoutParams(MATCH_PARENT_DP, WRAP_CONTENT_DP))
        }
        paintIntents()
    }

    private fun paintIntents() {
        val base = floatArrayOf(0.9f, 1.4f, 22.6f, 8.4f, 10.2f, 6.1f, 5.5f, 3.2f, 1.7f, 0.8f)
        var sum = 0f
        for (i in 0 until INTENTS) sum += base[i] + if (i == curIntent) 14f else 0f
        for (i in 0 until INTENTS) {
            val p = (base[i] + if (i == curIntent) 14f else 0f) / sum * 100f
            intentPcts[i]?.text = "${p.roundToInt()}%"
            val row = intentItems[i] ?: continue
            val tag = row.getChildAt(0) as TextView
            val name = row.getChildAt(1) as TextView
            val active = i == curIntent
            if (active) {
                tag.setTextColor(Color.WHITE)
                tag.setBackgroundResource(R.drawable.bg_pill_on)
                name.setTextColor(Theme.color(this, R.attr.accent))
            } else {
                tag.setTextColor(Theme.color(this, R.attr.textSecondary))
                tag.setBackgroundResource(R.drawable.bg_chip)
                name.setTextColor(Theme.color(this, R.attr.textPrimary))
            }
            val bar = row.getChildAt(2)
            val fill = intentBars[i] ?: continue
            bar.post {
                val w = bar.width
                if (w > 0) {
                    fill.layoutParams = FrameLayout.LayoutParams(
                        (w * p / 100f).toInt().coerceAtLeast(2),
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
            }
        }
    }

    private val MATCH_PARENT_DP = LinearLayout.LayoutParams.MATCH_PARENT
    private val WRAP_CONTENT_DP = LinearLayout.LayoutParams.WRAP_CONTENT

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}