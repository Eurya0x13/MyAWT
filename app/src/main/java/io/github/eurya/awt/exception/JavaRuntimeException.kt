package io.github.eurya.awt.exception

/**
 * Java运行时异常体系
 *
 * 功能：
 * - 定义Java运行时环境相关的异常类型，使用密封类封装所有可能的运行时错误
 * - 提供清晰的异常分类，便于错误处理和问题诊断
 *
 * @author qz919
 * @data 2025/10/02
 */
sealed class JavaRuntimeException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {

    /**
     * Java运行时环境初始化异常
     *
     * 在Java运行时环境初始化过程中发生的错误，包括：
     * - 原生库加载失败
     * - 环境变量设置失败
     * - 文件权限问题
     * - 资源分配失败
     *
     * @param message 详细的错误描述信息
     * @param cause 导致该异常的根本原因，可选
     */
    class InitializationException(message: String, cause: Throwable? = null) :
        JavaRuntimeException(message, cause)

    /**
     * Java应用程序启动异常
     *
     * 在启动Java应用程序过程中发生的错误，包括：
     * - JAR文件不存在或无法访问
     * - 主类找不到
     * - JVM启动参数错误
     * - 应用程序启动超时
     *
     * @param message 详细的错误描述信息
     * @param cause 导致该异常的根本原因，可选
     */
    class LaunchException(message: String, cause: Throwable? = null) :
        JavaRuntimeException(message, cause)

    /**
     * 无效状态异常
     *
     * 在非法操作序列或无效状态下调用方法时抛出，包括：
     * - 在未初始化时尝试启动应用程序
     * - 在已关闭状态下尝试操作
     * - 状态机转换错误
     *
     * @param message 详细的状态错误描述信息
     */
    class InvalidStateException(message: String) : JavaRuntimeException(message)
}