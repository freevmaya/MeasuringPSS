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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
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

import java.util.ArrayList;
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
    private Button btnConnect;
    private Button btnDisconnect;
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
    private int currentCsvRowNumber = 1;

    // Счетчик номера стропы в текущем ряду
    private int currentRowIndexCounter = 1;

    private static final int REQUEST_PERMISSIONS = 1;

    // Экземпляры классов
    private AppSettings appSettings;
    private AppState appState = new AppState();

    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "MeasuringPSS::WakeLock"
            );
            wakeLock.acquire(10 * 60 * 1000L /* 10 минут */);
        }

        // Инициализация настроек
        appSettings = new AppSettings(this);

        // Инициализация UI
        tvStatus = findViewById(R.id.tvStatus);
        tvWeightDistance = findViewById(R.id.tvWeightDistance);
        tvRawData = findViewById(R.id.tvRawData);
        tableLimitData = findViewById(R.id.tableLimitData);
        limitDataScrollView = findViewById(R.id.limitDataScrollView);
        btnScan = findViewById(R.id.btnScan);
        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);
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
        btnConnect.setOnClickListener(v -> connectToDevice());
        btnDisconnect.setOnClickListener(v -> disconnectDevice());
        btnSettings.setOnClickListener(v -> openSettings());
        btnClear.setOnClickListener(v -> clearLimitData());

        // Обновляем состояние кнопок
        updateUI(false);

        // Устанавливаем начальный статус
        tvStatus.setText("⏳ Ожидание превышения веса...");

        View btnDataTable = findViewById(R.id.btnDataTable);
        btnDataTable.setOnClickListener(v -> openDataTable());
    }

    private void openDataTable() {
        Intent intent = new Intent(this, DataTableActivity.class);
        startActivity(intent);
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUIFromSettings();
    }

    private void updateUIFromSettings() {
        double weightCf = appSettings.getWeightCf();
        int distanceAdd = appSettings.getDistanceAdd();
        double weightLimit = appSettings.getWeightLimit();

        tvStatus.setText(String.format("⚙️ Коэф: %.2f, Корр: %d, Предел: %.1f",
                weightCf, distanceAdd, weightLimit));
    }

    /**
     * Обновляет таблицу предельных данных
     * Колонки:
     * Ряд - буква колонки из CSV (A, B, C, D, E)
     * Ном. стропы - номер строки в пределах текущего ряда (1, 2, 3, ...)
     * Превышение - разница между измеренным весом и предельным
     * Расчет - строка: "Измеренная длина - Требуемая длина"
     * Разница - числовое значение diff
     */
    private void updateLimitDataTable() {
        tableLimitData.removeAllViews();

        // Всегда показываем 5 колонок
        String[] headers = {"Ряд", "Ном", "Прев", "Расч", "Разн"};
        addTableRow(headers, true);

        if (limitDataList.isEmpty()) {
            String[] emptyRow = {"", "", "", "", ""};
            addTableRow(emptyRow, false);
            return;
        }

        DataManager dataManager = DataManager.getInstance();
        boolean hasCsvData = dataManager.isCsvLoaded();

        for (DataSample sample : limitDataList) {
            // Ряд - буква колонки из CSV (A, B, C, D, E)
            String rowLetter;
            if (hasCsvData && sample.getRow() >= 1 && sample.getRow() <= 5) {
                switch (sample.getRow()) {
                    case 1: rowLetter = "A"; break;
                    case 2: rowLetter = "B"; break;
                    case 3: rowLetter = "C"; break;
                    case 4: rowLetter = "D"; break;
                    case 5: rowLetter = "E"; break;
                    default: rowLetter = "?";
                }
            } else {
                rowLetter = "";
            }

            // Ном. стропы - номер в пределах ряда
            String rowIndex = hasCsvData ? String.valueOf(sample.getRowIndex()) : "";

            // Превышение
            String weightOverLimit = String.valueOf(sample.getWeight());

            // Расчет: "Измеренная длина - Требуемая длина"
            String calculation;
            if (hasCsvData) {
                int measuredDistance = sample.getDistance();
                int requiredDistance = sample.getTargetWeight();
                calculation = String.format("%d - %d", measuredDistance, requiredDistance);
            } else {
                calculation = "";
            }

            // Разница
            String diffValue = hasCsvData ? String.format("%.0f", sample.getDiff()) : "";

            String[] rowData = new String[]{
                    rowLetter,       // Ряд
                    rowIndex,        // Ном. стропы
                    weightOverLimit, // Превышение
                    calculation,     // Расчет
                    diffValue        // Разница
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

        for (String column : columns) {
            TextView textView = new TextView(this);
            textView.setText(column != null ? column : "");
            textView.setPadding(12, 8, 12, 8);
            textView.setMaxLines(1);
            textView.setEllipsize(android.text.TextUtils.TruncateAt.END);

            if (isHeader) {
                textView.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
                textView.setTextColor(getResources().getColor(android.R.color.white));
                textView.setTextSize(16);
                textView.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                int position = tableLimitData.getChildCount();
                if (position % 2 == 1) {
                    textView.setBackgroundColor(0xFFF5F5F5);
                } else {
                    textView.setBackgroundColor(0xFFFFFFFF);
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
     * @param correctedWeight скорректированный вес с датчика
     * @param targetWeight значение из CSV таблицы (потребная длина)
     * @param distance скорректированное расстояние
     * @param diff разница с таблицей CSV
     * @param weightLimit текущий предел веса
     */
    private void addLimitDataRecord(double correctedWeight, double targetWeight, int distance, double diff, double weightLimit) {

        int weightOverLimit = (int) Math.round(correctedWeight - weightLimit);
        int targetWeightInt = (int) Math.round(targetWeight);

        // Создаем запись с номером стропы в пределах ряда
        DataSample sample = new DataSample(currentCsvRowNumber, currentColumnIndex + 1, currentRowIndexCounter,
                weightOverLimit, targetWeightInt, distance, diff);
        limitDataList.add(sample);

        // Увеличиваем счетчик стропы для следующей записи в этом же ряду
        currentRowIndexCounter++;

        updateLimitDataTable();
    }

    /**
     * Удаляет последнюю запись из списка предельных данных
     */
    private void clearLimitData() {
        if (limitDataList.isEmpty()) {
            Toast.makeText(this, "Нет данных для удаления", Toast.LENGTH_SHORT).show();
            return;
        }

        // Получаем удаляемую запись
        DataSample removedSample = limitDataList.get(limitDataList.size() - 1);

        // Удаляем последнюю запись
        limitDataList.remove(limitDataList.size() - 1);

        // Восстанавливаем счетчик стропы из удаленной записи
        // Если удаляемая запись была последней в своем ряду,
        // то счетчик должен стать на единицу меньше
        currentRowIndexCounter = removedSample.getRowIndex();
        currentCsvRowNumber = removedSample.getRowIndex() - 1;
        currentColumnIndex = removedSample.getRow() - 1;

        updateLimitDataTable();
        Toast.makeText(this, "Последняя запись удалена", Toast.LENGTH_SHORT).show();
    }

    /**
     * Сбрасывает счетчик стропы при смене ряда
     */
    private void resetRowIndexCounter() {
        currentRowIndexCounter = 1;
    }

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

                if (deviceName.equals("Measuring PSS")) {
                    runOnUiThread(() -> {
                        tvStatus.setText("✅ Найдено устройство: " + deviceName + "\nMAC: " + device.getAddress());
                        Toast.makeText(MainActivity.this, "Найдено: " + deviceName + ", подключение...", Toast.LENGTH_SHORT).show();
                    });

                    stopScan();
                    autoConnectToDevice(device);
                }
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
        currentCsvRowNumber = 1;
        currentRowIndexCounter = 1;
    }

    private boolean advanceToNextTableCell() {
        DataManager dataManager = DataManager.getInstance();

        if (!dataManager.isCsvLoaded()) {
            return false;
        }

        int rowCount = dataManager.getRowCount();
        int columnCount = dataManager.getColumnCount();

        if (rowCount == 0 || columnCount == 0) {
            isTableDataExhausted = true;
            return true;
        }

        if (currentRowIndex >= rowCount) {
            currentRowIndex = 0;
            currentColumnIndex++;
            currentCsvRowNumber = 1;

            if (currentColumnIndex >= columnCount) {
                isTableDataExhausted = true;
                return true;
            }

            String value = dataManager.getCellValue(currentRowIndex, currentColumnIndex);
            if (value == null) {
                return advanceToNextNonEmptyCell(dataManager);
            }

            return false;
        }

        String value = dataManager.getCellValue(currentRowIndex, currentColumnIndex);
        if (value == null) {
            return advanceToNextNonEmptyCell(dataManager);
        }

        return false;
    }

    private boolean advanceToNextNonEmptyCell(DataManager dataManager) {
        int rowCount = dataManager.getRowCount();
        int columnCount = dataManager.getColumnCount();

        for (int row = currentRowIndex; row < rowCount; row++) {
            for (int col = (row == currentRowIndex ? currentColumnIndex : 0); col < columnCount; col++) {
                String value = dataManager.getCellValue(row, col);
                if (value != null && !value.trim().isEmpty()) {
                    currentRowIndex = row;
                    currentColumnIndex = col;
                    currentCsvRowNumber = row + 1;
                    return false;
                }
            }
        }

        isTableDataExhausted = true;
        return true;
    }

    private Double getCurrentCsvValue() {
        DataManager dataManager = DataManager.getInstance();
        if (!dataManager.isCsvLoaded() || isTableDataExhausted) {
            return null;
        }

        String value = dataManager.getCellValue(currentRowIndex, currentColumnIndex);
        if (value == null) {
            return null;
        }

        try {
            value = value.replace(',', '.');
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int getCurrentRowNumber() {
        return currentCsvRowNumber;
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

            // Если буфер пуст, выходим
            if (buffer.isEmpty()) {
                return;
            }

            // Пытаемся найти и извлечь валидный JSON объект
            int startIndex = buffer.indexOf("{");
            int endIndex = -1;
            String validJson = null;

            while (startIndex != -1) {
                // Ищем закрывающую скобку
                endIndex = findMatchingBrace(buffer, startIndex);

                if (endIndex == -1) {
                    // Нет завершенного JSON - оставляем в буфере и выходим
                    break;
                }

                String potentialJson = buffer.substring(startIndex, endIndex + 1);

                // Проверяем, является ли это валидным JSON
                try {
                    gson.fromJson(potentialJson, DataModel.class);
                    validJson = potentialJson;
                    break;
                } catch (Exception e) {
                    // Этот кусок невалидный - ищем следующий JSON объект
                    startIndex = buffer.indexOf("{", startIndex + 1);
                }
            }

            if (validJson == null) {
                // Не нашли валидный JSON - очищаем буфер, чтобы не накапливать мусор
                if (buffer.length() > 1000) {
                    // Если буфер слишком большой, очищаем его
                    jsonBuffer.setLength(0);
                    tvRawData.setText("📨 Буфер очищен (слишком большой)");
                }
                return;
            }

            // Удаляем обработанный JSON из буфера вместе со всем мусором до него
            int processedEnd = buffer.indexOf(validJson) + validJson.length();
            jsonBuffer.delete(0, processedEnd);

            // Парсим валидный JSON
            tvRawData.setText("📨 RAW: " + validJson);

            try {
                DataModel dataModel = gson.fromJson(validJson, DataModel.class);

                double weightCf = appSettings.getWeightCf();
                int distanceAdd = appSettings.getDistanceAdd();
                double weightLimit = appSettings.getWeightLimit();

                double correctedWeight = dataModel.getWeight() * weightCf;
                int correctedDistance = dataModel.getDistance() + distanceAdd;

                tvWeightDistance.setText(String.format("⚖️: %.2f г, 📏: %d мм", correctedWeight, correctedDistance));

                if (appState.getCurrentState() == AppState.State.EXPECT_DATA) {
                    if (correctedWeight > weightLimit && !appState.isDataRecordedForCycle()) {
                        DataManager dataManager = DataManager.getInstance();

                        if (dataManager.isCsvLoaded()) {
                            processWithCsvData(correctedWeight, correctedDistance, dataModel, weightLimit);
                        } else {
                            addLimitDataRecord(correctedWeight, 0.0, correctedDistance, 0.0, weightLimit);
                            dataModel.setRecorded(true);
                            appState.setDataRecordedForCycle(true);
                            appState.setState(AppState.State.EXPECT_RETURN);
                            tvStatus.setText("⏳ Ожидание снижения веса...");
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
                // Если парсинг упал, пробуем найти следующий JSON в оставшемся буфере
                // Рекурсивно вызываем этот же метод для обработки остатка
                if (jsonBuffer.length() > 0) {
                    processJsonBuffer();
                }
            }
        }

        /**
         * Находит позицию закрывающей скобки, соответствующей открывающей на позиции start
         */
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
        private void processWithCsvData(double correctedWeight, int correctedDistance, DataModel dataModel, double weightLimit) {
            DataManager dataManager = DataManager.getInstance();

            if (isTableDataExhausted) {
                tvStatus.setText("❌ Данные из таблицы закончились!");
                return;
            }

            Double csvValue = getCurrentCsvValue();

            if (csvValue == null) {
                String columnLetter = getColumnLetter(currentColumnIndex);
                String message = String.format("⚠️ Ячейка %s%d не содержит число или пуста",
                        columnLetter, currentCsvRowNumber);
                tvStatus.setText(message);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();

                boolean exhausted = advanceToNextTableCell();
                if (exhausted) {
                    tvStatus.setText("❌ Данные из таблицы закончились!");
                }
                return;
            }

            // Вычисляем разницу с таблицей CSV
            double diff = correctedDistance - csvValue;

            // Определяем ряд: 0 = A, 1 = B, 2 = C, 3 = D, 4 = E
            int rowValue = currentColumnIndex + 1;

            // Проверяем, сменился ли ряд
            // Если это первая запись или ряд изменился - сбрасываем счетчик стропы
            if (!limitDataList.isEmpty()) {
                DataSample lastSample = limitDataList.get(limitDataList.size() - 1);
                if (lastSample.getRow() != rowValue) {
                    resetRowIndexCounter();
                }
            } else {
                resetRowIndexCounter();
            }

            // Добавляем запись
            addLimitDataRecord(correctedWeight, csvValue, correctedDistance, diff, weightLimit);

            String columnLetter = getColumnLetter(currentColumnIndex);
            String statusMsg = String.format("✅ Записано: %s%d (Ряд %d, Стропа %d), Diff: %.2f",
                    columnLetter, currentCsvRowNumber, rowValue, currentRowIndexCounter - 1, diff);
            tvStatus.setText(statusMsg);

            dataModel.setRecorded(true);
            appState.setDataRecordedForCycle(true);

            currentRowIndex++;
            currentCsvRowNumber++;

            boolean exhausted = advanceToNextTableCell();
            if (exhausted) {
                tvStatus.setText("✅ Все данные из таблицы обработаны!");
            }

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
        btnConnect.setEnabled(!connected && currentDevice != null);
        btnDisconnect.setEnabled(connected);

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
        // Освобождаем блокировку, чтобы экран мог гаснуть
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        stopScan();
        disconnectDevice();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopScan();
    }
}