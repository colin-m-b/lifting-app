package com.colinmb.liftingapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    /* Channel ids are duplicated in www/app.js; keep them in step. */
    public static final String REST_CHANNEL = "rest-alarm";
    public static final String REST_CHANNEL_LONG = "rest-alarm-long";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(RestAlarmPlugin.class);
        super.onCreate(savedInstanceState);
        createRestChannels();
    }

    /* The rest timer has to be heard with the phone on vibrate, the screen off and
       music playing in earbuds. A notification channel whose audio attributes say
       USAGE_ALARM plays on the alarm stream: ringer mode does not silence it, Do Not
       Disturb lets it through by default, and it mixes over media instead of being
       swallowed by it. Capacitor's LocalNotifications plugin cannot set audio
       attributes, so the channels are made here and JS only schedules onto them.
       Android keeps a channel's settings from the moment it is first created, so
       changing any of this needs a new channel id. */
    private void createRestChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;

        nm.createNotificationChannel(restChannel(REST_CHANNEL, "Rest timer", "Dings when the first rest period is over", R.raw.rest_ding));
        nm.createNotificationChannel(restChannel(REST_CHANNEL_LONG, "Rest timer (long)", "Dings when the second rest period is over", R.raw.rest_ding_long));

        /* Channels from earlier builds, whose sound followed the ringer. */
        nm.deleteNotificationChannel("rest");
        nm.deleteNotificationChannel("rest-v2");
    }

    private NotificationChannel restChannel(String id, String name, String description, int soundRes) {
        NotificationChannel channel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(description);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[] { 0, 250, 150, 250 });
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.setShowBadge(false);
        Uri sound = Uri.parse("android.resource://" + getPackageName() + "/" + soundRes);
        channel.setSound(sound, new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build());
        return channel;
    }
}
