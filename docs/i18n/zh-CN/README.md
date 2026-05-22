# Osmo360 Android 实时预览

非官方 Android 应用原型，用于 DJI Osmo 360 实时预览、360 相机串流、BLE 唤醒、Wi-Fi 相机连接，以及本地 H.264 预览渲染。

**语言:** [English](../../../README.md) | 中文 | [Русский](../ru/README.md)

本项目面向希望研究 Osmo 360 Android 连接流程的开发者。用户可以在应用内输入相机 Wi-Fi SSID、Wi-Fi 密码、可选 BLE 名称、可选 BSSID/MAC 和相机 IP 地址。连接设置只保存在设备本地，使用 Android `SharedPreferences`。

## 功能

- Android 原生 Osmo 360 实时预览原型
- 应用内相机连接配置
- Bluetooth/BLE 发现和唤醒流程
- 使用 Android `WifiNetworkSpecifier` 连接相机 Wi-Fi
- 使用 Android `MediaCodec` 进行本地 H.264 预览渲染
- 针对蓝牙、Wi-Fi 可见性、相机发现状态的连接提示
- 适合公开发布到 GitHub 的清理后源码结构

## 适用场景

- Osmo 360 Android 实时预览实验
- 360 相机 Android 串流研究
- BLE 辅助 Wi-Fi 相机连接原型
- 使用 H.264 和 `MediaCodec` 的本地相机预览应用
- 对自己拥有或有权限使用的相机进行非官方互操作测试

## 免责声明

本项目是独立的非官方项目。它不隶属于 DJI，也不受 DJI 认可、赞助或支持。DJI、Osmo 和 Mimo 是其各自所有者的商标。

请仅将本软件用于你拥有或有权限操作的相机。本原型仅用于互操作和实验目的，可能会因固件或官方应用更新而停止工作。

## 当前状态

- 版本: `v0.0.2`
- 平台: Android
- 目标相机: Osmo 360
- 状态: 实验性原型

## 构建

在 Android Studio 中打开本仓库并运行 `app` 配置，或在终端中构建：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
```

调试 APK 会生成在：

```text
app/build/outputs/apk/debug/
```

## 运行

1. 安装并打开 `Osmo Preview`。
2. 打开 Bluetooth。
3. 点击 `Settings`。
4. 输入相机 Wi-Fi SSID 和密码。
5. 如果知道 BLE 名称和 BSSID/MAC，可以选择填写。
6. 除非相机使用不同地址，否则保留默认相机 IP。
7. 确认你拥有或有权限连接该相机。
8. 保存设置，返回主界面，点击 `Start Osmo Preview`。

如果相机 Wi-Fi 不可见，应用会先尝试 BLE 唤醒，并可能从发现流程中学习实际相机 Wi-Fi SSID。

## 文档

- [构建](../../development/BUILDING.md)
- [架构](../../development/ARCHITECTURE.md)
- [连接流程](../../development/CONNECTION_FLOW.md)
- [故障排查](../../development/TROUBLESHOOTING.md)
- [路线图](../../development/ROADMAP.md)
- [法律声明](../../legal/NOTICE.md)

## 关键词

`osmo360`, `osmo 360`, `DJI Osmo 360`, `Android 实时预览`, `360 相机`, `BLE 相机`, `Wi-Fi 相机`, `H.264 预览`, `MediaCodec`, `Android 相机串流`, `WifiNetworkSpecifier`
