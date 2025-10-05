package io.github.eurya.awt.ui.screen

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.eurya.awt.data.state.AwtUiState
import io.github.eurya.awt.viewmodel.AwtViewModel
import kotlin.math.min

/**
 * AWT远程桌面流客户端主界面
 *
 * 功能：
 * - 提供两种显示模式：普通模式和全屏模式，点击连接跳转到全屏活动
 * - 普通模式显示完整的控制面板和统计信息，全屏模式专注于图像显示和交互
 * - TODO: 输入内容 和 布局内容
 *
 * @param viewModel 远程桌面视图模型，管理连接状态和图像数据
 *
 * @author qz919
 * @data 2025/10/2
 */
@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun AwtScreen(viewModel: AwtViewModel = hiltViewModel(), isFullMode: Boolean = true) {
    val activity = LocalActivity.current!!
    val uiState by viewModel.uiState.collectAsState()


    if (isFullMode) {
        DisplayViewFull()

        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            ConnectionConfigView(viewModel, uiState)

            Spacer(modifier = Modifier.height(16.dp))

            StatisticsView(uiState)

            Spacer(modifier = Modifier.height(16.dp))

            ImageDisplayView(uiState)

            ConnectionStatusView(uiState)
        }

        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}

/**
 * 连接配置面板组件
 *
 * 功能：
 * - 提供服务器连接配置界面，包括服务器地址、端口输入框和连接/断开按钮
 * - 根据当前连接状态动态更新界面元素的可交互状态和按钮样式
 *
 * @param viewModel 远程桌面视图模型，用于执行连接和断开操作
 * @param uiState 当前UI状态，用于获取连接状态和更新界面
 */
@Composable
fun ConnectionConfigView(viewModel: AwtViewModel, uiState: AwtUiState) {
    LocalActivity.current!!

    var serverHost by remember { mutableStateOf("localhost") }
    var serverPort by remember { mutableStateOf("8888") }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "屏幕流客户端配置", style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = serverHost,
                onValueChange = { serverHost = it },
                label = { Text("服务器地址") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isConnected
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = serverPort,
                onValueChange = { serverPort = it },
                label = { Text("服务器端口") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isConnected
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (uiState.isConnected) {
                        viewModel.disconnect()
                    } else {
                        viewModel.connect(serverHost, serverPort.toIntOrNull() ?: 8888)
                    }
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isConnected) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (uiState.isConnected) "断开连接" else "连接服务器")
            }
        }
    }
}

/**
 * 传输统计信息显示组件
 *
 * 功能：
 * - 显示远程桌面连接的性能统计信息，包括帧率、数据速率、分辨率等关键指标
 * - 仅在连接状态下显示详细统计，未连接时显示等待提示
 *
 * @param uiState 当前UI状态，包含所有需要显示的统计信息
 */
@SuppressLint("DefaultLocale")
@Composable
fun StatisticsView(uiState: AwtUiState) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "统计信息", style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isConnected) {
                StatisticItem("帧数", "${uiState.frameCount}")
                StatisticItem("FPS", String.format("%.1f", uiState.fps))
                StatisticItem("数据速率", String.format("%.2f MB/s", uiState.dataRate))
                StatisticItem("分辨率", "${uiState.width}x${uiState.height}")
                StatisticItem("像素格式", uiState.pixelFormat)
            } else {
                Text(
                    text = "等待连接...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 统计信息项显示组件
 *
 * 功能：
 * - 以标签-值对的形式显示单个统计信息，使用两端对齐的布局确保信息清晰易读
 *
 * @param label 统计项标签文本，描述统计内容的含义
 * @param value 统计项数值文本，显示具体的统计数值
 */
@Composable
fun StatisticItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 图像显示视图组件
 *
 * 功能：
 * - 显示远程桌面图像内容，支持图像缩放和居中对齐
 * - 在无图像时显示连接状态提示，在有图像时显示分辨率水印
 *
 * @param uiState 当前UI状态，包含要显示的位图图像和尺寸信息
 */
@Composable
fun ImageDisplayView(uiState: AwtUiState) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) {
            if (uiState.bitmap != null) {
                Image(
                    bitmap = uiState.bitmap.asImageBitmap(),
                    contentDescription = "屏幕流图像",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentScale = ContentScale.Fit
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "${uiState.width}×${uiState.height}",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (uiState.isConnected) "等待接收图像数据..." else "未连接",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!uiState.isConnected) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请先连接服务器",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

/**
 * 连接状态提示组件
 *
 * 功能：
 * - 在连接出现错误时显示错误信息提示，使用醒目的错误颜色方案吸引用户注意
 * - 无错误时该组件不显示任何内容
 *
 * @param uiState 当前UI状态，包含可能存在的错误信息
 */
@Composable
fun ConnectionStatusView(uiState: AwtUiState) {
    if (uiState.errorMessage != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * 全屏显示视图组件
 *
 * 功能：
 * - 专门为全屏模式设计的图像显示和交互界面，提供精确的点击坐标映射
 * - 支持图像保持原始比例居中显示，并带有红色边框标识图像边界
 *
 * @param viewModel 远程桌面视图模型，用于处理用户交互事件
 */
@Composable
fun DisplayViewFull(viewModel: AwtViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var imageDisplayRect by remember { mutableStateOf(Rect.Zero) }

    if (!uiState.isConnected)
        viewModel.connect("localhost", 8888)

    if (uiState.bitmap != null && uiState.width > 0 && uiState.height > 0) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(uiState.width.toFloat() / uiState.height.toFloat())
                .background(Color.Black)
                .pointerInput(uiState.width, uiState.height) {
                    detectTapGestures { offset ->
                        val relativeX = offset.x - imageDisplayRect.left
                        val relativeY = offset.y - imageDisplayRect.top

                        if (relativeX >= 0 && relativeX < imageDisplayRect.width && relativeY >= 0 && relativeY < imageDisplayRect.height) {

                            val imageX =
                                (relativeX / imageDisplayRect.width * uiState.width).toInt()
                            val imageY =
                                (relativeY / imageDisplayRect.height * uiState.height).toInt()

                            viewModel.moveMouse(imageX, imageY)

                        }
                    }
                }, contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = uiState.bitmap!!.asImageBitmap(),
                contentDescription = "AWT Canvas",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .drawWithContent {
                        val layoutSize = size
                        val imageWidth = uiState.width.toFloat()
                        val imageHeight = uiState.height.toFloat()

                        val scale = min(
                            layoutSize.width / imageWidth, layoutSize.height / imageHeight
                        )

                        val scaledWidth = imageWidth * scale
                        val scaledHeight = imageHeight * scale

                        val left = (layoutSize.width - scaledWidth) / 2
                        val top = (layoutSize.height - scaledHeight) / 2

                        imageDisplayRect = Rect(
                            left = left,
                            top = top,
                            right = left + scaledWidth,
                            bottom = top + scaledHeight
                        )

                        drawContent()

                        drawRect(
                            color = Color.Red,
                            topLeft = Offset(left, top),
                            size = Size(scaledWidth, scaledHeight),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    },
                contentScale = ContentScale.Fit
            )
        }
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview
@Composable
fun PreviewScreenStreamApp() {
    AwtScreen(hiltViewModel())
}