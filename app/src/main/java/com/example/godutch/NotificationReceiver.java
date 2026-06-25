package com.example.godutch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

public class NotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "reminder_channel")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Dining Out Tonight?")
                .setContentText("Don't stress about the check! Split bills and calculate tips with a simple scan 🍽️")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
    }
}
