# VibePop - 拟真/二次元蓝牙耳机自定义弹窗框架

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg)](https://kotlinlang.org)
[![TargetSDK](https://img.shields.io/badge/TargetSDK-34-orange.svg)](https://developer.android.com/about/versions/14)

VibePop 是一款轻量、优雅且高度可定制的 Android 蓝牙耳机拟真悬浮弹窗应用。支持类似 iOS AirPods 的底部吸附滑出动效、全格式多媒体（MP4 视频 / GIF 动图 / 静态图片 / Lottie 矢量动画）渲染、三电量（左耳、右耳、充电盒）拟态展示、锁屏熄屏自动点亮与穿透弹窗，以及手势弹性下拉收起与防抖机制。

---

## 🌟 核心特性

1. **🎨 模块化四面板架构（Material 3）**：
   - **控制台（Dashboard）**：拟真动效一键模拟、前台监听服务运行状态监控、核心配置速览与权限诊断卡片。
   - **动效工坊（Themes & Media）**：预设经典 AirPods、赛博机甲、声波脉冲矢量动效；支持从系统相册导入任意 MP4 视频与图片并自定义消退规则。
   - **设备管理（Devices）**：高对比度深色模式设备卡片、Material 胶囊开关、蓝牙耳机状态标识，支持按需配置白名单过滤。
   - **系统设置与权限（Settings）**：标准化权限申请面板、小米/HyperOS 锁屏与后台弹出 AppOps 动态检测、扬声器强制外放路由、消退时长滑块。

2. **⚡ 锁屏与熄屏自动唤醒穿透**：
   - **熄屏唤醒（WakeLock）**：耳机在熄屏状态连接时自动点亮屏幕。
   - **锁屏穿透（PopupActivity）**：配置 `setShowWhenLocked` 与 `setTurnScreenOn`，直接在系统锁屏界面呈现 40% 磨砂玻璃动效弹窗。

3. **🖼️ 原生全屏相册选择器**：
   - 采用系统原生全屏媒体选择器，彻底避免 Android 13/14 PhotoPicker 默认半屏抽屉需要向上拖拽的问题。

4. **🔋 三电量拟态指示器**：
   - 实时解析并呈现左耳 (L)、右耳 (R)、充电盒 (Case) 电量百分比。
   - 动态充电状态小闪电图标与低电量预警着色。

5. **👆 手势下拉跟随与弹性回弹**：
   - 手指按下拖拽跟随，释放时未达阈值触发拟真弹簧回弹，下滑速度超阈值快速消退。

6. **🛡️ 智能自动消退与防抖**：
   - 支持自定义 2~15 秒无操作自动淡出消退，触摸交互时自动暂停计时。
   - 内置时间窗口节流防抖，规避蓝牙重连广播抖动。

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
│       └── PreferencesRepository.kt   # SharedPreferences 本地持久化存储
├── overlay/
│   ├── PopupWindowManager.kt          # WindowManager 悬浮窗生命周期单例
│   ├── SlideDismissTouchListener.kt   # 下拉手势拦截、速度跟踪与回弹动效
│   ├── PopupAutoDismissManager.kt     # 倒计时消退调度器
│   └── PopupViewBinder.kt             # 视图绑定、多媒体解码与电量渲染
├── receiver/
│   ├── HeadsetBroadcastReceiver.kt    # 蓝牙连接广播监听器
│   ├── PopupTriggerLimiter.kt         # 节流防抖控制器
│   └── BootReceiver.kt                # 开机自启监听
├── service/
│   └── HeadsetMonitorService.kt       # 前台保活监听服务
├── ui/
│   ├── MainActivity.kt                # 四大模块化 Tab 交互与主界面
│   ├── MainViewModel.kt               # MVVM 状态管理与事件驱动
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
