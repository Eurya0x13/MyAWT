package io.github.eurya.awt.utils

import android.util.Log
import io.github.eurya.awt.data.JavaConfig
import io.github.eurya.awt.data.LaunchResult
import io.github.eurya.awt.exception.JavaRuntimeException
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.system.measureTimeMillis

/**
 * 原生Java启动器
 *
 * 功能:
 * - 负责在Android环境中初始化Java运行时环境并启动JVM
 * - 提供JAR应用程序和主类启动功能，支持环境变量配置、IO重定向和模块系统配置
 * - TODO: 使用Native方法优化性能并绕过Android平台限制
 *
 * @author qz919
 * @data 2025/10/02
 *
 * @property config Java运行时配置参数
 */
class NativeJavaLauncher private constructor(
    private val config: JavaConfig
) : Closeable {

    companion object {

        const val TAG = "NativeJavaLauncher"

        init {
            try {
                System.loadLibrary("my_awt")
            } catch (e: UnsatisfiedLinkError) {
                throw JavaRuntimeException.InitializationException(
                    "无法加载原生库 'awt'，请确保库文件存在且可访问", e
                )
            }
        }

        /**
         * 重定向标准输入输出到指定文件
         *
         * @param file 目标文件路径，所有标准输出和错误输出将被重定向到此文件
         */
        @JvmStatic
        external fun dup2(file: String)

        /**
         * 设置环境变量
         *
         * @param envName 环境变量名称
         * @param envValue 环境变量值
         */
        @JvmStatic
        external fun export(envName: String, envValue: String)

        /**
         * 改变当前工作目录
         *
         * @param name 新的工作目录路径
         */
        @JvmStatic
        external fun chdir(name: String)

        /**
         * 启动Java虚拟机
         *
         * @param args Java命令行参数数组
         * @return JVM退出代码，0表示成功，非0表示错误
         */
        @JvmStatic
        external fun nativeLaunchJvm(args: Array<String>): Int

        /**
         * 停止Java虚拟机
         *
         * 强制终止当前运行的JVM实例
         */
        @JvmStatic
        external fun nativeStopJvm()

        /**
         * 创建启动器实例
         *
         * @param config Java运行时配置参数
         * @return 配置完成的启动器实例
         */
        fun create(config: JavaConfig): NativeJavaLauncher {
            return NativeJavaLauncher(config).apply {
                if (!config.validatePaths()) {
                    Log.w(TAG, "警告: 某些路径不存在，可能会影响运行时功能")
                }
            }
        }
    }

    /** Java启动参数列表 */
    private val javaArgList = mutableListOf<String>()

    /** 用于判断当前环境是否初始化 */
    private var isInitialized = false

    /** 是否启动了JVM虚拟机 */
    private var jvmLaunched = false

    /** TODO: 现在没有任何作用 */
    private var nativeResourcesReleased = false

    /**
     * 初始化Java运行时环境
     *
     * 设置环境变量、IO重定向、检查可执行文件并准备Java启动参数
     * 必须在调用任何启动方法前执行
     *
     * @throws JavaRuntimeException.InitializationException 当环境设置失败时抛出
     */
    @Throws(JavaRuntimeException.InitializationException::class)
    fun initJavaRuntime() {
        if (isInitialized) {
            Log.w(TAG, "Java运行时环境已经初始化")
            return
        }

        try {
            Log.w(TAG, "开始初始化Java运行时环境...")

            setupEnvironment()
            setupIORedirection()
            checkJavaElfExecutable()
            copyDummyNativeLib("libawt_xawt.so")
            setupJavaArgs()

            isInitialized = true
            Log.w(TAG, "Java运行时环境初始化完成")

        } catch (e: Exception) {
            throw JavaRuntimeException.InitializationException(
                "Java运行时环境初始化失败: ${e.message}", e
            )
        }
    }

    /**
     * 启动JAR应用程序
     *
     * @param jarPath JAR文件绝对路径
     * @param args 传递给应用程序的命令行参数
     * @return 启动结果，包含执行时间和退出状态
     * @throws JavaRuntimeException.LaunchException 当JAR文件无效或启动失败时抛出
     */
    @Throws(JavaRuntimeException.LaunchException::class)
    fun launchJarApplication(jarPath: String, vararg args: String): LaunchResult {
        requireInitialized()
        validateJarPath(jarPath)

        prepareLaunchArguments(
            listOf(
                "-jar",
                jarPath,
            ) + args
        )
        return executeLaunch()
    }

    /**
     * 启动指定主类的应用程序
     *
     * @param mainClass 主类的全限定名
     * @param jarPath 包含主类的JAR文件路径（可选）
     * @param args 传递给主方法的命令行参数
     * @return 启动结果，包含执行时间和退出状态
     * @throws JavaRuntimeException.LaunchException 当类找不到或启动失败时抛出
     */
    @Throws(JavaRuntimeException.LaunchException::class)
    fun launchMainClass(
        mainClass: String, jarPath: String? = null, vararg args: String
    ): LaunchResult {
        requireInitialized()

        val launchArgs = mutableListOf<String>()
        if (jarPath != null) {
            validateJarPath(jarPath)
            launchArgs.addAll(listOf("-jar", jarPath))
        }
        launchArgs.add(mainClass)
        launchArgs.addAll(args)

        prepareLaunchArguments(launchArgs)
        return executeLaunch()
    }

    /**
     * 获取当前的Java虚拟机参数列表
     *
     * @return 当前配置的JVM参数只读列表
     */
    fun getJavaArguments(): List<String> = javaArgList.toList()

    /**
     * 添加自定义Java虚拟机参数
     *
     * @param argument 要添加的JVM参数，如"-Xmx512m"或"-Dproperty=value"
     */
    fun addJavaArgument(argument: String) {
        javaArgList.add(argument)
    }

    /**
     * 添加多个Java虚拟机参数
     *
     * @param arguments 要添加的JVM参数集合
     */
    fun addJavaArguments(arguments: Collection<String>) {
        javaArgList.addAll(arguments)
    }

    /**
     * 释放所有占用的原生资源
     *
     * TODO: 等待实现
     */
    override fun close() {
        if (!nativeResourcesReleased) {
            releaseNativeResources()
            nativeResourcesReleased = true
            Log.w(TAG, "原生资源已释放")
        }
    }

    /**
     * 验证Java运行时环境是否已初始化
     *
     * @throws JavaRuntimeException.InvalidStateException 当运行时环境未初始化时抛出
     */
    private fun requireInitialized() {
        if (!isInitialized) {
            throw JavaRuntimeException.InvalidStateException(
                "请先调用 initJavaRuntime() 方法初始化Java运行时环境"
            )
        }
    }

    /**
     * 验证JVM是否已启动
     *
     * @throws JavaRuntimeException.InvalidStateException 当JVM未启动时抛出
     */
    private fun requireIsLaunched() {
        if (!jvmLaunched) {
            throw JavaRuntimeException.InvalidStateException(
                "请先调用 launchJarApplication() 或 launchMainClass() 方法启动JVM"
            )
        }
    }

    /**
     * 验证JAR文件路径的有效性
     *
     * @param jarPath 要验证的JAR文件路径
     * @throws JavaRuntimeException.LaunchException 当JAR文件不存在、不可读或不是文件时抛出
     */
    private fun validateJarPath(jarPath: String) {
        val jarFile = File(jarPath)
        if (!jarFile.exists()) {
            throw JavaRuntimeException.LaunchException("JAR文件不存在: $jarPath")
        }
        if (!jarFile.isFile) {
            throw JavaRuntimeException.LaunchException("JAR路径不是文件: $jarPath")
        }
        if (!jarFile.canRead()) {
            throw JavaRuntimeException.LaunchException("无法读取JAR文件: $jarPath")
        }
    }

    /**
     * 准备JVM启动参数
     *
     * 组合Java可执行文件路径、系统参数和应用程序特定参数
     *
     * @param additionalArgs 应用程序特定的命令行参数
     */
    private fun prepareLaunchArguments(additionalArgs: List<String>) {
        val systemArgs = javaArgList.toList()
        javaArgList.clear()
        javaArgList.add("${config.jrePath}/bin/java")
        javaArgList.addAll(systemArgs)
        javaArgList.addAll(additionalArgs)

        Log.w(TAG, "启动参数: ${javaArgList.joinToString(" ")}")
    }

    /**
     * 执行JVM启动过程
     *
     * 调用原生方法启动Java虚拟机并测量执行时间
     *
     * @return 包含执行时间和退出状态的启动结果
     * @throws JavaRuntimeException.LaunchException 当JVM启动失败时抛出
     */
    private fun executeLaunch(): LaunchResult {
        val executionTime = measureTimeMillis {
            try {
                jvmLaunched = true
                val exitCode = nativeLaunchJvm(javaArgList.toTypedArray())
                if (exitCode != 0) {
                    return LaunchResult.Companion.failure(exitCode)
                }
            } catch (e: Exception) {
                throw JavaRuntimeException.LaunchException("启动JVM失败: ${e.message}", e)
            }
        }
        return LaunchResult.Companion.success(executionTime = executionTime)
    }

    /**
     * 检查并设置Java可执行文件的执行权限
     *
     * 遍历JRE bin目录下的所有文件，确保ELF可执行文件具有执行权限
     * 这是绕过Android SELinux限制的临时解决方案
     */
    private fun checkJavaElfExecutable() {
        File(config.jrePath, "bin/").listFiles()?.forEach { elf ->
            if (!elf.canExecute()) {
                elf.setExecutable(true)
            }
        }
    }

    /**
     * 复制原生库文件替换OpenJDK的原始库
     *
     * 将自定义的原生库文件复制到JRE库目录，用于替换特定的库
     *
     * @param sharedLibraryName 要替换的共享库文件名
     * @throws IOException 当文件复制失败时抛出
     */
    @Throws(IOException::class)
    private fun copyDummyNativeLib(sharedLibraryName: String) {
        val fileLib = File(config.jrePath, "lib/$sharedLibraryName")
        fileLib.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }
        if (!fileLib.exists()) fileLib.createNewFile()
        FileInputStream(File(config.nativePath, sharedLibraryName)).use { input ->
            FileOutputStream(fileLib).use { output ->
                input.copyTo(output)
            }
        }
    }
    /**
     * 设置Java运行时环境变量
     *
     * 配置HOME、JAVA_HOME和LD_LIBRARY_PATH环境变量
     * 这些变量对于JVM正确找到库文件和配置文件至关重要
     */
    private fun setupEnvironment() {

        val libPaths = listOf(
            config.nativePath, "${config.jrePath}/lib", "${config.jrePath}/lib/server"
        )
        val newPath = libPaths.joinToString(":")

        export("HOME", config.home)
        export("JAVA_HOME", config.jrePath)
        export("LD_LIBRARY_PATH", newPath)
    }

    /**
     * 设置输入输出重定向
     *
     * 改变工作目录到配置的home目录，并将标准输出重定向到日志文件
     */
    private fun setupIORedirection() {
        chdir(config.home)
        dup2("${config.home}/${config.logFile}")
        Log.w(TAG, "IO重定向设置完成")
    }

    /**
     * 设置Java虚拟机启动参数
     *
     * 配置系统属性、模块导出和引导类路径
     * 这些参数确保AWT和Swing组件在Android环境中正常工作
     */
    private fun setupJavaArgs() {
        addSystemProperties()
        addModuleExports()
        setupBootClasspath()
        Log.w(TAG, "Java参数设置完成")
    }

    /**
     * 添加系统属性参数
     *
     * 配置AWT、图形环境和字体管理相关的系统属性
     * 使用Cacio作为AWT工具包以在headless环境中提供图形支持
     */
    private fun addSystemProperties() {
        javaArgList.addAll(
            listOf(
                "-Djava.awt.headless=false",
                // 开启抗锯齿
                "-Dawt.useSystemAAFontSettings=on",
                "-Dswing.aatext=true",

                "-Dcacio.managed.screensize=${config.screenWidth}x${config.screenHeight}",
                "-Dcacio.font.fontmanager=sun.awt.X11FontManager",
                "-Dcacio.font.fontscaler=sun.font.FreetypeFontScaler",
                "-Dswing.defaultlaf=javax.swing.plaf.metal.MetalLookAndFeel",
                "-Dawt.toolkit=com.github.caciocavallosilano.cacio.ctc.CTCToolkit",
                "-Djava.awt.graphicsenv=com.github.caciocavallosilano.cacio.ctc.CTCGraphicsEnvironment",
                "-Djava.system.class.loader=com.github.caciocavallosilano.cacio.ctc.CTCPreloadClassLoader"
            )
        )
    }

    /**
     * 添加模块系统导出和打开指令
     *
     * 配置Java模块系统以允许访问内部API
     * 这些导出对于Cacio实现和AWT组件正常工作至关重要
     */
    private fun addModuleExports() {
        javaArgList.addAll(
            listOf(
                "--add-exports=java.desktop/java.awt=ALL-UNNAMED",
                "--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED",
                "--add-exports=java.desktop/java.awt.dnd.peer=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.awt.event=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.awt.datatransfer=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.font=ALL-UNNAMED",
                "--add-exports=java.base/sun.security.action=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED",
                "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED"
            )
        )
    }

    /**
     * 设置引导类路径
     *
     * 扫描Cacio目录下的JAR文件并添加到引导类路径
     * 特殊的argent JAR文件将作为Java代理加载
     */
    private fun setupBootClasspath() {
        val cacioDir = File(config.jrePath, "cacio")
        val jarFiles = cacioDir.listFiles { file ->
            file.isFile && file.name.endsWith(".jar")
        } ?: emptyArray()

        if (jarFiles.isNotEmpty()) {
            val classpath = StringBuilder().append("-Xbootclasspath/a:")
            jarFiles.forEach { file ->
                if (!file.name.contains("argent")) {
                    classpath.append(file.absolutePath).append(":")
                } else {
                    javaArgList.add("-javaagent:${file.absolutePath}")
                }
            }

            javaArgList.add(classpath.toString())
            Log.w(TAG, "添加Cacio类路径，包含 ${jarFiles.size} 个JAR文件")
        } else {
            Log.w(TAG, "警告: 未找到Cacio JAR文件")
        }
    }

    /**
     * 释放原生资源
     *
     * 清理原生方法分配的资源，停止JVM运行
     * 当前为待实现状态，需要在适当的时候完成实现
     */
    private fun releaseNativeResources() {

    }
}