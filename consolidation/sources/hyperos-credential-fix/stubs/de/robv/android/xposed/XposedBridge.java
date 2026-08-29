package de.robv.android.xposed;

import java.lang.reflect.Member;
import java.util.Set;

public final class XposedBridge {
    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {
        return null;
    }

    public static Object invokeOriginalMethod(Member method, Object thisObject, Object[] args) throws Throwable {
        return null;
    }

    public static void log(String text) {
    }

    public static void log(Throwable throwable) {
    }
}
