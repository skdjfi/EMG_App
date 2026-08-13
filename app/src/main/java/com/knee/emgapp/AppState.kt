package com.knee.emgapp

import com.knee.emgapp.model.RollingBuffer
import com.knee.emgapp.network.TcpClient
import com.knee.emgapp.protocol.EmgFrame
import com.knee.emgapp.protocol.EmgProtocol
import com.knee.emgapp.protocol.FrameListener
import com.knee.emgapp.protocol.FrameParser
import com.knee.emgapp.protocol.ImuFrame

/**
 * 全局应用状态(单例), 跨 Activity 重建存活:
 *  TCP 连接 / 采样缓冲 / 增益档位 / 连接回调桥。
 */
object AppState : FrameListener {

    /** 增益档位(与 STM32 端 ads1194.h 一致) */
    val GAINS = intArrayOf(1, 2, 3, 4, 6, 8, 12)
    /** 采样率档位(与 STM32 端 ads1194.h 一致) */
    val SPSS = intArrayOf(125, 250, 500, 1000, 2000, 4000, 8000)

    const val MAX_POINTS = 600

    val emgBuf = Array(4) { RollingBuffer(MAX_POINTS) }

    @Volatile
    var connected = false
    @Volatile
    var running = false
    @Volatile
    var gainIdx = 2
    @Volatile
    var spsIdx = 2

    val currentGain get() = GAINS[gainIdx]
    val currentSps get() = SPSS[spsIdx]

    /* IMU 最新值 + 姿态解算 */
    @Volatile
    var lastAcc: ShortArray = ShortArray(3)
    @Volatile
    var lastGyr: ShortArray = ShortArray(3)
    @Volatile
    var roll = 0f
    @Volatile
    var pitch = 0f
    @Volatile
    var yaw = 0f
    var lastInfo = ""

    private var tcp: TcpClient? = null
    private var parser: FrameParser? = null
    private var host = ""
    private var port = 5000

    /** UI 桥: 由 MainActivity 注册/注销 */
    interface UiBridge {
        fun onConnChanged(connected: Boolean)
        fun onInfo(text: String)
    }

    var ui: UiBridge? = null

    private var lastYawGyr = 0f
    private var lastYawAt = 0L

    val isConnecting get() = !connected && tcp != null

    fun connect(h: String, p: Int) {
        tcp?.stop()
        tcp = null
        host = h
        port = p
        parser = FrameParser(this)
        val pr = parser
        tcp = TcpClient(
            host = h,
            port = p,
            parser = pr!!,
            onConnected = {
                connected = true
                // 连接成功即开启 IMU
                sendCmd(EmgProtocol.CMD_SET_IMU, 1)
                ui?.onConnChanged(true)
            },
            onDisconnected = {
                connected = false
                running = false
                ui?.onConnChanged(false)
            }
        )
        tcp?.start()
    }

    fun disconnect() {
        tcp?.stop()
        tcp = null
        connected = false
        running = false
        ui?.onConnChanged(false)
    }

    fun sendCmd(cmd: Int, arg: Int): Boolean {
        val ok = tcp?.send(EmgProtocol.buildCmd(cmd, arg)) ?: false
        if (cmd == EmgProtocol.CMD_START && ok) running = true
        if (cmd == EmgProtocol.CMD_STOP && ok) running = false
        return ok
    }

    fun changeGain(delta: Int) {
        gainIdx = (gainIdx + delta).coerceIn(0, GAINS.size - 1)
        sendCmd(EmgProtocol.CMD_SET_GAIN, gainIdx)
    }

    fun changeSps(delta: Int) {
        spsIdx = (spsIdx + delta).coerceIn(0, SPSS.size - 1)
        sendCmd(EmgProtocol.CMD_SET_SPS, spsIdx)
    }

    /* ==================== 数据回调(后台线程) ==================== */

    override fun onEmg(frame: EmgFrame) {
        val values = frame.values()
        for (i in 0 until 4) {
            emgBuf[i].push(values[i].toFloat())
        }
    }

    override fun onImu(frame: ImuFrame) {
        lastAcc = frame.acc
        lastGyr = frame.gyr

        val ax = frame.acc[0].toFloat()
        val ay = frame.acc[1].toFloat()
        val az = frame.acc[2].toFloat()

        // 横滚/俯仰: 加速度计静态解算(比例无关)
        val mag = kotlin.math.sqrt(ax * ax + ay * ay + az * az).coerceAtLeast(1f)
        roll = (Math.toDegrees(Math.atan2(ay.toDouble(), az.toDouble()))).toFloat()
        pitch = (Math.toDegrees(Math.atan2((-ax).toDouble(), mag.toDouble()))).toFloat()

        // 偏航: 陀螺积分(艏向, 相对值)
        val now = System.currentTimeMillis()
        val dt = ((now - lastYawAt).coerceIn(1L, 200L)) / 1000f
        if (lastYawAt != 0L && running) {
            val gz = frame.gyr[2].toFloat() / 16.4f
            yaw += (gz + lastYawGyr) / 2f * dt
            yaw = ((yaw % 360f) + 360f) % 360f
        }
        lastYawGyr = frame.gyr[2].toFloat() / 16.4f
        lastYawAt = now
    }

    override fun onInfo(text: String) {
        lastInfo = text
        ui?.onInfo(text)
    }
}