package id.my.jasaorangsuruhan.mitra;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends Activity {
    private static final int REQUEST_LOCATION = 4101;
    private static final int REQUEST_NOTIFICATIONS = 4102;
    private WebView webView;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                installNativePushBridge();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false);
                } else {
                    geoOrigin = origin;
                    geoCallback = callback;
                    requestLocationPermission();
                }
            }
        });
        webView.addJavascriptInterface(new JosAndroidBridge(), "JOSAndroid");
        createNotificationChannel();
        requestNotificationPermissionIfNeeded();
        webView.loadUrl("https://mitra.jasaorangsuruhan.my.id/");
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            getSharedPreferences("jos_mitra", MODE_PRIVATE).edit().putString("fcm_token", token).apply();
            notifyWebToken(token);
        });
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= 23 && !hasLocationPermission()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION && geoCallback != null) {
            boolean granted = hasLocationPermission();
            geoCallback.invoke(geoOrigin, granted, false);
            geoCallback = null;
            geoOrigin = null;
        }
    }

    private void installNativePushBridge() {
        if (webView == null) return;
        String script = "(function(){"
            + "if(!window.JOSAndroid)return;"
            + "window.__josRegisterNativeFcmToken=async function(token){"
            + "if(!token||typeof db==='undefined'||typeof currentUser==='undefined'||!currentUser||!currentUser.id)return false;"
            + "const msg=document.getElementById('mitraPushMsg');"
            + "try{const result=await db.from('jos_mitra_fcm_tokens').upsert({user_id:currentUser.id,token:token,platform:'android',app_id:'id.my.jasaorangsuruhan.mitra',updated_at:new Date().toISOString()},{onConflict:'token'});"
            + "if(result.error)throw result.error;"
            + "if(msg){msg.textContent='✓ Notifikasi Android aktif. Perangkat berhasil terdaftar.';msg.classList.remove('err');}"
            + "return true;}catch(e){if(msg){msg.textContent='Gagal mendaftarkan perangkat: '+(e.message||'Kesalahan server');msg.classList.add('err');}return false;}"
            + "};"
            + "window.josAndroidFcmToken=function(token){window.__josNativeFcmToken=token;window.__josRegisterNativeFcmToken(token);};"
            + "window.josHandleEnableWebPush=async function(){"
            + "const b=document.getElementById('mitraEnablePush'),m=document.getElementById('mitraPushMsg');"
            + "if(b){b.disabled=true;b.textContent='Mengaktifkan...';}"
            + "if(m){m.textContent='Meminta izin notifikasi Android...';m.classList.remove('err');}"
            + "try{JOSAndroid.requestNotifications();const until=Date.now()+25000;let token='';"
            + "while(Date.now()<until){if(!JOSAndroid.areNotificationsEnabled()) {await new Promise(r=>setTimeout(r,400));continue;}"
            + "token=JOSAndroid.getFcmToken()||'';if(token)break;await new Promise(r=>setTimeout(r,500));}"
            + "if(!JOSAndroid.areNotificationsEnabled())throw new Error('Izin notifikasi Android belum diizinkan.');"
            + "if(!token)throw new Error('Token Firebase belum tersedia. Periksa konfigurasi Firebase pada APK.');"
            + "if(typeof currentUser==='undefined'||!currentUser||!currentUser.id)throw new Error('Sesi mitra belum siap. Silakan login ulang.');"
            + "if(!await window.__josRegisterNativeFcmToken(token))throw new Error('Token belum berhasil didaftarkan ke server.');"
            + "if(b)b.textContent='✓ Notifikasi HP aktif';"
            + "}catch(e){if(m){m.textContent='Gagal mengaktifkan notifikasi: '+(e.message||'Kesalahan tidak diketahui');m.classList.add('err');}if(b)b.textContent='Aktifkan notifikasi di HP';}"
            + "finally{if(b)b.disabled=false;}"
            + "};"
            + "const saved=JOSAndroid.getFcmToken();if(saved)window.josAndroidFcmToken(saved);
let attempts=0;const retry=setInterval(async()=>{attempts++;const t=JOSAndroid.getFcmToken();if(t&&typeof currentUser!=='undefined'&&currentUser&&currentUser.id){const ok=await window.__josRegisterNativeFcmToken(t);if(ok||attempts>=180)clearInterval(retry);}else if(attempts>=180)clearInterval(retry);},2000);"
            + "})();";
        webView.evaluateJavascript(script, null);
    }

    private void notifyWebToken(String token) {
        if (webView == null) return;
        String safe = org.json.JSONObject.quote(token);
        webView.post(() -> webView.evaluateJavascript(
            "if(window.josAndroidFcmToken){window.josAndroidFcmToken(" + safe + ");}", null));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                JosMitraMessagingService.CHANNEL_ID, "Pesanan JOS Mitra",
                android.app.NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifikasi pesanan baru untuk Mitra JOS");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 350, 180, 350});
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PRIVATE);
            getSystemService(android.app.NotificationManager.class).createNotificationChannel(channel);
        }
    }

    public class JosAndroidBridge {
        @JavascriptInterface public String getFcmToken() {
            return getSharedPreferences("jos_mitra", MODE_PRIVATE).getString("fcm_token", "");
        }
        @JavascriptInterface public void requestNotifications() { runOnUiThread(() -> requestNotificationPermissionIfNeeded()); }
        @JavascriptInterface public boolean areNotificationsEnabled() {
            return NotificationManagerCompat.from(MainActivity.this).areNotificationsEnabled();
        }
        @JavascriptInterface public void openAppSettings() {
            runOnUiThread(() -> startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        }
        @JavascriptInterface public void requestBackgroundLocationSettings() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 30) {
                    startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
                    Toast.makeText(MainActivity.this, "Pilih Izin lokasi lalu izinkan sepanjang waktu jika memang diperlukan.", Toast.LENGTH_LONG).show();
                } else if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 4103);
                }
            });
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
