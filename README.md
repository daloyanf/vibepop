# VibePop - 蓝牙耳机自定义拟真弹窗框架

[![Version](https://img.shields.io/badge/Version-1.1.2-blue.svg)](https://github.com/daloyanf/vibepop/releases/tag/v1.1.2)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple.svg)](https://kotlinlang.org)
[![TargetSDK](https://img.shields.io/badge/TargetSDK-34-orange.svg)](https://developer.android.com/about/versions/14)

VibePop 是一款轻量、优雅且高度可定制的 Android 蓝牙耳机拟真悬浮弹窗应用（Material 暗黑质感风格）。支持类似 iOS AirPods 的底部吸附滑出动效、多媒体（MP4/WebM 视频、GIF 逐帧动图、静态图片、Lottie 矢量动画）渲染、按耳机独立定制专属动效、右上角单路真实电量指示与低电量预警、锁屏熄屏自动点亮与穿透弹窗，以及手势弹性下拉收起与智能防抖机制。

---

## 📥 下载与体验

- **蓝奏云下载**：[点击下载最新 APK](https://wwbag.lanzouu.com/b0umfyvad)（提取码 / 密码：`g3t5`）
- **GitHub Releases**：[Releases 页面](https://github.com/daloyanf/vibepop/releases)

---

## 🌟 核心特性

### 1. 🎨 模块化四面板架构
- **控制台（Dashboard）**：拟真动效一键模拟、前台监听服务运行状态监控、白名单响应状态速览与系统蓝牙状态实时诊断。
- **动效工坊（Themes & Media）**：
  - 预设经典 AirPods 矢量动效与《伟大胜利》视频动效；
  - 支持按已配对耳机独立配置专属动效；
  - 支持从系统相册导入 MP4/WebM 视频、GIF 动图（逐帧动画渲染）、WebP/PNG/JPG 图片与 Lottie JSON 文件；
  - 导入时自动触发 `VideoCropDetector` 智能检测黑边并裁切，MKV/AVI/MOV 等非原生支持容器在导入时会被自动拦截提示。
- **设备管理（Devices）**：已配对耳机卡片、Material 胶囊开关、蓝牙耳机状态标识；采用白名单精确响应机制（默认全不响应），支持设备重命名与一键关闭全部响应。
- **设置与权限（Settings）**：标准化权限申请面板、小米/HyperOS 锁屏与后台弹出 AppOps 动态检测、扬声器强制外放路由、消退时长滑块。

### 2. 🎧 单耳机专属独立配置（Per-Device Customization）
- 支持为每一个已配对耳机（按 MAC 地址）独立绑定不同的动效主题、专属视频/图片与自定义名称。
- 支持设置全局默认动效，未单独定制的耳机自动继承全局配置。

### 3. 🔋 右上角真实电量指示器（Hardware Battery Indicator）
- 实时读取耳机硬件上报与系统蓝牙广播中的真实单路电量百分比，并在弹窗右上角半透明胶囊呈现。
- 低电量动态梯度着色（🟢 充足 >40% / 🟡 预警 21-40% / 🔴 告急 ≤20%），并在设备上报充电状态时自动点亮闪电图标。

### 4. ⚡ 锁屏与熄屏自动唤醒穿透
- **熄屏唤醒（WakeLock）**：耳机在熄屏状态连接时自动点亮屏幕。
- **锁屏穿透（PopupActivity）**：配置 `setShowWhenLocked` 与 `setTurnScreenOn`，直接在系统锁屏界面呈现透明弹窗。

### 5. 🎬 满铺无黑边视频引擎与智能黑边检测
- **`FillVideoView`**：基于 `TextureView` 的高性能视频播放控件，在测量期自适应计算矩阵，实现 100% Center-Crop 严丝合缝满铺，消除横竖屏黑边。
- **`VideoCropDetector`**：导入视频时后台毫秒级采样首帧缩略图，智能扫描上下左右黑边区域，自动生成有效画面归一化裁切矩形。

### 6. 🖼️ 全格式智能媒体导入与渲染
- 采用系统原生全屏媒体选择器，彻底避免 PhotoPicker 默认半屏抽屉问题。
- 自动识别 MIME 类型与文件后缀，保留真实文件格式；GIF 逐帧动画渲染（API 28+ `ImageDecoder` / API 26-27 `Movie` 兜底），静态图片后台采样解码并按 EXIF 自动校正旋转方向，避免大图卡顿与内存溢出。

### 7. 👆 手势下拉跟随与弹性回弹
- 手指按下拖拽跟随，释放时未达阈值触发拟真弹簧回弹（`OvershootInterpolator`），下滑速度超阈值快速消退。

### 8. 🛡️ 智能防打扰与安全性加固
- **广播解耦**：设备连接期间周期性上报电量仅局部刷新弹窗电量徽标，不重复打扰唤起弹窗。
- **安全性加固**：广播接收器显式配置 `android:exported="false"`，防止第三方应用伪造连接广播。
- **节流防抖**：内置时间窗口（3 秒）节流机制，规避蓝牙重连广播抖动。

---

## 📊 多媒体格式支持矩阵

| 媒体类型 | 支持格式 | 渲染引擎 | 特性说明 |
| :--- | :--- | :--- | :--- |
| **视频** | `.mp4`, `.webm` | `FillVideoView` (TextureView + MediaPlayer) | 测量期 Center-Crop 满铺无黑边、支持扬声器强制外放、播放完毕自动消退 |
| **动图** | `.gif` | `ImageDecoder` / `GifMovieDrawable` | API 28+ 系统逐帧解码，API 26-27 Movie 画布自绘兜底 |
| **静态图片** | `.png`, `.jpg`, `.jpeg`, `.webp`, `.bmp`, `.heic` | `BitmapDrawable` | 后台线程采样解码 (1600px 封顶)、EXIF 旋转方向自动校正 |
| **矢量动画** | `.json` | `LottieAnimationView` | 矢量无损缩放渲染、无限循环播放 |

---

## 📁 架构分层设计

```
com.vibepop
├── VibePopApp.kt                      # Application 全局初始化与通知渠道
├── data/
│   ├── model/
│   │   ├── DeviceBatteryState.kt      # 耳机与真实电量数据模型
│   │   └── PopupConfig.kt             # 弹窗偏好配置与消退策略模型
│   └── repository/
│       └── PreferencesRepository.kt   # SharedPreferences 本地持久化与单耳机专属独立配置
├── overlay/
│   ├── PopupWindowManager.kt          # WindowManager 悬浮窗生命周期单例
│   ├── FillVideoView.kt               # 测量期 Center-Crop 100% 满铺居中视频播放器
│   ├── SlideDismissTouchListener.kt   # 下拉手势拦截、速度跟踪与弹簧回弹动效
│   ├── PopupAutoDismissManager.kt     # 倒计时消退调度器
│   ├── PopupInteractionController.kt  # 手势与自动消退统一控制器（悬浮窗/锁屏共用）
│   ├── PopupMetrics.kt                # 弹窗卡片尺寸计算（40% 屏高）
│   ├── GifMovieDrawable.kt            # GIF 逐帧渲染（API 26-27 Movie 兜底）
│   └── PopupViewBinder.kt             # 视图绑定、全格式解码、音频路由与单路电量渲染
├── receiver/
│   ├── HeadsetBroadcastReceiver.kt    # 蓝牙连接与电量监听器（防重复解耦与真实电量读取）
│   ├── PopupTriggerLimiter.kt         # 节流防抖控制器
│   └── BootReceiver.kt                # 开机自启监听
├── service/
│   └── HeadsetMonitorService.kt       # 前台保活监听服务 (connectedDevice 类别)
├── ui/
│   ├── MainActivity.kt                # 四大模块化 Tab 交互与主界面
│   ├── MainViewModel.kt               # MVVM 状态管理与设备专属媒体管理
│   └── PopupActivity.kt               # 专用于锁屏/熄屏穿透的透明弹窗 Activity
└── util/
    ├── PermissionHelper.kt            # 悬浮窗/蓝牙/通知/AppOps厂商权限辅助类
    └── VideoCropDetector.kt           # 视频首帧智能黑边检测与裁剪比计算器

app/src/test/java/com/vibepop
├── data/model/PopupConfigTest.kt      # 消退模式与延迟计算测试
├── receiver/PopupTriggerLimiterTest.kt# 节流防抖时间窗口测试
└── ui/MediaTypeDetectionTest.kt       # 媒体 MIME/后缀识别与非法容器拦截测试
```

---

## 🚀 快速开始与编译运行

### 1. 环境要求
- **Android Studio** Hedgehog (2023.1.1) 或更高版本
- **JDK** 17
- **Gradle** 8.2+
- **最低支持系统**：Android 8.0 (API 26)
- **目标编译版本**：Android 14 (API 34)

### 2. 克隆项目
```bash
git clone https://github.com/daloyanf/vibepop.git
cd vibepop
```

### 3. 配置签名（可选）
如需正式签名打包，请复制 `keystore.properties.example` 为 `keystore.properties` 并填入您的签名信息：
```bash
cp keystore.properties.example keystore.properties
```

### 4. 构建与测试
```bash
# 运行单元测试
./gradlew test

# 编译 Debug APK
./gradlew assembleDebug

# 编译 Release APK
./gradlew assembleRelease
```

---

## 📄 开源协议

本项目采用 [MIT License](LICENSE) 授权许可。

