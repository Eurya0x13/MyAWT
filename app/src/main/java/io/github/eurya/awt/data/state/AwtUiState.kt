package io.github.eurya.awt.data.state

import android.graphics.Bitmap

/**
 * AWT远程桌面UI状态数据类
 *
 * 功能：
 * - 封装远程桌面连接和图像传输过程中的所有状态信息，用于驱动UI更新和性能监控。
 * - 包含连接状态、服务器信息、性能指标、图像数据和错误信息等完整的状态集合。
 *
 * @property isConnected 是否已建立与远程服务器的连接，true表示连接成功
 * @property serverHost 远程服务器主机地址或域名，用于显示和重连
 * @property serverPort 远程服务器监听端口，默认8888
 * @property frameCount 累计接收的图像帧数，用于计算帧率和统计
 * @property fps 实时帧率（Frames Per Second），表示每秒显示的图像帧数
 * @property dataRate 数据传输速率，单位为MB/s，反映网络带宽使用情况
 * @property width 远程桌面图像宽度，单位为像素，由服务器初始化时指定
 * @property height 远程桌面图像高度，单位为像素，由服务器初始化时指定
 * @property pixelFormat 当前图像数据的像素格式，如ARGB、RGB、RGB565等
 * @property bitmap 当前显示的位图图像，包含最新的远程桌面画面
 * @property errorMessage 错误信息描述，当连接或数据传输失败时显示
 * @property startTime 连接开始时间戳，用于计算运行时长和性能指标
 * @property totalData 累计接收的数据总量，单位为字节，用于统计和监控
 *
 * @author qz919
 * @data 2025/10/02
 */
data class AwtUiState(
    val isConnected: Boolean = false,
    val serverHost: String = "",
    val serverPort: Int = 8888,
    val frameCount: Long = 0,
    val fps: Double = 0.0,
    val dataRate: Double = 0.0,
    val width: Int = 0,
    val height: Int = 0,
    val pixelFormat: String = "",
    val bitmap: Bitmap? = null,
    val errorMessage: String? = null,
    val startTime: Long = System.currentTimeMillis(),
    val totalData: Long = 0
)