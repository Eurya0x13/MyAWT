package io.github.eurya.awt.data

import java.io.File

/**
 * Java运行时环境配置数据类
 *
 * 功能：
 * - 定义Java虚拟机启动和运行所需的所有配置参数，包括路径设置、显示参数和日志配置
 * - 提供参数验证和路径检查功能，确保运行时环境的正确性和可靠性
 *
 * @property home Java应用程序主目录路径，用于存储用户数据和配置文件
 * @property nativePath 原生共享库文件路径，包含JVM运行所需的本地库文件
 * @property jrePath Java运行时环境根目录路径，包含bin、lib等标准JRE目录结构
 * @property screenWidth 虚拟屏幕显示宽度，单位为像素，默认1280
 * @property screenHeight 虚拟屏幕显示高度，单位为像素，默认720
 * @property logFile 日志输出文件名，JVM标准输出和错误输出将重定向到此文件
 *
 * @author qz919
 * @data 2025/10/02
 */
data class JavaConfig(
    val home: String,
    val nativePath: String,
    val jrePath: String,
    val screenWidth: Int = 1280,
    val screenHeight: Int = 720,
    val logFile: String = "logcat",
) {

    /**
     * 初始化块 - 参数验证
     *
     * 在对象创建时自动验证所有配置参数的合法性，确保配置的完整性和有效性
     * 使用require函数进行前置条件检查，不满足条件时抛出IllegalArgumentException
     */
    init {
        require(home.isNotEmpty()) { "home路径不能为空" }
        require(nativePath.isNotEmpty()) { "nativePath不能为空" }
        require(jrePath.isNotEmpty()) { "jrePath不能为空" }
        require(screenWidth > 0) { "screenWidth必须大于0" }
        require(screenHeight > 0) { "screenHeight必须大于0" }
        require(logFile.isNotEmpty()) { "logFile不能为空" }
    }

    /**
     * 验证所有必需的目录路径是否存在
     *
     * 检查配置中指定的各个路径是否在文件系统中实际存在
     * 用于启动前的环境预检，避免因路径问题导致的运行时错误
     *
     * @return true表示所有路径都存在，false表示有路径不存在
     */
    fun validatePaths(): Boolean {
        return listOf(home, nativePath, jrePath).all { path ->
            File(path).exists().also { exists ->
                if (!exists) {
                    println("警告: 路径不存在: $path")
                }
            }
        }
    }
}