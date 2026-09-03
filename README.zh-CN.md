# No DHCP Hostname

[English](/README.md) | **简体中文**

一个用于阻止 Android NetworkStack 在 DHCP 请求中上报设备主机名与 Vendor-Class 的 LSPosed 模块。

在部分 Android 设备上，尤其是小米 / HyperOS 设备，DHCP 报文中可能包含以下选项，例如：

```text
Hostname (12): "Xiaomi-15"
Vendor-Class (60): "android-dhcp-16"
```

本模块会在 DHCP 报文发送前拦截 Option 12 与 Option 60，从而减少设备名称、系统版本等信息泄露给路由器或 DHCP 服务器。

## 功能说明

本模块会挂钩 Android NetworkStack，并阻止 DHCP Host Name（Option 12）与 Vendor Class Identifier（Option 60）被写入报文。

目标包名：

```text
com.android.networkstack
com.google.android.networkstack
```

运行时按顺序尝试以下类名（以加载的包中实际存在的类为准）：

```text
com.android.networkstack.android.net.dhcp.DhcpPacket
android.net.dhcp.DhcpPacket
com.google.android.networkstack.android.net.dhcp.DhcpPacket
```

Hook 的方法包括：

```java
addTlv(ByteBuffer, byte, String)
addTlv(ByteBuffer, byte, byte[])
addCommonClientTlvs(ByteBuffer)
```

当 DHCP option 编号为 `12`（Host Name）或 `60`（Vendor Class Identifier）时，模块会跳过原始方法，从而阻止这些选项被写入 DHCP 报文。

> 说明：在 HyperOS 4 的 NetworkStack 反编译代码中，Option 60 的值来自 `DhcpPacket.getVendorId()`，默认形如 `android-dhcp-<系统版本>`（例如 `android-dhcp-16`），在 `addCommonClientTlvs()` 中通过 `addTlv` 写入。除该路径外不存在其它 Option 60 写入点（自定义客户端选项中的类型 60 会被显式跳过，仅用于提供 Vendor ID 字符串），因此拦截 `addTlv` 与 `addCommonClientTlvs` 可以完整移除 Vendor-Class。作为双保险，模块在 `addCommonClientTlvs()` 执行前还会把 `mHostName` 与 `mVendorId` 字段置空，以兼容把这两个选项直接写入 ByteBuffer 的定制 ROM。

## 为什么不只使用 RRO Overlay？

Android NetworkStack 中存在一个资源开关：

```text
config_dhcp_client_hostname
```

在接近 AOSP 的系统中，将它覆盖为 `false` 可能可以阻止 DHCP Hostname 上报。

但是在已测试的 HyperOS / Android 16 系统中，即使：

```bash
cmd overlay lookup com.android.networkstack com.android.networkstack:bool/config_dhcp_client_hostname
```

返回：

```text
false
```

DHCP 报文中仍然可能出现 Option 12 Hostname。

因此，本模块选择直接拦截 DHCP TLV 的最终写入位置，而不是只依赖 RRO 资源覆盖。

## 使用要求

* 已 Root 的 Android 设备
* LSPosed 或兼容的 Xposed 框架
* 系统存在 NetworkStack 包：
  * `com.android.networkstack`
  * `com.google.android.networkstack`（Play 商店分发的 NetworkStack，常见于 EEA / Pixel 版本）
* 已测试环境：
  * HyperOS / Android 16
  * 搭载 Play 版 `com.google.android.networkstack` 的 HyperOS 3（Android 17）设备，详见兼容性说明
  * LSPosed API 101 运行时

其它 ROM 可能使用不同的 NetworkStack 类名，详见兼容性说明。

## 安装方法

1. 构建或下载模块 APK。
2. 在设备上安装 APK。
3. 打开 LSPosed。
4. 启用本模块。
5. 将作用域设置为：

```text
com.android.networkstack
com.google.android.networkstack
```

（如果 ROM 中只存在其中一个包，添加对应的那个即可。）

6. 重启设备。
7. 重新连接 Wi-Fi。

## 验证方法

可以使用 `tcpdump` 抓取 DHCP 报文：

```bash
su -c "$PREFIX/bin/tcpdump -i wlan0 -n -vvv -s 0 'udp and (port 67 or port 68)'"
```

如果只想过滤本机设备，请替换为你的 Wi-Fi MAC 地址：

```bash
su -c "$PREFIX/bin/tcpdump -i wlan0 -n -vvv -s 0 'ether src xx:xx:xx:xx:xx:xx and udp and (port 67 or port 68)'"
```

启用模块前，可能会看到：

```text
Hostname (12): "Xiaomi-15"
Vendor-Class (60): "android-dhcp-16"
```

启用模块后，DHCP 报文中不应再出现：

```text
Hostname (12)
Vendor-Class (60)
```

本模块不会移除 Client-ID、Requested-IP、Parameter-Request 等其它 DHCP 选项。

## 兼容性说明

**已测试通过的环境：**
* 小米15（`dada`），`HyperOS 3.0.302.0.WOCCNXM.C07 (Android 16)` — 包名 `com.android.networkstack`。
* 小米15 Ultra，HyperOS OS3（`Android 17`，EEA 版本 `OS3.0.301.0.WOAEUXM`）— 系统中没有 `com.android.networkstack`，模块注入 Play 版 `com.google.android.networkstack`，抓包确认 Option 12 Hostname 不再出现（由 [h3nnes](https://github.com/h3nnes) 贡献）。

从 v1.2.0 起，模块同时接受两个目标包，并在运行时尝试多种类名：
AOSP 风格的 `com.android.networkstack.android.net.dhcp.DhcpPacket`、旧版 AOSP 的
`android.net.dhcp.DhcpPacket`、以及 Google Play 版的
`com.google.android.networkstack.android.net.dhcp.DhcpPacket`。此外，模块会沿类继承链查找
（`getDeclaredField`）并将 `mHostName` / `mVendorId` 字段置空 —— 这些字段在 Google 版构建中不一定是 public。

如果模块在你的 ROM 上没有效果，可以反编译或查看 `NetworkStack.apk`，搜索以下关键词：

```text
DhcpPacket
addTlv
addCommonClientTlvs
Hostname
VendorId
mVendorId
getVendorId
DHCP_HOST_NAME
```

然后根据实际类名修改 Hook 目标。

> 模块的 Hook 策略已对照 HyperOS 4 的 NetworkStack（APK `versionName=17`，即目标 Android 17）反编译代码核对：`DhcpDiscoverPacket`、`DhcpRequestPacket`、`DhcpReleasePacket` 均通过 `addCommonClientTlvs()` → `addTlv()` 写入 Option 12/60，DHCP 服务端应答（Offer/Ack）不会写入 Option 60，DHCPv6 路径亦不携带 Vendor-Class。因此拦截上述方法即可完整移除 DHCPv4 客户端报文中的 Hostname 与 Vendor-Class。

## 局限性

本模块只阻止 DHCP Option 12 Hostname 与 Option 60 Vendor Class Identifier。

它不会隐藏：

* DHCP Client-ID（其中包含由 MAC 派生的标识，Android 默认随机 MAC 时会一并随机）
* MAC 地址（DHCP 的 chaddr 与以太网帧头中必然携带）
* mDNS / Bonjour 名称
* SSDP / UPnP 服务名称
* 蓝牙设备名
* Wi-Fi Direct 设备名
* 其它局域网服务发现流量

如果希望进一步减少局域网识别信息，可以同时考虑：

* 启用随机 MAC 地址
* 修改或清空 Android 设备名称
* 关闭不需要的局域网发现功能
* 在不需要投屏、打印、局域网发现时阻止 mDNS / SSDP

## 故障排查

### 模块已启用，但仍然看到 Hostname / Vendor-Class

请先检查 LSPosed 日志，确认模块已注入到你设备上实际的 NetworkStack 包
（`com.android.networkstack` 或 `com.google.android.networkstack`）——EEA / Pixel 等没有 AOSP 包的版本需要选择 Google 包。日志中会显示找到了哪个 `DhcpPacket` 类名（“Found DhcpPacket class: ...”），若全部找不到则会输出错误日志。

### Wi-Fi 或 DHCP 出现异常

请在 LSPosed 中禁用本模块并重启设备。

本模块会修改 DHCP 报文构造过程。虽然它只跳过 Option 12 与 Option 60，但不同 ROM 的 NetworkStack 可能存在额外定制。

### Hostname / Vendor-Class 消失后，路由器仍然能识别设备

路由器可能通过其它信息识别设备，例如：

* MAC 地址厂商前缀
* DHCP Client-ID
* mDNS
* SSDP / UPnP
* 设备指纹
* 路由器历史缓存记录

本模块只负责移除 DHCP Hostname 与 Vendor-Class。

## 免责声明

本项目用于隐私研究和个人设备控制。

请仅在你拥有或管理的设备上使用。作者不对网络异常、设备不稳定或滥用行为负责。

## 许可证

MIT License
