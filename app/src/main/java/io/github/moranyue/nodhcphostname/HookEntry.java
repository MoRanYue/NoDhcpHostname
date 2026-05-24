package io.github.moranyue.nodhcphostname;

import android.util.Log;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class HookEntry extends XposedModule {
    private static final String TAG = "NoDhcpHostname";
    private static final String TARGET_PACKAGE = "com.android.networkstack";
    private static final String DHCP_PACKET = "com.android.networkstack.android.net.dhcp.DhcpPacket";

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
            if (isDhcpHostnameOption(optionObj)) {
                log(Log.INFO, TAG, "Blocked DHCP Hostname TLV: String");
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
            if (isDhcpHostnameOption(optionObj)) {
                log(Log.INFO, TAG, "Blocked DHCP Hostname TLV: byte[]");
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
                    dhcpPacket.getField("mHostName").set(packet, null);
                }
            } catch (Throwable t) {
                log(Log.WARN, TAG, "Failed to clear mHostName before client TLVs", t);
            }
            return chain.proceed();
        });
    }

    private boolean isDhcpHostnameOption(Object optionObj) {
        if (!(optionObj instanceof Byte)) return false;
        int option = ((Byte) optionObj) & 0xff;
        return option == 12; // DHCP Option 12 = Host Name
    }
}