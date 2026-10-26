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
    public static final String KEY_MEASUREMENT_COUNT = "measurement_count"; // устаревший, не используется
    public static final String KEY_DIFF_THRESHOLD = "diff_threshold";
    public static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    public static final String KEY_MEASUREMENT_MODE = "measurement_mode";
    // НОВЫЙ КЛЮЧ: режим коррекции (заменяет correction_per_row)
    public static final String KEY_CORRECTION_MODE = "correction_mode";
    // НОВЫЙ КЛЮЧ: методика замера (заменяет measurement_count)
    public static final String KEY_MEASUREMENT_METHOD = "measurement_method";
    // НОВЫЙ КЛЮЧ: базовый URL каталога
    public static final String KEY_CATALOG_BASE_URL = "catalog_base_url";

    // Значения по умолчанию
    public static final double DEFAULT_WEIGHT_CF = -0.012;
    public static final int DEFAULT_DISTANCE_ADD = 0;
    public static final double DEFAULT_WEIGHT_LIMIT = 100.0;
    public static final int DEFAULT_MEASUREMENT_COUNT = 1; // устаревшее, не используется
    public static final int DEFAULT_DIFF_THRESHOLD = 20;
    public static final boolean DEFAULT_KEEP_SCREEN_ON = true;
    public static final String DEFAULT_MEASUREMENT_MODE = "single";
    // НОВОЕ ЗНАЧЕНИЕ ПО УМОЛЧАНИЮ
    public static final String DEFAULT_CORRECTION_MODE = "global";
    // НОВОЕ ЗНАЧЕНИЕ ПО УМОЛЧАНИЮ ДЛЯ МЕТОДИКИ ЗАМЕРА
    public static final String DEFAULT_MEASUREMENT_METHOD = "method_1";
    // НОВОЕ ЗНАЧЕНИЕ ПО УМОЛЧАНИЮ ДЛЯ БАЗОВОГО URL КАТАЛОГА
    public static final String DEFAULT_CATALOG_BASE_URL = "parasell.vmaya.ru";

    // Значения режимов коррекции
    public static final String CORRECTION_MODE_GLOBAL = "global";
    public static final String CORRECTION_MODE_PER_ROW = "per_row";
    public static final String CORRECTION_MODE_ROW_PREFIX = "row_";

    // Значения методик замера
    public static final String MEASUREMENT_METHOD_1 = "method_1"; // 1 замер
    public static final String MEASUREMENT_METHOD_2 = "method_2"; // 2 замера, среднее
    public static final String MEASUREMENT_METHOD_3 = "method_3"; // 3 замера, среднее
    public static final String MEASUREMENT_METHOD_4 = "method_4"; // 2 замера, 0.1 / 0.9
    public static final String MEASUREMENT_METHOD_5 = "method_5"; // 3 замера, квадрат
    public static final String MEASUREMENT_METHOD_6 = "method_6"; // 5 замеров, квадрат

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

    /**
     * Устаревший метод. Оставлен для совместимости, не используется в новой логике.
     * Возвращает количество замеров, соответствующее текущей методике.
     */
    public int getMeasurementCount() {
        return getMeasurementCountForMethod();
    }

    /**
     * Устаревший метод. Оставлен для совместимости, не используется в новой логике.
     * Устанавливает методику, ближайшую к заданному количеству замеров.
     */
    public void setMeasurementCount(int measurementCount) {
        if (measurementCount <= 1) {
            setMeasurementMethod(MEASUREMENT_METHOD_1);
        } else if (measurementCount == 2) {
            setMeasurementMethod(MEASUREMENT_METHOD_2);
        } else {
            setMeasurementMethod(MEASUREMENT_METHOD_3);
        }
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

    public boolean isKeepScreenOn() {
        return sharedPreferences.getBoolean(KEY_KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN_ON);
    }

    public void setKeepScreenOn(boolean keepScreenOn) {
        sharedPreferences.edit().putBoolean(KEY_KEEP_SCREEN_ON, keepScreenOn).apply();
    }

    public String getMeasurementMode() {
        return sharedPreferences.getString(KEY_MEASUREMENT_MODE, DEFAULT_MEASUREMENT_MODE);
    }

    public void setMeasurementMode(String mode) {
        sharedPreferences.edit().putString(KEY_MEASUREMENT_MODE, mode).apply();
    }

    public boolean isDualMode() {
        return "dual".equals(getMeasurementMode());
    }

    // ==================== МЕТОДИКА ЗАМЕРА ====================

    /**
     * Возвращает текущую методику замера.
     * Возможные значения: "method_1" ... "method_6"
     */
    public String getMeasurementMethod() {
        String value = sharedPreferences.getString(KEY_MEASUREMENT_METHOD, DEFAULT_MEASUREMENT_METHOD);
        if (value == null || value.isEmpty()) {
            return DEFAULT_MEASUREMENT_METHOD;
        }
        return value;
    }

    public void setMeasurementMethod(String method) {
        if (method == null || method.isEmpty()) {
            method = DEFAULT_MEASUREMENT_METHOD;
        }
        sharedPreferences.edit().putString(KEY_MEASUREMENT_METHOD, method).apply();
    }

    /**
     * Возвращает количество натяжений (замеров), необходимое для текущей методики.
     * method_1 -> 1
     * method_2 -> 2
     * method_3 -> 3
     * method_4 -> 2
     * method_5 -> 3
     * method_6 -> 5
     */
    public int getMeasurementCountForMethod() {
        String method = getMeasurementMethod();
        if (MEASUREMENT_METHOD_1.equals(method)) {
            return 1;
        } else if (MEASUREMENT_METHOD_2.equals(method)) {
            return 2;
        } else if (MEASUREMENT_METHOD_3.equals(method)) {
            return 3;
        } else if (MEASUREMENT_METHOD_4.equals(method)) {
            return 2;
        } else if (MEASUREMENT_METHOD_5.equals(method)) {
            return 3;
        } else if (MEASUREMENT_METHOD_6.equals(method)) {
            return 5;
        }
        return 1;
    }

    /**
     * Возвращает массив весов для текущей методики.
     * Сумма весов всегда равна 1.0.
     *
     * method_1 -> [1.0]
     * method_2 -> [0.5, 0.5]
     * method_3 -> [1/3, 1/3, 1/3]
     * method_4 -> [0.1, 0.9]
     * method_5 -> квадратичное распределение (i+1)^2 / sum, N=3
     * method_6 -> квадратичное распределение (i+1)^2 / sum, N=5
     */
    public double[] getWeightsForMethod() {
        String method = getMeasurementMethod();
        if (MEASUREMENT_METHOD_1.equals(method)) {
            return new double[]{1.0};
        } else if (MEASUREMENT_METHOD_2.equals(method)) {
            return new double[]{0.5, 0.5};
        } else if (MEASUREMENT_METHOD_3.equals(method)) {
            return new double[]{1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0};
        } else if (MEASUREMENT_METHOD_4.equals(method)) {
            return new double[]{0.1, 0.9};
        } else if (MEASUREMENT_METHOD_5.equals(method)) {
            return buildQuadraticWeights(3);
        } else if (MEASUREMENT_METHOD_6.equals(method)) {
            return buildQuadraticWeights(5);
        }
        return new double[]{1.0};
    }

    /**
     * Строит нормированные квадратичные веса: w_i = (i+1)^2 / sum_{k=1..N} k^2, i=0..N-1.
     * Сумма весов = 1.0, веса возрастают от первого к последнему.
     */
    private double[] buildQuadraticWeights(int n) {
        double[] weights = new double[n];
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            double w = (i + 1) * (i + 1);
            weights[i] = w;
            sum += w;
        }
        for (int i = 0; i < n; i++) {
            weights[i] = weights[i] / sum;
        }
        return weights;
    }

    // ==================== РЕЖИМ КОРРЕКЦИИ ====================

    /**
     * Возвращает текущий режим коррекции.
     * Возможные значения:
     * - "global" — общая коррекция
     * - "per_row" — коррекция по рядам
     * - "row_N" — коррекция по конкретному ряду (N — индекс колонки CSV)
     */
    public String getCorrectionMode() {
        String value = sharedPreferences.getString(KEY_CORRECTION_MODE, DEFAULT_CORRECTION_MODE);
        if (value == null || value.isEmpty()) {
            return DEFAULT_CORRECTION_MODE;
        }
        return value;
    }

    public void setCorrectionMode(String mode) {
        if (mode == null || mode.isEmpty()) {
            mode = DEFAULT_CORRECTION_MODE;
        }
        sharedPreferences.edit().putString(KEY_CORRECTION_MODE, mode).apply();
    }

    /**
     * Проверяет, что текущий режим — "Общая коррекция"
     */
    public boolean isCorrectionModeGlobal() {
        return CORRECTION_MODE_GLOBAL.equals(getCorrectionMode());
    }

    /**
     * Проверяет, что текущий режим — "По рядам"
     */
    public boolean isCorrectionModePerRow() {
        return CORRECTION_MODE_PER_ROW.equals(getCorrectionMode());
    }

    /**
     * Если текущий режим — "row_N", возвращает N (индекс колонки).
     * Иначе возвращает -1.
     */
    public int getCorrectionModeRowIndex() {
        String mode = getCorrectionMode();
        if (mode != null && mode.startsWith(CORRECTION_MODE_ROW_PREFIX)) {
            try {
                return Integer.parseInt(mode.substring(CORRECTION_MODE_ROW_PREFIX.length()));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Проверяет, что текущий режим — коррекция по конкретному ряду
     */
    public boolean isCorrectionModeSpecificRow() {
        return getCorrectionModeRowIndex() >= 0;
    }

    // ==================== БАЗОВЫЙ URL КАТАЛОГА ====================

    /**
     * Возвращает базовый URL каталога в нормализованном виде:
     * - если пусто — DEFAULT_CATALOG_BASE_URL;
     * - добавляет https:// если протокол не указан;
     * - убирает завершающие слэши.
     */
    public String getCatalogBaseUrl() {
        String value = sharedPreferences.getString(KEY_CATALOG_BASE_URL, DEFAULT_CATALOG_BASE_URL);
        if (value == null) {
            value = DEFAULT_CATALOG_BASE_URL;
        }
        value = value.trim();
        if (value.isEmpty()) {
            value = DEFAULT_CATALOG_BASE_URL;
        }
        String lower = value.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            value = "https://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public void setCatalogBaseUrl(String url) {
        if (url == null) {
            url = DEFAULT_CATALOG_BASE_URL;
        }
        sharedPreferences.edit().putString(KEY_CATALOG_BASE_URL, url.trim()).apply();
    }
}