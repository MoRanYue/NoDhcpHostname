package io.github.moranyue.nodhcphostname;

import android.util.Log;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class HookEntry extends XposedModule {
    private static final String TAG = "NoDhcpHostname";

    // Candidate target packages
    private static final String[] TARGET_PACKAGES = {
        "com.android.networkstack",
        "com.google.android.networkstack",
    };

    // Candidate DhcpPacket class names (tried in order)
    private static final String[] DHCP_PACKET_CLASSES = {
        "com.android.networkstack.android.net.dhcp.DhcpPacket",
        "android.net.dhcp.DhcpPacket",
        "com.google.android.networkstack.android.net.dhcp.DhcpPacket",
    };

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, TAG, "Module loaded. runtimeApi=" + getApiVersion());
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        boolean isTarget = false;
        for (String pkg : TARGET_PACKAGES) {
            if (pkg.equals(param.getPackageName())) {
                isTarget = true;
                break;
            }
        }
        if (!isTarget) return;
        if (!param.isFirstPackage()) return;

        ClassLoader cl = param.getDefaultClassLoader();
        Class<?> dhcpPacket = null;

        for (String className : DHCP_PACKET_CLASSES) {
            try {
                dhcpPacket = Class.forName(className, false, cl);
                log(Log.INFO, TAG, "Found DhcpPacket class: " + className);
                break;
            } catch (ClassNotFoundException ignored) {
                log(Log.DEBUG, TAG, "Class not found: " + className);
            }
        }

        if (dhcpPacket == null) {
            log(Log.ERROR, TAG, "DhcpPacket class not found in " + param.getPackageName());
            return;
        }

        try {
            hookAddTlvString(dhcpPacket);
            hookAddTlvBytes(dhcpPacket);
            hookAddCommonClientTlvs(dhcpPacket);
            log(Log.INFO, TAG, "Hooks installed in " + param.getPackageName());
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to install hooks", t);
        }
    }

    private void hookAddTlvString(Class<?> dhcpPacket) throws NoSuchMethodException {
        Method method = dhcpPacket.getDeclaredMethod("addTlv", ByteBuffer.class, byte.class, String.class);
        method.setAccessible(true);

        hook(method).intercept(chain -> {
            Object optionObj = chain.getArg(1);
            if (isDhcpHostnameOption(optionObj)) {
                log(Log.INFO, TAG, "Blocked DHCP Hostname TLV: String");
                return null;
            }
            return chain.proceed();
        });
    }

    private void hookAddTlvBytes(Class<?> dhcpPacket) throws NoSuchMethodException {
        Method method = dhcpPacket.getDeclaredMethod("addTlv", ByteBuffer.class, byte.class, byte[].class);
        method.setAccessible(true);

        hook(method).intercept(chain -> {
            Object optionObj = chain.getArg(1);
            if (isDhcpHostnameOption(optionObj)) {
                log(Log.INFO, TAG, "Blocked DHCP Hostname TLV: byte[]");
                return null;
            }
            return chain.proceed();
        });
    }

    private void hookAddCommonClientTlvs(Class<?> dhcpPacket) throws NoSuchMethodException {
        Method method = dhcpPacket.getDeclaredMethod("addCommonClientTlvs", ByteBuffer.class);
        method.setAccessible(true);

        hook(method).intercept(chain -> {
            try {
                Object packet = chain.getThisObject();
                if (packet != null) {
                    getDeclaredFieldInHierarchy(dhcpPacket, "mHostName").set(packet, null);
                }
            } catch (Throwable t) {
                log(Log.WARN, TAG, "Failed to clear mHostName before client TLVs", t);
            }
            return chain.proceed();
        });
    }

    private static java.lang.reflect.Field getDeclaredFieldInHierarchy(Class<?> cls, String name) throws NoSuchFieldException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private boolean isDhcpHostnameOption(Object optionObj) {
        if (!(optionObj instanceof Byte)) return false;
        int option = ((Byte) optionObj) & 0xff;
        return option == 12; // DHCP Option 12 = Host Name
    }
}
