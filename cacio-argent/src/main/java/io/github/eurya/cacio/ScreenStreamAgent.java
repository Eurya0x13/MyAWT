package io.github.eurya.cacio;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cacio屏幕流Java Agent代理
 * <p>
 * 通过Java Agent机制在AWT/Swing应用程序启动时自动注入屏幕流服务器功能
 * 支持静态加载（premain）和动态附加（agentmain）两种部署方式，提供零代码侵入式的远程桌面服务
 * 自动解析配置参数、启动流媒体服务器并管理完整的服务生命周期
 */
public class ScreenStreamAgent {

    /** 服务器启动状态原子标记，确保线程安全的启动控制 */
    private static final AtomicBoolean started = new AtomicBoolean(false);

    /** 服务器线程引用，用于生命周期管理 */
    private static Thread serverThread;

    /**
     * JVM启动时Agent预加载方法
     * <p>
     * 在目标应用程序主类执行前被JVM调用，用于早期初始化和服务注入
     * 这是Java Agent的标准入口点，适用于应用程序启动时即需要屏幕流服务的场景
     *
     * @param agentArgs 代理参数字符串，格式为"key1=value1,key2=value2"
     * @param inst Instrumentation服务实例，提供类转换和运行时监控能力
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("🚀 Cacio Screen Stream Agent 启动");
        System.out.println("📝 Agent参数: " + agentArgs);

        AgentConfig config = parseAgentArgs(agentArgs);

        startScreenStreamServer(config);

        addShutdownHook();

        System.out.println("✅ Cacio Screen Stream Agent 初始化完成");
    }

    /**
     * 动态附加Agent方法
     * <p>
     * 在目标JVM运行时动态加载Agent，支持对已运行应用程序的服务注入
     * 通过Attach API实现，适用于需要运行时启用屏幕流服务的场景
     *
     * @param agentArgs 代理参数字符串，格式与premain相同
     * @param inst Instrumentation服务实例
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        System.out.println("🔄 Cacio Screen Stream Agent 动态附加");
        premain(agentArgs, inst);
    }

    /**
     * 解析Agent配置参数字符串
     * <p>
     * 将逗号分隔的键值对字符串转换为结构化的配置对象
     * 支持端口、帧率、屏幕尺寸和自动启动等核心参数的配置
     *
     * @param agentArgs 代理参数字符串，格式："port=8888,fps=60,width=1280,height=720,autostart=true"
     * @return 解析后的AgentConfig配置对象，包含所有有效参数
     */
    private static AgentConfig parseAgentArgs(String agentArgs) {
        AgentConfig config = new AgentConfig();

        if (agentArgs != null && !agentArgs.trim().isEmpty()) {
            String[] args = agentArgs.split(",");
            for (String arg : args) {
                String[] keyValue = arg.split("=");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = keyValue[1].trim();

                    switch (key) {
                        case "port":
                            config.port = Integer.parseInt(value);
                            break;
                        case "fps":
                            config.frameRate = Integer.parseInt(value);
                            break;
                        case "width":
                            config.screenWidth = Integer.parseInt(value);
                            break;
                        case "height":
                            config.screenHeight = Integer.parseInt(value);
                            break;
                        case "autostart":
                            config.autoStart = Boolean.parseBoolean(value);
                            break;
                    }
                }
            }
        }

        return config;
    }

    /**
     * 启动屏幕流媒体服务器
     * <p>
     * 使用反射机制动态加载和初始化屏幕流服务器组件，避免编译时依赖
     * 支持配置验证、重复启动保护和异步服务启动
     *
     * @param config 屏幕流服务器配置参数
     */
    private static void startScreenStreamServer(AgentConfig config) {
        if (!config.autoStart) {
            System.out.println("⏸️  自动启动已禁用，屏幕流服务器未启动");
            return;
        }

        if (started.getAndSet(true)) {
            System.out.println("⚠️  屏幕流服务器已在运行");
            return;
        }

        serverThread = new Thread(() -> {
            try {
                System.out.println("🖥️  启动屏幕流服务器...");
                System.out.println("📊 配置: 端口=" + config.port +
                        ", 帧率=" + config.frameRate + "FPS" +
                        ", 屏幕尺寸=" + config.screenWidth + "x" + config.screenHeight);

                ClassLoader classLoader = ScreenStreamAgent.class.getClassLoader();
                Class<?> serverClass = classLoader.loadClass("io.github.eurya.cacio.ScreenStreamServer");
                Class<?> wrapperClass = classLoader.loadClass("io.github.eurya.cacio.CTCScreenWrapper");

                Object screenWrapper = wrapperClass.newInstance();

                if (config.screenWidth > 0 && config.screenHeight > 0) {
                    Method setSizeMethod = wrapperClass.getMethod("setScreenSize", int.class, int.class);
                    setSizeMethod.invoke(screenWrapper, config.screenWidth, config.screenHeight);
                }

                Object server = serverClass.getConstructor(wrapperClass, int.class, int.class)
                        .newInstance(screenWrapper, config.port, config.frameRate);

                Method startMethod = serverClass.getMethod("start");
                startMethod.invoke(server);

            } catch (Exception e) {
                System.err.println("❌ 启动屏幕流服务器失败: " + e.getMessage());
                e.printStackTrace();
                started.set(false);
            }
        });

        serverThread.setName("Cacio-Screen-Stream-Server");
        serverThread.setDaemon(true); // 设置为守护线程，随JVM退出自动终止
        serverThread.start();

        try {
            Thread.sleep(1000);
            System.out.println("✅ 屏幕流服务器启动完成，可通过端口 " + config.port + " 连接");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 注册JVM关闭钩子
     * <p>
     * 在JVM关闭时自动清理屏幕流服务器资源，确保优雅关闭
     * 防止资源泄漏和连接残留，提供完整的生命周期管理
     */
    private static void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("🛑 正在关闭屏幕流服务器...");
            if (serverThread != null && serverThread.isAlive()) {
                serverThread.interrupt();
            }
            System.out.println("✅ Cacio Screen Stream Agent 已关闭");
        }));
    }

    /**
     * 检查屏幕流服务器运行状态
     * <p>
     * 提供外部状态查询接口，用于监控Agent运行状态
     * 可在应用程序中调用此方法验证服务是否正常启动
     *
     * @return true表示服务器正在运行，false表示服务器未运行或已停止
     */
    public static boolean isServerRunning() {
        return started.get() && serverThread != null && serverThread.isAlive();
    }

    /**
     * 停止屏幕流服务器
     * <p>
     * 提供外部控制接口，允许运行时动态停止屏幕流服务
     * 适用于需要临时禁用远程桌面功能的场景
     */
    public static void stopServer() {
        if (serverThread != null) {
            serverThread.interrupt();
            started.set(false);
        }
    }
}