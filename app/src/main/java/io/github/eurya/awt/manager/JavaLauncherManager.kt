package io.github.eurya.awt.manager

import io.github.eurya.awt.data.JavaConfig
import io.github.eurya.awt.data.LaunchResult
import io.github.eurya.awt.utils.NativeJavaLauncher
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/**
 * Java应用程序启动管理器
 *
 * 功能：
 * - 负责管理Java应用程序的启动生命周期，提供异步启动、进度监控和运行状态管理功能
 * - 使用协程和Channel实现非阻塞的启动流程和实时进度反馈
 * - 利用协程Job状态来跟踪运行状态
 *
 * @author qz919
 * @data 2025/10/02
 */
class JavaLauncherManager {
    /**
     * 协程作用域，使用IO调度器和监督作业
     * 确保启动任务在后台线程执行，且一个任务的失败不会影响其他任务
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 当前正在执行的启动任务引用
     * 通过Job的活跃状态判断应用程序是否正在运行
     */
    private var currentJob: Job? = null

    /**
     * 使用流式进度更新启动Java应用程序
     *
     * 通过Channel提供实时的启动进度反馈，支持启动过程的各个阶段状态通知
     * 包含Java运行时环境初始化、应用程序启动和错误处理等完整流程
     *
     * @param config Java运行时配置参数，包含JRE路径、环境变量等设置
     * @param jarPath 要启动的JAR应用程序文件路径
     * @return 进度更新Channel，发送启动过程中的状态消息，完成后自动关闭
     *
     * @throws Exception 当启动过程中发生任何错误时，通过Channel发送错误信息
     */
    fun launchApplicationWithFlow(
        config: JavaConfig,
        jarPath: String
    ): Channel<String> {
        val progressChannel = Channel<String>()

        // 检查是否已有应用程序正在运行
        if (isApplicationRunning()) {
            scope.launch {
                progressChannel.send("错误：已有应用程序正在运行")
                progressChannel.close()
            }
            return progressChannel
        }

        currentJob = scope.launch {
            try {
                progressChannel.send("开始启动Java应用程序...")

                val result = withContext(Dispatchers.IO) {
                    NativeJavaLauncher.create(config).use { launcher ->
                        progressChannel.send("正在初始化Java运行时环境...")
                        launcher.initJavaRuntime()

                        progressChannel.send("Java运行时环境初始化完成，正在启动应用程序...")
                        launcher.launchJarApplication(jarPath)
                    }
                }

                progressChannel.send("应用程序启动成功")
                progressChannel.close()
            } catch (e: Exception) {
                progressChannel.send("应用程序启动失败: ${e.message}")
                progressChannel.close(e)
            }
        }

        return progressChannel
    }

    /**
     * 取消当前正在运行的应用程序启动
     *
     * 中断正在执行的启动流程，停止Java运行时环境初始化或应用程序启动过程
     * 协程Job取消后会自动更新运行状态
     */
    fun cancelCurrentLaunch() {
        currentJob?.cancel("用户取消启动")
    }

    /**
     * 检查当前是否有应用程序正在运行
     *
     * 通过协程Job的活跃状态判断应用程序运行状态
     *
     * @return true表示有应用程序正在运行，false表示当前处于空闲状态
     */
    fun isApplicationRunning(): Boolean = currentJob?.isActive == true

    /**
     * 关闭启动管理器并释放所有资源
     *
     * 取消所有正在进行的启动任务，清理协程作用域
     * 应在应用程序退出或不再需要启动服务时调用，防止资源泄漏
     */
    fun shutdown() {
        cancelCurrentLaunch()
        scope.cancel("JavaLauncherManager关闭")
    }
}