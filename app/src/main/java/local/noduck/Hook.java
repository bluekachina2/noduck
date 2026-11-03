package local.noduck;

import android.media.AudioAttributes;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Stops notification / system sounds from ducking (lowering) your music.
 *
 * Android ducks other apps when something requests audio focus with
 * AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK. This hook runs inside system_server and,
 * for notification-type usages only, answers "granted" without registering the
 * request. The focus stack never changes, so nothing is ducked. The sound still
 * plays, it just plays on top of the music.
 *
 * Navigation prompts, assistant, calls, alarms and ringtones are NOT touched.
 */
public class Hook implements IXposedHookLoadPackage {

    private static final String TAG = "NoDuck: ";

    // AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
    private static final int GAIN_TRANSIENT_MAY_DUCK = 3;
    // AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    private static final int REQUEST_GRANTED = 1;

    /** Usages whose "may duck" focus requests we swallow. */
    private static boolean isSystemSoundUsage(int usage) {
        switch (usage) {
            case 5:  // USAGE_NOTIFICATION
            case 7:  // USAGE_NOTIFICATION_COMMUNICATION_REQUEST
            case 8:  // USAGE_NOTIFICATION_COMMUNICATION_INSTANT
            case 9:  // USAGE_NOTIFICATION_COMMUNICATION_DELAYED
            case 10: // USAGE_NOTIFICATION_EVENT
            case 13: // USAGE_ASSISTANCE_SONIFICATION (charger, UI sounds)
                return true;
            default:
                return false;
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // system_server runs as package "android"
        if (!"android".equals(lpparam.packageName)) {
            return;
        }

        Class<?> cls = XposedHelpers.findClassIfExists(
                "com.android.server.audio.MediaFocusControl", lpparam.classLoader);
        if (cls == null) {
            XposedBridge.log(TAG + "MediaFocusControl not found, nothing hooked");
            return;
        }

        int hooked = XposedBridge.hookAllMethods(cls, "requestAudioFocus", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    AudioAttributes aa = null;
                    Integer gain = null;
                    // Signature varies by build, so find the args by type:
                    // AudioAttributes first, then the first int after it is the gain hint.
                    for (Object arg : param.args) {
                        if (aa == null && arg instanceof AudioAttributes) {
                            aa = (AudioAttributes) arg;
                        } else if (aa != null && gain == null && arg instanceof Integer) {
                            gain = (Integer) arg;
                        }
                    }
                    if (aa == null || gain == null) {
                        return;
                    }
                    if (gain == GAIN_TRANSIENT_MAY_DUCK && isSystemSoundUsage(aa.getUsage())) {
                        XposedBridge.log(TAG + "skipped duck request, usage=" + aa.getUsage());
                        param.setResult(REQUEST_GRANTED);
                    }
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "hook error: " + t);
                }
            }
        }).size();

        XposedBridge.log(TAG + "hooked " + hooked + " requestAudioFocus overload(s)");
        if (hooked == 0) {
            XposedBridge.log(TAG + "WARNING: requestAudioFocus not found, this ROM differs from AOSP");
        }
    }
}
