// app/src/main/java/vmaya/para/measuringpss/AppSettings.java
package vmaya.para.measuringpss;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

public class AppSettings {
    // Константы для ключей настроек
    public static final String KEY_WEIGHT_CF = "weight_cf";
    public static final String KEY_DISTANCE_ADD = "distance_add";
    public static final String KEY_WEIGHT_LIMIT = "weight_limit";
    public static final String KEY_MEASUREMENT_COUNT = "measurement_count";
    public static final String KEY_DIFF_THRESHOLD = "diff_threshold";
    public static final String KEY_KEEP_SCREEN_ON = "keep_screen_on"; // Новый ключ

    // Значения по умолчанию
    public static final double DEFAULT_WEIGHT_CF = 1.0;
    public static final int DEFAULT_DISTANCE_ADD = 0;
    public static final double DEFAULT_WEIGHT_LIMIT = 100.0;
    public static final int DEFAULT_MEASUREMENT_COUNT = 1;
    public static final int DEFAULT_DIFF_THRESHOLD = 20;
    public static final boolean DEFAULT_KEEP_SCREEN_ON = true; // По умолчанию включено

    private final SharedPreferences sharedPreferences;

    public AppSettings(Context context) {
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public double getWeightCf() {
        String value = sharedPreferences.getString(KEY_WEIGHT_CF, String.valueOf(DEFAULT_WEIGHT_CF));
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return DEFAULT_WEIGHT_CF;
        }
    }

    public void setWeightCf(double weightCf) {
        sharedPreferences.edit().putString(KEY_WEIGHT_CF, String.valueOf(weightCf)).apply();
    }

    public int getDistanceAdd() {
        String value = sharedPreferences.getString(KEY_DISTANCE_ADD, String.valueOf(DEFAULT_DISTANCE_ADD));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return DEFAULT_DISTANCE_ADD;
        }
    }

    public void setDistanceAdd(int distanceAdd) {
        sharedPreferences.edit().putString(KEY_DISTANCE_ADD, String.valueOf(distanceAdd)).apply();
    }

    public double getWeightLimit() {
        String value = sharedPreferences.getString(KEY_WEIGHT_LIMIT, String.valueOf(DEFAULT_WEIGHT_LIMIT));
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return DEFAULT_WEIGHT_LIMIT;
        }
    }

    public void setWeightLimit(double weightLimit) {
        sharedPreferences.edit().putString(KEY_WEIGHT_LIMIT, String.valueOf(weightLimit)).apply();
    }

    public int getMeasurementCount() {
        String value = sharedPreferences.getString(KEY_MEASUREMENT_COUNT, String.valueOf(DEFAULT_MEASUREMENT_COUNT));
        try {
            int count = Integer.parseInt(value);
            return Math.max(1, Math.min(10, count));
        } catch (NumberFormatException e) {
            return DEFAULT_MEASUREMENT_COUNT;
        }
    }

    public void setMeasurementCount(int measurementCount) {
        int clampedValue = Math.max(1, Math.min(10, measurementCount));
        sharedPreferences.edit().putString(KEY_MEASUREMENT_COUNT, String.valueOf(clampedValue)).apply();
    }

    public int getDiffThreshold() {
        String value = sharedPreferences.getString(KEY_DIFF_THRESHOLD, String.valueOf(DEFAULT_DIFF_THRESHOLD));
        try {
            int threshold = Integer.parseInt(value);
            return Math.max(10, Math.min(200, threshold));
        } catch (NumberFormatException e) {
            return DEFAULT_DIFF_THRESHOLD;
        }
    }

    public void setDiffThreshold(int threshold) {
        int clampedValue = Math.max(10, Math.min(200, threshold));
        sharedPreferences.edit().putString(KEY_DIFF_THRESHOLD, String.valueOf(clampedValue)).apply();
    }

    // Новый метод для получения настройки Keep Screen On
    public boolean isKeepScreenOn() {
        return sharedPreferences.getBoolean(KEY_KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN_ON);
    }

    public void setKeepScreenOn(boolean keepScreenOn) {
        sharedPreferences.edit().putBoolean(KEY_KEEP_SCREEN_ON, keepScreenOn).apply();
    }
}