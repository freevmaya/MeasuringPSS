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

    // Значения по умолчанию
    public static final double DEFAULT_WEIGHT_CF = 1.0;
    public static final int DEFAULT_DISTANCE_ADD = 0;
    public static final double DEFAULT_WEIGHT_LIMIT = 100.0;
    public static final int DEFAULT_MEASUREMENT_COUNT = 1;

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
            // Ограничиваем значение от 1 до 10
            return Math.max(1, Math.min(10, count));
        } catch (NumberFormatException e) {
            return DEFAULT_MEASUREMENT_COUNT;
        }
    }

    public void setMeasurementCount(int measurementCount) {
        // Ограничиваем значение от 1 до 10
        int clampedValue = Math.max(1, Math.min(10, measurementCount));
        sharedPreferences.edit().putString(KEY_MEASUREMENT_COUNT, String.valueOf(clampedValue)).apply();
    }
}