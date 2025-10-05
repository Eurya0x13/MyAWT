package io.github.eurya.awt.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.sharp.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.eurya.awt.data.state.InitState
import io.github.eurya.awt.data.RuntimeLibrary
import io.github.eurya.awt.viewmodel.InitViewModel
import kotlinx.coroutines.delay

/**
 * 应用程序初始化界面
 *
 * 功能：
 * - 负责显示运行库检查和提取过程的完整流程，包括空闲、检查、解压、成功和错误五种状态
 * - 自动触发初始化检查并在完成后通过回调通知父组件
 *
 * @param viewModel 初始化视图模型，管理运行库状态和提取流程
 * @param onInitComplete 初始化完成回调函数，在所有运行库准备就绪后调用
 *
 * @author qz919
 * @data 2025/10/02
 */
@Composable
fun InitScreen(
    viewModel: InitViewModel = hiltViewModel(), onInitComplete: () -> Unit
) {
    val initState by viewModel.initState.collectAsState();
    val libraries by viewModel.libraries.collectAsState()

    LaunchedEffect(initState) {
        if (initState is InitState.Success) {
            delay(1000)
            onInitComplete()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkAndExtractLibraries()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        when (val state = initState) {
            is InitState.Idle -> {
                InitIdleView()
            }

            is InitState.Checking -> {
                CheckingView()
            }

            is InitState.Extracting -> {
                ExtractingView()
            }

            is InitState.Success -> {
                InitSuccessView(libraries = libraries)
            }

            is InitState.Error -> {
                InitErrorView(
                    errorMessage = state.message, onRetry = viewModel::retryExtraction
                )
            }
        }
    }
}

/**
 * 初始化空闲状态界面
 *
 * 功能：
 * - 显示初始等待界面，通常在初始化流程开始前的短暂时刻显示
 * - 包含"准备初始化"文本和圆形进度指示器
 */
@Composable
private fun InitIdleView() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "准备初始化", style = MaterialTheme.typography.headlineMedium
        )
        CircularProgressIndicator()
    }
}

/**
 * 运行库检查状态界面
 *
 * 功能：
 * - 显示运行库完整性检查过程，告知用户当前正在进行库文件验证
 * - 使用圆形进度指示器表示检查操作正在进行中
 */
@Composable
private fun CheckingView() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "检查运行库...", style = MaterialTheme.typography.headlineMedium
        )
        CircularProgressIndicator()
    }
}

/**
 * 运行库解压状态界面
 *
 * 功能：
 * - 显示运行库解压进度
 * - 实时更新每个库文件的解压状态
 */
@Composable
private fun ExtractingView() {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "解压运行库...", style = MaterialTheme.typography.headlineMedium
        )

        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(0.8f),
            color = MaterialTheme.colorScheme.primary
        )

    }
}

/**
 * 初始化成功状态界面
 *
 * 功能：
 * - 显示初始化完成确认界面，使用对勾图标和成功文本提示用户
 * - 展示所有已成功解压的运行库列表供用户确认
 *
 * @param libraries 已成功解压的运行库列表，显示每个库的详细信息
 */
@Composable
private fun InitSuccessView(libraries: List<RuntimeLibrary>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "成功",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )

        Text(
            text = "初始化完成", style = MaterialTheme.typography.headlineMedium
        )

        LibraryList(libraries = libraries)
    }
}

/**
 * 初始化错误状态界面
 *
 * 功能：
 * - 显示初始化过程中遇到的错误信息，提供重试操作按钮
 * - 使用错误颜色的图标和文本来突出显示问题状态
 *
 * @param errorMessage 详细的错误描述信息，帮助用户理解问题原因
 * @param onRetry 重试回调函数，用户点击重试按钮时触发重新初始化
 */
@Composable
private fun InitErrorView(errorMessage: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Icon(
            imageVector = Icons.Sharp.Close,
            contentDescription = "错误",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )

        Text(
            text = "初始化失败", style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )

        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}

/**
 * 运行库列表显示组件
 *
 * 功能：
 * - 以垂直列表形式展示所有运行库的当前状态，每个库显示为独立的卡片项
 * - 在解压和成功状态中都会显示，用于提供详细的进度反馈
 *
 * @param libraries 要显示的运行库列表，每个库包含名称、版本和解压状态
 */
@Composable
private fun LibraryList(libraries: List<RuntimeLibrary>) {
    Column(
        modifier = Modifier.fillMaxWidth(0.8f), verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        libraries.forEach { library ->
            LibraryItem(library = library)
        }
    }
}

/**
 * 单个运行库项显示组件
 *
 * 功能：
 * - 以卡片形式展示单个运行库的详细信息，包括库名称、版本号和解压状态图标
 * - 已解压的库显示对勾图标，未解压的库显示进度指示器
 *
 * @param library 要显示的运行库数据对象，包含所有需要展示的信息
 */
@Composable
private fun LibraryItem(library: RuntimeLibrary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = library.name, style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "v${library.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (library.isExtracted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已解压",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Preview
@Composable
fun preViewLibraryItem() {
    MaterialTheme {
        LibraryItem(
            RuntimeLibrary(
                name = "test", version = "1.0", size = 512 * 512
            )
        )
    }
}