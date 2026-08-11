# EMG_App — 肌电/IMU 手机上位机 (Android)

Android 原生 Kotlin app，通过 WiFi TCP 连接 ESP32 网关，实时显示 4 通道肌电波形、
IMU 姿态数据，并下发采集控制命令。

## 功能

- 4 通道 EMG 实时滚动波形（MPAndroidChart）
- IMU 加速度/角速度显示与滚动曲线
- 设备信息读取、启动/停止采集
- 增益（x1–x12 共 7 档）与采样率（125–8000 SPS 共 7 档）调节
- IMU 开启/关闭
- 断线自动重连

## 整体链路

```
STM32 (USART2) --串口帧--> ESP32 网关 --TCP--> 手机 App
手机 App ---------TCP 命令--> ESP32 网关 --串口帧--> STM32
```

协议与 STM32 端 `esp32_link.h` 完全一致：
```
上行: | 0xAA | 0x55 | LEN | SEQ | TYPE | DATA | CHECK(XOR) |
下行: | 0x55 | 0xAA | CMD | ARG | CHECK(XOR) |
```

类型:`TYPE_EMG=0x01`(`4x2` 字节 + LOFF)、`TYPE_IMU=0x03`(ACC 3x2 + GYR 3x2)、`TYPE_INFO=0x02`。
命令：`START=0x01`、`STOP=0x02`、`SET_GAIN=0x03`、`SET_SPS=0x04`、`GET_INFO=0x05`、`SET_IMU=0x06`。

## 构建（Android Studio）

1. 安装 [Android Studio](https://developer.android.com/studio)（需支持 JDK 17，新版自带），
   首次启动会引导安装 Android SDK。
2. `File → Open`，选择本目录 `EMG_App`。
3. 等待 Gradle 同步完成（首次会下载依赖，需网络）。
4. 手机开启「开发者选项 → USB 调试」，连上电脑点 **Run ▶** 安装；
   或 `Build → Build APK(s)` 后在 `app/build/outputs/apk/debug` 取 APK 直接安装。

> 若镜像下载慢，可把 `settings.gradle.kts` 里的 google() 换成国内镜像，或在
> `gradle.properties` 加 `systemProp.http.proxyHost=...`。
> 宽容 HTTP 明文 `<usesCleartextTraffic=true>` 已配置，局域网开发无需 HTTPS。

## 使用步骤

1. **组网**：打开 ESP32 网关（默认 AP 热点 `EMG_Gateway` / `12345678`），手机连上该热点
   （或 STA 模式连同一路由器）。
2. **连接**：app 顶部 IP 填 `192.168.4.1`（AP 模式），端口填 `5000`，点「连接」。
3. 连接成功后波形自动滚动，底部按钮控制增益/采样率/启停。

| 控件 | 动作 |
|------|------|
| 连接 / 断开 | 建立或断开与 ESP32 的 TCP 连接 |
| 启动采集 / 停止采集 | 发送 START / STOP |
| -增益 / +增益 | 降 / 升增益（1,2,3,4,6,8,12 倍） |
| -采样 / +采样 | 降采样率（125,250,500,1000,2000,4000,8000 SPS） |
| 开启IMU / 关闭IMU | 发送 SET_IMU(1/0) |
| 设备信息 | 请求 INFO 帧并显示 |

## 工程结构

```
EMG_App/
├── settings.gradle.kts      # 项目配置(jitpack 仓库/依赖)
├── build.gradle.kts         # 工程级插件
├── gradlew / gradlew.bat    # Gradle 包装脚本
├── gradle/wrapper/          # Gradle 包装器
└── app/
    ├── build.gradle.kts     # App 级依赖与构建配置
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/knee/emgapp/
        │   ├── MainActivity.kt        # UI 与业务
        │   ├── model/RollingBuffer.kt # 滚动波形缓冲
        │   ├── network/TcpClient.kt   # TCP 收发/自动重连
        │   └── protocol/              # 帧协议/解析器
        │       ├── EmgProtocol.kt
        │       └── FrameParser.kt
        └── res/                       # 布局/资源/图标
```

## 移植到其他设备名

若你改了 STM32 端帧格式，同步修改：
- `protocol/EmgProtocol.kt`：类型/命令/帧构建/校验
- `app/build.gradle.kts` 与 `AndroidManifest.xml`：应用名（`res/values/strings.xml` 的 `app_name`）

## 常见问题

- **连不上**:先 Ping 通模块 IP(如 `192.168.4.1`)；确认端口是 5000；STA 模式写对路由器 SSID/密码。
- **有连接无波形**:上电后 STM32 默认开机自启采集;确认 STM32 串口波特率 115200 与 ESP32 一致。
- **波形只显示一条**:EMG 帧 4 通道均有数据,可先对单通道测试,或调大 `MAX_POINTS` 观察。
- **数据乱**:多为帧协议版本不一致,优先核对 `FrameParser` 与 STM32 端 `esp32_link.c` 一致。