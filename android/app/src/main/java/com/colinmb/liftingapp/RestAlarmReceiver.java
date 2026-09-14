package com.colinmb.liftingapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/* Plays the rest-timer chime when an alarm set by RestAlarmPlugin goes off.

   Posting a notification is not enough: some phones (Samsung among them) mute
   every notification sound while the ringer is on vibrate, whatever audio
   attributes the channel carries. Playing the sound here with MediaPlayer lets us
   pick the stream instead. The alarm stream survives vibrate mode, but Android
   deliberately mirrors alarms to the phone speaker so they cannot be missed, which
   is wrong with earbuds in. So play as media whenever a headset is connected (it
   goes to the headset alone, ducks the music, and vibrate mode does not silence
   media either) and keep the alarm stream for the bare phone. */
public class RestAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        int which = intent.getIntExtra("which", 1);

        /* Hold the CPU awake long enough to finish playing; the timeout releases it. */
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            PowerManager.WakeLock lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "liftingapp:rest");
            lock.acquire(15000);
        }

        vibrate(context, which);
        play(context, which);
    }

    private void vibrate(Context context, int which) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) return;
        long[] pattern = which == 2
            ? new long[] { 0, 250, 150, 250, 150, 250 }
            : new long[] { 0, 250, 150, 250 };
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception e) { /* some phones refuse; the sound is the point */ }
    }

    /* Anything worn on the head: the ding belongs there and nowhere else. */
    private boolean headsetConnected(AudioManager audio) {
        if (audio == null) return false;
        try {
            for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int type = device.getType();
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    || type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    || type == AudioDeviceInfo.TYPE_USB_HEADSET) return true;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && type == AudioDeviceInfo.TYPE_BLE_HEADSET) return true;
            }
        } catch (Exception e) { /* fall back to the alarm stream */ }
        return false;
    }

    private void play(Context context, int which) {
        final AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(headsetConnected(audio) ? AudioAttributes.USAGE_MEDIA : AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

        /* Duck whatever is playing rather than fighting it. */
        final AudioFocusRequest focus;
        if (audio != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .build();
            audio.requestAudioFocus(focus);
        } else {
            focus = null;
        }

        try {
            AssetFileDescriptor fd = context.getResources()
                .openRawResourceFd(which == 2 ? R.raw.rest_ding_long : R.raw.rest_ding);
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(attributes);
            player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            player.prepare();
            player.setOnCompletionListener(finished -> {
                finished.release();
                if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
            });
            player.start();
        } catch (Exception e) {
            if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
        }
    }
}
