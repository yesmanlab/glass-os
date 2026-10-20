package app.glasshome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * Fire OS sometimes wipes the accessibility settings (seen when its AccessibilityFSFProvider restarts), which turns
 * off HomeWatcher and sends Home back to Amazon's launcher. This puts it back: instantly from Main's settings observer,
 * at boot, after an update, and every 15 minutes as a fallback. Only while set-home.ps1 has set glasshome_home=1, so
 * "set-home.ps1 -Undo" really undoes. Needs WRITE_SECURE_SETTINGS, granted once over ADB by set-home.ps1.
 */
public class Keeper extends BroadcastReceiver {
    static final String SERVICE = "app.glasshome/app.glasshome.HomeWatcher", FLAG = "glasshome_home";

    @Override public void onReceive(Context c, Intent i) { ensure(c); schedule(c); }

    static void ensure(Context c) {
        ContentResolver r = c.getContentResolver();
        if (Settings.Secure.getInt(r, FLAG, 0) != 1) return;
        if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) return;
        String on = Settings.Secure.getString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (on == null || !on.contains(SERVICE))
            Settings.Secure.putString(r, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, on == null || on.isEmpty() ? SERVICE : on + ":" + SERVICE);
        if (Settings.Secure.getInt(r, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1)
            Settings.Secure.putInt(r, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
    }

    /** Inexact, non-waking: costs nothing while the TV sleeps. */
    static void schedule(Context c) {
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, new Intent(c, Keeper.class), PendingIntent.FLAG_UPDATE_CURRENT);
        ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES, AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi);
    }
}
