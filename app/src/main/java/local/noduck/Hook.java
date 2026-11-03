package local.noduck;

import android.media.AudioAttributes;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * NoDuck:
 *  1. Stops notification / system sounds from ducking (lowering) your music.
 *  2. Removes the 3-second countdown of the built-in screen recorder.
 *  3. Removes the vibration and sound of the screen recorder's start/stop notifications.
 *
 * Each part has an on/off constant below.
 *
 * Ducking: Android ducks other apps when something requests audio focus with
 * AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK. The hook runs inside system_server and, for
 * notification-type usages only, answers "granted" without registering the request.
 * The sound still plays, it just plays on top of the music.
 * Navigation prompts, assistant, calls, alarms and ringtones are NOT touched.
 */
public class Hook implements IXposedHookLoadPackage {

    private static final String TAG = "NoDuck: ";

    // Set to false to keep the stock 3-second screen recorder countdown.
    private static final boolean REMOVE_RECORDER_COUNTDOWN = true;

    // Set to false to keep the vibration when the screen recorder starts and stops.
    private static final boolean REMOVE_RECORDER_VIBRATION = true;

    // Set to false to keep the notification sound when the screen recorder starts and stops.
    private static final boolean REMOVE_RECORDER_SOUND = true;

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
        if ("android".equals(lpparam.packageName)) {
            // system_server
            hookAudioFocus(lpparam);
            if (REMOVE_RECORDER_VIBRATION) {
                hookRecorderAlert(lpparam, "playVibration");
            }
            if (REMOVE_RECORDER_SOUND) {
                hookRecorderAlert(lpparam, "playSound");
            }
        } else if (REMOVE_RECORDER_COUNTDOWN && "com.android.systemui".equals(lpparam.packageName)) {
            hookRecorderCountdown(lpparam);
            hookCountDownTimerFallback(lpparam);
        }
    }

    // ---------------------------------------------------------------- screen recorder countdown

    /** Makes the screen recorder start right away instead of after the 3-second countdown. */
    private void hookRecorderCountdown(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> cls = XposedHelpers.findClassIfExists(
                "com.android.systemui.screenrecord.RecordingController", lpparam.classLoader);
        if (cls == null) {
            XposedBridge.log(TAG + "RecordingController not found, relying on the timer fallback");
            return;
        }

        int hooked = XposedBridge.hookAllMethods(cls, "startCountdown", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    // startCountdown(long ms, long interval, PendingIntent start, PendingIntent stop)
                    if (param.args.length > 0 && param.args[0] instanceof Long) {
                        param.args[0] = 1L;
                        XposedBridge.log(TAG + "recorder countdown skipped (startCountdown)");
                    }
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "countdown hook error: " + t);
                }
            }
        }).size();

        XposedBridge.log(TAG + "hooked " + hooked + " startCountdown overload(s)");
    }

    /**
     * Fallback that does not depend on Motorola's class or method names: inside System UI, any
     * android.os.CountDownTimer of 1 to 5 seconds that is created from code whose class name
     * contains "record" is shortened to 1 ms. Every such timer is logged, so if the countdown is
     * still there the log shows which class creates it.
     */
    private void hookCountDownTimerFallback(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> cls = XposedHelpers.findClassIfExists("android.os.CountDownTimer", lpparam.classLoader);
        if (cls == null) {
            XposedBridge.log(TAG + "CountDownTimer not found, no timer fallback");
            return;
        }

        int hooked = XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (param.args.length < 2 || !(param.args[0] instanceof Long)) {
                        return;
                    }
                    long ms = (Long) param.args[0];
                    String recordCaller = null;
                    for (StackTraceElement e : new Throwable().getStackTrace()) {
                        if (e.getClassName().toLowerCase().contains("record")) {
                            recordCaller = e.getClassName() + "." + e.getMethodName();
                            break;
                        }
                    }
                    if (recordCaller == null && ms != 3000L) {
                        return;
                    }
                    XposedBridge.log(TAG + "CountDownTimer ms=" + ms + " interval=" + param.args[1]
                            + " caller=" + recordCaller);
                    if (recordCaller != null && ms >= 1000L && ms <= 5000L) {
                        param.args[0] = 1L;
                        XposedBridge.log(TAG + "recorder countdown skipped (timer fallback)");
                    }
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "timer fallback error: " + t);
                }
            }
        }).size();

        XposedBridge.log(TAG + "hooked " + hooked + " CountDownTimer constructor(s)");
    }

    // ------------------------------------------------- screen recorder notification vibration/sound

    /**
     * Blocks the vibration or sound ("playVibration" / "playSound") of the screen recorder's
     * notifications (System UI, channel "screen_record"). The channel's own settings are locked by
     * the system, so this is done in system_server, right where the alert would be played.
     */
    private void hookRecorderAlert(XC_LoadPackage.LoadPackageParam lpparam, final String methodName) {
        Class<?> cls = XposedHelpers.findClassIfExists(
                "com.android.server.notification.NotificationManagerService", lpparam.classLoader);
        if (cls == null) {
            XposedBridge.log(TAG + "NotificationManagerService not found, " + methodName + " unchanged");
            return;
        }

        int hooked = XposedBridge.hookAllMethods(cls, methodName, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (param.args.length == 0 || param.args[0] == null) {
                        return;
                    }
                    Object record = param.args[0];
                    Object sbn = XposedHelpers.callMethod(record, "getSbn");
                    String pkg = (String) XposedHelpers.callMethod(sbn, "getPackageName");
                    if (!"com.android.systemui".equals(pkg)) {
                        return;
                    }
                    Object channel = XposedHelpers.callMethod(record, "getChannel");
                    String id = channel == null
                            ? null : (String) XposedHelpers.callMethod(channel, "getId");
                    XposedBridge.log(TAG + "systemui notification " + methodName + ", channel=" + id);
                    if (id != null && id.startsWith("screen_record")) {
                        Class<?> rt = ((java.lang.reflect.Method) param.method).getReturnType();
                        param.setResult(rt == boolean.class ? Boolean.FALSE : null);
                        XposedBridge.log(TAG + "skipped recorder " + methodName);
                    }
                } catch (Throwable t) {
                    XposedBridge.log(TAG + methodName + " hook error: " + t);
                }
            }
        }).size();

        XposedBridge.log(TAG + "hooked " + hooked + " " + methodName + " overload(s)");
        if (hooked == 0) {
            XposedBridge.log(TAG + "WARNING: " + methodName + " not found, unchanged");
        }
    }

    // ------------------------------------------------------------------------------ audio ducking

    /** Stops notification / system sounds from ducking music. */
    private void hookAudioFocus(XC_LoadPackage.LoadPackageParam lpparam) {
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
