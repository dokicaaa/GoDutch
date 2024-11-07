//package com.example.godutch;
//
//import android.app.Notification;
//import android.app.PendingIntent;
//import android.app.TaskStackBuilder;
//import android.content.BroadcastReceiver;
//import android.content.Context;
//import android.content.Intent;
//
//import androidx.core.app.NotificationCompat;
//
//public class AlarmReciver extends BroadcastReceiver {
//    @Override
//    public void onReceive(Context context, Intent intent) {
//        Intent notificationIntent = new Intent(context, FirstScreen.class);
//
//        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
//        stackBuilder.addParentStack(FirstScreen.class);
//        stackBuilder.addNextIntent(notificationIntent);
//
//        PendingIntent pendingIntent = stackBuilder.getPendingIntent(100, PendingIntent.FLAG_UPDATE_CURRENT);
//
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
//    }
//}
