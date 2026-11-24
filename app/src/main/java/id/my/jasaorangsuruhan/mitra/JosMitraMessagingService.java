package id.my.jasaorangsuruhan.mitra;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class JosMitraMessagingService extends FirebaseMessagingService {
    // Versioned channel ensures Android applies the new JOS sound on existing installations.
    public static final String CHANNEL_ID = "jos_mitra_orders_v2";

    @Override public void onNewToken(String token) {
        getSharedPreferences("jos_mitra", MODE_PRIVATE).edit().putString("fcm_token", token).apply();
        Intent intent = new Intent("id.my.jasaorangsuruhan.mitra.FCM_TOKEN_UPDATED");
        intent.setPackage(getPackageName());
        intent.putExtra("token", token);
        sendBroadcast(intent);
    }

    @Override public void onMessageReceived(RemoteMessage message) {
        String title = message.getNotification() != null && message.getNotification().getTitle() != null
            ? message.getNotification().getTitle() : message.getData().getOrDefault("title", "Pesanan JOS tersedia");
        String body = message.getNotification() != null && message.getNotification().getBody() != null
            ? message.getNotification().getBody() : message.getData().getOrDefault("body", "Ada pesanan baru yang bisa Anda ambil.");
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title).setContentText(body).setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true).setDefaults(Notification.DEFAULT_VIBRATE).setContentIntent(pending).build();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify((int) (System.currentTimeMillis() & 0x7fffffff), notification);
    }
}