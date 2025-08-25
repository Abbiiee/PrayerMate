package com.ebaa.prayermate;

import android.content.Context;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings;

public class LocationHelper {

    private Context context;

    public LocationHelper(Context context) {
        this.context = context;
    }

    /**
     * فحص ما إذا كان GPS مفعل
     */
    public boolean isGPSEnabled() {
        try {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * فحص ما إذا كانت خدمات الموقع مفعلة بشكل عام
     */
    public boolean isLocationEnabled() {
        try {
            int locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
            return locationMode != Settings.Secure.LOCATION_MODE_OFF;
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * فحص الاتصال بالإنترنت
     */
    public boolean isInternetAvailable() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * فحص ما إذا كان WiFi متصل
     */
    public boolean isWiFiConnected() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                NetworkInfo wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                return wifiInfo != null && wifiInfo.isConnected();
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * فحص ما إذا كانت بيانات الجوال متصلة
     */
    public boolean isMobileDataConnected() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                NetworkInfo mobileInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                return mobileInfo != null && mobileInfo.isConnected();
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * الحصول على رسالة حالة الاتصال
     */
    public String getConnectivityStatus() {
        if (isInternetAvailable()) {
            if (isWiFiConnected()) {
                return "متصل عبر WiFi";
            } else if (isMobileDataConnected()) {
                return "متصل عبر بيانات الجوال";
            } else {
                return "متصل بالإنترنت";
            }
        } else {
            return "غير متصل بالإنترنت";
        }
    }

    /**
     * الحصول على رسالة حالة الموقع
     */
    public String getLocationStatus() {
        if (isLocationEnabled()) {
            if (isGPSEnabled()) {
                return "GPS مفعل";
            } else {
                return "خدمات الموقع مفعلة (بدون GPS)";
            }
        } else {
            return "خدمات الموقع مغلقة";
        }
    }
}