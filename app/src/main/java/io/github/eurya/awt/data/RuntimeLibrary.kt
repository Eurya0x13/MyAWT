package io.github.eurya.awt.data

/**
 * 运行库数据类
 *
 * 功能：
 * - 表示一个运行时库的完整信息，包含库文件的基本属性、版本信息和提取状态
 * - 用于跟踪和管理应用程序依赖的Java运行时库文件
 *
 * @property name 运行库文件名，包含文件扩展名（如：jre17.zip）
 * @property version 运行库版本号，标识Java运行时版本（如：17）
 * @property size 运行库文件大小，以字节为单位，用于验证文件完整性
 * @property isExtracted 库文件是否已成功提取到目标目录，用于状态跟踪
 *
 * @author qz919
 * @data 2025/10/02
 */
data class RuntimeLibrary(
    val name: String,
    val version: String,
    val size: Long,
    val isExtracted: Boolean = false
)