# VibePop - 蓝牙耳机自定义拟真弹窗框架

[![Version](https://img.shields.io/badge/Version-1.1.0-blue.svg)](https://github.com/daloyanf/vibepop/releases/tag/v1.1.0)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg)](https://kotlinlang.org)
[![TargetSDK](https://img.shields.io/badge/TargetSDK-34-orange.svg)](https://developer.android.com/about/versions/14)

VibePop 是一款轻量、优雅且高度可定制的 Android 蓝牙耳机拟真悬浮弹窗应用。支持类似 iOS AirPods 的底部吸附滑出动效、全格式多媒体（MP4 视频 / GIF 动图 / 静态图片 / Lottie 矢量动画）渲染、按耳机独立定制专属动效、右上角单路真实电量指示与低电量预警、锁屏熄屏自动点亮与穿透弹窗，以及手势弹性下拉收起与防抖机制。

---

## 🌟 核心特性

1. **🎨 模块化四面板架构（Material 3）**：
   - **控制台（Dashboard）**：拟真动效一键模拟、前台监听服务运行状态监控、白名单状态速览与系统蓝牙状态诊断。
   - **动效工坊（Themes & Media）**：预设经典 AirPods 矢量动效与《伟大胜利》视频动效；支持按已配对耳机独立配置专属动效，支持从系统相册导入任意 MP4 视频、GIF 动图与图片并自定义消退规则。
   - **设备管理（Devices）**：已配对耳机卡片、Material 胶囊开关、蓝牙耳机状态标识；采用白名单精确响应机制（默认全不响应），支持设备重命名与专属弹窗即时预览。
   - **设置与权限（Settings）**：标准化权限申请面板、小米/HyperOS 锁屏与后台弹出 AppOps 动态检测、扬声器强制外放路由、消退时长滑块。

2. **🎧 单耳机专属独立配置（Per-Device Customization）**：
   - 支持为每一个已配对耳机（按 MAC 地址）独立绑定不同的动效主题、专属视频/图片与自定义名称。
   - 支持设置全局默认动效，未单独定制的耳机自动继承全局配置。

3. **🔋 右上角真实电量指示器（Hardware Battery Indicator）**：
   - 实时读取耳机硬件上报与系统蓝牙广播中的真实单路电量百分比，并在弹窗右上角半透明胶囊精致呈现。
   - 内置充电状态闪电图标与低电量动态梯度着色（绿色充足 / 黄色预警 / 红色告急）。

4. **⚡ 锁屏与熄屏自动唤醒穿透**：
   - **熄屏唤醒（WakeLock）**：耳机在熄屏状态连接时自动点亮屏幕。
   - **锁屏穿透（PopupActivity）**：配置 `setShowWhenLocked` 与 `setTurnScreenOn`，直接在系统锁屏界面呈现磨砂玻璃动效弹窗。

5. **🖼️ 全格式智能媒体导入**：
   - 采用系统原生全屏媒体选择器，彻底避免 PhotoPicker 默认半屏抽屉问题。
   - 自动识别 MIME 类型与文件后缀，保留 `.png`、`.jpg`、`.webp`、`.gif` 等真实图片与视频格式。

6. **👆 手势下拉跟随与弹性回弹**：
   - 手指按下拖拽跟随，释放时未达阈值触发拟真弹簧回弹，下滑速度超阈值快速消退。

7. **🛡️ 智能防打扰与安全性加固**：
   - **广播解耦**：设备连接期间周期性上报电量仅局部刷新弹窗电量徽标，不重复打扰唤起弹窗。
   - **安全性加固**：广播接收器显式配置 `android:exported="false"`，防止第三方应用伪造连接广播。
   - **节流防抖**：内置时间窗口节流机制，规避蓝牙重连广播抖动。

---

## 📁 架构分层设计

```
com.vibepop
├── VibePopApp.kt                      # Application 全局初始化与通知渠道
├── data/
│   ├── model/
│   │   ├── DeviceBatteryState.kt      # 耳机与电量数据模型
│   │   └── PopupConfig.kt             # 弹窗偏好配置模型
│   └── repository/
│       └── PreferencesRepository.kt   # SharedPreferences 本地持久化与独立配置
├── overlay/
│   ├── PopupWindowManager.kt          # WindowManager 悬浮窗生命周期单例
│   ├── SlideDismissTouchListener.kt   # 下拉手势拦截、速度跟踪与回弹动效
│   ├── PopupAutoDismissManager.kt     # 倒计时消退调度器
│   └── PopupViewBinder.kt             # 视图绑定、全格式解码与单路电量渲染
├── receiver/
│   ├── HeadsetBroadcastReceiver.kt    # 蓝牙连接与电量监听器（防重复解耦）
│   ├── PopupTriggerLimiter.kt         # 节流防抖控制器
│   └── BootReceiver.kt                # 开机自启监听
├── service/
│   └── HeadsetMonitorService.kt       # 前台保活监听服务
├── ui/
│   ├── MainActivity.kt                # 四大模块化 Tab 交互与主界面
│   ├── MainViewModel.kt               # MVVM 状态管理与设备专属媒体管理
│   └── PopupActivity.kt               # 专用于锁屏/熄屏穿透的透明弹窗 Activity
└── util/
    └── PermissionHelper.kt            # 悬浮窗/蓝牙/通知/AppOps厂商权限辅助类
```

---

## 🚀 快速开始与编译运行

### 1. 克隆项目
```bash
git clone https://github.com/daloyanf/vibepop.git
cd vibepop
```

### 2. 配置签名（可选）
如需正式签名打包，请复制 `keystore.properties.example` 为 `keystore.properties` 并填入您的签名信息：
```bash
cp keystore.properties.example keystore.properties
```

### 3. 构建与运行
- 在 **Android Studio** 中点击 `Open` 并选择项目根目录；
- 连接真机或启动 Android 10+ 模拟器；
- 点击 **Run** 即可编译并安装至设备。

---

## 📄 开源协议

本项目采用 [MIT License](LICENSE) 授权许可。
