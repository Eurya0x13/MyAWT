package io.github.eurya.awt.data.state

import io.github.eurya.awt.data.RuntimeLibrary

/**
 * 应用程序初始化状态密封类
 *
 * 功能：
 * - 定义应用程序启动时运行库检查和提取过程的完整状态机
 * - 使用密封类确保状态完整性，便于在UI层进行状态驱动的界面更新
 * - 每个状态对象都包含该阶段所需的相关数据
 *
 * @author qz919
 * @data 2025/10/02
 */
sealed class InitState {

    /**
     * 初始空闲状态
     *
     * 表示初始化过程尚未开始，应用程序处于就绪等待状态
     * 这是所有初始化流程的起点状态
     */
    object Idle : InitState()

    /**
     * 运行库检查状态
     *
     * 表示正在检查运行库的完整性和是否需要提取
     * 此阶段会验证已存在的库文件并确定是否需要执行解压操作
     */
    object Checking : InitState()

    /**
     * 运行库提取状态
     *
     * 表示正在从应用资源中提取运行库文件到设备存储
     * 此阶段涉及文件解压、完整性验证和权限设置等操作
     */
    object Extracting : InitState()

    /**
     * 初始化成功状态
     *
     * 表示运行库检查和提取过程已成功完成
     */
    object Success : InitState()

    /**
     * 初始化错误状态
     *
     * 表示在初始化过程中遇到了错误，包含详细的错误描述信息
     *
     * @property message 描述错误原因和上下文的人类可读信息
     */
    data class Error(val message: String) : InitState()
}