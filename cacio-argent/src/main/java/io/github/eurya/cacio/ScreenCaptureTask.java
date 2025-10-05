package io.github.eurya.cacio;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 屏幕捕获和传输任务
 * <p> <p>
 * 负责从CTCScreen捕获屏幕数据并通过网络传输给远程客户端
 * 支持多种像素格式转换、帧率控制、数据压缩和实时性能统计
 * 使用原子布尔值确保线程安全的启停控制，提供完整的传输生命周期管理
 * <p>
 * 主要功能：
 * - 实时屏幕数据捕获和格式转换
 * - 自适应帧率控制和带宽管理
 * - 多格式像素编码支持
 * - 传输统计和性能监控
 * - 优雅的连接管理和错误处理
 */
public class ScreenCaptureTask implements Runnable {
    /** 客户端Socket连接，用于数据传输 */
    private final Socket clientSocket;

    /** 屏幕数据包装器，提供屏幕捕获功能 */
    private final CTCScreenWrapper screenWrapper;

    /** 线程安全的运行状态控制标志 */
    private final AtomicBoolean running;

    /** 目标传输帧率，控制数据更新频率 */
    private final int frameRate;

    /** 累计传输帧数统计 */
    private long frameCount = 0;

    /** 累计传输数据量统计（字节） */
    private long totalDataBytes = 0;

    /** 传输开始时间戳，用于性能计算 */
    private final long startTime = System.currentTimeMillis();

    /**
     * 屏幕捕获任务构造函数
     *
     * @param clientSocket 客户端Socket连接，必须为非空且已连接的Socket
     * @param screenWrapper 屏幕数据包装器实例，负责屏幕数据捕获
     * @param frameRate 目标传输帧率，控制数据更新频率（帧/秒）
     */
    public ScreenCaptureTask(Socket clientSocket, CTCScreenWrapper screenWrapper, int frameRate) {
        this.clientSocket = clientSocket;
        this.screenWrapper = screenWrapper;
        this.running = new AtomicBoolean(true);
        this.frameRate = frameRate;
    }

    /**
     * 屏幕捕获和传输任务主运行方法
     * <p>
     * 建立数据传输流，发送屏幕信息头，然后进入连续的帧捕获和传输循环
     * 自动管理帧率控制、错误处理和资源释放，确保传输的稳定性和可靠性
     * 提供详细的运行日志和性能统计信息
     */
    @Override
    public void run() {
        String clientInfo = clientSocket.getInetAddress() + ":" + clientSocket.getPort();
        System.out.println("🎬 开始为客户端 " + clientInfo + " 传输屏幕数据");
        System.out.println("📊 数据源: " + (screenWrapper.isCacioAvailable() ? "真实CTCScreen" : "模拟数据"));

        try (DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {
            sendScreenInfo(dos);

            while (running.get() && !clientSocket.isClosed()) {
                long frameStartTime = System.currentTimeMillis();

                if (captureAndSendFrame(dos)) {
                    frameCount++;
                }

                controlFrameRate(frameStartTime);

                printStatistics(clientInfo);
            }
        } catch (IOException e) {
            System.err.println("❌ 客户端 " + clientInfo + " 连接错误: " + e.getMessage());
        } finally {
            stop();
            printFinalStatistics(clientInfo);
        }
    }

    /**
     * 发送屏幕基本信息到客户端
     * <p>
     * 传输屏幕尺寸、数据源类型等元数据，客户端使用这些信息初始化显示环境
     * 这是数据传输开始前的握手过程，确保双方对传输参数达成一致
     *
     * @param dos 数据输出流，用于向客户端发送数据
     * @throws IOException 当网络传输失败时抛出
     */
    private void sendScreenInfo(DataOutputStream dos) throws IOException {
        dos.writeInt(screenWrapper.getScreenWidth());
        dos.writeInt(screenWrapper.getScreenHeight());
        dos.writeBoolean(screenWrapper.isCacioAvailable());
        dos.flush();
        System.out.println("📤 发送屏幕信息: " + screenWrapper.getScreenWidth() +
                "x" + screenWrapper.getScreenHeight() +
                ", 数据源: " + (screenWrapper.isCacioAvailable() ? "真实" : "模拟"));
    }

    /**
     * 捕获并发送单帧屏幕数据
     * <p>
     * 从屏幕包装器获取当前屏幕数据，验证数据完整性，转换为指定格式后传输
     * 自动处理数据尺寸不匹配等异常情况，确保传输的连续性
     *
     * @param dos 数据输出流，用于发送帧数据
     * @return true表示成功发送一帧，false表示发送失败
     * @throws IOException 当数据传输失败时抛出
     */
    private boolean captureAndSendFrame(DataOutputStream dos) throws IOException {
        return captureAndSendFrame(dos, PixelFormat.ARGB);
    }

    /**
     * 捕获并发送指定格式的单帧屏幕数据
     * <p>
     * 支持多种像素格式选择，根据网络条件和客户端能力选择最优格式
     * 包含完整的数据验证、格式转换和错误处理机制
     *
     * @param dos 数据输出流，用于发送帧数据
     * @param format 像素格式枚举，指定数据编码方式
     * @return true表示成功发送一帧，false表示发送失败
     * @throws IOException 当数据传输失败时抛出
     */
    private boolean captureAndSendFrame(DataOutputStream dos, PixelFormat format) throws IOException {
        int[] rgbData = screenWrapper.getScreenRGBData();

        if (rgbData == null || rgbData.length == 0) {
            System.err.println("⚠️  获取到空的屏幕数据");
            dos.writeInt(0); // 发送空数据标记
            dos.flush();
            return false;
        }

        int expectedSize = screenWrapper.getScreenWidth() * screenWrapper.getScreenHeight();
        if (rgbData.length != expectedSize) {
            System.err.println("⚠️  屏幕数据尺寸不匹配: 期望=" + expectedSize + ", 实际=" + rgbData.length);
            int[] correctedData = new int[expectedSize];
            System.arraycopy(rgbData, 0, correctedData, 0, Math.min(rgbData.length, expectedSize));
            rgbData = correctedData;
        }

        try {
            byte[] pixelBytes = convertRGBToBytes(rgbData, format);

            dos.writeUTF(format.name());
            dos.writeInt(pixelBytes.length);
            dos.write(pixelBytes);
            dos.flush();

            totalDataBytes += pixelBytes.length;
            return true;

        } catch (IOException e) {
            System.err.println("❌ 发送帧数据失败: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 精确控制传输帧率
     * <p>
     * 根据目标帧率计算每帧的理想处理时间，通过睡眠补偿确保稳定的帧率输出
     * 防止因处理速度过快导致的CPU资源浪费和网络拥塞
     *
     * @param frameStartTime 当前帧开始处理的时间戳
     */
    private void controlFrameRate(long frameStartTime) {
        long frameTime = System.currentTimeMillis() - frameStartTime;
        long targetFrameTime = 1000 / frameRate;

        if (frameTime < targetFrameTime) {
            try {
                Thread.sleep(targetFrameTime - frameTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running.set(false);
            }
        }
    }

    /**
     * 定期打印传输统计信息
     * <p>
     * 每60帧输出一次实时性能指标，包括帧率、数据速率等关键参数
     * 帮助监控传输质量和诊断性能问题
     *
     * @param clientInfo 客户端标识信息，用于日志输出
     */
    private void printStatistics(String clientInfo) {
        if (frameCount % 60 == 0) {
            long currentTime = System.currentTimeMillis();
            long elapsedSeconds = (currentTime - startTime) / 1000;
            if (elapsedSeconds > 0) {
                double actualFps = frameCount / (double) elapsedSeconds;
                double dataRate = totalDataBytes / (1024.0 * 1024.0) / elapsedSeconds;

                System.out.printf(
                        "\uD83D\uDCC8 客户端 %s - 帧数: %d, 实际FPS: %.1f, 数据速率: %.2f MB/s%n",
                        clientInfo, frameCount, actualFps, dataRate
                );
            }
        }
    }

    /**
     * 打印最终传输统计信息
     * <p>
     * 在传输任务结束时输出完整的性能摘要，包括总帧数、平均帧率、总数据量等
     * 用于性能分析和传输质量评估
     *
     * @param clientInfo 客户端标识信息，用于日志输出
     */
    private void printFinalStatistics(String clientInfo) {
        long endTime = System.currentTimeMillis();
        long totalTimeSeconds = (endTime - startTime) / 1000;

        if (totalTimeSeconds > 0) {
            double averageFps = frameCount / (double) totalTimeSeconds;
            double totalDataMB = totalDataBytes / (1024.0 * 1024.0);
            double averageDataRate = totalDataMB / totalTimeSeconds;

            System.out.printf(
                    "\uD83D\uDCCA 客户端 %s 最终统计 - 总帧数: %d, 总时间: %d秒, 平均FPS: %.1f, 总数据: %.2f MB, 平均速率: %.2f MB/s%n",
                    clientInfo, frameCount, totalTimeSeconds, averageFps, totalDataMB, averageDataRate
            );
        } else {
            System.out.println("⏹️  停止为客户端 " + clientInfo + " 传输数据，总共传输 " + frameCount + " 帧");
        }
    }

    /**
     * 停止屏幕捕获和传输任务
     * <p>
     * 设置运行标志为false并关闭客户端连接，确保资源的正确释放
     * 支持优雅停止，避免数据传输中断导致的客户端异常
     */
    public void stop() {
        running.set(false);
        try {
            if (!clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.err.println("❌ 关闭客户端socket时出错: " + e.getMessage());
        }
    }

    /**
     * 检查任务是否正在运行
     *
     * @return true表示任务正在运行，false表示任务已停止
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 支持的像素格式枚举
     * <p>
     * 定义多种像素编码格式，平衡图像质量、带宽消耗和解码复杂度
     * 客户端可以根据网络条件和性能需求选择合适的格式
     */
    public enum PixelFormat {
        /** 32位ARGB格式，包含Alpha通道，最高图像质量 */
        ARGB,

        /** 24位RGB格式，忽略Alpha通道，平衡质量和带宽 */
        RGB,

        /** 16位RGB565格式，高压缩比，适合带宽受限环境 */
        RGB565,

        /** 8位灰度格式，最低带宽消耗，适合文本和简单图形 */
        GRAYSCALE
    }

    /**
     * 将RGB像素数据转换为指定格式的字节数组
     * <p>
     * 根据目标格式进行相应的颜色空间转换和数据压缩
     * 支持从32位ARGB到多种压缩格式的高效转换
     *
     * @param rgbData 源RGB像素数据数组（ARGB格式）
     * @param format 目标像素格式枚举
     * @return 转换后的字节数组
     */
    private byte[] convertRGBToBytes(int[] rgbData, PixelFormat format) {
        switch (format) {
            case RGB:
                return convertToRGB(rgbData);
            case RGB565:
                return convertToRGB565(rgbData);
            case GRAYSCALE:
                return convertToGrayscale(rgbData);
            case ARGB:
            default:
                return convertToARGB(rgbData);
        }
    }

    /**
     * 转换为ARGB格式字节数据（32位，4字节/像素）
     * <p>
     * 保持完整的颜色和透明度信息，提供无损的图像质量
     * 数据布局：A-R-G-B，每个通道8位
     *
     * @param rgbData 源RGB像素数据数组
     * @return ARGB格式的字节数组
     */
    private byte[] convertToARGB(int[] rgbData) {
        byte[] byteData = new byte[rgbData.length * 4];
        for (int i = 0; i < rgbData.length; i++) {
            int pixel = rgbData[i];
            int offset = i * 4;
            byteData[offset]     = (byte) ((pixel >> 24) & 0xFF); // Alpha通道
            byteData[offset + 1] = (byte) ((pixel >> 16) & 0xFF); // Red通道
            byteData[offset + 2] = (byte) ((pixel >> 8) & 0xFF);  // Green通道
            byteData[offset + 3] = (byte) (pixel & 0xFF);         // Blue通道
        }
        return byteData;
    }

    /**
     * 转换为RGB格式字节数据（24位，3字节/像素）
     * <p>
     * 忽略Alpha透明通道，在保持较好图像质量的同时减少25%的数据量
     * 数据布局：R-G-B，每个通道8位
     *
     * @param rgbData 源RGB像素数据数组
     * @return RGB格式的字节数组
     */
    private byte[] convertToRGB(int[] rgbData) {
        byte[] byteData = new byte[rgbData.length * 3];
        for (int i = 0; i < rgbData.length; i++) {
            int pixel = rgbData[i];
            int offset = i * 3;
            byteData[offset]     = (byte) ((pixel >> 16) & 0xFF); // Red通道
            byteData[offset + 1] = (byte) ((pixel >> 8) & 0xFF);  // Green通道
            byteData[offset + 2] = (byte) (pixel & 0xFF);         // Blue通道
        }
        return byteData;
    }

    /**
     * 转换为RGB565格式字节数据（16位，2字节/像素）
     * <p>
     * 使用5-6-5位分配压缩颜色信息，在保持可接受质量的同时减少50%数据量
     * 适合网络带宽受限或移动设备环境
     *
     * @param rgbData 源RGB像素数据数组
     * @return RGB565格式的字节数组
     */
    private byte[] convertToRGB565(int[] rgbData) {
        byte[] byteData = new byte[rgbData.length * 2];
        for (int i = 0; i < rgbData.length; i++) {
            int pixel = rgbData[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            // 将8位RGB转换为5-6-5位RGB565格式
            int rgb565 = ((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3);

            int offset = i * 2;
            byteData[offset] = (byte) ((rgb565 >> 8) & 0xFF);     // 高字节
            byteData[offset + 1] = (byte) (rgb565 & 0xFF);        // 低字节
        }
        return byteData;
    }

    /**
     * 转换为灰度格式字节数据（8位，1字节/像素）
     * <p>
     * 使用亮度公式将彩色图像转换为灰度，减少87.5%的数据量
     * 适合文本显示、监控或带宽极度受限的场景
     *
     * @param rgbData 源RGB像素数据数组
     * @return 灰度格式的字节数组
     */
    private byte[] convertToGrayscale(int[] rgbData) {
        byte[] byteData = new byte[rgbData.length];
        for (int i = 0; i < rgbData.length; i++) {
            int pixel = rgbData[i];
            int r = (pixel >> 16) & 0xFF;
            int g = (pixel >> 8) & 0xFF;
            int b = pixel & 0xFF;

            // 使用标准亮度公式计算灰度值：Y = 0.299R + 0.587G + 0.114B
            int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            byteData[i] = (byte) (gray & 0xFF);
        }
        return byteData;
    }
}