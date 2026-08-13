package com.knee.emgapp.protocol

/**
 * 流式帧解析器。
 * 将 TCP 收到的字节流按协议切分成完整帧。字节长度:
 *   上行帧: 4 字节头(HEAD0,HEAD1,LEN,SEQ) + TYPE + DATA + CHECK
 * 支持三种上行帧: EMG / IMU / INFO, 以及无下行指令解析需求(下行由按钮直接发)。
 */
class FrameParser(private val listener: FrameListener) {

    /* 解析状态机 */
    private var state = 0
    private var len = 0          // 待收数据部分长度
    private var type = 0
    private var seq = 0
    private var cnt = 0          // 已收字节计数(相对 LEN 段内)
    private var buf = ByteArray(0)   // DATA 暂存

    fun reset() {
        state = 0
        len = 0
        type = 0
        seq = 0
        cnt = 0
        buf = ByteArray(0)
    }

    /** 喂入一块字节, 内部解析并回调。 */
    fun feed(data: ByteArray) {
        for (b in data) {
            step(b)
        }
    }

    private fun step(b: Byte) {
        val v = b.toInt() and 0xFF
        when (state) {
            0 -> if (v == EmgProtocol.HEAD0) state = 1                          // 0xAA
            1 -> if (v == EmgProtocol.HEAD1) state = 2 else state = 0           // 0x55
            2 -> { len = v; state = 3 }                                          // LEN
            3 -> { seq = v; state = 4 }                                          // SEQ
            4 -> {
                type = v
                // LEN = SEQ + TYPE 数据长度, 必须 >= 2, 否则视为坏帧重新同步
                if (len < 2) {
                    state = 0
                    listener.onParseError("bad LEN")
                } else {
                    state = 5
                    cnt = 0
                    buf = ByteArray(len - 2)
                }
            }                                                                     // TYPE
            5 -> {
                if (cnt < buf.size) {
                    buf[cnt++] = b
                    if (cnt >= buf.size) {
                        // 数据收齐, 再读 1 字节 CHECK
                        state = 6
                    }
                }
            }
            6 -> {
                state = 0
                // 校验: 从帧头(HEAD0,HEAD1,LEN,SEQ,TYPE)到数据末尾异或 == CHECK
                val check = EmgProtocol.checksum(
                    byteArrayOf(
                        EmgProtocol.HEAD0.toByte(), EmgProtocol.HEAD1.toByte(),
                        len.toByte(), seq.toByte(), type.toByte()
                    ).plus(buf), 5 + buf.size
                )
                if (check.toInt() != v) {
                    listener.onParseError("CHECK mismatch")
                    return
                }
                dispatch(type, seq, buf)
            }
        }
    }

    private fun dispatch(type: Int, seq: Int, data: ByteArray) {
        when (type) {
            EmgProtocol.TYPE_EMG -> dispatchEmg(seq, data)
            EmgProtocol.TYPE_IMU -> dispatchImu(seq, data)
            EmgProtocol.TYPE_INFO -> {
                val txt = buildString {
                    for (b in data) {
                        val c = b.toInt() and 0xFF
                        append(if (c in 0x20..0x7E) c.toChar() else ' ')
                    }
                }
                listener.onInfo(txt.trim())
            }
            else -> listener.onParseError("unknown type 0x${type.toString(16)}")
        }
    }

    private fun dispatchEmg(seq: Int, data: ByteArray) {
        if (data.size < 9) {
            listener.onParseError("EMG frame too short: ${data.size}")
            return
        }
        var idx = 0
        fun read16(): Short {
            val hi = data[idx].toInt() and 0xFF
            val lo = data[idx + 1].toInt() and 0xFF
            idx += 2
            return ((hi shl 8) or lo).toShort()
        }
        val frame = EmgFrame(
            seq = seq,
            ch0 = read16(), ch1 = read16(), ch2 = read16(), ch3 = read16(),
            loff = data[idx].toInt() and 0xFF
        )
        listener.onEmg(frame)
    }

    private fun dispatchImu(seq: Int, data: ByteArray) {
        if (data.size < 12) {
            listener.onParseError("IMU frame too short: ${data.size}")
            return
        }
        var idx = 0
        fun read16(): Short {
            val v = data[idx].toInt() and 0xFF
            val w = data[idx + 1].toInt() and 0xFF
            idx += 2
            return ((v shl 8) or w).toShort()
        }
        val acc = ShortArray(3) { read16() }
        val gyr = ShortArray(3) { read16() }
        listener.onImu(ImuFrame(seq, acc, gyr))
    }
}

/**
 * 轻量回调接口, 全部方法带默认空实现。
 */
interface FrameListener {
    fun onEmg(frame: EmgFrame) {}
    fun onImu(frame: ImuFrame) {}
    fun onInfo(text: String) {}
    fun onParseError(msg: String) {}
}