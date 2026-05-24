# No DHCP Hostname

[English](/README.md) | **简体中文**

一个用于阻止 Android NetworkStack 在 DHCP 请求中上报设备主机名的 LSPosed 模块。

在部分 Android 设备上，尤其是小米 / HyperOS 设备，DHCP 报文中可能包含 Option 12 Host Name，例如：

```text
Hostname (12): "Xiaomi-15"
Vendor-Class (60): "android-dhcp-16"
```

本模块会在 DHCP 报文发送前拦截 Option 12，从而减少设备名称泄露给路由器或 DHCP 服务器。

## 功能说明

本模块会挂钩 Android NetworkStack，并阻止 DHCP Host Name Option 被写入报文。

目标包名：

```text
com.android.networkstack
```

在已测试的 HyperOS / Android 16 系统中，目标类名为：

```text
com.android.networkstack.android.net.dhcp.DhcpPacket
```

Hook 的方法包括：

```java
addTlv(ByteBuffer, byte, String)
addTlv(ByteBuffer, byte, byte[])
```

当 DHCP option 编号为 `12` 时，模块会跳过原始方法，从而阻止 Hostname 被写入 DHCP 报文。

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
* 已测试环境：
  * HyperOS / Android 16
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
```

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
```

启用模块后，DHCP 报文中不应再出现：

```text
Hostname (12)
```

本模块不会移除 Vendor-Class、Client-ID、Requested-IP、Parameter-Request 等其它 DHCP 选项。

## 兼容性说明

**此模块仅在小米15（`dada`），系统版本`HyperOS 3.0.302.0.WOCCNXM.C07 (Android 16)`上测试通过。**

本模块默认针对以下类名：

```text
com.android.networkstack.android.net.dhcp.DhcpPacket
```

部分 ROM 可能使用 AOSP 原始类名：

```text
android.net.dhcp.DhcpPacket
```

如果模块在你的 ROM 上没有效果，可以反编译或查看 `NetworkStack.apk`，搜索以下关键词：

```text
DhcpPacket
addTlv
addCommonClientTlvs
Hostname
DHCP_HOST_NAME
```

然后根据实际类名修改 Hook 目标。

## 局限性

本模块只阻止 DHCP Option 12 Hostname。

它不会隐藏：

* DHCP Vendor-Class，例如 `android-dhcp-16`
* DHCP Client-ID
* MAC 地址
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

### 模块已启用，但仍然看到 Hostname

请先检查 LSPosed 日志，确认模块已经注入：

```text
com.android.networkstack
```

同时确认你的 ROM 是否使用了预期类名：

```text
com.android.networkstack.android.net.dhcp.DhcpPacket
```

### Wi-Fi 或 DHCP 出现异常

请在 LSPosed 中禁用本模块并重启设备。

本模块会修改 DHCP 报文构造过程。虽然它只跳过 Option 12，但不同 ROM 的 NetworkStack 可能存在额外定制。

### Hostname 消失后，路由器仍然能识别设备

路由器可能通过其它信息识别设备，例如：

* MAC 地址厂商前缀
* DHCP Vendor-Class
* mDNS
* SSDP / UPnP
* 设备指纹
* 路由器历史缓存记录

本模块只负责移除 DHCP Hostname。

## 免责声明

本项目用于隐私研究和个人设备控制。

请仅在你拥有或管理的设备上使用。作者不对网络异常、设备不稳定或滥用行为负责。

## 许可证

MIT License
