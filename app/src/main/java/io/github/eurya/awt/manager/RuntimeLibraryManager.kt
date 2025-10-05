package io.github.eurya.awt.manager

import android.content.Context
import android.util.Log
import io.github.eurya.awt.data.RuntimeLibrary
import io.github.eurya.awt.utils.Architecture
import jakarta.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * 运行库管理器
 *
 * 功能：
 * - 负责管理和解压应用程序运行所需的Java运行时库文件
 * - 从assets目录提取ZIP格式的运行库到应用数据目录，并提供库状态检查、解压进度跟踪等功能
 * - 支持增量解压和完整性验证，确保运行时环境的可靠性
 *
 * @author qz919
 * @data 2025/10/02
 */
class RuntimeLibraryManager @Inject constructor(
    private val context: Context, private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val RUNTIME_DIR_NAME = "runtime_libs/jre17"
    }

    /**
     * 检查运行库是否需要解压
     *
     * 验证目标目录是否存在以及所有期望的库文件是否已完整解压
     * 用于判断应用程序启动时是否需要执行解压流程
     *
     * @return true表示需要解压，false表示所有库文件已就绪
     */
    suspend fun checkLibrariesNeedExtraction(): Boolean {
        return withContext(dispatcher) {
            val targetDir = getRuntimeDir()
            if (!targetDir.exists()) return@withContext true

            // 检查是否所有库都已解压完成
            !isExtractionComplete(targetDir)
        }
    }

    /**
     * 解压所有运行库文件到统一的jre17目录
     *
     * 遍历预定义的库列表，逐个从assets目录解压到统一的jre17目录
     * 创建目标目录结构，确保文件权限正确设置
     *
     * @return 解压完成的库信息列表，包含解压状态标记
     * @throws RuntimeException 当解压过程中发生I/O错误时抛出
     */
    suspend fun extractLibraries(): List<RuntimeLibrary> {
        return withContext(dispatcher) {
            val targetDir = getRuntimeDir()
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            val libraries = getExpectedLibraries()
            val extractedLibraries = mutableListOf<RuntimeLibrary>()

            // 按顺序解压所有库到同一个目录
            libraries.forEach { library ->
                extractZipFromAssets(library, targetDir)
                extractedLibraries.add(library.copy(isExtracted = true))
            }

            // 所有库解压完成后创建标记文件
            File(targetDir, ".extraction_complete").createNewFile()

            extractedLibraries
        }
    }

    /**
     * 获取已解压的库列表及其状态
     *
     * 扫描目标目录，检查每个期望的库是否已解压并验证完整性
     * 返回包含当前解压状态的库信息列表
     *
     * @return 库信息列表，包含每个库的解压状态
     */
    suspend fun getExtractedLibraries(): List<RuntimeLibrary> {
        return withContext(dispatcher) {
            val targetDir = getRuntimeDir()
            if (!targetDir.exists()) return@withContext emptyList()

            getExpectedLibraries().map { library ->
                library.copy(
                    isExtracted = targetDir.exists() && isExtractionComplete(targetDir)
                )
            }
        }
    }

    /**
     * 获取应用程序期望的运行时库配置
     *
     * 定义应用程序运行所需的所有Java运行时库
     * 每个库包含文件名、版本号和预期大小信息
     *
     * @return 预定义的运行时库配置列表
     */
    private fun getExpectedLibraries(): List<RuntimeLibrary> {
        val jreRuntime = if (Architecture.deviceArchitecture == "arm64") {
            RuntimeLibrary(
                "jre17-arm64.zip",  // 修正文件名
                "17", 15104 * 4096
            )
        } else {
            RuntimeLibrary("jre17-x86_64.zip", "17", 16256 * 4096)
        }

        Log.d("RuntimeLibraryManager", "Expected libraries: ${jreRuntime.name}")

        return listOf(
            RuntimeLibrary("cacio.zip", "18", 296 * 4096),
            RuntimeLibrary("universal.zip", "17", 75088 * 4096),
            jreRuntime
        )
    }

    /**
     * 从assets目录解压ZIP文件到目标目录
     *
     * 打开assets中的ZIP文件流，逐个解压条目到目标目录
     * 包含安全检查防止路径遍历攻击
     *
     * @param library 要解压的库信息
     * @param targetDir 解压目标目录（统一的jre17目录）
     * @throws SecurityException 当检测到不安全的ZIP条目时抛出
     * @throws RuntimeException 当解压过程中发生I/O错误时抛出
     */
    private fun extractZipFromAssets(library: RuntimeLibrary, targetDir: File) {
        try {
            context.assets.open("runtime_libs/${library.name}").use { input ->
                ZipInputStream(input).use { zis ->
                    var entry: ZipEntry?
                    while (zis.nextEntry.also { entry = it } != null) {
                        val entryName = entry!!.name
                        val entryFile = File(targetDir, entryName)

                        if (!entryFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                            throw SecurityException("不安全的ZIP条目: $entryName")
                        }

                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            entryFile.parentFile?.mkdirs()

                            FileOutputStream(entryFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                    }
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("解压运行库 ${library.name} 失败", e)
        }
    }

    /**
     * 检查解压是否完整
     *
     * 通过检查是否存在解压完成标记文件来验证所有库是否已成功解压
     *
     * @param runtimeDir 运行时目录
     * @return true表示解压完整，false表示解压可能被中断
     */
    private fun isExtractionComplete(runtimeDir: File): Boolean {
        val completionMarker = File(runtimeDir, ".extraction_complete")
        return completionMarker.exists()
    }

    /**
     * 获取运行时目录
     *
     * @return 统一的运行时目录 File 对象
     */
    private fun getRuntimeDir(): File {
        return File(context.filesDir, RUNTIME_DIR_NAME)
    }

    /**
     * 获取已解压的运行时目录
     *
     * @return 运行时目录，如果未解压则返回null
     */
    suspend fun getRuntimeDirectory(): File? {
        return withContext(dispatcher) {
            val runtimeDir = getRuntimeDir()
            if (runtimeDir.exists() && isExtractionComplete(runtimeDir)) {
                runtimeDir
            } else {
                null
            }
        }
    }

    /**
     * 获取已解压库的文件列表
     *
     * 扫描运行时目录，返回所有实际文件（排除标记文件）
     * 用于验证解压结果或获取运行时需要的文件路径
     *
     * @return 运行时目录中的所有文件列表
     */
    suspend fun getExtractedLibraryFiles(): List<File> {
        return withContext(dispatcher) {
            val runtimeDir = getRuntimeDir()

            if (!runtimeDir.exists()) {
                return@withContext emptyList()
            }

            runtimeDir.walk().filter { it.isFile && it.name != ".extraction_complete" }.toList()
        }
    }

    /**
     * 清理所有已解压的库文件
     *
     * 删除整个运行库目录及其所有内容
     * 用于重置应用程序状态或释放存储空间
     *
     * @return true表示清理成功，false表示清理过程中发生错误
     */
    suspend fun cleanupExtractedLibraries(): Boolean {
        return withContext(dispatcher) {
            try {
                val runtimeDir = getRuntimeDir()
                if (runtimeDir.exists()) {
                    runtimeDir.deleteRecursively()
                }
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * 获取ZIP文件内容列表
     *
     * 在不解压的情况下读取ZIP文件内容，显示其中的目录和文件结构
     * 用于调试或向用户展示ZIP包内容
     *
     * @param library 要检查的库信息
     * @return ZIP文件中所有条目的描述列表，包含类型和大小信息
     */
    suspend fun getZipContents(library: RuntimeLibrary): List<String> {
        return withContext(dispatcher) {
            try {
                val contents = mutableListOf<String>()
                context.assets.open("runtime_libs/${library.name}").use { input ->
                    ZipInputStream(input).use { zis ->
                        var entry: ZipEntry?
                        while (zis.nextEntry.also { entry = it } != null) {
                            // 格式化条目信息：[类型] 路径 (大小字节)
                            val type = if (entry!!.isDirectory) "[DIR]" else "[FILE]"
                            contents.add("$type ${entry.name} (${entry.size} bytes)")
                            zis.closeEntry()
                        }
                    }
                }
                contents
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}