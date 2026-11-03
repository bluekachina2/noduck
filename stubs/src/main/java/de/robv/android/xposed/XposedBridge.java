package de.robv.android.xposed;

import java.util.Set;

// Compile-time stub. The real class is provided by LSPosed at runtime.
public final class XposedBridge {
    public static void log(String text) {
    }

    public static Set<XC_MethodHook.Unhook> hookAllConstructors(
            Class<?> hookClass, XC_MethodHook callback) {
        return null;
    }

    public static Set<XC_MethodHook.Unhook> hookAllMethods(
            Class<?> hookClass, String methodName, XC_MethodHook callback) {
        return null;
    }
}
