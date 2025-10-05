package io.github.eurya.cacio;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;

/**
 * CTCScreen反射包装类
 * <p>
 * 提供对Cacio库中CTCScreen类的安全访问封装，通过反射机制动态调用屏幕数据获取方法
 * 实现与Cacio库的解耦，允许在Cacio库不存在时优雅降级到模拟数据模式
 * 支持实时屏幕数据捕获、尺寸获取和图像格式转换等功能
 * <p>
 * 主要功能：
 * - 动态检测和加载Cacio库中的CTCScreen类
 * - 通过反射安全调用屏幕数据获取方法
 * - 提供模拟数据后备方案确保系统可靠性
 * - 支持屏幕尺寸动态检测和手动配置
 * - 实现RGB数据到BufferedImage的高效转换
 */
public class CTCScreenWrapper {

    /** 当前屏幕尺寸信息，包含宽度和高度 */
    private Dimension screenSize;

    /** CTCScreen实例的反射对象引用 */
    private Object ctcscreen;

    /** Cacio库可用性标志，true表示Cacio库已正确加载 */
    private boolean cacioAvailable = false;

    /** CTCScreen类的反射Class对象 */
    private Class<?> ctcscreenClass;

    /** FullScreenWindowFactory类的反射Class对象，用于获取屏幕尺寸 */
    private Class<?> fullScreenWindowFactoryClass;

    /** getCurrentScreenRGB方法的反射Method对象 */
    private Method getCurrentScreenRGBMethod;

    /** getScreenDimension方法的反射Method对象 */
    private Method getScreenDimensionMethod;

    /**
     * CTCScreenWrapper构造函数
     * <p>
     * 自动初始化Cacio屏幕访问功能，尝试通过反射加载Cacio相关类和方法
     * 如果Cacio库不可用，自动启用后备方案提供模拟屏幕数据
     */
    public CTCScreenWrapper() {
        initializeCacioScreen();
    }

    /**
     * 初始化Cacio屏幕访问功能（反射模式）
     * <p>
     * 使用Java反射机制动态加载Cacio库中的相关类和方法：
     * - CTCScreen：负责提供屏幕像素数据
     * - FullScreenWindowFactory：负责提供屏幕尺寸信息
     * <p>
     * 如果任何类或方法加载失败，将自动切换到后备模式
     * 初始化过程包含详细的日志输出，便于问题诊断
     */
    private void initializeCacioScreen() {
        try {
            // 使用反射动态加载Cacio核心类
            ctcscreenClass = Class.forName("com.github.caciocavallosilano.cacio.ctc.CTCScreen");
            fullScreenWindowFactoryClass = Class.forName("com.github.caciocavallosilano.cacio.peer.managed.FullScreenWindowFactory");

            // 获取CTCScreen单例实例
            Method getInstanceMethod = ctcscreenClass.getDeclaredMethod("getInstance");
            getInstanceMethod.setAccessible(true);
            ctcscreen = getInstanceMethod.invoke(null);

            // 获取屏幕尺寸信息
            getScreenDimensionMethod = fullScreenWindowFactoryClass.getDeclaredMethod("getScreenDimension");
            Dimension d = (Dimension) getScreenDimensionMethod.invoke(null);
            this.screenSize = new Dimension(d.width, d.height);

            // 获取屏幕数据捕获方法
            getCurrentScreenRGBMethod = ctcscreenClass.getDeclaredMethod("getCurrentScreenRGB");

            cacioAvailable = true;
            System.out.println("✅ CTCScreen包装器初始化成功（反射模式）");
            System.out.println("📐 屏幕尺寸: " + screenSize.width + "x" + screenSize.height);

        } catch (ClassNotFoundException e) {
            System.err.println("❌ 未找到Cacio相关类，请确保Cacio库在类路径中");
            System.err.println("⚠️  将使用模拟屏幕数据");
            initializeFallback();
        } catch (Exception e) {
            System.err.println("❌ 初始化CTCScreen失败: " + e.getMessage());
            System.err.println("⚠️  将使用模拟屏幕数据");
            initializeFallback();
        }
    }

    /**
     * 初始化后备方案
     * <p>
     * 当Cacio库不可用时，设置默认的屏幕尺寸并启用模拟数据生成
     * 确保系统在缺少Cacio依赖的情况下仍能正常运行
     */
    private void initializeFallback() {
        this.screenSize = new Dimension(800, 600);
        cacioAvailable = false;
        ctcscreenClass = null;
        fullScreenWindowFactoryClass = null;
        getCurrentScreenRGBMethod = null;
        getScreenDimensionMethod = null;
    }

    /**
     * 设置自定义屏幕尺寸
     * <p>
     * 允许手动覆盖自动检测的屏幕尺寸，用于特定场景下的尺寸配置
     * 例如在远程桌面传输时设置固定的分辨率
     *
     * @param width 屏幕宽度，单位像素
     * @param height 屏幕高度，单位像素
     */
    public void setScreenSize(int width, int height) {
        this.screenSize = new Dimension(width, height);
        System.out.println("🔄 设置屏幕尺寸: " + width + "x" + height);
    }

    /**
     * 获取当前屏幕的RGB像素数据
     * <p>
     * 使用反射调用CTCScreen.getCurrentScreenRGB()方法获取屏幕像素数据
     * 如果Cacio不可用或调用失败，自动返回模拟的屏幕数据
     *
     * @return 包含屏幕像素数据的int数组，格式为ARGB
     */
    public int[] getScreenRGBData() {
        if (!cacioAvailable || getCurrentScreenRGBMethod == null) {
            return getFallbackScreenData();
        }

        try {
            // 使用反射调用getCurrentScreenRGB静态方法
            int[] screenData = (int[]) getCurrentScreenRGBMethod.invoke(null);

            if (screenData != null && screenData.length > 0) {
                return screenData;
            } else {
                System.err.println("⚠️  CTCScreen返回空数据，使用后备数据");
                return getFallbackScreenData();
            }

        } catch (Exception e) {
            System.err.println("❌ 获取屏幕数据失败: " + e.getMessage());
            return getFallbackScreenData();
        }
    }

    /**
     * 获取后备屏幕数据（模拟数据）
     * <p>
     * 当Cacio库不可用时，生成动态的渐变色模拟数据
     * 使用三角函数创建流动的彩色图案，便于视觉验证系统运行状态
     *
     * @return 模拟的屏幕像素数据数组
     */
    private int[] getFallbackScreenData() {
        int width = screenSize.width;
        int height = screenSize.height;
        int[] rgbData = new int[width * height];

        // 基于时间的动态效果种子
        long time = System.currentTimeMillis() / 50;

        // 生成动态渐变色图案
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 使用三角函数创建流动的彩色效果
                int r = (int) ((Math.sin(x * 0.1 + time * 0.1) * 127 + 128));
                int g = (int) ((Math.cos(y * 0.1 + time * 0.05) * 127 + 128));
                int b = (int) ((Math.sin((x + y) * 0.05 + time * 0.02) * 127 + 128));

                // 组合RGB颜色值（Alpha固定为255）
                rgbData[y * width + x] = (r << 16) | (g << 8) | b;
            }
        }

        return rgbData;
    }

    /**
     * 获取当前屏幕尺寸
     *
     * @return 包含屏幕宽度和高度的Dimension对象
     */
    public Dimension getScreenSize() {
        return screenSize;
    }

    /**
     * 将RGB像素数据转换为BufferedImage图像
     * <p>
     * 将一维的RGB像素数组转换为标准的BufferedImage对象，
     * 便于图像处理、显示或编码传输
     *
     * @param rgbData 源RGB像素数据数组
     * @return 转换后的BufferedImage对象，如果输入为null则返回null
     */
    public BufferedImage convertRGBToImage(int[] rgbData) {
        if (rgbData == null) {
            return null;
        }

        Dimension size = getScreenSize();
        BufferedImage image = new BufferedImage(
                size.width, size.height, BufferedImage.TYPE_INT_RGB);

        image.setRGB(0, 0, size.width, size.height, rgbData, 0, size.width);
        return image;
    }

    /**
     * 获取屏幕宽度
     *
     * @return 屏幕宽度，单位像素
     */
    public int getScreenWidth() {
        return screenSize.width;
    }

    /**
     * 获取屏幕高度
     *
     * @return 屏幕高度，单位像素
     */
    public int getScreenHeight() {
        return screenSize.height;
    }

    /**
     * 检查Cacio库是否可用
     *
     * @return true表示Cacio库已正确初始化，false表示使用后备模式
     */
    public boolean isCacioAvailable() {
        return cacioAvailable;
    }

    /**
     * 获取CTCScreen反射实例
     * <p>
     * 用于需要直接操作CTCScreen实例的高级场景
     * 注意：仅在Cacio可用时返回有效对象
     *
     * @return CTCScreen实例的反射对象，Cacio不可用时返回null
     */
    public Object getCTCScreen() {
        return ctcscreen;
    }

    /**
     * 获取CTCScreen反射Class对象
     * <p>
     * 用于需要直接进行反射操作的高级场景
     *
     * @return CTCScreen类的Class对象，Cacio不可用时返回null
     */
    public Class<?> getCTCScreenClass() {
        return ctcscreenClass;
    }

    /**
     * 动态刷新屏幕尺寸
     * <p>
     * 重新调用Cacio库的屏幕尺寸检测方法，更新当前屏幕尺寸信息
     * 用于处理屏幕分辨率动态变化的场景
     */
    public void refreshScreenSize() {
        if (!cacioAvailable || getScreenDimensionMethod == null) {
            return;
        }

        try {
            Dimension d = (Dimension) getScreenDimensionMethod.invoke(null);
            this.screenSize = new Dimension(d.width, d.height);
            System.out.println("🔄 刷新屏幕尺寸: " + screenSize.width + "x" + screenSize.height);
        } catch (Exception e) {
            System.err.println("❌ 刷新屏幕尺寸失败: " + e.getMessage());
        }
    }

    /**
     * 静态方法：检查Cacio库是否在类路径中
     * <p>
     * 在不实例化包装器的情况下检测Cacio库的可用性
     * 用于系统启动前的环境检查
     *
     * @return true表示Cacio核心类在类路径中可用
     */
    public static boolean isCacioInClasspath() {
        try {
            Class.forName("com.github.caciocavallosilano.cacio.ctc.CTCScreen");
            Class.forName("com.github.caciocavallosilano.cacio.peer.managed.FullScreenWindowFactory");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 静态方法：获取Cacio版本信息
     * <p>
     * 尝试从包信息中提取Cacio库的版本号
     * 用于诊断和版本兼容性检查
     *
     * @return Cacio库版本字符串，如果无法获取则返回描述性文本
     */
    public static String getCacioVersion() {
        try {
            Package cacioPackage = Package.getPackage("com.github.caciocavallosilano.cacio");
            if (cacioPackage != null) {
                String version = cacioPackage.getImplementationVersion();
                return version != null ? version : "未知版本";
            }
            return "版本信息不可用";
        } catch (Exception e) {
            return "无法获取版本信息";
        }
    }
}