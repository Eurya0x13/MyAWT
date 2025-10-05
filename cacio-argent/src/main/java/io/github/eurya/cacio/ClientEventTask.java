package io.github.eurya.cacio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.Socket;

/**
 * 客户端事件处理任务 - 使用CTCAndroidInput和反射机制
 * <p>
 * 负责接收和处理远程客户端发送的鼠标、键盘和触摸事件，通过反射机制调用Cacio库的输入处理方法
 * 将网络传输的输入事件转换为Java AWT可识别的系统输入事件，实现远程输入控制
 * <p>
 * 支持的事件类型包括：
 * - 鼠标移动、点击、释放、滚轮
 * - 键盘按键按下、释放
 * - 字符输入
 * <p>
 * 使用反射机制动态加载CTCAndroidInput类，避免编译时依赖，提高代码灵活性
 */
public class ClientEventTask implements Runnable {
    /** 客户端Socket连接，用于接收事件数据 */
    private final Socket clientSocket;

    /** 任务运行状态标志，用于优雅停止事件处理 */
    private volatile boolean running;

    /** 客户端地址信息，用于日志和调试 */
    private final String clientAddress;

    // CTCAndroidInput 反射相关字段

    /** receiveData方法反射对象，核心的输入事件分发方法 */
    private Method receiveDataMethod;

    /** CTCAndroidInput是否可用的标志，初始化失败时禁用事件处理 */
    private boolean ctcAvailable = false;

    // CTCAndroidInput 事件类型常量定义
    /** 光标位置事件类型，对应鼠标移动操作 */
    private static final int EVENT_TYPE_CURSOR_POS = 1003;

    /** 键盘按键事件类型，处理物理按键操作 */
    private static final int EVENT_TYPE_KEY = 1005;

    /** 鼠标按钮事件类型，处理鼠标点击操作 */
    private static final int EVENT_TYPE_MOUSE_BUTTON = 1006;

    /** 滚轮滚动事件类型，处理鼠标滚轮操作 */
    private static final int EVENT_TYPE_SCROLL = 1007;

    /** 字符输入事件类型，处理文本字符输入 */
    private static final int EVENT_TYPE_CHAR = 1000;

    /**
     * 客户端事件处理任务构造函数
     * <p>
     * 初始化客户端连接信息并尝试加载CTCAndroidInput类
     * 如果CTCAndroidInput初始化失败，任务仍会运行但不会处理输入事件
     *
     * @param clientSocket 客户端Socket连接，必须为非空且已连接的Socket
     */
    public ClientEventTask(Socket clientSocket) {
        this.clientSocket = clientSocket;
        this.clientAddress = clientSocket.getInetAddress().getHostAddress();
        this.running = true;

        // 初始化CTCAndroidInput反射机制
        initializeCTCAndroidInput();
    }

    /**
     * 使用反射机制初始化CTCAndroidInput类和方法
     * <p>
     * 动态加载Cacio库中的CTCAndroidInput类，并获取其receiveData静态方法
     * 如果类或方法不存在，将设置ctcAvailable为false，事件处理将进入降级模式
     * 此设计允许在没有Cacio依赖的情况下编译和运行，提高代码的适应性
     */
    private void initializeCTCAndroidInput() {
        try {
            // 使用反射动态加载CTCAndroidInput类
            /* CTCAndroidInput类反射对象，用于动态调用输入处理方法 */
            Class<?> ctcAndroidInputClass = Class.forName("com.github.caciocavallosilano.cacio.ctc.CTCAndroidInput");

            // 获取receiveData静态方法，该方法接收5个int参数
            receiveDataMethod = ctcAndroidInputClass.getMethod("receiveData",
                    int.class, int.class, int.class, int.class, int.class);

            ctcAvailable = true;
            System.out.println("✅ CTCAndroidInput 反射初始化成功");

        } catch (ClassNotFoundException e) {
            System.err.println("❌ CTCAndroidInput 类未找到，请确保依赖已添加: " + e.getMessage());
            ctcAvailable = false;
        } catch (NoSuchMethodException e) {
            System.err.println("❌ CTCAndroidInput.receiveData 方法未找到: " + e.getMessage());
            ctcAvailable = false;
        } catch (Exception e) {
            System.err.println("❌ 初始化CTCAndroidInput时发生错误: " + e.getMessage());
            ctcAvailable = false;
        }
    }

    /**
     * 事件处理任务主运行方法
     * <p>
     * 创建输入流监听客户端发送的事件消息，持续处理直到连接断开或任务被停止
     * 使用BufferedReader按行读取事件数据，确保事件处理的实时性和顺序性
     * 自动管理资源释放，在任务结束时关闭输入流和Socket连接
     */
    @Override
    public void run() {
        System.out.println("🎯 开始处理客户端事件: " + clientAddress);
        System.out.println("📊 CTCAndroidInput 可用: " + ctcAvailable);

        if (!ctcAvailable) {
            System.err.println("⚠️  CTCAndroidInput不可用，事件处理将不会生效");
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()))) {

            String message;
            // 持续读取事件消息，直到连接关闭或任务停止
            while (running && (message = reader.readLine()) != null) {
                processEvent(message);
            }

        } catch (IOException e) {
            if (running) {
                System.err.println("❌ 读取客户端事件时出错: " + e.getMessage());
            }
        } finally {
            System.out.println("🔌 客户端事件处理结束: " + clientAddress);
        }
    }

    /**
     * 处理客户端发送的单个事件消息
     * <p>
     * 解析事件消息格式，根据事件类型分发到对应的处理方法
     * 事件格式：EVENT_TYPE|param1|param2|...
     * 如果CTCAndroidInput不可用，仅记录事件日志而不实际处理
     *
     * @param message 客户端发送的原始事件消息字符串
     */
    private void processEvent(String message) {
        if (!ctcAvailable) {
            // CTC不可用时只打印日志，不处理事件
            System.out.println("📝 收到事件(CTC不可用): " + message);
            return;
        }

        try {
            // 使用竖线分隔符解析事件消息
            String[] parts = message.split("\\|");
            if (parts.length < 1) {
                System.err.println("⚠️  无效的事件格式: " + message);
                return;
            }

            String eventType = parts[0];

            // 根据事件类型分发到对应的处理方法
            switch (eventType) {
                case "MOUSE_MOVE":
                    handleMouseMove(parts);
                    break;
                case "MOUSE_PRESS":
                    handleMousePress(parts);
                    break;
                case "MOUSE_RELEASE":
                    handleMouseRelease(parts);
                    break;
                case "MOUSE_WHEEL":
                    handleMouseWheel(parts);
                    break;
                case "KEY_PRESS":
                    handleKeyPress(parts);
                    break;
                case "KEY_RELEASE":
                    handleKeyRelease(parts);
                    break;
                case "CHAR_INPUT":
                    handleCharInput(parts);
                    break;
                default:
                    System.err.println("⚠️  未知事件类型: " + eventType);
            }

        } catch (Exception e) {
            System.err.println("❌ 处理事件时出错: " + e.getMessage());
        }
    }

    /**
     * 处理鼠标移动事件
     * <p>
     * 事件格式: MOUSE_MOVE|x|y
     * 转换为CTC事件: EVENT_TYPE_CURSOR_POS, x, y, 0, 0
     *
     * @param parts 分割后的事件参数数组，包含x和y坐标
     */
    private void handleMouseMove(String[] parts) {
        if (parts.length != 3) {
            System.err.println("⚠️  无效的鼠标移动事件格式");
            return;
        }

        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);

            invokeCTCReceiveData(EVENT_TYPE_CURSOR_POS, x, y, 0, 0);
            System.out.println("🖱️  鼠标移动到: (" + x + ", " + y + ")");

        } catch (NumberFormatException e) {
            System.err.println("❌ 鼠标坐标格式错误: " + e.getMessage());
        }
    }

    /**
     * 处理鼠标按下事件
     * <p>
     * 事件格式: MOUSE_PRESS|button
     * 按钮编码: 1=左键, 2=中键, 3=右键
     * 转换为CTC事件: EVENT_TYPE_MOUSE_BUTTON, button, 1, 0, 0
     *
     * @param parts 分割后的事件参数数组，包含按钮编号
     */
    private void handleMousePress(String[] parts) {
        if (parts.length != 2) {
            System.err.println("⚠️  无效的鼠标按下事件格式");
            return;
        }

        try {
            int button = Integer.parseInt(parts[1]);
            int ctcButton = convertToCTCButton(button);

            if (ctcButton != -1) {
                invokeCTCReceiveData(EVENT_TYPE_MOUSE_BUTTON, ctcButton, 1, 0, 0);
                System.out.println("🖱️  鼠标按下: 按钮" + button + " (CTC:" + ctcButton + ")");
            }

        } catch (NumberFormatException e) {
            System.err.println("❌ 鼠标按钮格式错误: " + e.getMessage());
        }
    }

    /**
     * 处理鼠标释放事件
     * <p>
     * 事件格式: MOUSE_RELEASE|button
     * 转换为CTC事件: EVENT_TYPE_MOUSE_BUTTON, button, 0, 0, 0
     *
     * @param parts 分割后的事件参数数组，包含按钮编号
     */
    private void handleMouseRelease(String[] parts) {
        if (parts.length != 2) {
            System.err.println("⚠️  无效的鼠标释放事件格式");
            return;
        }

        try {
            int button = Integer.parseInt(parts[1]);
            int ctcButton = convertToCTCButton(button);

            if (ctcButton != -1) {
                invokeCTCReceiveData(EVENT_TYPE_MOUSE_BUTTON, ctcButton, 0, 0, 0);
                System.out.println("🖱️  鼠标释放: 按钮" + button + " (CTC:" + ctcButton + ")");
            }

        } catch (NumberFormatException e) {
            System.err.println("❌ 鼠标按钮格式错误: " + e.getMessage());
        }
    }

    /**
     * 处理鼠标滚轮事件
     * <p>
     * 事件格式: MOUSE_WHEEL|scrollAmount
     * 转换为CTC事件: EVENT_TYPE_SCROLL, 0, scrollAmount, 0, 0
     *
     * @param parts 分割后的事件参数数组，包含滚轮滚动量
     */
    private void handleMouseWheel(String[] parts) {
        if (parts.length != 2) {
            System.err.println("⚠️  无效的鼠标滚轮事件格式");
            return;
        }

        try {
            int scrollAmount = Integer.parseInt(parts[1]);
            invokeCTCReceiveData(EVENT_TYPE_SCROLL, 0, scrollAmount, 0, 0);
            System.out.println("🖱️  鼠标滚轮: " + scrollAmount);

        } catch (NumberFormatException e) {
            System.err.println("❌ 滚轮数值格式错误: " + e.getMessage());
        }
    }

    /**
     * 处理按键按下事件
     * <p>
     * 事件格式: KEY_PRESS|keyCode
     * 转换为CTC事件: EVENT_TYPE_KEY, 0, keyCode, 1, 0
     *
     * @param parts 分割后的事件参数数组，包含按键代码
     */
    private void handleKeyPress(String[] parts) {
        if (parts.length != 2) {
            System.err.println("⚠️  无效的按键按下事件格式");
            return;
        }

        try {
            int keyCode = Integer.parseInt(parts[1]);
            invokeCTCReceiveData(EVENT_TYPE_KEY, 0, keyCode, 1, 0);
            System.out.println("⌨️  按键按下: 代码 " + keyCode);

        } catch (NumberFormatException e) {
            System.err.println("❌ 按键代码格式错误: " + e.getMessage());
        }
    }

    /**
     * 处理按键释放事件
     * <p>
     * 事件格式: KEY_RELEASE|keyCode
     * 转换为CTC事件: EVENT_TYPE_KEY, 0, keyCode, 0, 0
     *
     * @param parts 分割后的事件参数数组，包含按键代码
     */
    private void handleKeyRelease(String[] parts) {
        if (parts.length != 2) {
            System.err.println("⚠️  无效的按键释放事件格式");
            return;
        }

        try {
            int keyCode = Integer.parseInt(parts[1]);
            invokeCTCReceiveData(EVENT_TYPE_KEY, 0, keyCode, 0, 0);
            System.out.println("⌨️  按键释放: 代码 " + keyCode);

        } catch (NumberFormatException e) {
            System.err.println("❌ 按键代码格式错误: " + e.getMessage());
        }
    }

    /**
     * 处理字符输入事件
     * <p>
     * 事件格式: CHAR_INPUT|charCode
     * 转换为CTC事件: EVENT_TYPE_CHAR, charCode, 0, 0, 0
     * 用于处理文本输入，如文本框中的字符输入
     *
     * @param parts 分割后的事件参数数组，包含字符代码
     */
    private void handleCharInput(String[] parts) {
        if (parts.length != 2) {
            System.err.println("⚠️  无效的字符输入事件格式");
            return;
        }

        try {
            int charCode = Integer.parseInt(parts[1]);
            invokeCTCReceiveData(EVENT_TYPE_CHAR, charCode, 0, 0, 0);
            System.out.println("⌨️  字符输入: '" + (char)charCode + "' (代码: " + charCode + ")");

        } catch (NumberFormatException e) {
            System.err.println("❌ 字符代码格式错误: " + e.getMessage());
        }
    }

    /**
     * 将标准鼠标按钮编号转换为CTC按钮编号
     * <p>
     * CTC按钮编号使用位掩码方式表示不同的鼠标按钮
     * 如果CTC使用不同的编号系统，需要在此方法中进行映射转换
     *
     * @param button 标准鼠标按钮编号 (1=左键, 2=中键, 3=右键)
     * @return CTC按钮编号，如果输入无效返回-1
     */
    private int convertToCTCButton(int button) {
        // 这里假设CTC使用相同的按钮编号
        // 如果CTC使用不同的编号系统，需要在这里进行转换
        switch (button) {
            case 1: return 1 << 10; // 左键
            case 2: return 1 << 11; // 中键
            case 3: return 1 << 12; // 右键
            default:
                System.err.println("⚠️  无效的鼠标按钮: " + button);
                return -1;
        }
    }

    /**
     * 使用反射调用CTCAndroidInput.receiveData静态方法
     * <p>
     * 将处理后的输入事件参数传递给Cacio库进行实际的事件处理
     * 如果调用失败，会自动禁用CTCAndroidInput以避免重复错误
     *
     * @param type 事件类型，使用预定义的常量
     * @param i1 第一个参数，坐标||按钮信息
     * @param i2 第二个参数，坐标||按键代码
     * @param i3 第三个参数，按下||释放状态
     * @param i4 第四个参数，保留参数通常为0
     */
    private void invokeCTCReceiveData(int type, int i1, int i2, int i3, int i4) {
        if (!ctcAvailable || receiveDataMethod == null) {
            return;
        }

        try {
            // 调用静态方法，第一个参数为null
            receiveDataMethod.invoke(null, type, i1, i2, i3, i4);
        } catch (Exception e) {
            System.err.println("❌ 调用CTCAndroidInput.receiveData时出错: " + e.getMessage());
            // 如果调用失败，标记为不可用，避免重复错误
            ctcAvailable = false;
        }
    }

    /**
     * 停止事件处理任务
     * <p>
     * 设置运行标志为false并关闭客户端Socket连接
     * 用于优雅停止事件处理，确保资源正确释放
     */
    public void stop() {
        running = false;
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
        return running;
    }

    /**
     * 检查CTCAndroidInput功能是否可用
     *
     * @return true表示CTCAndroidInput初始化成功，可以处理输入事件
     */
    public boolean isCTCAvailable() {
        return ctcAvailable;
    }
}