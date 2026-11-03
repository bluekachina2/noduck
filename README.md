# NoDuck (LSPosed module)

Stops notification and system sounds (charger, messages, UI sounds) from lowering your music.

**What it does:** inside system_server it hooks `MediaFocusControl.requestAudioFocus`. When a
notification-type sound asks for "transient may duck" focus, the module answers "granted"
without registering the request. The focus stack never changes, so Android never ducks the music.
Navigation prompts, assistant, calls, alarms and ringtones are left alone.

**Status:** syntax-checked only. It has not been run on a Motorola device.

## 0. Confirm the cause first (30 seconds)

Play music, trigger the sound (plug the charger), then right away run:

    su -c "dumpsys audio" | grep -iE "requestAudioFocus|duck"

If you see a `requestAudioFocus` line with `usage=USAGE_NOTIFICATION` or
`USAGE_ASSISTANCE_SONIFICATION` and a `MAY_DUCK` gain around that moment, this module targets the
right thing. If you see nothing, the ducking comes from somewhere else (vendor audio HAL / Moto
audio effects), so send me the output before installing.

## 1. Build (GitHub, no Android Studio needed)

1. On github.com create a new repository (private is fine).
2. Upload the contents of this folder, including the hidden `.github` folder.
   (If `.github` doesn't upload: Add file > Create new file, name it `.github/workflows/build.yml`,
   and paste in the contents of that file.)
3. Open the repo's **Actions** tab. The "Build APK" run starts on its own (or press Run workflow).
4. When it's green, open the run and download the `NoDuck-apk` artifact. Unzip it to get `app-debug.apk`.

Building locally instead: open in Android Studio and build. No extra jars are needed.

## 2. Install

1. Install the APK (it updates in place over v1.1 or later).
2. LSPosed > Modules > NoDuck > turn on, and tick **System Framework** and **System UI** in its scope.
3. Reboot.
4. LSPosed > Logs > Modules should show: `NoDuck: hooked 1 requestAudioFocus overload(s)`.
5. Play music, plug the charger. The log shows `NoDuck: skipped duck request` and the music stays at full volume.

## If something goes wrong

- Hooked 0 overloads: your ROM renamed the method. Send me the log.
- Bootloop: disable LSPosed from adb or recovery:
  `touch /data/adb/modules/zygisk_lsposed/disable` (run as root), then reboot.

## Screen recorder (v1.2+)

- Countdown: `RecordingController.startCountdown` in System UI is shortened to ~0.
  Log line: `NoDuck: recorder countdown skipped`.
- Vibration (v1.3): the recorder's notification channel is locked by the system, so the module
  skips `NotificationManagerService.playVibration` for System UI's `screen_record` channel.
  Log line: `NoDuck: skipped recorder vibration`.
  Every System UI notification vibration also logs its channel id (`systemui notification
  vibration, channel=...`), which shows what to change if something still vibrates.
- Sound (v1.4): same idea for `playSound`. Log line: `NoDuck: skipped recorder playSound`.
- Countdown fallback (v1.4): inside System UI, any `CountDownTimer` of 1-5 s created by code whose
  class name contains "record" is shortened. It logs `NoDuck: CountDownTimer ms=... caller=...`.
  If the countdown is still there, that log line names the class that creates it.
- Each one has an on/off constant at the top of `Hook.java`.

## Tuning

Edit `isSystemSoundUsage` in `Hook.java` to add or remove usages. For example, remove `13` if you
still want UI sounds to duck.
