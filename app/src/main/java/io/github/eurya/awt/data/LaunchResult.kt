package io.github.eurya.awt.data

/**
 * JVM启动结果数据类
 *
 * 功能：
 * - 封装Java虚拟机启动过程的执行结果，包含退出代码、执行时间和成功状态。
 * - 用于传递启动操作的完整状态信息，支持成功和失败两种结果的便捷创建。
 *
 * @property exitCode JVM进程退出代码，0表示成功，非0表示错误
 * @property executionTime 从启动到结束的总执行时间，单位为毫秒
 * @property success 启动是否成功的布尔标志，true表示成功启动并正常退出
 *
 * @author qz919
 * @data 2025/10/02
 */
data class LaunchResult(
    val exitCode: Int,
    val executionTime: Long,
    val success: Boolean
) {

    companion object {

        /**
         * 创建成功的启动结果
         *
         * @param exitCode 退出代码，默认为0表示成功
         * @param executionTime 执行时间，默认为0毫秒
         * @return 表示成功启动的LaunchResult实例
         */
        fun success(exitCode: Int = 0, executionTime: Long = 0) =
            LaunchResult(exitCode, executionTime, true)

        /**
         * 创建失败的启动结果
         *
         * @param exitCode 退出代码，默认为-1表示未知错误
         * @param executionTime 执行时间，默认为0毫秒
         * @return 表示启动失败的LaunchResult实例
         */
        fun failure(exitCode: Int = -1, executionTime: Long = 0) =
            LaunchResult(exitCode, executionTime, false)
    }
}