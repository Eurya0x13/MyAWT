package io.github.eurya.cacio;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cacio屏幕流服务器主控制器
 * <p>
 * 负责管理远程桌面服务的完整生命周期，包括客户端连接管理、屏幕数据传输和输入事件处理
 * 采用多线程架构支持多客户端并发访问，提供高效的资源管理和优雅的服务启停机制
 * <p>
 * 核心功能：
 * - 多客户端并发连接管理
 * - 屏幕数据实时捕获和流式传输
 * - 客户端输入事件接收和处理
 * - 动态资源分配和清理
 * - 服务状态监控和统计
 */
public class ScreenStreamServer {
    /** 服务器监听端口号 */
    private final int port;

    /** 屏幕数据传输帧率（帧/秒） */
    private final int frameRate;

    /** 线程池执行器，管理所有客户端任务的并发执行 */
    private final ExecutorService executor;

    /** 线程安全的屏幕捕获任务列表，支持并发访问 */
    private final CopyOnWriteArrayList<ScreenCaptureTask> clientTasks;

    /** 线程安全的客户端事件处理任务列表 */
    private final CopyOnWriteArrayList<ClientEventTask> eventTasks;

    /** 服务器运行状态标志，volatile确保多线程可见性 */
    private volatile boolean running;

    /** 服务器Socket监听器 */
    private ServerSocket serverSocket;

    /** 屏幕数据包装器，提供屏幕捕获功能 */
    private final CTCScreenWrapper screenWrapper;

    /**
     * 屏幕流服务器构造函数
     * <p>
     * 初始化服务器配置参数和资源管理器，准备服务启动环境
     * 使用缓存线程池动态管理客户端连接，优化资源利用率
     *
     * @param screenWrapper 屏幕数据包装器实例，负责屏幕数据捕获
     * @param port 服务器监听端口，客户端通过此端口连接
     * @param frameRate 屏幕数据传输帧率，控制画面更新频率
     */
    public ScreenStreamServer(CTCScreenWrapper screenWrapper, int port, int frameRate) {
        this.screenWrapper = screenWrapper;
        this.port = port;
        this.frameRate = frameRate;
        this.executor = Executors.newCachedThreadPool();
        this.clientTasks = new CopyOnWriteArrayList<>();
        this.eventTasks = new CopyOnWriteArrayList<>();
        this.running = false;
    }

    /**
     * 启动屏幕流服务器
     * <p>
     * 初始化服务器Socket监听器，启动客户端接受循环，开始服务
     * 输出详细的启动信息和配置参数，便于监控和故障诊断
     * 支持重复启动保护，避免资源冲突
     */
    public void start() {
        if (running) {
            System.out.println("⚠️  服务器已在运行");
            return;
        }

        running = true;

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("🎯 Cacio屏幕流服务器启动在端口 " + port);
            System.out.println("📏 屏幕尺寸: " + screenWrapper.getScreenWidth() + "x" + screenWrapper.getScreenHeight());
            System.out.println("🎞️  目标帧率: " + frameRate + " FPS");
            System.out.println("📊 数据源: " + (screenWrapper.isCacioAvailable() ? "真实CTCScreen" : "模拟数据"));
            System.out.println("🖱️⌨️  已启用客户端事件处理");

            startClientAcceptLoop();

        } catch (IOException e) {
            System.err.println("❌ 启动服务器时出错: " + e.getMessage());
            running = false;
        }
    }

    /**
     * 启动客户端连接接受循环
     * <p>
     * 在独立线程中持续监听客户端连接请求，为每个连接创建专用的数据传输和事件处理任务
     * 使用守护线程确保JVM退出时自动终止，避免资源泄漏
     */
    private void startClientAcceptLoop() {
        Thread acceptThread = new Thread(() -> {
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    String clientAddress = clientSocket.getInetAddress().getHostAddress();
                    System.out.println("🔗 新的客户端连接: " + clientAddress);

                    ScreenCaptureTask screenTask = new ScreenCaptureTask(clientSocket, screenWrapper, frameRate);
                    clientTasks.add(screenTask);
                    executor.execute(screenTask);

                    ClientEventTask eventTask = new ClientEventTask(clientSocket);
                    eventTasks.add(eventTask);
                    executor.execute(eventTask);

                    cleanupFinishedTasks();

                } catch (IOException e) {
                    if (running) {
                        System.err.println("❌ 接受客户端连接时出错: " + e.getMessage());
                    }
                }
            }
        });

        acceptThread.setName("Client-Accept-Thread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /**
     * 清理已完成的任务
     * <p>
     * 定期移除已停止的客户端任务，释放相关资源
     * 维护活动连接统计，提供运行状态监控
     */
    private void cleanupFinishedTasks() {
        clientTasks.removeIf(task -> !task.isRunning());

        eventTasks.removeIf(task -> !task.isRunning());

        System.out.println("📊 当前连接客户端数: " + clientTasks.size() +
                ", 事件处理任务数: " + eventTasks.size());
    }

    /**
     * 停止服务器并释放所有资源
     * <p>
     * 执行优雅关闭流程：停止接受新连接、终止所有客户端任务、关闭线程池和服务器Socket
     * 确保所有网络连接和系统资源得到正确释放
     */
    public void stop() {
        running = false;

        for (ScreenCaptureTask task : clientTasks) {
            task.stop();
        }
        clientTasks.clear();

        for (ClientEventTask task : eventTasks) {
            task.stop();
        }
        eventTasks.clear();

        executor.shutdown();

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("❌ 关闭服务器socket时出错: " + e.getMessage());
            }
        }

        System.out.println("🛑 服务器已停止");
    }

    /**
     * 获取当前连接的客户端数量
     * <p>
     * 统计当前活跃的屏幕捕获任务数量，反映实际连接的客户端数
     *
     * @return 当前活跃的客户端连接数量
     */
    public int getConnectedClientCount() {
        return (int) clientTasks.stream().filter(ScreenCaptureTask::isRunning).count();
    }

    /**
     * 获取活跃的事件处理任务数量
     * <p>
     * 统计当前正在处理客户端输入事件的任务数量
     *
     * @return 当前活跃的事件处理任务数量
     */
    public int getEventTaskCount() {
        return (int) eventTasks.stream().filter(ClientEventTask::isRunning).count();
    }

    /**
     * 检查服务器运行状态
     * <p>
     * 提供服务器当前运行状态的查询接口
     *
     * @return true表示服务器正在运行，false表示服务器已停止
     */
    public boolean isRunning() {
        return running;
    }
}