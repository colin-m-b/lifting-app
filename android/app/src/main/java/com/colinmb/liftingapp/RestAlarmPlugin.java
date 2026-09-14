package com.colinmb.liftingapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.getcapacitor.JSArray;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/* Schedules the rest-timer dings with AlarmManager. RestAlarmReceiver does the
   actual sounding, so the ding does not go through the notification system and
   is not silenced by the ringer being on vibrate. */
@CapacitorPlugin(name = "RestAlarm")
public class RestAlarmPlugin extends Plugin {

    /* One request code per ding, plus one for the Settings test. */
    private static final int[] REQUEST_CODES = { 8101, 8102, 8199 };

    @PluginMethod
    public void schedule(PluginCall call) {
        JSArray at = call.getArray("at");
        cancelAll();
        if (at == null) {
            call.reject("No times given");
            return;
        }
        try {
            for (int i = 0; i < at.length() && i < 2; i++) {
                long when = (long) at.getDouble(i);
                if (when <= System.currentTimeMillis() + 500) continue;
                setAlarm(REQUEST_CODES[i], when, i + 1);
            }
        } catch (Exception e) {
            call.reject("Could not schedule: " + e.getMessage());
            return;
        }
        call.resolve();
    }

    @PluginMethod
    public void test(PluginCall call) {
        Integer delay = call.getInt("delayMs", 1000);
        setAlarm(REQUEST_CODES[2], System.currentTimeMillis() + (delay == null ? 1000 : delay), 1);
        call.resolve();
    }

    @PluginMethod
    public void cancel(PluginCall call) {
        cancelAll();
        call.resolve();
    }

    private void setAlarm(int requestCode, long when, int which) {
        Context context = getContext();
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        PendingIntent pending = pendingIntent(context, requestCode, which);
        boolean exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms();
        if (exact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
        }
    }

    private void cancelAll() {
        Context context = getContext();
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        for (int i = 0; i < REQUEST_CODES.length; i++) {
            alarms.cancel(pendingIntent(context, REQUEST_CODES[i], 1));
        }
    }

    private PendingIntent pendingIntent(Context context, int requestCode, int which) {
        Intent intent = new Intent(context, RestAlarmReceiver.class).putExtra("which", which);
        return PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
