package com.knee.emgapp.protocol

/**
 * 与 STM32/ESP32 网关一致的帧协议定义。
 * 上行(设备->手机): | 0xAA | 0x55 | LEN | SEQ | TYPE | DATA | CHECK(XOR) |
 * 下行(手机->设备): | 0x55 | 0xAA | CMD | ARG | CHECK(XOR) |
 *
 * LEN = SEQ + TYPE + 数据长度(不含帧头/长度/校验)
 * CHECK = 从帧头到数据末尾所有字节的异或
 */
object EmgProtocol {

    /* 帧头 */
    const val HEAD0: Int = 0xAA
    const val HEAD1: Int = 0x55
    const val CMD0: Int = 0x55   // 下行命令帧头(与上行区分)
    const val CMD1: Int = 0xAA

    /* 上行数据类型 */
    const val TYPE_EMG: Int = 0x01    // 肌电 4 通道 x 2 字节 + LOFF 1 字节
    const val TYPE_INFO: Int = 0x02   // 设备信息 32 字节
    const val TYPE_IMU: Int = 0x03    // IMU 六轴 12 字节

    /* 下行命令 */
    const val CMD_START: Int = 0x01
    const val CMD_STOP: Int = 0x02
    const val CMD_SET_GAIN: Int = 0x03
    const val CMD_SET_SPS: Int = 0x04
    const val CMD_GET_INFO: Int = 0x05
    const val CMD_SET_IMU: Int = 0x06

    /** 由 buf[0..len) 计算异或校验。 */
    fun checksum(buf: ByteArray, len: Int): Byte {
        var sum = 0
        for (i in 0 until len) {
            sum = sum xor (buf[i].toInt() and 0xFF)
        }
        return (sum and 0xFF).toByte()
    }

    /** 组装一帧上行数据(type + data), 返回完整帧字节数组。 */
    fun buildUpFrame(type: Int, data: ByteArray, seq: Int): ByteArray {
        val len = 1 + 1 + data.size
        val frame = ByteArray(2 + 1 + 1 + len + 1)
        frame[0] = HEAD0.toByte()
        frame[1] = HEAD1.toByte()
        frame[2] = len.toByte()
        frame[3] = (seq and 0xFF).toByte()
        frame[4] = type.toByte()
        System.arraycopy(data, 0, frame, 5, data.size)
        frame[5 + data.size] = checksum(frame, 5 + data.size)
        return frame
    }

    /** 组装一帧下行命令。 */
    fun buildCmd(cmd: Int, arg: Int): ByteArray {
        val frame = ByteArray(5)
        frame[0] = CMD0.toByte()
        frame[1] = CMD1.toByte()
        frame[2] = cmd.toByte()
        frame[3] = arg.toByte()
        frame[4] = checksum(frame, 4)
        return frame
    }
}

/** 上行一帧肌电数据(解析结果)。 */
data class EmgFrame(
    val seq: Int,
    val ch0: Short, val ch1: Short, val ch2: Short, val ch3: Short,
    val loff: Int
) {
    fun values(): ShortArray = shortArrayOf(ch0, ch1, ch2, ch3)
}

/** 一帧 IMU 数据。 */
data class ImuFrame(
    val seq: Int,
    val acc: ShortArray = ShortArray(3),
    val gyr: ShortArray = ShortArray(3)
)