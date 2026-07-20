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
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
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
import java.util.Locale;
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
    private TextView tvWeightDistance; // Объединённое поле для веса и расстояния
    private TextView tvRawData;
    private TextView tvLimitData;
    private Button btnScan;
    private Button btnConnect;
    private Button btnDisconnect;
    private Button btnSettings;
    private Button btnClear;
    private Spinner spinnerRow; // Spinner для выбора ряда

    private Gson gson = new Gson();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothDevice currentDevice;

    // Буфер для накопления данных
    private final StringBuilder jsonBuffer = new StringBuilder();

    // Список для хранения записей предельных данных
    private final List<DataSample> limitDataList = new ArrayList<>();
    private int recordCounter = 0;

    private static final int REQUEST_PERMISSIONS = 1;

    // Экземпляры классов
    private AppSettings appSettings;
    private AppState appState = new AppState();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Инициализация настроек
        appSettings = new AppSettings(this);

        // Инициализация UI
        tvStatus = findViewById(R.id.tvStatus);
        tvWeightDistance = findViewById(R.id.tvWeightDistance); // Обновлённый ID
        tvRawData = findViewById(R.id.tvRawData);
        tvLimitData = findViewById(R.id.tvLimitData);
        btnScan = findViewById(R.id.btnScan);
        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        btnSettings = findViewById(R.id.btnSettings);
        btnClear = findViewById(R.id.btnClear);
        spinnerRow = findViewById(R.id.spinnerRow); // Инициализация Spinner

        // Настройка Spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.row_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRow.setAdapter(adapter);

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

        // Обновляем отображение списка
        updateLimitDataDisplay();

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
        // Обновляем UI при возврате из настроек
        updateUIFromSettings();
    }

    private void updateUIFromSettings() {
        // Обновляем отображение текущих настроек
        double weightCf = appSettings.getWeightCf();
        int distanceAdd = appSettings.getDistanceAdd();
        double weightLimit = appSettings.getWeightLimit();

        // Обновляем статус с текущими настройками
        tvStatus.setText(String.format("⚙️ Коэф: %.2f, Корр: %d, Предел: %.1f",
                weightCf, distanceAdd, weightLimit));
    }

    /**
     * Обновляет отображение списка предельных данных в tvLimitData
     */
    private void updateLimitDataDisplay() {
        StringBuilder displayText = new StringBuilder("📋 Предельные данные:");

        if (!limitDataList.isEmpty()) {
            for (DataSample sample : limitDataList) {
                displayText.append("\n").append(sample.toString());
            }
        }

        tvLimitData.setText(displayText.toString());
    }

    /**
     * Добавляет новую запись в список предельных данных
     */
    private void addLimitDataRecord(double weight, int distance) {
        recordCounter++;
        // Получаем выбранный ряд из Spinner
        String selectedRow = spinnerRow.getSelectedItem().toString();
        // Преобразуем в int (0 для A, 1 для B и т.д.), либо можно хранить как строку.
        // В DataSample поле row - int. Предположим, что A=1, B=2, C=3, D=4, E=5.
        int rowValue = 0;
        switch (selectedRow) {
            case "A": rowValue = 1; break;
            case "B": rowValue = 2; break;
            case "C": rowValue = 3; break;
            case "D": rowValue = 4; break;
            case "E": rowValue = 5; break;
            default: rowValue = 1; // На случай, если значение не распознано
        }

        DataSample sample = new DataSample(recordCounter, rowValue, weight, distance);
        limitDataList.add(sample);
        updateLimitDataDisplay();
    }

    /**
     * Удаляет последнюю запись из списка предельных данных
     */
    private void clearLimitData() {
        if (limitDataList.isEmpty()) {
            Toast.makeText(this, "Нет данных для удаления", Toast.LENGTH_SHORT).show();
            return;
        }

        // Удаляем последнюю запись
        limitDataList.remove(limitDataList.size() - 1);
        updateLimitDataDisplay();
        Toast.makeText(this, "Последняя запись удалена", Toast.LENGTH_SHORT).show();
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

        // Проверяем разрешения для сканирования
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
        btnScan.setText("⏹️ Стоп");
        tvStatus.setText("🔍 Поиск устройств...");
        btnScan.setEnabled(true);

        // Запускаем сканирование
        bleScanner.startScan(scanCallback);

        // Автоматически останавливаем через 15 секунд
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
            btnScan.setText("🔍 Скан.");
        }
    }

    /**
     * Автоматическое подключение к найденному устройству
     */
    @SuppressLint("MissingPermission")
    private void autoConnectToDevice(BluetoothDevice device) {
        if (device == null) {
            return;
        }

        // Проверяем разрешение на подключение
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

                    // Останавливаем сканирование
                    stopScan();

                    // Автоматически подключаемся
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
                btnScan.setText("🔍 Скан.");
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
            int startIndex = buffer.indexOf("{");

            while (startIndex != -1) {
                int endIndex = buffer.indexOf("}", startIndex + 1);

                if (endIndex == -1) {
                    break;
                }

                String fullJson = buffer.substring(startIndex, endIndex + 1);
                tvRawData.setText("📨 RAW: " + fullJson);

                try {
                    DataModel dataModel = gson.fromJson(fullJson, DataModel.class);

                    double weightCf = appSettings.getWeightCf();
                    int distanceAdd = appSettings.getDistanceAdd();
                    double weightLimit = appSettings.getWeightLimit();

                    double correctedWeight = dataModel.getWeight() * weightCf;
                    int correctedDistance = dataModel.getDistance() + distanceAdd;

                    // Обновляем объединённое поле
                    tvWeightDistance.setText(String.format("⚖️: %.2f г, 📏: %d мм", correctedWeight, correctedDistance));

                    if (appState.getCurrentState() == AppState.State.EXPECT_DATA) {
                        if (correctedWeight > weightLimit && !appState.isDataRecordedForCycle()) {
                            // Добавляем запись в список как DataSample
                            addLimitDataRecord(correctedWeight, correctedDistance);

                            dataModel.setRecorded(true);
                            appState.setDataRecordedForCycle(true);

                            appState.setState(AppState.State.EXPECT_RETURN);
                            tvStatus.setText("⏳ Ожидание снижения веса...");
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
                }

                buffer = buffer.substring(endIndex + 1);
                startIndex = buffer.indexOf("{");
            }

            jsonBuffer.setLength(0);
            jsonBuffer.append(buffer);

            if (jsonBuffer.length() > 0) {
                tvRawData.setText("📨 Буфер: " + jsonBuffer.toString());
            }
        }
    };

    private void updateUI(boolean connected) {
        btnScan.setEnabled(!connected);
        btnConnect.setEnabled(!connected && currentDevice != null);
        btnDisconnect.setEnabled(connected);

        if (!connected) {
            tvWeightDistance.setText("⚖️: --, 📏: --");
            tvRawData.setText("📨 Ожидание данных...");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScan();
        disconnectDevice();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopScan();
    }
}