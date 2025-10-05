package io.github.eurya.awt.ui.activity

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import io.github.eurya.awt.data.JavaConfig
import io.github.eurya.awt.manager.JavaLauncherManager
import io.github.eurya.awt.ui.screen.AwtScreen
import io.github.eurya.awt.ui.screen.InitScreen
import io.github.eurya.awt.ui.theme.MyAWTTheme
import io.github.eurya.awt.utils.NativeJavaLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 主活动
 * @author qz919
 * @data 2025/10/2
 */
@AndroidEntryPoint
class AwtActivity : ComponentActivity() {
    private val javaLauncherManager = JavaLauncherManager()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyAWTTheme {
                var isInitialized by remember { mutableStateOf(false) }
                var isFullMode by remember { mutableStateOf(true) }

                Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
                    TopAppBar(
                        actions = {
                            IconButton(onClick = { isFullMode = !isFullMode }) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "切换全屏模式"
                                )
                            }
                        },
                        title = { Text("Awt Test") },
                    )
                }) { innerPadding ->
                    Row(modifier = Modifier.padding(innerPadding)) {
                        if (isInitialized) {
                            LaunchedEffect(Unit) {
                                startJavaApplication(
                                    javaLauncherManager = javaLauncherManager,
                                )
                            }

                            AwtScreen(isFullMode = isFullMode)
                        } else {
                            InitScreen(
                                onInitComplete = { isInitialized = true },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        javaLauncherManager.shutdown()
        NativeJavaLauncher.nativeStopJvm()
    }

    /**
     * 启动 Java 应用
     * @param javaLauncherManager JavaLauncherManager
     */
    private fun startJavaApplication(
        javaLauncherManager: JavaLauncherManager,
    ) {
        val config = JavaConfig(
            home = filesDir.absolutePath,
            nativePath = applicationInfo.nativeLibraryDir,
            jrePath = "${filesDir.absolutePath}/runtime_libs/jre17"
        )

        val jarPath = "${config.home}/app.jar"

        val progressChannel = javaLauncherManager.launchApplicationWithFlow(config, jarPath)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                for (progress in progressChannel) {
                    Log.d(TAG, progress)
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动失败: ${e.message}", e)
            }
        }
    }

    companion object {
        const val TAG = "AwtActivity"
    }
}