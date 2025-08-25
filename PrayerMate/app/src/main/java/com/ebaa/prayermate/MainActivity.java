package com.ebaa.prayermate;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private TextView tvLocation, tvCurrentTime, tvNextPrayer;
    private RecyclerView recyclerViewPrayers;
    private FloatingActionButton fabQibla;
    private PrayerTimesAdapter adapter;
    private List<PrayerTime> prayerTimes;

    private FusedLocationProviderClient fusedLocationClient;
    private PrayerTimesApi api;
    private Handler timeHandler;
    private Runnable timeRunnable;

    private double currentLatitude = 30.0444; // Default: Cairo
    private double currentLongitude = 31.2357;

    // إضافة المتغيرات الجديدة للفحوصات
    private LocationHelper locationHelper;
    private AlertDialog connectivityDialog;
    private boolean hasShownConnectivityWarning = false;
    private boolean hasShownLocationWarning = false;
    private BroadcastReceiver connectivityReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_main);

            initViews();
            setupRecyclerView();
            setupLocation();
            setupClickListeners();

            // تهيئة مساعد الموقع والاتصال
            locationHelper = new LocationHelper(this);

            // فحص الاتصال والموقع عند بدء التطبيق
            checkConnectivityAndLocation();

            // تسجيل مستمع تغيرات الشبكة
            registerConnectivityReceiver();

            startTimeUpdater();

            // Load default prayer times first
            loadDefaultPrayerTimes();

            // Then request location permission
            requestLocationPermission();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "خطأ في تشغيل التطبيق: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initViews() {
        try {
            tvLocation = findViewById(R.id.tvLocation);
            tvCurrentTime = findViewById(R.id.tvCurrentTime);
            tvNextPrayer = findViewById(R.id.tvNextPrayer);
            recyclerViewPrayers = findViewById(R.id.recyclerViewPrayers);
            fabQibla = findViewById(R.id.fabQibla);

            // Check if views are found
            if (tvLocation == null || tvCurrentTime == null || tvNextPrayer == null ||
                    recyclerViewPrayers == null || fabQibla == null) {
                throw new RuntimeException("فشل في العثور على العناصر في التخطيط");
            }

            // Initialize API with error handling
            try {
                api = ApiClient.getClient().create(PrayerTimesApi.class);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "خطأ في تهيئة API", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("خطأ في تهيئة العناصر: " + e.getMessage());
        }
    }

    private void setupRecyclerView() {
        try {
            prayerTimes = new ArrayList<>();
            adapter = new PrayerTimesAdapter(prayerTimes);
            recyclerViewPrayers.setLayoutManager(new LinearLayoutManager(this));
            recyclerViewPrayers.setAdapter(adapter);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "خطأ في تهيئة قائمة الصلوات", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupLocation() {
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "خطأ في تهيئة خدمة الموقع", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupClickListeners() {
        try {
            fabQibla.setOnClickListener(v -> {
                try {
                    // فحص الموقع قبل فتح القبلة
                    if (!locationHelper.isLocationEnabled()) {
                        showLocationSettingsDialog("لاستخدام بوصلة القبلة بدقة، يرجى تفعيل خدمات الموقع");
                        return;
                    }

                    Intent intent = new Intent(MainActivity.this, QiblaActivity.class);
                    intent.putExtra("latitude", currentLatitude);
                    intent.putExtra("longitude", currentLongitude);
                    startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(MainActivity.this, "خطأ في فتح بوصلة القبلة", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // فحص الاتصال والموقع
    private void checkConnectivityAndLocation() {
        try {
            boolean internetAvailable = locationHelper.isInternetAvailable();
            boolean locationEnabled = locationHelper.isLocationEnabled();

            // عرض تنبيه إذا لم يكن الإنترنت متاح
            if (!internetAvailable && !hasShownConnectivityWarning) {
                showConnectivityWarning();
                hasShownConnectivityWarning = true;
            }

            // عرض تنبيه إذا لم تكن خدمات الموقع مفعلة
            if (!locationEnabled && !hasShownLocationWarning) {
                showLocationWarning();
                hasShownLocationWarning = true;
            }

            // تحديث معلومات الحالة
            updateConnectivityStatus(internetAvailable, locationEnabled);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showConnectivityWarning() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ تنبيه الاتصال")
                .setMessage("لا يوجد اتصال بالإنترنت!\n\n• لن يتم الحصول على أوقات الصلاة الدقيقة\n• سيتم استخدام الأوقات الافتراضية للقاهرة\n• تأكد من الاتصال بالإنترنت للحصول على أوقات دقيقة حسب موقعك")
                .setPositiveButton("موافق", null)
                .setNegativeButton("إعدادات الشبكة", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void showLocationWarning() {
        new AlertDialog.Builder(this)
                .setTitle("📍 تنبيه الموقع")
                .setMessage("خدمات الموقع غير مفعلة!\n\n• لن يتم تحديد موقعك الحالي\n• ستظهر أوقات القاهرة الافتراضية\n• فعل خدمات الموقع للحصول على أوقات دقيقة\n• بوصلة القبلة قد لا تعمل بدقة")
                .setPositiveButton("موافق", null)
                .setNegativeButton("فتح الإعدادات", (dialog, which) -> {
                    showLocationSettingsDialog(null);
                })
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }

    private void showLocationSettingsDialog(String customMessage) {
        String message = customMessage != null ? customMessage : "يرجى تفعيل خدمات الموقع للحصول على أوقات دقيقة";

        new AlertDialog.Builder(this)
                .setTitle("تفعيل خدمات الموقع")
                .setMessage(message)
                .setPositiveButton("فتح الإعدادات", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(this, "لا يمكن فتح إعدادات الموقع", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    // دالة واحدة فقط لتحديث معلومات الحالة
    private void updateConnectivityStatus(boolean internetAvailable, boolean locationEnabled) {
        try {
            String locationText = "الموقع: ";

            // أولاً فحص حالة خدمات الموقع والإنترنت
            if (!locationEnabled) {
                locationText += "القاهرة، مصر (افتراضي) ⚠️ الموقع مغلق";
            } else if (!internetAvailable) {
                if (currentLatitude != 30.0444 || currentLongitude != 31.2357) {
                    locationText += getLocationName(currentLatitude, currentLongitude) + " ⚠️ لا يوجد إنترنت";
                } else {
                    locationText += "القاهرة، مصر (افتراضي) ⚠️ لا يوجد إنترنت";
                }
            } else {
                // الموقع والإنترنت متاحان
                if (currentLatitude != 30.0444 || currentLongitude != 31.2357) {
                    locationText += getLocationName(currentLatitude, currentLongitude) + " ✅";
                } else {
                    locationText += "القاهرة، مصر (افتراضي) 🔄";
                }
            }

            if (tvLocation != null) {
                tvLocation.setText(locationText);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // تسجيل مستمع تغيرات الشبكة
    private void registerConnectivityReceiver() {
        try {
            connectivityReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        boolean internetAvailable = locationHelper.isInternetAvailable();

                        if (internetAvailable) {
                            Toast.makeText(context, "✅ تم الاتصال بالإنترنت - جاري تحديث أوقات الصلاة", Toast.LENGTH_SHORT).show();
                            // إعادة محاولة تحميل أوقات الصلاة
                            if (currentLatitude != 0 && currentLongitude != 0) {
                                loadPrayerTimes(currentLatitude, currentLongitude);
                            }
                            hasShownConnectivityWarning = false;
                        } else {
                            Toast.makeText(context, "❌ انقطع الاتصال بالإنترنت - استخدام الأوقات المحفوظة", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };

            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            registerReceiver(connectivityReceiver, filter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startTimeUpdater() {
        timeHandler = new Handler();
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    updateCurrentTime();
                    timeHandler.postDelayed(this, 1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        timeHandler.post(timeRunnable);
    }

    private void requestLocationPermission() {
        try {
            Dexter.withContext(this)
                    .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    .withListener(new PermissionListener() {
                        @Override
                        public void onPermissionGranted(PermissionGrantedResponse response) {
                            getUserLocation();
                        }

                        @Override
                        public void onPermissionDenied(PermissionDeniedResponse response) {
                            Toast.makeText(MainActivity.this, "⚠️ يجب السماح بالوصول للموقع للحصول على أوقات دقيقة", Toast.LENGTH_LONG).show();
                            // Keep default prayer times and location
                            tvLocation.setText("الموقع: القاهرة، مصر (افتراضي) - لم يتم منح إذن الموقع");
                        }

                        @Override
                        public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                            token.continuePermissionRequest();
                        }
                    }).check();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "خطأ في طلب إذن الموقع", Toast.LENGTH_SHORT).show();
        }
    }

    // دالة واحدة فقط للحصول على الموقع
    private void getUserLocation() {
        try {
            // فحص خدمات الموقع قبل المحاولة
            if (!locationHelper.isLocationEnabled()) {
                updateLocationDisplay("القاهرة، مصر (افتراضي) - خدمات الموقع مغلقة");
                return;
            }

            if (fusedLocationClient == null) {
                updateLocationDisplay("القاهرة، مصر (افتراضي) - خطأ في خدمة الموقع");
                loadDefaultPrayerTimes();
                return;
            }

            // عرض رسالة التحميل
            updateLocationDisplay("جاري تحديد الموقع... 🔄");

            // محاولة الحصول على الموقع الحالي أولاً
            fusedLocationClient.getCurrentLocation(
                    com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY,
                    null
            ).addOnSuccessListener(this, location -> {
                handleLocationResult(location, true);
            }).addOnFailureListener(e -> {
                // إذا فشل الموقع الحالي، جرب آخر موقع معروف
                getLastKnownLocation();
            });

        } catch (SecurityException e) {
            e.printStackTrace();
            updateLocationDisplay("القاهرة، مصر (افتراضي) - لا يوجد إذن موقع");
        } catch (Exception e) {
            e.printStackTrace();
            updateLocationDisplay("القاهرة، مصر (افتراضي) - خطأ في الموقع");
        }
    }

    // دالة للحصول على آخر موقع معروف
    private void getLastKnownLocation() {
        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        handleLocationResult(location, false);
                    })
                    .addOnFailureListener(e -> {
                        e.printStackTrace();
                        updateLocationDisplay("القاهرة، مصر (افتراضي) - فشل في تحديد الموقع");
                        Toast.makeText(this, "❌ فشل في الحصول على الموقع", Toast.LENGTH_SHORT).show();
                    });
        } catch (SecurityException e) {
            e.printStackTrace();
            updateLocationDisplay("القاهرة، مصر (افتراضي) - لا يوجد إذن موقع");
        }
    }

    // دالة موحدة للتعامل مع نتيجة الموقع
    private void handleLocationResult(Location location, boolean isCurrentLocation) {
        try {
            if (location != null) {
                currentLatitude = location.getLatitude();
                currentLongitude = location.getLongitude();

                String locationName = getLocationName(currentLatitude, currentLongitude);
                String locationSource = isCurrentLocation ? "الحالي" : "المحفوظ";

                updateLocationDisplay(locationName + " ✅");
                loadPrayerTimes(currentLatitude, currentLongitude);

                Toast.makeText(this, "✅ تم تحديد الموقع " + locationSource + ": " + locationName, Toast.LENGTH_SHORT).show();
            } else {
                updateLocationDisplay("القاهرة، مصر (افتراضي) - لم يتم العثور على الموقع");
                Toast.makeText(this, "❌ لم يتم العثور على الموقع، تأكد من تفعيل GPS", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            updateLocationDisplay("القاهرة، مصر (افتراضي) - خطأ في معالجة الموقع");
        }
    }

    // دالة لتحديث نص الموقع
    private void updateLocationDisplay(String locationText) {
        try {
            if (tvLocation != null) {
                String fullText = "الموقع: " + locationText;

                // إضافة تحذير الإنترنت إذا لزم الأمر
                if (!locationHelper.isInternetAvailable() && !locationText.contains("بدون إنترنت") && !locationText.contains("⚠️")) {
                    fullText += " (بدون إنترنت)";
                }

                tvLocation.setText(fullText);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadPrayerTimes(double latitude, double longitude) {
        // فحص الإنترنت قبل المحاولة
        if (!locationHelper.isInternetAvailable()) {
            Toast.makeText(this, "❌ لا يوجد اتصال بالإنترنت - استخدام الأوقات الافتراضية", Toast.LENGTH_SHORT).show();
            loadDefaultPrayerTimes();
            return;
        }

        if (api == null) {
            Toast.makeText(this, "❌ خطأ في API - استخدام الأوقات الافتراضية", Toast.LENGTH_SHORT).show();
            loadDefaultPrayerTimes();
            return;
        }

        try {
            Toast.makeText(this, "🔄 جاري تحميل أوقات الصلاة...", Toast.LENGTH_SHORT).show();

            String date = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());

            Call<PrayerTimesResponse> call = api.getPrayerTimes(latitude, longitude, date);
            call.enqueue(new Callback<PrayerTimesResponse>() {
                @Override
                public void onResponse(Call<PrayerTimesResponse> call, Response<PrayerTimesResponse> response) {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            Toast.makeText(MainActivity.this, "✅ تم تحميل أوقات الصلاة بنجاح", Toast.LENGTH_SHORT).show();
                            updatePrayerTimes(response.body());
                        } else {
                            Toast.makeText(MainActivity.this, "⚠️ فشل في تحميل أوقات الصلاة - استخدام الأوقات الافتراضية", Toast.LENGTH_SHORT).show();
                            loadDefaultPrayerTimes();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        loadDefaultPrayerTimes();
                    }
                }

                @Override
                public void onFailure(Call<PrayerTimesResponse> call, Throwable t) {
                    t.printStackTrace();
                    Toast.makeText(MainActivity.this, "❌ خطأ في الاتصال - استخدام الأوقات الافتراضية", Toast.LENGTH_SHORT).show();
                    loadDefaultPrayerTimes();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            loadDefaultPrayerTimes();
        }
    }

    private void updatePrayerTimes(PrayerTimesResponse response) {
        try {
            prayerTimes.clear();

            PrayerTimesResponse.Data data = response.getData();
            if (data == null) {
                loadDefaultPrayerTimes();
                return;
            }

            PrayerTimesResponse.Timings timings = data.getTimings();
            if (timings == null) {
                loadDefaultPrayerTimes();
                return;
            }

            // Clean time format (remove timezone info)
            String fajr = cleanTimeFormat(timings.getFajr());
            String sunrise = cleanTimeFormat(timings.getSunrise());
            String dhuhr = cleanTimeFormat(timings.getDhuhr());
            String asr = cleanTimeFormat(timings.getAsr());
            String maghrib = cleanTimeFormat(timings.getMaghrib());
            String isha = cleanTimeFormat(timings.getIsha());

            // إضافة جميع الصلوات مع الإيموجي المناسب لكل صلاة
            prayerTimes.add(new PrayerTime("الفجر", fajr, "🌅"));
            prayerTimes.add(new PrayerTime("الشروق", sunrise, "☀️"));
            prayerTimes.add(new PrayerTime("الظهر", dhuhr, "🌞"));
            prayerTimes.add(new PrayerTime("العصر", asr, "🌤️"));
            prayerTimes.add(new PrayerTime("المغرب", maghrib, "🌅"));
            prayerTimes.add(new PrayerTime("العشاء", isha, "🌙"));

            adapter.notifyDataSetChanged();
            updateNextPrayer();
        } catch (Exception e) {
            e.printStackTrace();
            loadDefaultPrayerTimes();
        }
    }

    private String cleanTimeFormat(String time) {
        if (time == null) return "00:00";
        // Remove timezone info like "(EET)" or "+02:00"
        return time.split(" ")[0].substring(0, Math.min(time.length(), 5));
    }

    private void loadDefaultPrayerTimes() {
        try {
            prayerTimes.clear();
            // إضافة جميع الصلوات الافتراضية مع الإيموجي
            prayerTimes.add(new PrayerTime("الفجر", "04:34", "🌅"));
            prayerTimes.add(new PrayerTime("الشروق", "06:13", "☀️"));
            prayerTimes.add(new PrayerTime("الظهر", "13:01", "🌞"));
            prayerTimes.add(new PrayerTime("العصر", "16:39", "🌤️"));
            prayerTimes.add(new PrayerTime("المغرب", "19:50", "🌅"));
            prayerTimes.add(new PrayerTime("العشاء", "21:15", "🌙"));

            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            updateNextPrayer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateCurrentTime() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            String currentTime = sdf.format(new Date());
            if (tvCurrentTime != null) {
                tvCurrentTime.setText("الوقت الحالي: " + currentTime);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateNextPrayer() {
        try {
            if (prayerTimes.isEmpty() || tvNextPrayer == null) {
                return;
            }

            // Simple logic to find next prayer
            Calendar now = Calendar.getInstance();
            int currentHour = now.get(Calendar.HOUR_OF_DAY);
            int currentMinute = now.get(Calendar.MINUTE);
            int currentTimeInMinutes = currentHour * 60 + currentMinute;

            String nextPrayerName = "الفجر"; // Default to Fajr

            for (PrayerTime prayer : prayerTimes) {
                try {
                    // تجاهل الشروق لأنه ليس صلاة
                    if (prayer.getName().equals("الشروق")) {
                        continue;
                    }

                    String[] timeParts = prayer.getTime().split(":");
                    if (timeParts.length >= 2) {
                        int prayerHour = Integer.parseInt(timeParts[0]);
                        int prayerMinute = Integer.parseInt(timeParts[1]);
                        int prayerTimeInMinutes = prayerHour * 60 + prayerMinute;

                        if (prayerTimeInMinutes > currentTimeInMinutes) {
                            nextPrayerName = prayer.getName();
                            break;
                        }
                    }
                } catch (Exception e) {
                    // Skip this prayer time if parsing fails
                    continue;
                }
            }

            tvNextPrayer.setText("الصلاة القادمة: " + nextPrayerName);
        } catch (Exception e) {
            e.printStackTrace();
            if (tvNextPrayer != null) {
                tvNextPrayer.setText("الصلاة القادمة: الفجر");
            }
        }
    }

    // دالة جديدة لتحويل الإحداثيات لاسم مكان مفهوم
    private String getLocationName(double latitude, double longitude) {
        try {
            // إذا كان الموقع قريب من القاهرة
            if (isNearCairo(latitude, longitude)) {
                return "القاهرة، مصر";
            }

            // إذا كان في مصر بشكل عام
            if (isInEgypt(latitude, longitude)) {
                return String.format(Locale.getDefault(), "مصر (%.4f, %.4f)", latitude, longitude);
            }

            // إذا كان في دولة عربية
            String arabCountry = getArabCountry(latitude, longitude);
            if (arabCountry != null) {
                return String.format(Locale.getDefault(), "%s (%.4f, %.4f)", arabCountry, latitude, longitude);
            }

            // إذا كان في دولة أجنبية معروفة
            String foreignCountry = getForeignCountry(latitude, longitude);
            if (foreignCountry != null) {
                return String.format(Locale.getDefault(), "%s (%.4f, %.4f)", foreignCountry, latitude, longitude);
            }

            // عرض رسالة واضحة مع الإحداثيات
            return String.format(Locale.getDefault(), "موقع غير محدد (%.4f, %.4f)", latitude, longitude);

        } catch (Exception e) {
            return String.format(Locale.getDefault(), "%.4f, %.4f", latitude, longitude);
        }
    }

    // إضافة دالة للدول الأجنبية المهمة
    private String getForeignCountry(double lat, double lng) {
        // أمريكا
        if (lat >= 25.0 && lat <= 49.0 && lng >= -125.0 && lng <= -66.0) {
            return "الولايات المتحدة";
        }
        // بريطانيا
        if (lat >= 50.0 && lat <= 60.0 && lng >= -8.0 && lng <= 2.0) {
            return "بريطانيا";
        }
        // فرنسا
        if (lat >= 41.0 && lat <= 51.0 && lng >= -5.0 && lng <= 9.0) {
            return "فرنسا";
        }
        // ألمانيا
        if (lat >= 47.0 && lat <= 55.0 && lng >= 5.0 && lng <= 16.0) {
            return "ألمانيا";
        }
        // إيطاليا
        if (lat >= 36.0 && lat <= 47.0 && lng >= 6.0 && lng <= 19.0) {
            return "إيطاليا";
        }
        // إسبانيا
        if (lat >= 36.0 && lat <= 44.0 && lng >= -10.0 && lng <= 4.0) {
            return "إسبانيا";
        }
        // تركيا
        if (lat >= 36.0 && lat <= 42.0 && lng >= 26.0 && lng <= 45.0) {
            return "تركيا";
        }
        // إيران
        if (lat >= 25.0 && lat <= 40.0 && lng >= 44.0 && lng <= 63.0) {
            return "إيران";
        }
        // الهند
        if (lat >= 8.0 && lat <= 37.0 && lng >= 68.0 && lng <= 97.0) {
            return "الهند";
        }
        // الصين
        if (lat >= 18.0 && lat <= 54.0 && lng >= 73.0 && lng <= 135.0) {
            return "الصين";
        }
        // اليابان
        if (lat >= 24.0 && lat <= 46.0 && lng >= 123.0 && lng <= 146.0) {
            return "اليابان";
        }
        // أستراليا
        if (lat >= -44.0 && lat <= -10.0 && lng >= 113.0 && lng <= 154.0) {
            return "أستراليا";
        }
        // كندا
        if (lat >= 42.0 && lat <= 83.0 && lng >= -141.0 && lng <= -52.0) {
            return "كندا";
        }
        // البرازيل
        if (lat >= -34.0 && lat <= 5.0 && lng >= -74.0 && lng <= -32.0) {
            return "البرازيل";
        }
        // روسيا
        if (lat >= 41.0 && lat <= 82.0 && lng >= 19.0 && lng <= -169.0) {
            return "روسيا";
        }

        return null; // دولة غير معروفة
    }

    // فحص إذا كان الموقع قريب من القاهرة
    private boolean isNearCairo(double lat, double lng) {
        double cairoLat = 30.0444;
        double cairoLng = 31.2357;
        double distance = calculateDistance(lat, lng, cairoLat, cairoLng);
        return distance < 50; // أقل من 50 كم من القاهرة
    }

    // فحص إذا كان الموقع في مصر
    private boolean isInEgypt(double lat, double lng) {
        return lat >= 22.0 && lat <= 31.7 && lng >= 25.0 && lng <= 37.0;
    }

    // تحديد الدولة العربية
    private String getArabCountry(double lat, double lng) {
        // السعودية
        if (lat >= 16.0 && lat <= 32.0 && lng >= 34.0 && lng <= 56.0) {
            return "السعودية";
        }
        // الإمارات
        if (lat >= 22.0 && lat <= 26.5 && lng >= 51.0 && lng <= 57.0) {
            return "الإمارات";
        }
        // الكويت
        if (lat >= 28.5 && lat <= 30.1 && lng >= 46.5 && lng <= 48.5) {
            return "الكويت";
        }
        // قطر
        if (lat >= 24.4 && lat <= 26.2 && lng >= 50.7 && lng <= 51.7) {
            return "قطر";
        }
        // البحرين
        if (lat >= 25.5 && lat <= 26.7 && lng >= 50.3 && lng <= 50.8) {
            return "البحرين";
        }
        // الأردن
        if (lat >= 29.0 && lat <= 33.5 && lng >= 34.8 && lng <= 39.5) {
            return "الأردن";
        }
        // لبنان
        if (lat >= 33.0 && lat <= 34.7 && lng >= 35.0 && lng <= 36.7) {
            return "لبنان";
        }
        // سوريا
        if (lat >= 32.3 && lat <= 37.3 && lng >= 35.7 && lng <= 42.4) {
            return "سوريا";
        }
        // العراق
        if (lat >= 29.0 && lat <= 37.4 && lng >= 38.8 && lng <= 48.8) {
            return "العراق";
        }
        // المغرب
        if (lat >= 21.0 && lat <= 36.0 && lng >= -17.0 && lng <= -1.0) {
            return "المغرب";
        }
        // الجزائر
        if (lat >= 19.0 && lat <= 37.1 && lng >= -8.7 && lng <= 12.0) {
            return "الجزائر";
        }
        // تونس
        if (lat >= 30.2 && lat <= 37.5 && lng >= 7.5 && lng <= 11.6) {
            return "تونس";
        }
        // ليبيا
        if (lat >= 20.0 && lat <= 33.2 && lng >= 9.3 && lng <= 25.2) {
            return "ليبيا";
        }
        // السودان
        if (lat >= 8.7 && lat <= 22.0 && lng >= 21.8 && lng <= 38.6) {
            return "السودان";
        }
        // عُمان
        if (lat >= 16.6 && lat <= 26.4 && lng >= 51.1 && lng <= 60.0) {
            return "عُمان";
        }
        // اليمن
        if (lat >= 12.1 && lat <= 19.0 && lng >= 42.3 && lng <= 54.5) {
            return "اليمن";
        }

        return null; // ليس في دولة عربية معروفة
    }

    // حساب المسافة بين نقطتين
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371; // نصف قطر الأرض بالكيلومتر

        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            // إعادة فحص الاتصال والموقع عند العودة للتطبيق
            checkConnectivityAndLocation();

            // إعادة محاولة الحصول على الموقع إذا كان لا يزال افتراضياً
            if (currentLatitude == 30.0444 && currentLongitude == 31.2357) {
                new Handler().postDelayed(() -> {
                    if (locationHelper.isLocationEnabled()) {
                        getUserLocation();
                    }
                }, 1000); // انتظار ثانية واحدة
            }

            // إعادة تشغيل مؤقت الوقت إذا توقف
            if (timeHandler != null && timeRunnable != null) {
                timeHandler.removeCallbacks(timeRunnable);
                timeHandler.post(timeRunnable);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            // إيقاف مؤقت الوقت لتوفير البطارية
            if (timeHandler != null && timeRunnable != null) {
                timeHandler.removeCallbacks(timeRunnable);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            // إلغاء تسجيل مستمع الشبكة
            if (connectivityReceiver != null) {
                unregisterReceiver(connectivityReceiver);
            }

            // تنظيف الموارد
            if (timeHandler != null && timeRunnable != null) {
                timeHandler.removeCallbacks(timeRunnable);
            }

            // إغلاق أي حوارات مفتوحة
            if (connectivityDialog != null && connectivityDialog.isShowing()) {
                connectivityDialog.dismiss();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}