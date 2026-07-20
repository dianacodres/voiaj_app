package com.example.mobile_app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationHelper {
    public static final String CHANNEL_TRIPS = "channel_trips";
    public static final String CHANNEL_INACTIVITY = "channel_inactivity";

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel tripsChannel = new NotificationChannel(
                    CHANNEL_TRIPS,
                    "Vacation Updates",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            tripsChannel.setDescription("Notifications about your upcoming trips");

            NotificationChannel inactivityChannel = new NotificationChannel(
                    CHANNEL_INACTIVITY,
                    "App Engagement",
                    NotificationManager.IMPORTANCE_LOW
            );
            inactivityChannel.setDescription("Reminders to plan your next journey");

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(tripsChannel);
                manager.createNotificationChannel(inactivityChannel);
            }
        }
    }

    public static void sendNotification(Context context, int id, String channelId, String title, String content) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(context, DiscoverActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_ai) // Înlocuiește cu iconița aplicației tale
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).notify(id, builder.build());
    }
}