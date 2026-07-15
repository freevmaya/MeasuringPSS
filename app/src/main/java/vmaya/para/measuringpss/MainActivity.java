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
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;

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
    private TextView tvWeight;
    private TextView tvDistance;
    private TextView tvRawData;
    private Button btnScan;
    private Button btnConnect;
    private Button btnDisconnect;

    private Gson gson = new Gson();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothDevice currentDevice;

    // Буфер для накопления данных
    private final StringBuilder jsonBuffer = new StringBuilder();

    private static final int REQUEST_PERMISSIONS = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Инициализация UI
        tvStatus = findViewById(R.id.tvStatus);
        tvWeight = findViewById(R.id.tvWeight);
        tvDistance = findViewById(R.id.tvDistance);
        tvRawData = findViewById(R.id.tvRawData);
        btnScan = findViewById(R.id.btnScan);
        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);

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

        // Обновляем состояние кнопок
        updateUI(false);
    }

    private void checkPermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-11 (API 23-30)
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        } else {
            // Android 5 и ниже (API 21-22)
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
        btnScan.setText("⏹️ Остановить");
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
            btnScan.setText("🔍 Сканировать");
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            @SuppressLint("MissingPermission") String deviceName = device.getName();

            // Логируем найденные устройства
            if (deviceName != null && !deviceName.isEmpty()) {
                runOnUiThread(() -> {
                    tvStatus.setText("📱 Найдено: " + deviceName + "\nMAC: " + device.getAddress());
                });

                // Ищем устройство "Measuring PSS"
                if (deviceName.equals("Measuring PSS")) {
                    runOnUiThread(() -> {
                        tvStatus.setText("✅ Найдено устройство: " + deviceName + "\nMAC: " + device.getAddress());
                        Toast.makeText(MainActivity.this, "Найдено: " + deviceName, Toast.LENGTH_SHORT).show();
                    });

                    // Останавливаем сканирование
                    stopScan();

                    // Сохраняем устройство для подключения
                    currentDevice = device;
                    runOnUiThread(() -> {
                        btnConnect.setEnabled(true);
                        btnScan.setEnabled(true);
                        btnScan.setText("🔍 Сканировать");
                    });
                }
            }
        }

        @Override
        public void onBatchScanResults(java.util.List<ScanResult> results) {
            for (ScanResult result : results) {
                BluetoothDevice device = result.getDevice();
                @SuppressLint("MissingPermission") String deviceName = device.getName();
                if (deviceName != null && deviceName.equals("Measuring PSS")) {
                    currentDevice = device;
                    runOnUiThread(() -> {
                        tvStatus.setText("✅ Найдено: " + deviceName + "\nMAC: " + device.getAddress());
                        btnConnect.setEnabled(true);
                        btnScan.setEnabled(true);
                        btnScan.setText("🔍 Сканировать");
                    });
                    stopScan();
                    break;
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            runOnUiThread(() -> {
                isScanning = false;
                btnScan.setText("🔍 Сканировать");
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
        jsonBuffer.setLength(0); // Очищаем буфер при отключении
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                isConnected = true;
                jsonBuffer.setLength(0); // Очищаем буфер при подключении
                runOnUiThread(() -> {
                    tvStatus.setText("✅ Подключено к " + currentDevice.getName());
                    updateUI(true);
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

                        // Подписываемся на уведомления
                        gatt.setCharacteristicNotification(characteristic, true);

                        // Добавляем дескриптор для уведомлений
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
                    // Добавляем полученные данные в буфер
                    jsonBuffer.append(receivedData);

                    // Обрабатываем буфер в цикле, чтобы извлечь все возможные JSON-объекты
                    processJsonBuffer();

                    // Если в буфере остались данные, показываем их как "неполный JSON"
                    if (jsonBuffer.length() > 0) {
                        tvRawData.setText("📨 Буфер: " + jsonBuffer.toString());
                    }
                });
            }
        }

        /**
         * Обрабатывает буфер jsonBuffer, извлекая все полные JSON-объекты.
         * Если объект неполный (нет закрывающей скобки), он остается в буфере.
         */
        private void processJsonBuffer() {
            String buffer = jsonBuffer.toString();
            int startIndex = buffer.indexOf("{");

            while (startIndex != -1) {
                int endIndex = buffer.indexOf("}", startIndex + 1);

                if (endIndex == -1) {
                    // Закрывающая скобка не найдена, выходим из цикла
                    break;
                }

                // Извлекаем полный JSON
                String fullJson = buffer.substring(startIndex, endIndex + 1);

                // Отображаем сырые данные
                tvRawData.setText("📨 RAW: " + fullJson);

                try {
                    // Парсим JSON
                    DataModel dataModel = gson.fromJson(fullJson, DataModel.class);

                    // Обновляем UI
                    tvWeight.setText(String.format("⚖️ Вес: %.2f г", dataModel.getWeight()));
                    tvDistance.setText(String.format("📏 Расстояние: %d мм", dataModel.getDistance()));

                } catch (Exception e) {
                    tvWeight.setText("⚠️ Ошибка парсинга: " + e.getMessage());
                    e.printStackTrace();
                }

                // Удаляем обработанный JSON из буфера, начиная с символа, который идет за ним
                buffer = buffer.substring(endIndex + 1);
                startIndex = buffer.indexOf("{");
            }

            // Обновляем буфер, оставляя только необработанные данные
            jsonBuffer.setLength(0);
            jsonBuffer.append(buffer);
        }
    };

    private void updateUI(boolean connected) {
        btnScan.setEnabled(!connected);
        btnConnect.setEnabled(!connected && currentDevice != null);
        btnDisconnect.setEnabled(connected);

        if (!connected) {
            tvWeight.setText("⚖️ Вес: --");
            tvDistance.setText("📏 Расстояние: --");
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