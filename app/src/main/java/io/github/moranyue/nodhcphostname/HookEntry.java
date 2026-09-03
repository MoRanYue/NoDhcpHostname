package io.github.moranyue.nodhcphostname;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class HookEntry extends XposedModule {
    private static final String TAG = "NoDhcpHostname";
    private static final String TARGET_PACKAGE = "com.android.networkstack";
    private static final String DHCP_PACKET = "com.android.networkstack.android.net.dhcp.DhcpPacket";

    // DHCP option codes (RFC 2132)
    private static final int OPT_HOST_NAME = 12;          // Host Name
    private static final int OPT_VENDOR_CLASS_ID = 60;    // Vendor Class Identifier

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, TAG, "Module loaded. runtimeApi=" + getApiVersion());
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) return;
        if (!param.isFirstPackage()) return;

        try {
            ClassLoader cl = param.getDefaultClassLoader();
            Class<?> dhcpPacket = Class.forName(DHCP_PACKET, false, cl);

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

    private void clearField(Class<?> dhcpPacket, Object packet, String fieldName) {
        try {
            Field field = dhcpPacket.getField(fieldName);
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
