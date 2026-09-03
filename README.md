# No DHCP Hostname

**English** | [简体中文](/README.zh-CN.md)

An LSPosed module that prevents Android NetworkStack from sending the device hostname and Vendor-Class in DHCP requests.

On some Android devices, especially Xiaomi / HyperOS devices, DHCP packets may include the following options, such as:

```text
Hostname (12): "Xiaomi-15"
Vendor-Class (60): "android-dhcp-16"
```

This module blocks DHCP Option 12 and Option 60 before the packet is sent, reducing device-name and OS-version leakage to routers and DHCP servers.

## What it does

This module hooks Android NetworkStack and blocks DHCP Host Name (Option 12) and Vendor Class Identifier (Option 60) option generation.

Target package:

```text
com.android.networkstack
```

Known target class on tested HyperOS / Android 16 builds:

```text
com.android.networkstack.android.net.dhcp.DhcpPacket
```

Hooked methods:

```java
addTlv(ByteBuffer, byte, String)
addTlv(ByteBuffer, byte, byte[])
addCommonClientTlvs(ByteBuffer)
```

When the DHCP option code is `12` (Host Name) or `60` (Vendor Class Identifier), the original method is skipped.

> Note: in decompiled HyperOS 4 NetworkStack code, the Option 60 value comes from `DhcpPacket.getVendorId()`, defaulting to something like `android-dhcp-<os version>` (e.g. `android-dhcp-16`), and is written in `addCommonClientTlvs()` via `addTlv`. There is no other Option 60 write path (type 60 entries in the customized client options are explicitly skipped and only feed the Vendor ID string), so hooking `addTlv` and `addCommonClientTlvs` removes Vendor-Class completely. As a second line of defense, the module also nulls the `mHostName` and `mVendorId` fields before `addCommonClientTlvs()` runs, covering custom ROMs that serialize these options straight into the ByteBuffer.

## Why not only use an RRO overlay?

Android NetworkStack has a resource flag:

```text
config_dhcp_client_hostname
```

On AOSP-like systems, setting this value to `false` may prevent DHCP hostname transmission.

However, on tested HyperOS / Android 16 builds, even when:

```bash
cmd overlay lookup com.android.networkstack com.android.networkstack:bool/config_dhcp_client_hostname
```

returns:

```text
false
```

DHCP packets may still contain Option 12 Hostname.

Therefore, this module blocks the final DHCP TLV writing path directly.

## Requirements

* Rooted Android device
* LSPosed / compatible Xposed framework
* Android NetworkStack package:
  * `com.android.networkstack`
* Tested on:
  * HyperOS / Android 16
  * LSPosed API 101 runtime

Other ROMs may use a different NetworkStack class name. See the compatibility notes below.

## Installation

1. Build or download the module APK.
2. Install the APK on the device.
3. Open LSPosed.
4. Enable the module.
5. Set the scope to:

```text
com.android.networkstack
```

6. Reboot the device.
7. Reconnect Wi-Fi.

## Verification

Use `tcpdump` to capture DHCP packets:

```bash
su -c "$PREFIX/bin/tcpdump -i wlan0 -n -vvv -s 0 'udp and (port 67 or port 68)'"
```

To filter only your device, replace the MAC address:

```bash
su -c "$PREFIX/bin/tcpdump -i wlan0 -n -vvv -s 0 'ether src xx:xx:xx:xx:xx:xx and udp and (port 67 or port 68)'"
```

Before enabling this module, you may see:

```text
Hostname (12): "Xiaomi-15"
Vendor-Class (60): "android-dhcp-16"
```

After enabling this module, DHCP packets should no longer contain:

```text
Hostname (12)
Vendor-Class (60)
```

Other DHCP options, such as Client-ID, Requested-IP, and Parameter-Request, are not removed by this module.

## Compatibility notes

**This module was successfully tested on Xiaomi 15 (`dada`) with `HyperOS 3.0.302.0.WOCCNXM.C07 (Android 16)`.**

This module was designed around the following class name:

```text
com.android.networkstack.android.net.dhcp.DhcpPacket
```

Some ROMs may use the AOSP class name instead:

```text
android.net.dhcp.DhcpPacket
```

If the module does not work on your ROM, decompile or inspect your `NetworkStack.apk` and search for:

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

Then adjust the hook target accordingly.

> The hook strategy has been checked against decompiled HyperOS 4 (Android 17 beta target) NetworkStack code: `DhcpDiscoverPacket`, `DhcpRequestPacket` and `DhcpReleasePacket` all write Option 12/60 through `addCommonClientTlvs()` → `addTlv()`; the DHCP server responses (Offer/Ack) never write Option 60, and the DHCPv6 path carries no Vendor-Class. Hooking those methods therefore removes Hostname and Vendor-Class from all DHCPv4 client messages.

## Limitations

This module only blocks DHCP Option 12 Hostname and Option 60 Vendor Class Identifier.

It does not hide:

* DHCP Client-ID (carries a MAC-derived identifier; it is randomized together with the MAC when MAC randomization is on)
* MAC address (always present in DHCP chaddr and the Ethernet frame header)
* mDNS / Bonjour names
* SSDP / UPnP service names
* Bluetooth device name
* Wi-Fi Direct device name
* Other LAN service discovery traffic

For stronger privacy, also consider:

* Enabling randomized MAC addresses
* Changing or clearing the Android device name
* Disabling local network discovery features when not needed
* Blocking mDNS / SSDP if those features are not required

## Troubleshooting

### The module is enabled but Hostname / Vendor-Class is still visible

Check LSPosed logs and confirm that the module is loaded into:

```text
com.android.networkstack
```

Also verify that your ROM uses the expected class name:

```text
com.android.networkstack.android.net.dhcp.DhcpPacket
```

### Wi-Fi or DHCP behaves abnormally

Disable the module in LSPosed and reboot.

This module modifies DHCP packet construction. Although it only skips Option 12 and Option 60, unexpected ROM modifications may cause compatibility issues.

### Hostname / Vendor-Class disappears, but the router still identifies the device

Routers may identify devices through other signals, including:

* MAC address OUI
* DHCP Client-ID
* mDNS
* SSDP / UPnP
* Device fingerprinting
* Previously cached records

This module only removes DHCP Hostname and Vendor-Class.

## Disclaimer

This project is intended for privacy research and personal device control.

Use it only on devices you own or administer. The author is not responsible for network issues, device instability, or misuse.

## License

MIT License
