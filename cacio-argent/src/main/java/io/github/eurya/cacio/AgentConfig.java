package io.github.eurya.cacio;

/**
 * AWT远程桌面代理服务器配置类
 * <p>
 * 定义远程桌面代理服务器的核心运行参数，包括网络连接、图像传输和显示设置。
 * 提供默认配置值，支持服务器端和客户端的配置同步。
 * 使用简单字段设计便于JSON序列化和配置持久化。
 */
public class AgentConfig {
    /**
     * 服务器监听端口号，用于建立客户端连接
     * 默认值8888，应在防火墙中开放此端口以允许客户端连接
     */
    public int port = 8888;

    /**
     * 屏幕图像传输帧率，控制图像更新频率
     * 默认值60帧/秒，影响网络带宽使用和画面流畅度
     * 较高值提供更流畅体验但增加带宽消耗
     */
    public int frameRate = 60;

    /**
     * 虚拟屏幕显示宽度，单位为像素
     * 默认值1280像素，定义远程桌面的水平分辨率
     */
    public int screenWidth = 1280;

    /**
     * 虚拟屏幕显示高度，单位为像素
     * 默认值720像素，定义远程桌面的垂直分辨率
     */
    public int screenHeight = 720;

    /**
     * 服务器启动后是否自动开始服务
     * 默认true，设置为false时需要手动启动服务
     */
    public boolean autoStart = true;

    /**
     * 生成配置信息的格式化字符串表示
     *
     * 用于调试日志和配置验证，显示所有关键配置参数的当前值。
     * 格式：AgentConfig{port=8888, fps=60, screen=1280x720, autoStart=true}
     *
     * @return 包含所有配置参数的格式化字符串
     */
    @SuppressWarnings("DefaultLocale")
    @Override
    public String toString() {
        return String.format("AgentConfig{port=%d, fps=%d, screen=%dx%d, autoStart=%s}",
                port, frameRate, screenWidth, screenHeight, autoStart);
    }
}