package com.ebaa.prayermate;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.Animation;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class QiblaActivity extends AppCompatActivity implements SensorEventListener {

    private ImageView compassImage, qiblaArrow;
    private TextView tvDirection, tvDegree;

    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer, rotationVectorSensor;

    private float[] gravity, geomagnetic;
    private float azimuth = 0f;
    private float currentAzimuth = 0f;

    // نظام القراءات المحسن
    private static class CompassReading {
        float azimuth;
        long timestamp;
        float accuracy;

        CompassReading(float azimuth, long timestamp, float accuracy) {
            this.azimuth = azimuth;
            this.timestamp = timestamp;
            this.accuracy = Math.max(0.1f, accuracy);
        }
    }

    private List<CompassReading> readings = new ArrayList<>();
    private static final int MAX_READINGS = 15;
    private static final float MIN_CHANGE = 3.0f;

    // Kalman Filter مبسط
    private static class SimpleKalmanFilter {
        private float estimate = 0;
        private float errorCovariance = 1;
        private final float processNoise = 0.008f;
        private final float measurementNoise = 0.1f;
        private boolean initialized = false;

        public float update(float measurement) {
            if (!initialized) {
                estimate = measurement;
                initialized = true;
                return estimate;
            }

            // Prediction
            errorCovariance += processNoise;

            // Handle circular nature of angles
            float innovation = measurement - estimate;
            if (innovation > 180) innovation -= 360;
            if (innovation < -180) innovation += 360;

            // Update
            float kalmanGain = errorCovariance / (errorCovariance + measurementNoise);
            estimate = estimate + kalmanGain * innovation;
            estimate = (estimate + 360) % 360;
            errorCovariance = (1 - kalmanGain) * errorCovariance;

            return estimate;
        }
    }

    private SimpleKalmanFilter kalmanFilter = new SimpleKalmanFilter();

    private double userLatitude, userLongitude;
    private double qiblaDirection = 0;
    private double qiblaDistance = 0;

    // إحداثيات الكعبة المشرفة (دقيقة)
    private static final double KAABA_LATITUDE = 21.4224779;
    private static final double KAABA_LONGITUDE = 39.8251832;

    private int sensorAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;

    // متغيرات للتحكم في التحديث
    private long lastUpdateTime = 0;
    private long lastAnimationTime = 0;
    private static final int UPDATE_INTERVAL = 200;
    private static final int ANIMATION_COOLDOWN = 600;

    // متغيرات لتتبع اتجاه القبلة
    private float qiblaAngleDifference = 0f;
    private boolean isPointingToQibla = false;
    private static final float QIBLA_ACCURACY_RANGE = 8.0f;

    // متغيرات المعايرة
    private boolean isCalibrating = false;
    private long calibrationStartTime = 0;
    private List<Float> calibrationReadings = new ArrayList<>();
    private float calibrationOffset = 0f;

    // نوع المستشعر المستخدم
    private static final int SENSOR_TYPE_ROTATION = 1;
    private static final int SENSOR_TYPE_MAGNETIC = 2;
    private int activeSensorType = 0;

    // تصحيح الانحراف المغناطيسي
    private float magneticDeclination = 0f;

    // مراقبة جودة الإشارة
    private float signalVariance = 0f;
    private long lastQualityCheck = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qibla);

        try {
            initViews();
            setupToolbar();
            loadCalibrationData();
            setupSensors();
            getLocationData();
            calculateQiblaDirectionPrecise();
            calculateMagneticDeclination();
            showInstructions();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "خطأ في تهيئة البوصلة", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initViews() {
        compassImage = findViewById(R.id.compassImage);
        qiblaArrow = findViewById(R.id.qiblaArrow);
        tvDirection = findViewById(R.id.tvDirection);
        tvDegree = findViewById(R.id.tvDegree);
    }

    private void setupToolbar() {
        try {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("بوصلة القبلة المحسنة");
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadCalibrationData() {
        SharedPreferences prefs = getSharedPreferences("compass", MODE_PRIVATE);
        calibrationOffset = prefs.getFloat("calibration_offset", 0f);
    }

    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

            // تحديد نوع المستشعر المفضل
            if (rotationVectorSensor != null) {
                activeSensorType = SENSOR_TYPE_ROTATION;
            } else if (magnetometer != null && accelerometer != null) {
                activeSensorType = SENSOR_TYPE_MAGNETIC;
            } else {
                showStaticCompass();
            }
        }
    }

    private void showInstructions() {
        String instructions = "📱 امسك الهاتف بشكل أفقي\n" +
                "🧭 اتبع السهم الأحمر للقبلة\n" +
                "⚙️ اضغط مطولاً للمعايرة";

        Toast.makeText(this, instructions, Toast.LENGTH_LONG).show();

        // إضافة إمكانية المعايرة
        if (compassImage != null) {
            compassImage.setOnLongClickListener(v -> {
                startCalibration();
                return true;
            });
        }
    }

    private void getLocationData() {
        userLatitude = getIntent().getDoubleExtra("latitude", 30.0444);
        userLongitude = getIntent().getDoubleExtra("longitude", 31.2357);
    }

    // حساب اتجاه القبلة بدقة عالية
    private void calculateQiblaDirectionPrecise() {
        try {
            double lat1 = Math.toRadians(userLatitude);
            double lon1 = Math.toRadians(userLongitude);
            double lat2 = Math.toRadians(KAABA_LATITUDE);
            double lon2 = Math.toRadians(KAABA_LONGITUDE);

            double deltaLon = lon2 - lon1;

            // استخدام Great Circle calculation للدقة العالية
            double y = Math.sin(deltaLon) * Math.cos(lat2);
            double x = Math.cos(lat1) * Math.sin(lat2) -
                    Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon);

            qiblaDirection = Math.toDegrees(Math.atan2(y, x));
            qiblaDirection = (qiblaDirection + 360) % 360;

            calculateQiblaDistance();
        } catch (Exception e) {
            e.printStackTrace();
            qiblaDirection = 0;
        }
    }

    private void calculateQiblaDistance() {
        try {
            // استخدام Haversine formula
            double R = 6371.0; // نصف قطر الأرض بالكيلومتر
            double lat1 = Math.toRadians(userLatitude);
            double lon1 = Math.toRadians(userLongitude);
            double lat2 = Math.toRadians(KAABA_LATITUDE);
            double lon2 = Math.toRadians(KAABA_LONGITUDE);

            double deltaLat = lat2 - lat1;
            double deltaLon = lon2 - lon1;

            double a = Math.sin(deltaLat/2) * Math.sin(deltaLat/2) +
                    Math.cos(lat1) * Math.cos(lat2) *
                            Math.sin(deltaLon/2) * Math.sin(deltaLon/2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

            qiblaDistance = R * c;
        } catch (Exception e) {
            e.printStackTrace();
            qiblaDistance = 0;
        }
    }

    // حساب الانحراف المغناطيسي
    private void calculateMagneticDeclination() {
        try {
            if (userLatitude != 0 && userLongitude != 0) {
                GeomagneticField geoField = new GeomagneticField(
                        (float) userLatitude,
                        (float) userLongitude,
                        0f, // altitude
                        System.currentTimeMillis()
                );

                magneticDeclination = geoField.getDeclination();
            }
        } catch (Exception e) {
            e.printStackTrace();
            magneticDeclination = 0f;
        }
    }

    private void showStaticCompass() {
        try {
            Toast.makeText(this, "البوصلة المغناطيسية غير متوفرة\nسيتم عرض اتجاه القبلة الثابت", Toast.LENGTH_LONG).show();

            if (qiblaArrow != null) {
                RotateAnimation qiblaAnimation = new RotateAnimation(
                        0, (float) qiblaDirection,
                        Animation.RELATIVE_TO_SELF, 0.5f,
                        Animation.RELATIVE_TO_SELF, 0.5f);
                qiblaAnimation.setDuration(1000);
                qiblaAnimation.setFillAfter(true);
                qiblaArrow.startAnimation(qiblaAnimation);
            }

            updateStaticDisplay();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateStaticDisplay() {
        try {
            String directionText = getDirectionFromDegrees(qiblaDirection);

            if (tvDirection != null) {
                tvDirection.setText("🕋 اتجاه القبلة المقدسة\n" +
                        "📍 الاتجاه: " + directionText + "\n" +
                        String.format("📐 الزاوية: %.1f°", qiblaDirection) + "\n" +
                        String.format("📏 المسافة: %.0f كم", qiblaDistance) + "\n\n" +
                        "⬆️ اتجه نحو السهم الأحمر");
            }

            if (tvDegree != null) {
                tvDegree.setText(String.format("القبلة: %.1f°", qiblaDirection));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (sensorManager != null && activeSensorType != 0) {
                if (activeSensorType == SENSOR_TYPE_ROTATION) {
                    sensorManager.registerListener(this, rotationVectorSensor,
                            SensorManager.SENSOR_DELAY_GAME);
                } else if (activeSensorType == SENSOR_TYPE_MAGNETIC) {
                    sensorManager.registerListener(this, accelerometer,
                            SensorManager.SENSOR_DELAY_GAME);
                    sensorManager.registerListener(this, magnetometer,
                            SensorManager.SENSOR_DELAY_GAME);
                }
            } else {
                showStaticCompass();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (sensorManager != null) {
                sensorManager.unregisterListener(this);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        try {
            if (activeSensorType == 0) return;

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdateTime < UPDATE_INTERVAL) {
                return;
            }
            lastUpdateTime = currentTime;

            float newAzimuth = 0f;
            float accuracy = getAccuracyFromSensor(event);

            if (activeSensorType == SENSOR_TYPE_ROTATION &&
                    event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                newAzimuth = getAzimuthFromRotationVector(event.values);
            } else if (activeSensorType == SENSOR_TYPE_MAGNETIC) {
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    gravity = lowPassFilter(event.values.clone(), gravity, false);
                } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                    geomagnetic = lowPassFilter(event.values.clone(), geomagnetic, false);
                }

                if (gravity != null && geomagnetic != null) {
                    newAzimuth = getAzimuthFromMagneticField();
                }
            }

            if (newAzimuth != 0f) {
                processNewAzimuth(newAzimuth, accuracy, currentTime);
            }

            // فحص جودة الإشارة كل 3 ثوان
            if (currentTime - lastQualityCheck > 3000) {
                checkSignalQuality();
                lastQualityCheck = currentTime;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private float getAccuracyFromSensor(SensorEvent event) {
        // تحويل دقة المستشعر إلى وزن
        switch (sensorAccuracy) {
            case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
                return 1.0f;
            case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
                return 0.7f;
            case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
                return 0.4f;
            default:
                return 0.2f;
        }
    }

    private float getAzimuthFromRotationVector(float[] rotationVector) {
        try {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector);

            float[] orientationValues = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientationValues);

            float azimuth = (float) Math.toDegrees(orientationValues[0]);
            return (azimuth + 360) % 360;
        } catch (Exception e) {
            return azimuth;
        }
    }

    private float getAzimuthFromMagneticField() {
        try {
            float[] rotationMatrix = new float[9];
            boolean success = SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic);

            if (success) {
                float[] orientationValues = new float[3];
                SensorManager.getOrientation(rotationMatrix, orientationValues);
                float azimuth = (float) Math.toDegrees(orientationValues[0]);

                // تطبيق تصحيح الانحراف المغناطيسي
                azimuth = (azimuth + magneticDeclination + 360) % 360;

                return azimuth;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return azimuth;
    }

    private void processNewAzimuth(float newAzimuth, float accuracy, long timestamp) {
        // تطبيق تصحيح المعايرة
        newAzimuth = (newAzimuth + calibrationOffset + 360) % 360;

        // إضافة القراءة الجديدة
        readings.add(new CompassReading(newAzimuth, timestamp, accuracy));

        // الاحتفاظ بأحدث القراءات
        if (readings.size() > MAX_READINGS) {
            readings.remove(0);
        }

        if (readings.size() < 3) return;

        // حساب المتوسط المرجح للزوايا الدائرية
        float weightedAverage = calculateWeightedCircularAverage();

        // تطبيق Kalman Filter
        float filteredAzimuth = kalmanFilter.update(weightedAverage);

        // تحديث فقط إذا كان التغيير كبير أو مضى وقت كافي
        float change = calculateAngleDifference(filteredAzimuth, azimuth);
        long timeSinceLastAnimation = timestamp - lastAnimationTime;

        if (change >= MIN_CHANGE || timeSinceLastAnimation > ANIMATION_COOLDOWN) {
            azimuth = filteredAzimuth;
            updateCompass(timestamp);
        } else {
            // تحديث النصوص بدون أنيميشن
            updateDisplayTexts();
        }

        // معالجة المعايرة إذا كانت نشطة
        if (isCalibrating) {
            processCalibration(filteredAzimuth);
        }
    }

    // حساب المتوسط المرجح للزوايا الدائرية
    private float calculateWeightedCircularAverage() {
        if (readings.isEmpty()) return azimuth;

        double sumSin = 0, sumCos = 0, totalWeight = 0;
        long currentTime = System.currentTimeMillis();

        for (CompassReading reading : readings) {
            // وزن أكبر للقراءات الأحدث والأدق
            float ageWeight = Math.max(0.1f, 1.0f - (currentTime - reading.timestamp) / 5000f);
            float weight = ageWeight * reading.accuracy;

            sumSin += Math.sin(Math.toRadians(reading.azimuth)) * weight;
            sumCos += Math.cos(Math.toRadians(reading.azimuth)) * weight;
            totalWeight += weight;
        }

        if (totalWeight > 0) {
            double avgAngle = Math.toDegrees(Math.atan2(sumSin / totalWeight,
                    sumCos / totalWeight));
            return (float)((avgAngle + 360) % 360);
        }

        return azimuth;
    }

    private float calculateAngleDifference(float angle1, float angle2) {
        float diff = Math.abs(angle1 - angle2);
        return Math.min(diff, 360 - diff);
    }

    private float[] lowPassFilter(float[] input, float[] output, boolean isStatic) {
        if (output == null) return input;

        float alpha = isStatic ? 0.1f : 0.15f;
        for (int i = 0; i < input.length; i++) {
            output[i] = output[i] + alpha * (input[i] - output[i]);
        }
        return output;
    }

    private void updateCompass(long currentTime) {
        try {
            lastAnimationTime = currentTime;

            // حساب الفرق بين الاتجاه الحالي واتجاه القبلة
            qiblaAngleDifference = (float) (qiblaDirection - azimuth);
            if (qiblaAngleDifference > 180) {
                qiblaAngleDifference -= 360;
            } else if (qiblaAngleDifference < -180) {
                qiblaAngleDifference += 360;
            }

            // تحديد ما إذا كنا نتجه نحو القبلة
            boolean wasPointingToQibla = isPointingToQibla;
            isPointingToQibla = Math.abs(qiblaAngleDifference) <= QIBLA_ACCURACY_RANGE;

            // تحريك البوصلة بسلاسة
            RotateAnimation compassAnimation = new RotateAnimation(
                    currentAzimuth, -azimuth,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);

            compassAnimation.setDuration(400);
            compassAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
            compassAnimation.setFillAfter(true);

            if (compassImage != null) {
                compassImage.startAnimation(compassAnimation);
            }

            // تحريك سهم القبلة
            float qiblaRotation = (float) qiblaDirection - azimuth;
            RotateAnimation qiblaAnimation = new RotateAnimation(
                    (float)(qiblaDirection - currentAzimuth), qiblaRotation,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);

            qiblaAnimation.setDuration(400);
            qiblaAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
            qiblaAnimation.setFillAfter(true);

            if (qiblaArrow != null) {
                qiblaArrow.startAnimation(qiblaAnimation);
            }

            currentAzimuth = -azimuth;

            // تحديث النصوص
            updateDisplayTexts();

            // عرض رسالة عند الوصول للقبلة
            if (isPointingToQibla && !wasPointingToQibla) {
                Toast.makeText(this, "🕋 ✅ أنت متجه نحو القبلة!", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateDisplayTexts() {
        try {
            String currentDirection = getDirectionFromDegrees(azimuth);
            String qiblaDirectionText = getDirectionFromDegrees(qiblaDirection);

            if (tvDirection != null) {
                String text = "";
                String accuracyIndicator = getAccuracyIndicator();

                if (isPointingToQibla) {
                    text = "🕋 ✅ أنت متجه نحو القبلة المقدسة!\n\n" +
                            "📍 الكعبة المشرفة في هذا الاتجاه\n" +
                            String.format("📏 المسافة: %.0f كم", qiblaDistance) + "\n" +
                            accuracyIndicator + "\n" +
                            "🤲 يمكنك الآن أداء الصلاة";
                } else {
                    text = "🧭 اتجاهك الحالي: " + currentDirection + "\n" +
                            String.format("📍 اتجاه القبلة: %s (%.1f°)", qiblaDirectionText, qiblaDirection) + "\n" +
                            String.format("📏 المسافة إلى مكة: %.0f كم", qiblaDistance) + "\n" +
                            accuracyIndicator + "\n\n" +
                            getQiblaInstruction();
                }

                tvDirection.setText(text);
            }

            if (tvDegree != null) {
                String degreeText = String.format("%.1f°", Math.abs(azimuth));
                if (isPointingToQibla) {
                    degreeText = "🕋 ✅ " + degreeText;
                } else {
                    degreeText = "🧭 " + degreeText;
                }
                tvDegree.setText(degreeText);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getAccuracyIndicator() {
        if (signalVariance < 5.0f) {
            return "🟢 دقة عالية";
        } else if (signalVariance < 15.0f) {
            return "🟡 دقة متوسطة";
        } else {
            return "🔴 دقة منخفضة";
        }
    }

    private String getQiblaInstruction() {
        float absDifference = Math.abs(qiblaAngleDifference);

        if (absDifference <= 15) {
            return "📍 قريب جداً! اتجه " + (qiblaAngleDifference > 0 ? "يميناً قليلاً ➡️" : "يساراً قليلاً ⬅️");
        } else if (absDifference <= 45) {
            return String.format("🔄 دور %s حوالي %.0f درجة",
                    (qiblaAngleDifference > 0 ? "يميناً" : "يساراً"), absDifference);
        } else if (absDifference <= 90) {
            return "🔄 دور " + (qiblaAngleDifference > 0 ? "يميناً" : "يساراً") + " كثيراً";
        } else {
            return "🔄 دور حوالي نصف دورة - القبلة خلفك";
        }
    }

    private String getDirectionFromDegrees(double degrees) {
        String[] directions = {"شمال", "شمال شرق", "شرق", "جنوب شرق",
                "جنوب", "جنوب غرب", "غرب", "شمال غرب"};
        int index = (int) Math.round(((degrees % 360) / 45.0)) % 8;
        return directions[index];
    }

    // نظام المعايرة
    private void startCalibration() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("معايرة البوصلة")
                .setMessage("حرك الهاتف في شكل 8 لمدة 15 ثانية\nسيساعد هذا في تحسين الدقة")
                .setPositiveButton("ابدأ المعايرة", (dialog, which) -> {
                    isCalibrating = true;
                    calibrationStartTime = System.currentTimeMillis();
                    calibrationReadings.clear();
                    Toast.makeText(this, "🔄 المعايرة بدأت... حرك الهاتف في شكل 8", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void processCalibration(float azimuth) {
        if (!isCalibrating) return;

        calibrationReadings.add(azimuth);

        long elapsed = System.currentTimeMillis() - calibrationStartTime;
        if (elapsed > 15000) { // 15 ثانية
            finishCalibration();
        } else {
            // عرض التقدم
            int progress = (int) (elapsed / 150); // نسبة مئوية
            if (progress % 20 == 0) { // كل 3 ثوان
                Toast.makeText(this, "🔄 معايرة... " + (progress / 10) + "%", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void finishCalibration() {
        isCalibrating = false;

        if (calibrationReadings.size() > 10) {
            // حساب offset من بيانات المعايرة
            float newOffset = calculateCalibrationOffset();
            calibrationOffset = newOffset;

            // حفظ المعايرة
            SharedPreferences.Editor editor = getSharedPreferences("compass", MODE_PRIVATE).edit();
            editor.putFloat("calibration_offset", calibrationOffset);
            editor.apply();

            Toast.makeText(this, "✅ تمت المعايرة بنجاح! الدقة محسنة", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "❌ فشلت المعايرة - لم يتم جمع بيانات كافية", Toast.LENGTH_SHORT).show();
        }

        calibrationReadings.clear();
    }

    private float calculateCalibrationOffset() {
        if (calibrationReadings.isEmpty()) return 0f;

        // حساب المتوسط الدائري للمعايرة
        double sumSin = 0, sumCos = 0;
        for (float reading : calibrationReadings) {
            sumSin += Math.sin(Math.toRadians(reading));
            sumCos += Math.cos(Math.toRadians(reading));
        }

        double avgAngle = Math.toDegrees(Math.atan2(sumSin / calibrationReadings.size(),
                sumCos / calibrationReadings.size()));

        // حساب الانحراف المطلوب للتصحيح
        float targetDirection = 0f; // الشمال المغناطيسي
        float offset = (float) (targetDirection - avgAngle);
        return (offset + 360) % 360;
    }

    // مراقبة جودة الإشارة
    private void checkSignalQuality() {
        if (readings.size() < 5) return;

        // حساب التباين في القراءات
        float sum = 0, sumSquares = 0;
        for (CompassReading reading : readings) {
            sum += reading.azimuth;
            sumSquares += reading.azimuth * reading.azimuth;
        }

        float mean = sum / readings.size();
        signalVariance = (sumSquares / readings.size()) - (mean * mean);

        // تحذيرات جودة الإشارة
        if (sensorAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            showQualityWarning("⚠️ دقة منخفضة - ابتعد عن المعادن والمغناطيس");
        } else if (signalVariance > 20.0f) {
            showQualityWarning("⚠️ إشارة غير مستقرة - امسك الهاتف بثبات");
        } else if (signalVariance > 35.0f) {
            showQualityWarning("🔴 تداخل مغناطيسي قوي - غير موقعك");
        }
    }

    private void showQualityWarning(String message) {
        // عرض التحذير مرة واحدة كل 10 ثوان
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastQualityCheck > 10000) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    // حساب التباين للقراءات (للزوايا الدائرية)
    private float calculateVariance() {
        if (readings.size() < 3) return 0f;

        // تحويل الزوايا إلى متجهات وحساب التباين
        double sumSin = 0, sumCos = 0;
        for (CompassReading reading : readings) {
            sumSin += Math.sin(Math.toRadians(reading.azimuth));
            sumCos += Math.cos(Math.toRadians(reading.azimuth));
        }

        double meanSin = sumSin / readings.size();
        double meanCos = sumCos / readings.size();
        double R = Math.sqrt(meanSin * meanSin + meanCos * meanCos);

        // تحويل إلى درجات التباين
        return (float) (Math.toDegrees(Math.acos(R)));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        sensorAccuracy = accuracy;

        String accuracyMessage = "";
        switch (accuracy) {
            case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
                accuracyMessage = "🟢 دقة عالية";
                break;
            case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
                accuracyMessage = "🟡 دقة متوسطة";
                break;
            case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
                accuracyMessage = "🟠 دقة منخفضة";
                break;
            case SensorManager.SENSOR_STATUS_UNRELIABLE:
                accuracyMessage = "🔴 غير موثوق - ابتعد عن المعادن";
                break;
        }

        // عرض تحديث الدقة للمستخدم
        if (!accuracyMessage.isEmpty()) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {

            }, 100);
        }
    }

    // تحسينات إضافية لواجهة المستخدم
    private void updateAccuracyIndicator() {
        try {
            if (tvDirection != null) {
                String accuracyInfo = "\n" + getDetailedAccuracyInfo();
                String currentText = tvDirection.getText().toString();

                // إضافة معلومات الدقة إذا لم تكن موجودة
                if (!currentText.contains("الدقة:")) {
                    tvDirection.setText(currentText + accuracyInfo);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getDetailedAccuracyInfo() {
        StringBuilder info = new StringBuilder();

        info.append("📊 الدقة: ").append(getAccuracyIndicator()).append("\n");

        if (magneticDeclination != 0) {
            info.append(String.format("🧭 تصحيح مغناطيسي: %.1f°", magneticDeclination)).append("\n");
        }

        if (calibrationOffset != 0) {
            info.append("⚙️ معايرة مطبقة").append("\n");
        }

        info.append(String.format("🔄 قراءات: %d/%d", readings.size(), MAX_READINGS));

        return info.toString();
    }

    // دوال مساعدة إضافية
    private void resetReadings() {
        readings.clear();
        kalmanFilter = new SimpleKalmanFilter();
    }

    private void showCompassInfo() {
        String sensorInfo = "نوع المستشعر: ";
        if (activeSensorType == SENSOR_TYPE_ROTATION) {
            sensorInfo += "مستشعر الدوران (دقة عالية)";
        } else if (activeSensorType == SENSOR_TYPE_MAGNETIC) {
            sensorInfo += "مستشعر مغناطيسي + مقياس التسارع";
        } else {
            sensorInfo += "وضع ثابت";
        }

        Toast.makeText(this, sensorInfo, Toast.LENGTH_SHORT).show();
    }

    // إضافة قائمة خيارات للمستخدم
    private void showOptionsMenu() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String[] options = {
                "🔄 إعادة تعيين القراءات",
                "⚙️ معايرة البوصلة",
                "ℹ️ معلومات المستشعر",
                "📊 إحصائيات الدقة"
        };

        builder.setTitle("خيارات البوصلة")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            resetReadings();
                            Toast.makeText(this, "تم إعادة تعيين القراءات", Toast.LENGTH_SHORT).show();
                            break;
                        case 1:
                            startCalibration();
                            break;
                        case 2:
                            showCompassInfo();
                            break;
                        case 3:
                            showAccuracyStats();
                            break;
                    }
                })
                .show();
    }

    private void showAccuracyStats() {
        String stats = String.format(
                "📊 إحصائيات الدقة:\n\n" +
                        "🎯 التباين: %.1f°\n" +
                        "📊 عدد القراءات: %d\n" +
                        "⚡ معدل التحديث: %dms\n" +
                        "🧭 انحراف مغناطيسي: %.1f°\n" +
                        "⚙️ تصحيح المعايرة: %.1f°",
                signalVariance,
                readings.size(),
                UPDATE_INTERVAL,
                magneticDeclination,
                calibrationOffset
        );

        new AlertDialog.Builder(this)
                .setTitle("إحصائيات الدقة")
                .setMessage(stats)
                .setPositiveButton("موافق", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    // إضافة إمكانية عرض قائمة الخيارات عند الضغط على الشاشة
    private void setupScreenTouchListeners() {
        if (tvDirection != null) {
            tvDirection.setOnClickListener(v -> showOptionsMenu());
        }
    }

    // تحسين دورة الحياة
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (sensorManager != null) {
                sensorManager.unregisterListener(this);
            }
            readings.clear();
            calibrationReadings.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // حفظ الحالة الحالية
        if (readings.size() > 0) {
            SharedPreferences.Editor editor = getSharedPreferences("compass", MODE_PRIVATE).edit();
            editor.putFloat("last_azimuth", azimuth);
            editor.putLong("last_update", System.currentTimeMillis());
            editor.apply();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // استرجاع الحالة المحفوظة
        SharedPreferences prefs = getSharedPreferences("compass", MODE_PRIVATE);
        float lastAzimuth = prefs.getFloat("last_azimuth", 0f);
        long lastUpdate = prefs.getLong("last_update", 0);

        // إذا كان آخر تحديث قريب، استخدم القيمة المحفوظة
        if (System.currentTimeMillis() - lastUpdate < 30000) { // 30 ثانية
            azimuth = lastAzimuth;
            currentAzimuth = -lastAzimuth;
        }
    }
}