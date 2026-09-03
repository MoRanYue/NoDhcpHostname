package io.github.moranyue.nodhcphostname;

import android.util.Log;

import java.lang.reflect.Field;
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

    // DHCP option codes (RFC 2132)
    private static final int OPT_HOST_NAME = 12;          // Host Name
    private static final int OPT_VENDOR_CLASS_ID = 60;    // Vendor Class Identifier

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
        Method method = dhcpPacket.getDeclaredMethod(
                "addTlv",
                ByteBuffer.class,
                byte.class,
                String.class
        );
        method.setAccessible(true);

        hook(method).intercept(chain -> {
            Object optionObj = chain.getArg(1);
            int option = toOptionCode(optionObj);
            if (isBlockedOption(option)) {
                log(Log.INFO, TAG, "Blocked DHCP option " + option + " (" + optionName(option) + ") TLV: String");
                return null; // skip original static void method
            }
            return chain.proceed();
        });
    }

    private void hookAddTlvBytes(Class<?> dhcpPacket) throws NoSuchMethodException {
        Method method = dhcpPacket.getDeclaredMethod(
                "addTlv",
                ByteBuffer.class,
                byte.class,
                byte[].class
        );
        method.setAccessible(true);

        hook(method).intercept(chain -> {
            Object optionObj = chain.getArg(1);
            int option = toOptionCode(optionObj);
            if (isBlockedOption(option)) {
                log(Log.INFO, TAG, "Blocked DHCP option " + option + " (" + optionName(option) + ") TLV: byte[]");
                return null; // skip original static void method
            }
            return chain.proceed();
        });
    }

    private void hookAddCommonClientTlvs(Class<?> dhcpPacket) throws NoSuchMethodException {
        Method method = dhcpPacket.getDeclaredMethod(
                "addCommonClientTlvs",
                ByteBuffer.class
        );
        method.setAccessible(true);

        hook(method).intercept(chain -> {
            try {
                Object packet = chain.getThisObject();
                if (packet != null) {
                    // Defense in depth: clear the fields the ROM reads when building
                    // the common client TLVs, so nothing is written even if a ROM
                    // serializes option 12/60 without going through addTlv().
                    clearField(dhcpPacket, packet, "mHostName");
                    clearField(dhcpPacket, packet, "mVendorId");
                }
            } catch (Throwable t) {
                log(Log.WARN, TAG, "Failed to clear client TLV fields before addCommonClientTlvs", t);
            }
            return chain.proceed();
        });
    }

    private static Field getDeclaredFieldInHierarchy(Class<?> cls, String name) throws NoSuchFieldException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private void clearField(Class<?> dhcpPacket, Object packet, String fieldName) {
        try {
            Field field = getDeclaredFieldInHierarchy(dhcpPacket, fieldName);
            field.set(packet, null);
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to clear field " + fieldName, t);
        }
    }

    private int toOptionCode(Object optionObj) {
        if (!(optionObj instanceof Byte)) return -1;
        return ((Byte) optionObj) & 0xff;
    }

    private boolean isBlockedOption(int option) {
        return option == OPT_HOST_NAME || option == OPT_VENDOR_CLASS_ID;
    }

    private String optionName(int option) {
        switch (option) {
            case OPT_HOST_NAME:
                return "Host Name";
            case OPT_VENDOR_CLASS_ID:
                return "Vendor Class Identifier";
            default:
                return "unknown";
        }
    }
}
