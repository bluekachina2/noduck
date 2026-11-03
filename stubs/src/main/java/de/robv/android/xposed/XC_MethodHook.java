package de.robv.android.xposed;

// Compile-time stub. The real class is provided by LSPosed at runtime.
public abstract class XC_MethodHook {
    public XC_MethodHook() {
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public static final class MethodHookParam {
        public Object[] args;
        public Object thisObject;

        public void setResult(Object result) {
        }
    }

    public class Unhook {
    }
}
