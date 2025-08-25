package com.ebaa.prayermate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.widget.Toast;

public class NetworkChangeReceiver extends BroadcastReceiver {

    private static boolean wasConnected = true;

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (intent.getAction() != null &&
                    intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {

                boolean isConnected = isNetworkAvailable(context);

                if (isConnected && !wasConnected) {
                    // تم الاتصال بالإنترنت
                    Toast.makeText(context, "✅ تم الاتصال بالإنترنت - جاري تحديث أوقات الصلاة",
                            Toast.LENGTH_SHORT).show();

                    // إرسال broadcast للتطبيق الرئيسي لتحديث البيانات
                    Intent updateIntent = new Intent("com.ebaa.prayermate.CONNECTIVITY_CHANGED");
                    updateIntent.putExtra("connected", true);
                    context.sendBroadcast(updateIntent);

                } else if (!isConnected && wasConnected) {
                    // انقطع الاتصال بالإنترنت
                    Toast.makeText(context, "❌ انقطع الاتصال بالإنترنت - استخدام الأوقات المحفوظة",
                            Toast.LENGTH_SHORT).show();

                    Intent updateIntent = new Intent("com.ebaa.prayermate.CONNECTIVITY_CHANGED");
                    updateIntent.putExtra("connected", false);
                    context.sendBroadcast(updateIntent);
                }

                wasConnected = isConnected;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * فحص الاتصال بالإنترنت باستخدام الطريقة الحديثة والمتوافقة مع Android الجديد
     */
    private boolean isNetworkAvailable(Context context) {
        try {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (connectivityManager == null) {
                return false;
            }

            // للإصدارات الأحدث من Android M (API 23+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = connectivityManager.getActiveNetwork();
                if (activeNetwork == null) return false;

                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
                return capabilities != null && (
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                );
            }
            // للإصدارات الأقدم
            else {
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}