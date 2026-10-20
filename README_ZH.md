# 🛡️ 摇一摇守卫 (ShakeGuard)

**[🇨🇳 简体中文](README_ZH.md) | [🇺🇸 English](README.md) | [⚖️ 免责声明与合规说明](DISCLAIMER.md)**

> **Android 开源运动传感器防误触辅助与跨应用调用二次确认工具（无障碍与传感器权限管理技术研究项目）**  
> 纯原生 Android SDK + Kotlin 编写，**零联网权限、100% 本地离线运行、严守技术中立原则（不内置任何特定第三方企业或商业 SDK 的定向规则）**，支持顶栏 **中文 / English** 一键实时切换。

---

## ⚖️ 合规与免责声明（重要）

1. **研究与无障碍辅助定位**：本项目仅用于研究 Android 系统 `AccessibilityService` 无障碍辅助、隐式 `Intent` 跨应用跳转二次确认以及 `AppOps` 传感器权限管理机制，旨在辅助用户在乘车颠簸、步行或手部震颤场景下防止运动传感器误触发，保障用户对跨应用调用的知情同意权。
2. **100% 厂商中立（零特定第三方规则）**：本仓库源码与预编译 APK 中**绝不内置任何针对特定第三方企业、特定商业 App 或特定商业 SDK 的包名、协议头（URI Scheme）或专有控件 ID**。所有内置条目均为 `com.example.*` 与 `shakeguarddemo://` 通用教学沙盒占位符。用户在本地设备自行配置的任何自定义规则均由用户本人独立承担责任。详见 [DISCLAIMER.md](DISCLAIMER.md)。

---

## 📦 安装包直接下载 (v1.3.0 Release)

| 版本 | 默认界面语言 | 应用内语言切换 | 下载链接 |
|---|---|---|---|
| 🇨🇳 **中文版 (`zh-CN`)** | **简体中文（摇一摇守卫）** | 顶栏点击 `🌐 EN` 一键切英文 | [**点击下载 `ShakeGuard-v1.3.0-zh-CN.apk`**](https://github.com/xuanbin159/shake-guard/releases/download/v1.3.0/ShakeGuard-v1.3.0-zh-CN.apk) |
| 🇺🇸 **英文版 (`en-US`)** | **English (`ShakeGuard`)** | 顶栏点击 `🌐 中文` 一键切中文 | [**Download `ShakeGuard-v1.3.0-en-US.apk`**](https://github.com/xuanbin159/shake-guard/releases/download/v1.3.0/ShakeGuard-v1.3.0-en-US.apk) |

---

## 🚀 三层核心技术机制

| 层级 | 模块名称 | 技术原理与辅助效果 | 权限要求 |
|---|---|---|---|
| **L1** | **无障碍交互辅助与防误触提示** (`ShakeGuardAccessibilityService`) | 1. 根据用户自定义的通用文本或控件 ID 辅助点击倒计时/关闭按钮（支持坐标手势兜底）。<br>2. 识别屏幕上的摇动/倾斜提示词，自动开启 7 秒防误触保护窗口，并通过 `TYPE_ACCESSIBILITY_OVERLAY` 显示顶部状态胶囊。 | 本地无障碍服务（免 Root） |
| **L2** | **跨应用调用二次确认** (`DeepLinkInterceptorActivity`) | 1. 对受控跨应用 URI Scheme 调用提供半透明底部二次确认弹窗（支持 3 秒超时自动取消、保护期静默阻断、仅本次放行）。<br>2. 在防误触保护期内监测未经手指触屏确认的跨包名突跳行为，辅助执行系统 `GLOBAL_ACTION_BACK` 返回原应用。 | 免 Root / 免 Shizuku |
| **L3** | **系统传感器权限管理** (`ShizukuSensorController`) | 1. 在授权 Shizuku 或 Root 时，支持对用户自选应用在启动前 $N$ 秒（默认 6 秒）或全程执行 `cmd appops set <pkg> SENSORS ignore`，防止颠簸或晃动引起加速度计/陀螺仪误触发。<br>2. 内置免 Root ADB 传感器权限配置脚本生成器。 | Shizuku / Root / ADB（可选） |

---

## 🧪 内置「运动传感器防误触实验室」(`ShakeAdLabActivity`)

点击 App 右上角 **「🧪 靶场 / Lab」** 即可进入内置硬件沙盒实验室：
- **实时加速度计与陀螺仪波形仪**：实时显示手机合成加速度与晃动增量 $\Delta a$ 曲线。
- **沙盒协议自测**：通过内置通用测试协议 (`shakeguarddemo://`) 安全验证 L1 辅助点击、L2 跨应用二次确认弹窗与 L3 传感器冻结效果。

---

## 🔓 Android 13 / 14 / 15「受限设置」解除与后台保活指南

### 1. 15 秒解除「受限设置 / 系统拒绝应用授权」
1. 打开 App，点 **「第 2 步：前往【无障碍设置】开启服务」**，在系统「已下载的应用」里点一下「摇一摇守卫」（触发受限记录）。
2. 回到 App 首页，点橙色按钮 **「🔓 第 1 步：打开【本应用详情】→ 点右上角 ⋮ 允许受限设置」**。
3. 在系统「应用详情」页点击**右上角 `⋮`（三个点）**（部分机型在页面底部）→ 点击 **「允许受限设置」** 并验证锁屏密码。
4. 返回无障碍设置即可正常开启。

### 2. 系统省电白名单与自启动保活
在 App 首页底部 **「🔋 防杀后台保活专享卡」** 中：
- 点击 **「⚡ 第 1 项：一键申请【忽略电池优化】」** 加入后台无限制白名单；
- 点击 **「🛡️ 第 2 项：一键打开本机【自启动】设置页」** 开启允许自启动。

---

## 🛠️ 从源码构建

```bash
# 同时编译生成中文版 (zh-CN) 与英文版 (en-US) 签名 APK：
PYTHONPATH=src python3 -m shake_guard build

# 运行自动化测试：
PYTHONPATH=src python3 tests/test_shake_guard.py
```

---

## 📄 开源协议

MIT License — 请务必阅读并遵守 [DISCLAIMER.md](DISCLAIMER.md) 中的免责与合规条款。
