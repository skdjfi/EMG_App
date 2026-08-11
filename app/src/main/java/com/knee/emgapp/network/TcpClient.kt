package com.knee.emgapp.network

import com.knee.emgapp.protocol.FrameParser
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * TCP 客户端, 连接 ESP32 网关的 TCP 服务器。
 *
 * 功能:
 *  - connect(): 在后台线程建立连接
 *  - readLoop(): 阻塞读取数据 -> 喂给 FrameParser
 *  - send(): 发送下行命令(由任意线程调用)
 *
 * 注意: 读写均在专用线程执行; 通过回调通知 UI。
 */
class TcpClient(
    private val host: String,
    private val port: Int,
    private val parser: FrameParser,
    private val onConnected: () -> Unit,
    private val onDisconnected: (String) -> Unit
) {

    @Volatile
    private var running = false
    @Volatile
    private var connected = false
    private var socket: Socket? = null
    private var output: OutputStream? = null

    private val readBuffer = ByteArray(512)

    /** 启动连接线程。 */
    fun start() {
        if (running) return
        running = true
        Thread({ runLoop() }, "tcp-client").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        connected = false
    }

    /** 发送下行命令帧(线程安全)。 */
    @Synchronized
    fun send(data: ByteArray): Boolean {
        if (!connected) return false
        return try {
            output?.write(data)
            output?.flush()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun runLoop() {
        while (running) {
            if (!connected) {
                runCatching { reconnect() }
            }
            if (connected) {
                readLoop()
            }
            if (running) {
                Thread.sleep(1000)   // 重连间隔
            }
        }
    }

    private fun reconnect() {
        try {
            val sock = Socket()
            sock.connect(InetSocketAddress(host, port), 5000)   // 5s 超时
            sock.tcpNoDelay = true
            socket = sock
            output = sock.getOutputStream()
            connected = true
            parser.reset()
            onConnected()
        } catch (e: Exception) {
            // 连接失败, 稍后重试
        }
    }

    private fun readLoop() {
        val sock = socket ?: return
        val input: InputStream = sock.getInputStream()
        runCatching {
            while (connected && running) {
                val n = input.read(readBuffer)
                if (n < 0) {
                    throw Exception("stream closed")
                }
                val chunk = ByteArray(n)
                System.arraycopy(readBuffer, 0, chunk, 0, n)
                parser.feed(chunk)
            }
        }.onFailure {
            connected = false
            output = null
            onDisconnected(it.message ?: "disconnected")
        }
    }
}