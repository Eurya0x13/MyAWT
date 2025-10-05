package io.github.eurya.awt.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.eurya.awt.data.RuntimeLibrary
import io.github.eurya.awt.data.state.InitState
import io.github.eurya.awt.manager.RuntimeLibraryManager
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 初始化视图模型
 *
 * 功能:
 * - 负责应用程序启动时的运行库检查和提取流程管理
 * - 监控运行库状态并在需要时自动提取必要的库文件
 *
 * @author qz919
 * @data 2025/10/2
 */
@HiltViewModel
class InitViewModel @Inject constructor(
    private val libraryManager: RuntimeLibraryManager
) : ViewModel() {

    /**
     * 初始化状态的可变数据流
     *
     * 跟踪应用程序初始化过程的不同状态：
     * - Idle: 初始空闲状态
     * - Checking: 正在检查运行库
     * - Extracting: 正在提取运行库
     * - Success: 初始化成功
     * - Error: 初始化失败
     */
    private val _initState = MutableStateFlow<InitState>(InitState.Idle)

    /**
     * 对外暴露的初始化状态只读数据流
     */
    val initState: StateFlow<InitState> = _initState

    /**
     * 运行库列表的可变状态列表
     *
     * 存储已提取或需要提取的运行库信息
     */
    private val _libraries = MutableStateFlow<List<RuntimeLibrary>>(emptyList())

    /**
     * 对外暴露的运行库只读列表
     */
    val libraries: StateFlow<List<RuntimeLibrary>> = _libraries.asStateFlow()

    /**
     * 检查并提取运行库
     *
     * 启动运行库检查流程，根据检查结果决定是否需要提取运行库
     * 如果运行库已存在则直接使用，否则启动提取流程
     */
    fun checkAndExtractLibraries() {
        viewModelScope.launch {
            _initState.value = InitState.Checking

            try {
                val needsExtraction = libraryManager.checkLibrariesNeedExtraction()

                if (needsExtraction) {
                    _initState.value = InitState.Extracting
                    extractLibraries()
                } else {
                    val existingLibraries = libraryManager.getExtractedLibraries()
                    _libraries.value = existingLibraries
                    _initState.value = InitState.Success
                }
            } catch (e: Exception) {
                _initState.value = InitState.Error("检查运行库失败: ${e.message}")
            }
        }
    }

    /**
     * 解压运行库
     *
     * 从应用程序资源中提取运行库文件到设备存储
     * 更新运行库列表并在完成后通知初始化状态
     */
    private suspend fun extractLibraries() {
        try {
            val extractedLibraries = libraryManager.extractLibraries()

            _libraries.value = extractedLibraries
            _initState.value = InitState.Success

        } catch (e: Exception) {
            _initState.value = InitState.Error("解压运行库失败: ${e.message}")
        }
    }

    /**
     * 重试提取操作
     *
     * 在初始化失败时重新尝试提取运行库
     */
    fun retryExtraction() {
        viewModelScope.launch {
            extractLibraries()
        }
    }
}