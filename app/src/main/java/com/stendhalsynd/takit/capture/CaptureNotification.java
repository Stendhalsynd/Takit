package com.stendhalsynd.takit.capture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.stendhalsynd.takit.MainActivity;
import com.stendhalsynd.takit.R;

public final class CaptureNotification {
    public static final String CHANNEL_ID = "takit_capture";

    private CaptureNotification() {
    }

    public static Notification build(Context context, String title, String text) {
        ensureChannel(context);
        Intent openApp = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openApp,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        return new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_takit)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    private static void ensureChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_capture),
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }
}
