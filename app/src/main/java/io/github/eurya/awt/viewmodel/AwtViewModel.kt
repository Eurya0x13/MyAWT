package io.github.eurya.awt.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.eurya.awt.data.state.AwtUiState
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.DataInputStream
import java.io.IOException
import java.io.PrintWriter
import java.net.Socket
import java.net.InetSocketAddress
import javax.inject.Inject

/**
 * AWT (Android Window Toolkit) 远程桌面视图模型
 *
 * 负责管理与远程服务器的连接、图像数据接收、像素格式转换和用户输入处理
 * 支持多种像素格式的实时图像传输和显示
 *
 * 功能：
 * - 建立和维护与远程服务器的Socket连接
 * - 接收和解析图像帧数据
 * - 转换不同像素格式为Android Bitmap
 * - 计算并显示FPS和数据传输速率
 * - 处理鼠标移动等用户输入事件
 *
 * @author qz919
 * @data 2025/10/02
 */
@HiltViewModel
class AwtViewModel @Inject constructor() : ViewModel() {

    /** UI状态的可变数据流，用于观察界面状态变化 */
    private val _uiState = MutableStateFlow(AwtUiState())

    /** 对外暴露的UI状态只读数据流 */
    val uiState: StateFlow<AwtUiState> = _uiState.asStateFlow()

    /** 网络Socket连接实例 */
    private var socket: Socket? = null

    /** 数据输入流，用于接收服务器发送的图像数据 */
    private var dataInputStream: DataInputStream? = null

    /** 连接任务引用，用于取消连接操作 */
    private var connectionJob: Job? = null

    /**
     * 支持的像素格式枚举
     *
     * - ARGB: 带透明通道的32位颜色格式
     * - RGB:  24位RGB颜色格式
     * - RGB565: 16位RGB颜色格式
     * - GRAYSCALE: 8位灰度格式
     */
    enum class PixelFormat {
        ARGB, RGB, RGB565, GRAYSCALE
    }

    /**
     * 连接到远程AWT服务器
     *
     * 建立Socket连接并开始接收图像数据流连接成功后会自动处理图像帧的接收和显示
     *
     * @param host 服务器主机地址
     * @param port 服务器监听端口
     *
     * @throws Exception 连接失败或数据处理异常时会更新错误状态
     */
    fun connect(host: String, port: Int) {
        connectionJob?.cancel()

        connectionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(errorMessage = null) }

                // 使用重试机制建立连接
                socket = retryConnect(host, port, 10000) // 10秒超时
                dataInputStream = DataInputStream(socket!!.getInputStream())

                val width = dataInputStream!!.readInt()
                val height = dataInputStream!!.readInt()
                val isRealData = dataInputStream!!.readBoolean()

                _uiState.update { state ->
                    state.copy(
                        isConnected = true,
                        serverHost = host,
                        serverPort = port,
                        width = width,
                        height = height,
                        startTime = System.currentTimeMillis()
                    )
                }

                // 开始接收数据循环
                startDataReceivingLoop(width, height)

            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        errorMessage = "连接失败: ${e.message}",
                        isConnected = false
                    )
                }
            } finally {
                if (socket?.isConnected != true) {
                    disconnect()
                }
            }
        }
    }

    /**
     * 重试连接机制
     *
     * 在指定超时时间内每500ms尝试连接一次，直到连接成功或超时
     *
     * @param host 服务器主机地址
     * @param port 服务器监听端口
     * @param timeoutMs 超时时间（毫秒），默认10秒
     * @return 成功建立的Socket连接
     * @throws IOException 连接超时或所有重试尝试都失败
     */
    private suspend fun retryConnect(host: String, port: Int, timeoutMs: Long = 10000): Socket {
        val startTime = System.currentTimeMillis()
        var attemptCount = 0

        while (true) {
            attemptCount++
            try {
                _uiState.update { state ->
                    state.copy(
//                        connectionStatus = "尝试连接中... (第${attemptCount}次)",
                        isConnected = false
                    )
                }

                // 尝试连接，设置连接超时2秒
                val socket = Socket()
                socket.soTimeout = 2000
                socket.connect(InetSocketAddress(host, port), 2000)

                _uiState.update { state ->
                    state.copy(
//                        connectionStatus = "连接成功!",
                        isConnected = true
                    )
                }
                return socket

            } catch (e: Exception) {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    throw IOException("连接超时 (${timeoutMs}ms)，最后错误: ${e.message}")
                }

                val remainingTime = timeoutMs - (System.currentTimeMillis() - startTime)
                _uiState.update { state ->
                    state.copy(
//                        connectionStatus = "连接失败，${remainingTime}ms后重试...",
                        isConnected = false
                    )
                }

                // 等待500ms后重试
                delay(500)
            }
        }
    }

    /**
     * 开始接收数据循环
     *
     * 在连接成功后持续接收和处理图像帧数据
     *
     * @param width 图像宽度
     * @param height 图像高度
     */
    private fun startDataReceivingLoop(width: Int, height: Int) {
        while (socket != null && !socket!!.isClosed) {
            try {
                val formatStr = dataInputStream!!.readUTF()
                val format = PixelFormat.valueOf(formatStr)

                val dataLength = dataInputStream!!.readInt()

                if (dataLength == 0) {
                    continue
                }

                if (dataLength == -1) {
                    updateFrameCount()
                    continue
                }

                val pixelBytes = ByteArray(dataLength)
                dataInputStream!!.readFully(pixelBytes)

                val bitmap = convertBytesToBitmap(pixelBytes, format, width, height)

                if (bitmap != null) {
                    updateUIWithNewFrame(bitmap, formatStr, dataLength)
                }

            } catch (e: Exception) {
                if (socket?.isClosed != true) {
                    _uiState.update { state ->
                        state.copy(errorMessage = "接收数据错误: ${e.message}")
                    }
                }
                break
            }
        }
    }

    /**
     * 断开与服务器的连接
     *
     * 取消连接任务，关闭输入流和Socket连接，并更新UI状态为未连接
     */
    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null

        try {
            dataInputStream?.close()
            socket?.close()
        } catch (_: IOException) {
        }

        dataInputStream = null
        socket = null

        _uiState.update { state ->
            state.copy(
                isConnected = false,
//                connectionStatus = null
            )
        }
    }

    /**
     * 发送鼠标移动事件到服务器
     *
     * 将鼠标移动坐标发送到远程服务器，模拟鼠标移动操作
     *
     * @param x 鼠标在X轴的坐标
     * @param y 鼠标在Y轴的坐标
     */
    fun moveMouse(x: Int, y: Int) {
        handleInput { printWriter ->
            // 发送鼠标移动和点击事件序列
            printWriter.println("MOUSE_MOVE|$x|$y")
            printWriter.println("MOUSE_PRESS|1")
            printWriter.println("MOUSE_RELEASE|1")
        }
    }

    /**
     * 更新帧计数和性能统计信息
     *
     * 在收到不含图像数据的特殊帧时调用，用于更新FPS和数据传输速率
     */
    private fun updateFrameCount() {
        _uiState.update { state ->
            val newFrameCount = state.frameCount + 1
            val currentTime = System.currentTimeMillis()
            val elapsedSeconds = (currentTime - state.startTime) / 1000.0

            val fps = if (elapsedSeconds > 0) newFrameCount / elapsedSeconds else 0.0
            val dataRate =
                if (elapsedSeconds > 0) state.totalData / (1024.0 * 1024.0) / elapsedSeconds else 0.0

            state.copy(
                frameCount = newFrameCount,
                fps = fps,
                dataRate = dataRate
            )
        }
    }

    /**
     * 使用新接收的图像帧更新UI状态
     *
     * 更新帧计数、总数据量、FPS、数据传输速率，并设置新的Bitmap图像
     *
     * @param bitmap 转换后的Bitmap图像
     * @param format 像素格式字符串
     * @param dataLength 当前帧的数据长度（字节数）
     */
    private fun updateUIWithNewFrame(bitmap: Bitmap, format: String, dataLength: Int) {
        _uiState.update { state ->
            val newFrameCount = state.frameCount + 1
            val newTotalData = state.totalData + dataLength
            val currentTime = System.currentTimeMillis()
            val elapsedSeconds = (currentTime - state.startTime) / 1000.0

            val fps = if (elapsedSeconds > 0) newFrameCount / elapsedSeconds else 0.0
            val dataRate =
                if (elapsedSeconds > 0) newTotalData / (1024.0 * 1024.0) / elapsedSeconds else 0.0

            state.copy(
                frameCount = newFrameCount,
                totalData = newTotalData,
                fps = fps,
                dataRate = dataRate,
                pixelFormat = format,
                bitmap = bitmap
            )
        }
    }

    /**
     * 将字节数组转换为Android Bitmap图像
     *
     * 根据不同的像素格式解析字节数据并创建对应的Bitmap对象
     *
     * @param pixelBytes 原始像素数据字节数组
     * @param format 像素格式
     * @param width 图像宽度
     * @param height 图像高度
     * @return 转换后的Bitmap对象，转换失败时返回null
     */
    private fun convertBytesToBitmap(
        pixelBytes: ByteArray,
        format: PixelFormat,
        width: Int,
        height: Int
    ): Bitmap? {
        return try {
            when (format) {
                PixelFormat.ARGB -> {
                    val pixels = IntArray(width * height)
                    for (i in pixels.indices) {
                        val offset = i * 4
                        val a = (pixelBytes[offset].toInt() and 0xFF) shl 24       // Alpha通道
                        val r = (pixelBytes[offset + 1].toInt() and 0xFF) shl 16   // Red通道
                        val g = (pixelBytes[offset + 2].toInt() and 0xFF) shl 8    // Green通道
                        val b = (pixelBytes[offset + 3].toInt() and 0xFF)          // Blue通道
                        pixels[i] = a or r or g or b
                    }
                    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
                }

                PixelFormat.RGB -> {
                    val pixels = IntArray(width * height)
                    for (i in pixels.indices) {
                        val offset = i * 3
                        val r = (pixelBytes[offset].toInt() and 0xFF) shl 16       // Red通道
                        val g = (pixelBytes[offset + 1].toInt() and 0xFF) shl 8    // Green通道
                        val b = (pixelBytes[offset + 2].toInt() and 0xFF)          // Blue通道
                        pixels[i] = 0xFF000000.toInt() or r or g or b              // 固定Alpha为255
                    }
                    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
                }

                PixelFormat.RGB565 -> {
                    val pixels = IntArray(width * height)
                    for (i in pixels.indices) {
                        val offset = i * 2
                        val rgb565 =
                            ((pixelBytes[offset].toInt() and 0xFF) shl 8) or (pixelBytes[offset + 1].toInt() and 0xFF)

                        val r = ((rgb565 shr 11) and 0x1F) * 255 / 31
                        val g = ((rgb565 shr 5) and 0x3F) * 255 / 63
                        val b = (rgb565 and 0x1F) * 255 / 31

                        pixels[i] = 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
                    }
                    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
                }

                PixelFormat.GRAYSCALE -> {
                    val pixels = IntArray(width * height)
                    for (i in pixels.indices) {
                        val gray = pixelBytes[i].toInt() and 0xFF
                        pixels[i] = 0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
                    }
                    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 处理用户输入事件
     *
     * 在IO线程中安全地发送用户输入命令到服务器
     *
     * @param block 包含输入处理逻辑的lambda表达式，接收PrintWriter参数用于发送数据
     */
    private fun handleInput(block: (printWriter: PrintWriter) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            socket?.apply {
                if (!isClosed) {
                    block(PrintWriter(outputStream, true)) // autoFlush = true
                }
            }
        }
    }

    /**
     * ViewModel生命周期结束时的清理操作
     *
     * 当ViewModel被销毁时自动调用，确保网络连接被正确关闭
     */
    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}