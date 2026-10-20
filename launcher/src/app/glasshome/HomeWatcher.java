package app.glasshome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS hard-wires Home to Amazon's launcher (its key policy handles Home before any app sees it)
 * and won't let that launcher be disabled. So when Amazon's home screen comes to the front we open
 * Glass Home over it. Only window changes from that one package are delivered; nothing is read.
 */
public class HomeWatcher extends AccessibilityService {
    @Override protected void onServiceConnected() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.packageNames = new String[] { "com.amazon.tv.launcher" };
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        setServiceInfo(info);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (!String.valueOf(e.getClassName()).endsWith(".HomeActivity_vNext")) return;
        // After Home, Android holds app-started launches for 5 s. A launch the system performs is exempt,
        // so hand it to AlarmManager for "now" instead of calling startActivity ourselves.
        PendingIntent pi = PendingIntent.getActivity(this, 0,
            new Intent(this, Main.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT);
        ((AlarmManager) getSystemService(ALARM_SERVICE)).setExact(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime(), pi);
    }

    @Override public void onInterrupt() { }
}
