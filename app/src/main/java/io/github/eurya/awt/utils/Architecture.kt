package io.github.eurya.awt.utils

import android.os.Build

/**
 * 简化的架构识别类，只区分 arm64 和 x86_64
 *
 * @author qz919
 * @data 2025/10/2
 */
object Architecture {
    val deviceArchitecture: String
        /**
         * 获取当前设备的架构类型
         * @return "arm64" 或 "x86_64"
         */
        get() {
            for (abi in Build.SUPPORTED_64_BIT_ABIS) {
                if (abi == "x86_64") {
                    return "x86_64"
                }
            }
            return "arm64"
        }
}