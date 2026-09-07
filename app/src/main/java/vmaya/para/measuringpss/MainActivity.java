// app/src/main/java/vmaya/para/measuringpss/MainActivity.java
package vmaya.para.measuringpss;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // UUID из вашего кода ESP32
    private static final UUID SERVICE_UUID = UUID.fromString("4e292be3-71e6-4ca1-a06e-b1b3660df15c");
    private static final UUID NOTIFY_UUID = UUID.fromString("087dde8f-83e6-4f77-b9b0-7e966a7706a8");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt bluetoothGatt;
    private boolean isConnected = false;
    private boolean isScanning = false;

    private TextView tvStatus;
    private TextView tvWeightDistance;
    private TextView tvRawData;
    private TableLayout tableLimitData;
    private ScrollView limitDataScrollView;
    private Button btnScan;
    private Button btnSettings;
    private Button btnClear;

    private Gson gson = new Gson();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothDevice currentDevice;

    // Буфер для накопления данных
    private final StringBuilder jsonBuffer = new StringBuilder();

    // Список для хранения записей предельных данных
    private final List<DataSample> limitDataList = new ArrayList<>();

    // Для работы с CSV таблицей
    private int currentRowIndex = 0;
    private int currentColumnIndex = 0;
    private boolean isTableDataExhausted = false;

    // Очередь измерений для режима "Две консоли"
    private final List<MeasurementTarget> measurementQueue = new ArrayList<>();
    private int currentQueueIndex = 0;

    private static final int REQUEST_PERMISSIONS = 1;

    // Экземпляры классов
    private AppSettings appSettings;
    private AppState appState = new AppState();

    private PowerManager.WakeLock wakeLock;
    private PowerManager powerManager;

    private final List<Double> weightOverLimitBuffer = new ArrayList<>();
    private final List<Integer> distanceBuffer = new ArrayList<>();
    private double lastCsvValue = 0.0;
    private SoundManager soundManager;

    // Переменная для хранения динамически вычисленной коррекции расстояния
    private int dynamicDistanceAdd = 0;

    // Внутренний класс для хранения цели измерения
    private static class MeasurementTarget {
        int col;
        int row;
        String side; // "l" или "r"

        MeasurementTarget(int col, int row, String side) {
            this.col = col;
            this.row = row;
            this.side = side != null ? side : "";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        // Инициализация настроек
        appSettings = new AppSettings(this);

        // Инициализация UI
        tvStatus = findViewById(R.id.tvStatus);
        tvWeightDistance = findViewById(R.id.tvWeightDistance);
        tvRawData = findViewById(R.id.tvRawData);
        tableLimitData = findViewById(R.id.tableLimitData);
        limitDataScrollView = findViewById(R.id.limitDataScrollView);
        btnScan = findViewById(R.id.btnScan);
        btnSettings = findViewById(R.id.btnSettings);
        btnClear = findViewById(R.id.btnClear);

        // Применение настроек
        updateUIFromSettings();

        // Инициализация Bluetooth
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            tvStatus.setText("❌ Bluetooth не поддерживается");
            Toast.makeText(this, "Bluetooth не поддерживается", Toast.LENGTH_LONG).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            tvStatus.setText("⚠️ Включите Bluetooth");
            Toast.makeText(this, "Пожалуйста, включите Bluetooth", Toast.LENGTH_LONG).show();
        }

        bleScanner = bluetoothAdapter.getBluetoothLeScanner();

        // Проверка разрешений
        checkPermissions();

        // Обработчики кнопок
        btnScan.setOnClickListener(v -> startScan());
        btnSettings.setOnClickListener(v -> openSettings());
        btnClear.setOnClickListener(v -> clearLimitData());

        // Новая кнопка статистики
        View btnStatistics = findViewById(R.id.btnStatistics);
        btnStatistics.setOnClickListener(v -> openStatistics());

        // Обновляем состояние кнопок
        updateUI(false);

        // Устанавливаем начальный статус
        tvStatus.setText("⏳ Ожидание превышения веса...");

        View btnDataTable = findViewById(R.id.btnDataTable);
        btnDataTable.setOnClickListener(v -> openDataTable());

        Button btnSaveDiff = findViewById(R.id.btnSaveDiff);
        btnSaveDiff.setOnClickListener(v -> saveDiffData());

        soundManager = SoundManager.getInstance(this);
        soundManager.testSound();
    }

    /**
     * Открывает активность со статистикой по группам
     */
    private void openStatistics() {
        StatisticsDataHolder.getInstance().setDataSamples(limitDataList);
        Intent intent = new Intent(this, StatisticsActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUIFromSettings();
        updateWakeLockState();

        if (isConnected && currentDevice != null) {
            tvStatus.setText("✅ Подключено к " + currentDevice.getName());
        }

        // Перестраиваем очередь, если CSV загружен
        DataManager dataManager = DataManager.getInstance();
        if (dataManager.isCsvLoaded()) {
            // Если очередь пуста или текущий индекс вышел за пределы
            if (measurementQueue.isEmpty() || currentQueueIndex >= measurementQueue.size()) {
                buildMeasurementQueue();
                // Если очередь не пуста, обновляем статус
                if (!measurementQueue.isEmpty()) {
                    updateStatusForCurrentTarget();
                } else {
                    tvStatus.setText("⚠️ Нет данных для измерений. Загрузите CSV.");
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseWakeLock();
    }

    /**
     * Обновляет состояние WakeLock в зависимости от настроек
     */
    private void updateWakeLockState() {
        if (appSettings.isKeepScreenOn()) {
            acquireWakeLock();
        } else {
            releaseWakeLock();
        }
    }

    /**
     * Захватывает WakeLock, чтобы экран не гас
     */
    private void acquireWakeLock() {
        if (wakeLock == null || !wakeLock.isHeld()) {
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "MeasuringPSS::WakeLock"
                );
                wakeLock.acquire(10 * 60 * 1000L);
            }
        }
    }

    /**
     * Освобождает WakeLock, чтобы экран мог гаснуть
     */
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    private void openDataTable() {
        Intent intent = new Intent(this, DataTableActivity.class);
        startActivity(intent);
    }

    /**
     * Сохраняет diff значения из DataSample в CSV файл.
     * Формат: Индекс,Ном. нижн.,Превышение,Расчет,Разница
     */
    private void saveDiffData() {
        DataManager dataManager = DataManager.getInstance();

        if (!dataManager.isCsvLoaded() || limitDataList.isEmpty()) {
            Toast.makeText(this, "Нет данных для сохранения. Загрузите CSV и дождитесь записей.", Toast.LENGTH_LONG).show();
            return;
        }

        Uri originalUri = dataManager.getCurrentFileUri();
        if (originalUri == null) {
            Toast.makeText(this, "Не удалось определить путь к оригинальному файлу.", Toast.LENGTH_LONG).show();
            return;
        }

        String originalFileName = dataManager.getCurrentFileName();
        String newFileName;
        if (originalFileName.contains(".")) {
            int dotIndex = originalFileName.lastIndexOf('.');
            newFileName = originalFileName.substring(0, dotIndex) + "_diff" + originalFileName.substring(dotIndex);
        } else {
            newFileName = originalFileName + "_diff.csv";
        }

        StringBuilder csvContent = new StringBuilder();
        csvContent.append("Индекс,Ном. нижн.,Превышение,Расчет,Разница\n");
        for (DataSample sample : limitDataList) {
            String fullIndex = sample.getFullIndex();
            String lowerTierNumber = sample.getLowerTierNumber() != 0 ? String.valueOf(sample.getLowerTierNumber()) : "";
            String weightOverLimit = String.valueOf(sample.getWeight());
            String calculation = String.format("%d - %d", sample.getDistance(), sample.getTargetWeight());
            String diff = String.format("%.0f", sample.getDiff());
            csvContent.append(fullIndex).append(",")
                    .append(lowerTierNumber).append(",")
                    .append(weightOverLimit).append(",")
                    .append(calculation).append(",")
                    .append(diff).append("\n");
        }

        boolean success = saveFile(newFileName, csvContent.toString());

        if (success) {
            Toast.makeText(this, "Файл сохранен: " + newFileName, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Ошибка при сохранении файла.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Вспомогательный метод для сохранения строки в файл с использованием MediaStore (Android 10+)
     */
    private boolean saveFile(String fileName, String content) {
        ContentResolver resolver = getContentResolver();
        ContentValues contentValues = new ContentValues();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        } else {
            try {
                java.io.File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                java.io.File file = new java.io.File(downloadsDir, fileName);
                if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                    return false;
                }
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                    fos.write(content.getBytes(StandardCharsets.UTF_8));
                    return true;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
        if (uri == null) {
            return false;
        }

        try (OutputStream os = resolver.openOutputStream(uri)) {
            if (os == null) {
                return false;
            }
            os.write(content.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            resolver.delete(uri, null, null);
            e.printStackTrace();
            return false;
        }
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void updateUIFromSettings() {
        double weightCf = appSettings.getWeightCf();
        int distanceAdd = appSettings.getDistanceAdd();
        double weightLimit = appSettings.getWeightLimit();
        int diffThreshold = appSettings.getDiffThreshold();

        if (distanceAdd == 0) {
            if (!limitDataList.isEmpty()) {
                recalculateAllDataSamples();
            }
            tvStatus.setText(String.format("⚙️ Коэф: %.2f, Корр: ДИН (%d), Предел: %.1f, Порог: %d мм",
                    weightCf, dynamicDistanceAdd, weightLimit, diffThreshold));
        } else {
            tvStatus.setText(String.format("⚙️ Коэф: %.2f, Корр: %d, Предел: %.1f, Порог: %d мм",
                    weightCf, distanceAdd, weightLimit, diffThreshold));
        }

        updateWakeLockState();
    }

    /**
     * Пересчитывает все записи в limitDataList с текущей коррекцией
     */
    private void recalculateAllDataSamples() {
        int distanceAdd = appSettings.getDistanceAdd();
        boolean useDynamicCorrection = (distanceAdd == 0);

        for (DataSample sample : limitDataList) {
            int rawDist = sample.getRawDistance();
            int correctedDistance;
            if (useDynamicCorrection) {
                correctedDistance = rawDist + dynamicDistanceAdd;
            } else {
                correctedDistance = rawDist + distanceAdd;
            }
            sample.setDistance(correctedDistance);

            int targetWeight = sample.getTargetWeight();
            double diff = correctedDistance - targetWeight;
            sample.setDiff(diff);
        }

        updateLimitDataTable();
    }

    /**
     * Вычисляет динамическую коррекцию расстояния
     */
    private int calculateDynamicDistanceAdd() {
        if (limitDataList.isEmpty()) {
            return 0;
        }

        List<Double> diffs = new ArrayList<>();
        for (DataSample sample : limitDataList) {
            double diff = sample.getTargetWeight() - sample.getRawDistance();
            diffs.add(diff);
        }

        double median = calculateMedian(diffs);
        double mad = calculateMAD(diffs, median);
        if (mad < 0.001) {
            return (int) Math.round(median);
        }

        double weightedSum = 0.0;
        double weightSum = 0.0;
        double scale = 1.0;

        for (double diff : diffs) {
            double normalizedDiff = (diff - median) / (scale * mad);
            double weight = 1.0 / (1.0 + normalizedDiff * normalizedDiff);
            weightedSum += weight * diff;
            weightSum += weight;
        }

        double weightedAvg = weightSum > 0 ? weightedSum / weightSum : median;
        return (int) Math.round(weightedAvg);
    }

    private double calculateMedian(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        } else {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        }
    }

    private double calculateMAD(List<Double> values, double median) {
        double sumAbsDev = 0.0;
        for (double value : values) {
            sumAbsDev += Math.abs(value - median);
        }
        return sumAbsDev / values.size();
    }

    /**
     * Парсит значение ячейки CSV, чтобы получить номер стропы нижнего яруса
     */
    private int parseLowerTierNumber(String cellValue) {
        if (cellValue == null || cellValue.trim().isEmpty()) {
            return 0;
        }

        String trimmedValue = cellValue.trim();
        String[] parts = trimmedValue.split("/");
        if (parts.length == 2) {
            try {
                return Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Получает целевое значение из текущей ячейки CSV
     */
    private Double getCurrentCsvValue() {
        DataManager dataManager = DataManager.getInstance();
        if (!dataManager.isCsvLoaded() || isTableDataExhausted) {
            return null;
        }

        // Получаем текущую цель
        MeasurementTarget target = getCurrentTarget();
        if (target == null) {
            return null;
        }

        String value = dataManager.getCellValue(target.row, target.col);
        if (value == null) {
            return null;
        }

        return dataManager.parseTargetWeightFromCell(value);
    }

    /**
     * Обновляет таблицу предельных данных
     * Колонки: Ном, Расч, Разн
     */
    private void updateLimitDataTable() {
        tableLimitData.removeAllViews();

        String[] headers = {"Ном", "Расч", "Разн"};
        addTableRow(headers, true);

        if (limitDataList.isEmpty()) {
            String[] emptyRow = {"", "", ""};
            addTableRow(emptyRow, false);
            return;
        }

        DataManager dataManager = DataManager.getInstance();
        boolean hasCsvData = dataManager.isCsvLoaded();

        for (DataSample sample : limitDataList) {
            String fullIndex = sample.getFullIndex();

            String calculation;
            if (hasCsvData) {
                int measuredDistance = sample.getDistance();
                int requiredDistance = sample.getTargetWeight();
                calculation = String.format("%d - %d", measuredDistance, requiredDistance);
            } else {
                calculation = "";
            }

            String diffValue = hasCsvData ? String.format("%.0f", sample.getDiff()) : "";

            String[] rowData = new String[]{
                    fullIndex,
                    calculation,
                    diffValue
            };
            addTableRow(rowData, false);
        }

        limitDataScrollView.post(() -> {
            limitDataScrollView.fullScroll(View.FOCUS_DOWN);
        });
    }

    /**
     * Добавляет строку в таблицу
     */
    private void addTableRow(String[] columns, boolean isHeader) {
        TableRow tableRow = new TableRow(this);
        tableRow.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));

        int diffThreshold = appSettings.getDiffThreshold();

        boolean highlightDiff = false;
        double diffValue = 0.0;
        if (!isHeader && columns.length == 3 && !columns[2].isEmpty()) {
            try {
                diffValue = Double.parseDouble(columns[2]);
                if (Math.abs(diffValue) > diffThreshold) {
                    highlightDiff = true;
                }
            } catch (NumberFormatException e) {
                // Игнорируем
            }
        }

        for (int i = 0; i < columns.length; i++) {
            TextView textView = new TextView(this);
            String column = columns[i] != null ? columns[i] : "";
            textView.setText(column);
            textView.setPadding(12, 8, 12, 8);
            textView.setMaxLines(1);
            textView.setEllipsize(android.text.TextUtils.TruncateAt.END);

            if (isHeader) {
                textView.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
                textView.setTextColor(getResources().getColor(android.R.color.white));
                textView.setTextSize(16);
                textView.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                if (highlightDiff && i == 2) {
                    textView.setBackgroundColor(0xFFFFC0CB);
                } else {
                    int position = tableLimitData.getChildCount();
                    if (position % 2 == 1) {
                        textView.setBackgroundColor(0xFFF5F5F5);
                    } else {
                        textView.setBackgroundColor(0xFFFFFFFF);
                    }
                }
            }
            tableRow.addView(textView);
        }

        if (!isHeader) {
            View divider = new View(this);
            divider.setLayoutParams(new TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    1
            ));
            divider.setBackgroundColor(0xFFE0E0E0);
            tableRow.addView(divider);
        }

        tableLimitData.addView(tableRow);
    }

    /**
     * Добавляет новую запись в список предельных данных
     */
    private void addLimitDataRecord(double correctedWeight, double targetWeight, int rawDistance, int distance, double diff, double weightLimit, int lowerTierNumber, String side) {
        int weightOverLimit = (int) Math.round(correctedWeight - weightLimit);
        int targetWeightInt = (int) Math.round(targetWeight);

        soundManager.playClickSound();

        // Создаем запись с номером стропы, номером нижнего яруса и стороной
        DataSample sample = new DataSample(currentColumnIndex, currentRowIndex,
                weightOverLimit, targetWeightInt, rawDistance, distance, diff, lowerTierNumber, side);
        limitDataList.add(sample);

        int settingsDistanceAdd = appSettings.getDistanceAdd();
        if (settingsDistanceAdd == 0) {
            dynamicDistanceAdd = calculateDynamicDistanceAdd();
            recalculateAllDataSamples();

            double weightCf = appSettings.getWeightCf();
            tvStatus.setText(String.format("⚙️ Коэф: %.2f, Корр: ДИН (%d), Предел: %.1f",
                    weightCf, dynamicDistanceAdd, weightLimit));
        } else {
            updateLimitDataTable();
        }
    }

    /**
     * Удаляет последнюю запись из списка предельных данных
     */
    private void clearLimitData() {
        if (limitDataList.isEmpty()) {
            weightOverLimitBuffer.clear();
            distanceBuffer.clear();
            dynamicDistanceAdd = 0;
            measurementQueue.clear();
            currentQueueIndex = 0;
            updateLimitDataTable();
            return;
        }

        // Получаем последнюю запись перед удалением
        DataSample lastSample = limitDataList.get(limitDataList.size() - 1);
        int removedCol = lastSample.getCol();
        int removedRow = lastSample.getRowIndex();
        String removedSide = lastSample.getSide();

        // Удаляем последнюю запись
        limitDataList.remove(limitDataList.size() - 1);

        // Очищаем буферы
        weightOverLimitBuffer.clear();
        distanceBuffer.clear();

        // Пересчитываем динамическую коррекцию
        int settingsDistanceAdd = appSettings.getDistanceAdd();
        if (settingsDistanceAdd == 0) {
            if (limitDataList.isEmpty()) {
                dynamicDistanceAdd = 0;
            } else {
                dynamicDistanceAdd = calculateDynamicDistanceAdd();
                recalculateAllDataSamples();
            }
        }

        // ВАЖНО: Не сбрасываем очередь полностью, а обновляем ее состояние
        // Находим индекс удаленной записи в очереди
        int targetQueueIndex = -1;
        for (int i = 0; i < measurementQueue.size(); i++) {
            MeasurementTarget target = measurementQueue.get(i);
            if (target.col == removedCol && target.row == removedRow &&
                    (target.side == null ? "" : target.side).equals(removedSide)) {
                targetQueueIndex = i;
                break;
            }
        }

        if (targetQueueIndex != -1) {
            // Устанавливаем текущий индекс на удаленную запись
            // Это позволит при следующем измерении перезаписать ее
            currentQueueIndex = targetQueueIndex;

            // Обновляем текущие координаты
            MeasurementTarget currentTarget = getCurrentTarget();
            if (currentTarget != null) {
                currentColumnIndex = currentTarget.col;
                currentRowIndex = currentTarget.row;
                updateStatusForCurrentTarget();
            }
        } else {
            // Если запись не найдена в очереди (например, очередь была перестроена),
            // просто обновляем таблицу
            updateLimitDataTable();
        }

        // Сбрасываем флаг завершения
        isTableDataExhausted = false;

        // Обновляем таблицу
        updateLimitDataTable();
    }

    // ==================== МЕТОДЫ ДЛЯ РАБОТЫ С ОЧЕРЕДЬЮ ИЗМЕРЕНИЙ ====================

    /**
     * Построить очередь измерений в зависимости от режима
     */
    private void buildMeasurementQueue() {
        measurementQueue.clear();
        currentQueueIndex = 0;

        DataManager dataManager = DataManager.getInstance();
        if (!dataManager.isCsvLoaded()) {
            return;
        }

        int columnCount = dataManager.getColumnCount();
        boolean isDualMode = appSettings.isDualMode();

        for (int col = 0; col < columnCount; col++) {
            int rowCount = dataManager.getRowCount(col);

            if (isDualMode) {
                // --- РЕЖИМ "ДВЕ КОНСОЛИ" (как в документации) ---
                // 1. Все левые стропы (от последней к первой)
                for (int row = rowCount - 1; row >= 0; row--) {
                    String value = dataManager.getCellValue(row, col);
                    if (value != null && !value.trim().isEmpty()) {
                        measurementQueue.add(new MeasurementTarget(col, row, "l"));
                    }
                }
                // 2. Все правые стропы (от первой к последней)
                for (int row = 0; row < rowCount; row++) {
                    String value = dataManager.getCellValue(row, col);
                    if (value != null && !value.trim().isEmpty()) {
                        measurementQueue.add(new MeasurementTarget(col, row, "r"));
                    }
                }
            } else {
                // --- РЕЖИМ "ОДНА КОНСОЛЬ" (как в документации) ---
                for (int row = 0; row < rowCount; row++) {
                    String value = dataManager.getCellValue(row, col);
                    if (value != null && !value.trim().isEmpty()) {
                        measurementQueue.add(new MeasurementTarget(col, row, "")); // side пустой
                    }
                }
            }
        }

        // СИНХРОНИЗИРУЕМ ГЛОБАЛЬНЫЕ ПЕРЕМЕННЫЕ С ПЕРВОЙ ЦЕЛЬЮ
        syncCurrentIndexesWithQueue();
    }

    /**
     * Синхронизирует currentRowIndex и currentColumnIndex с первой целью в очереди.
     * Это гарантирует, что первое измерение будет иметь правильный индекс.
     */
    private void syncCurrentIndexesWithQueue() {
        MeasurementTarget firstTarget = getCurrentTarget();
        if (firstTarget != null) {
            currentColumnIndex = firstTarget.col;
            currentRowIndex = firstTarget.row;
        }
    }

    /**
     * Получить текущую цель измерения
     */
    private MeasurementTarget getCurrentTarget() {
        if (measurementQueue.isEmpty() || currentQueueIndex >= measurementQueue.size()) {
            return null;
        }
        return measurementQueue.get(currentQueueIndex);
    }

    /**
     * Перейти к следующей цели измерения
     */
    private void advanceToNextTarget() {
        currentQueueIndex++;
        MeasurementTarget next = getCurrentTarget();
        if (next != null) {
            currentColumnIndex = next.col;
            currentRowIndex = next.row;
            updateStatusForCurrentTarget();
            isTableDataExhausted = false; // Сбрасываем флаг, если есть следующая цель
        } else {
            isTableDataExhausted = true;
            tvStatus.setText("✅ Все данные из таблицы обработаны!");
        }
    }

    /**
     * Обновить статус для текущей цели
     */
    private void updateStatusForCurrentTarget() {
        MeasurementTarget target = getCurrentTarget();
        if (target == null) {
            tvStatus.setText("⏳ Ожидание данных...");
            return;
        }

        // Обновляем глобальные индексы, если они не совпадают с целевыми
        if (currentColumnIndex != target.col || currentRowIndex != target.row) {
            currentColumnIndex = target.col;
            currentRowIndex = target.row;
        }

        String colLetter = getColumnLetter(target.col);
        String rowNum = String.valueOf(target.row + 1);
        String sideDisplay = "";
        if (!target.side.isEmpty()) {
            sideDisplay = target.side.equals("l") ? " (левая)" : " (правая)";
        }

        // Используем правильное формирование индекса: a4l, b2r и т.д.
        String index = colLetter.toLowerCase() + rowNum + target.side;
        String statusMsg = String.format("⏳ Ожидание превышения веса для стропы %s%s", index, sideDisplay);
        tvStatus.setText(statusMsg);
    }

    /**
     * Переход к следующей ячейке таблицы
     * @return true если данные закончились
     */
    private boolean advanceToNextTableCell() {
        if (measurementQueue.isEmpty()) {
            isTableDataExhausted = true;
            return true;
        }

        advanceToNextTarget(); // Переходим к следующему элементу в очереди

        if (isTableDataExhausted) {
            return true;
        }

        // Проверяем, что следующая цель существует и не пуста
        MeasurementTarget target = getCurrentTarget();
        while (target != null) {
            String value = DataManager.getInstance().getCellValue(target.row, target.col);
            if (value != null && !value.trim().isEmpty()) {
                currentColumnIndex = target.col;
                currentRowIndex = target.row;
                updateStatusForCurrentTarget();
                return false; // Успешно перешли на следующую ячейку
            }
            // Если ячейка пуста, пропускаем её
            advanceToNextTarget();
            target = getCurrentTarget();
        }

        isTableDataExhausted = true;
        return true;
    }

    // ==================== МЕТОДЫ BLUETOOTH ====================

    private void checkPermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        } else {
            return;
        }

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, "Разрешения получены", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Для работы приложения нужны разрешения", Toast.LENGTH_LONG).show();
                tvStatus.setText("❌ Нет разрешений");
            }
        }
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return false;
        }
        try {
            return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }

    private void showLocationDisabledDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Включите геолокацию")
                .setMessage("Для поиска BLE-устройств необходимо включить геолокацию на устройстве. " +
                        "Это требование системы Android. Перейти в настройки?")
                .setPositiveButton("Настройки", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Отмена", (dialog, which) -> {
                    tvStatus.setText("❌ Сканирование невозможно: геолокация выключена");
                })
                .show();
    }

    private void startScan() {
        if (bleScanner == null) {
            Toast.makeText(this, "BLE не поддерживается", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            tvStatus.setText("⚠️ Включите Bluetooth");
            Toast.makeText(this, "Пожалуйста, включите Bluetooth", Toast.LENGTH_LONG).show();
            return;
        }

        if (!isLocationEnabled()) {
            showLocationDisabledDialog();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                tvStatus.setText("❌ Нет разрешения BLUETOOTH_SCAN");
                return;
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            tvStatus.setText("❌ Нет разрешения ACCESS_FINE_LOCATION");
            return;
        }

        if (isScanning) {
            stopScan();
            return;
        }

        isScanning = true;
        btnScan.setText("⏹️");
        btnScan.setEnabled(true);

        bleScanner.startScan(scanCallback);

        handler.postDelayed(() -> {
            if (isScanning) {
                stopScan();
                tvStatus.setText("⏹️ Сканирование остановлено (таймаут)");
                Toast.makeText(MainActivity.this, "Сканирование завершено", Toast.LENGTH_SHORT).show();
            }
        }, 15000);
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        if (bleScanner != null && isScanning) {
            try {
                bleScanner.stopScan(scanCallback);
            } catch (Exception e) {
                e.printStackTrace();
            }
            isScanning = false;
            btnScan.setText("🔍");
        }
    }

    @SuppressLint("MissingPermission")
    private void autoConnectToDevice(BluetoothDevice device) {
        if (device == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                tvStatus.setText("❌ Нет разрешения BLUETOOTH_CONNECT");
                return;
            }
        }

        currentDevice = device;
        tvStatus.setText("🔗 Автоподключение к " + device.getName() + "...");
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            @SuppressLint("MissingPermission") String deviceName = device.getName();

            if (deviceName != null && !deviceName.isEmpty()) {
                runOnUiThread(() -> {
                    tvStatus.setText("📱 Найдено: " + deviceName + "\nMAC: " + device.getAddress());
                });
            }

            if (deviceName != null && deviceName.equals("Measuring PSS")) {
                runOnUiThread(() -> {
                    tvStatus.setText("✅ Найдено устройство: " + deviceName + "\nMAC: " + device.getAddress());
                    Toast.makeText(MainActivity.this, "Найдено: " + deviceName + ", подключение...", Toast.LENGTH_SHORT).show();
                });

                stopScan();
                autoConnectToDevice(device);
            }
        }

        @Override
        public void onBatchScanResults(java.util.List<ScanResult> results) {
            for (ScanResult result : results) {
                BluetoothDevice device = result.getDevice();
                @SuppressLint("MissingPermission") String deviceName = device.getName();
                if (deviceName != null && deviceName.equals("Measuring PSS")) {
                    runOnUiThread(() -> {
                        tvStatus.setText("✅ Найдено: " + deviceName + "\nMAC: " + device.getAddress());
                        Toast.makeText(MainActivity.this, "Найдено: " + deviceName + ", подключение...", Toast.LENGTH_SHORT).show();
                    });

                    stopScan();
                    autoConnectToDevice(device);
                    break;
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            runOnUiThread(() -> {
                isScanning = false;
                btnScan.setText("");
                String errorMsg;
                switch (errorCode) {
                    case SCAN_FAILED_ALREADY_STARTED:
                        errorMsg = "Сканирование уже запущено";
                        break;
                    case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                        errorMsg = "Ошибка регистрации приложения";
                        break;
                    case SCAN_FAILED_INTERNAL_ERROR:
                        errorMsg = "Внутренняя ошибка";
                        break;
                    case SCAN_FAILED_FEATURE_UNSUPPORTED:
                        errorMsg = "Функция не поддерживается";
                        break;
                    default:
                        errorMsg = "Ошибка: " + errorCode;
                }
                tvStatus.setText("❌ Ошибка сканирования: " + errorMsg);
                Toast.makeText(MainActivity.this, "Ошибка сканирования: " + errorMsg, Toast.LENGTH_LONG).show();
            });
        }
    };

    private void connectToDevice() {
        if (currentDevice == null) {
            Toast.makeText(this, "Сначала отсканируйте устройство", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                tvStatus.setText("❌ Нет разрешения BLUETOOTH_CONNECT");
                return;
            }
        }

        tvStatus.setText("🔗 Подключение к " + currentDevice.getName() + "...");
        bluetoothGatt = currentDevice.connectGatt(this, false, gattCallback);
    }

    private void disconnectDevice() {
        if (bluetoothGatt != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothGatt.disconnect();
                    bluetoothGatt.close();
                }
            } else {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
            }
            bluetoothGatt = null;
        }
        isConnected = false;
        updateUI(false);
        tvStatus.setText("❌ Отключено");
        jsonBuffer.setLength(0);
        resetTableState();
    }

    private void resetTableState() {
        currentRowIndex = 0;
        currentColumnIndex = 0;
        isTableDataExhausted = false;
        weightOverLimitBuffer.clear();
        distanceBuffer.clear();
        dynamicDistanceAdd = 0;
        measurementQueue.clear();
        currentQueueIndex = 0;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                isConnected = true;
                jsonBuffer.setLength(0);
                runOnUiThread(() -> {
                    tvStatus.setText("✅ Подключено к " + currentDevice.getName());
                    updateUI(true);
                    appState.setState(AppState.State.EXPECT_DATA);
                    tvStatus.setText("⏳ Ожидание превышения веса...");
                    resetTableState();
                    // НОВОЕ: строим очередь измерений при подключении
                    buildMeasurementQueue();
                    // Если очередь не пуста, обновляем статус
                    if (!measurementQueue.isEmpty()) {
                        updateStatusForCurrentTarget();
                    } else {
                        tvStatus.setText("⚠️ Нет данных для измерений. Загрузите CSV.");
                    }
                });

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                }
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                isConnected = false;
                jsonBuffer.setLength(0);
                runOnUiThread(() -> {
                    tvStatus.setText("❌ Отключено");
                    updateUI(false);
                    resetTableState();
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(NOTIFY_UUID);
                    if (characteristic != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                return;
                            }
                        }

                        gatt.setCharacteristicNotification(characteristic, true);

                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                        );

                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);
                        }

                        runOnUiThread(() -> tvStatus.setText("✅ Подписка на уведомления активирована"));
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(NOTIFY_UUID)) {
                byte[] data = characteristic.getValue();
                String receivedData = new String(data);

                runOnUiThread(() -> {
                    jsonBuffer.append(receivedData);
                    processJsonBuffer();
                });
            }
        }

        private void processJsonBuffer() {
            String buffer = jsonBuffer.toString();

            if (buffer.isEmpty()) {
                return;
            }

            int startIndex = buffer.indexOf("{");
            int endIndex = -1;
            String validJson = null;

            while (startIndex != -1) {
                endIndex = findMatchingBrace(buffer, startIndex);

                if (endIndex == -1) {
                    break;
                }

                String potentialJson = buffer.substring(startIndex, endIndex + 1);

                try {
                    gson.fromJson(potentialJson, DataModel.class);
                    validJson = potentialJson;
                    break;
                } catch (Exception e) {
                    startIndex = buffer.indexOf("{", startIndex + 1);
                }
            }

            if (validJson == null) {
                if (buffer.length() > 1000) {
                    jsonBuffer.setLength(0);
                    tvRawData.setText("📨 Буфер очищен (слишком большой)");
                }
                return;
            }

            int processedEnd = buffer.indexOf(validJson) + validJson.length();
            jsonBuffer.delete(0, processedEnd);

            tvRawData.setText("📨 RAW: " + validJson);

            try {
                DataModel dataModel = gson.fromJson(validJson, DataModel.class);

                double weightCf = appSettings.getWeightCf();
                int distanceAdd = appSettings.getDistanceAdd();
                double weightLimit = appSettings.getWeightLimit();

                double correctedWeight = dataModel.getWeight() * weightCf;
                int rawDistance = dataModel.getDistance();

                int correctedDistance;
                if (distanceAdd == 0) {
                    correctedDistance = rawDistance + dynamicDistanceAdd;
                } else {
                    correctedDistance = rawDistance + distanceAdd;
                }

                tvWeightDistance.setText(String.format("⚖️: %.2f г, 📏: %d мм", correctedWeight, rawDistance));

                if (appState.getCurrentState() == AppState.State.EXPECT_DATA) {
                    if (correctedWeight > weightLimit && !appState.isDataRecordedForCycle()) {
                        DataManager dataManager = DataManager.getInstance();
                        double overWeight = correctedWeight - weightLimit;

                        if (overWeight < 1000) {
                            if (dataManager.isCsvLoaded()) {
                                processWithCsvData(correctedWeight, correctedDistance, rawDistance, dataModel, weightLimit);
                            } else {
                                addLimitDataRecord(correctedWeight, 0.0, rawDistance, correctedDistance, 0.0, weightLimit, 0, "");
                                dataModel.setRecorded(true);
                                appState.setDataRecordedForCycle(true);
                                appState.setState(AppState.State.EXPECT_RETURN);
                                tvStatus.setText("⏳ Ожидание снижения веса...");
                            }
                        } else {
                            Toast.makeText(MainActivity.this, "Слишком большое превышение веса!", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else if (appState.getCurrentState() == AppState.State.EXPECT_RETURN) {
                    double returnThreshold = weightLimit * 0.9;
                    if (correctedWeight < returnThreshold) {
                        appState.setState(AppState.State.EXPECT_DATA);
                        tvStatus.setText("⏳ Ожидание превышения веса...");
                    }
                }

            } catch (Exception e) {
                tvWeightDistance.setText("⚠️ Ошибка парсинга: " + e.getMessage());
                e.printStackTrace();
                if (jsonBuffer.length() > 0) {
                    processJsonBuffer();
                }
            }
        }

        private int findMatchingBrace(String buffer, int start) {
            int depth = 0;
            for (int i = start; i < buffer.length(); i++) {
                char c = buffer.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
            return -1;
        }

        /**
         * Обработка данных с использованием CSV таблицы
         */
        private void processWithCsvData(double correctedWeight, int correctedDistance, int rawDistance, DataModel dataModel, double weightLimit) {
            DataManager dataManager = DataManager.getInstance();

            if (isTableDataExhausted) {
                tvStatus.setText("❌ Данные из таблицы закончились!");
                return;
            }

            // Проверяем, есть ли очередь
            if (measurementQueue.isEmpty()) {
                buildMeasurementQueue();
                if (measurementQueue.isEmpty()) {
                    tvStatus.setText("❌ Нет данных для измерений. Загрузите CSV.");
                    return;
                }
            }

            MeasurementTarget target = getCurrentTarget();
            if (target == null) {
                // Пытаемся перейти к следующей цели
                advanceToNextTarget();
                target = getCurrentTarget();
                if (target == null) {
                    tvStatus.setText("❌ Нет текущей цели измерения!");
                    return;
                }
            }

            String rawCellValue = dataManager.getCellValue(target.row, target.col);
            Double csvValue = dataManager.parseTargetWeightFromCell(rawCellValue);
            int lowerTierNumber = parseLowerTierNumber(rawCellValue);

            if (csvValue == null) {
                String columnLetter = getColumnLetter(target.col);
                String message = String.format("⚠️ Ячейка %s%d не содержит число или пуста",
                        columnLetter, target.row + 1);
                tvStatus.setText(message);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();

                advanceToNextTableCell();
                if (isTableDataExhausted) {
                    tvStatus.setText("❌ Данные из таблицы закончились!");
                }
                return;
            }

            soundManager.playClickSound();

            double diff = correctedDistance - csvValue;
            lastCsvValue = csvValue;

            double weightOverLimit = correctedWeight - weightLimit;
            weightOverLimitBuffer.add(weightOverLimit);
            distanceBuffer.add(correctedDistance);

            int measurementCount = appSettings.getMeasurementCount();

            if (weightOverLimitBuffer.size() >= measurementCount) {
                double avgWeightOverLimit = 0.0;
                double avgDistance = 0.0;
                for (Double w : weightOverLimitBuffer) {
                    avgWeightOverLimit += w;
                }
                for (Integer d : distanceBuffer) {
                    avgDistance += d;
                }
                avgWeightOverLimit /= weightOverLimitBuffer.size();
                avgDistance /= distanceBuffer.size();

                double avgDiff = avgDistance - lastCsvValue;

                String side = target.side;

                addLimitDataRecord(
                        correctedWeight,
                        lastCsvValue,
                        rawDistance,
                        (int) Math.round(avgDistance),
                        avgDiff,
                        weightLimit,
                        lowerTierNumber,
                        side
                );

                String colLetter = getColumnLetter(target.col);
                String index = colLetter.toLowerCase() + (target.row + 1) + side;

                String statusMsg = String.format("✅ Записано (усреднено из %d замеров): %s, Diff: %.2f",
                        measurementCount, index, avgDiff);
                tvStatus.setText(statusMsg);

                weightOverLimitBuffer.clear();
                distanceBuffer.clear();

                advanceToNextTableCell();
                if (isTableDataExhausted) {
                    tvStatus.setText("✅ Все данные из таблицы обработаны!");
                }
            } else {
                MeasurementTarget currentTarget = getCurrentTarget();
                if (currentTarget != null) {
                    String colLetter = getColumnLetter(currentTarget.col);
                    String index = colLetter.toLowerCase() + (currentTarget.row + 1) + currentTarget.side;

                    String statusMsg = String.format("⏳ Замер %d из %d для стропы %s",
                            weightOverLimitBuffer.size(), measurementCount, index);
                    tvStatus.setText(statusMsg);
                }
            }

            dataModel.setRecorded(true);
            appState.setDataRecordedForCycle(true);
            appState.setState(AppState.State.EXPECT_RETURN);
        }
    };

    private String getColumnLetter(int index) {
        StringBuilder sb = new StringBuilder();
        index++;

        while (index > 0) {
            index--;
            char letter = (char) ('A' + (index % 26));
            sb.insert(0, letter);
            index = index / 26;
        }

        return sb.toString();
    }

    private void updateUI(boolean connected) {
        btnScan.setEnabled(!connected);

        if (!connected && !isScanning) {
            btnScan.setText("🔍");
        }

        if (!connected) {
            tvWeightDistance.setText("⚖️: --, 📏: --");
            tvRawData.setText("📨 Ожидание данных...");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseWakeLock();
        if (soundManager != null) {
            soundManager.release();
        }
        stopScan();
        disconnectDevice();
    }

    /**
     * Принудительно обновляет очередь измерений
     */
    private void refreshMeasurementQueue() {
        DataManager dataManager = DataManager.getInstance();
        if (!dataManager.isCsvLoaded()) {
            tvStatus.setText("⚠️ CSV не загружен. Загрузите файл.");
            return;
        }

        measurementQueue.clear();
        currentQueueIndex = 0;
        buildMeasurementQueue();

        if (!measurementQueue.isEmpty()) {
            tvStatus.setText("🔄 Очередь обновлена. Всего целей: " + measurementQueue.size());
            updateStatusForCurrentTarget();
        } else {
            tvStatus.setText("⚠️ Нет данных для измерений.");
        }
    }
}