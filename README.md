# MyAWT

一个让 Java GUI 应用程序在 Android 上运行的开源项目。

## 🚀 项目特性

- ✅ **完整的 Java 运行时环境** - 在 Android 上运行标准的 Java 应用程序
- ✅ **Swing/AWT 支持** - 基于 Cacio 实现 Swing 应用程序的 Android 渲染
- 🔄 **JavaFX 支持** - 初步实现（开发中）
- 🔄 **Compose 支持** - 开发中
- 🔄 **程序屏幕自适应** - 开发中

## 📋 系统要求

- Android 8 (API 26) 或更高版本
- ARM64 或 x86_64 架构
- 至少 500MB 可用存储空间
- 至少 6GB RAM 推荐

## 🚧 实现原理
Swing Application:

+-----------------------------------+     +-----------------------------------+
|       Swing Application           |     |        Android System             |
+-----------------------------------+     +-----------------------------------+
|       Swing Components            |     |        Android View               |
+-------------------+---------------+     +-------------------+---------------+
                    |                                         |
                    | Cacio Toolkit     GraphicsEnvironment   |
                    |---------------------------------------->|
                    |                                         |
                    | Convert RGB array to bitmap for display |
                    |---------------------------------------->|
                    |                                         |
                    | Swing InputEvent   Android InputEvent   |
                    |<----------------------------------------|
                    |                                         |
+-------------------+---------------+     +-------------------+---------------+
|  Cacio Toolkit (CTCToolkit)       |     |   Cacio Android Bridge            |
|  Cacio GraphicsEnvironment        |     |   SocketFactory                   |                    
+-----------------------------------+     +-----------------------------------+

Compose Application:
TODO

## 🙏 致谢

* [Caciocavallo headless Swing UI testing](https://github.com/CaciocavalloSilano/caciocavallo)
* [OpenJDK](https://github.com/openjdk/jdk/tree/jdk-17%2B35)


