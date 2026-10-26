// app/src/main/java/vmaya/para/measuringpss/DataTableActivity.java
package vmaya.para.measuringpss;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class DataTableActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_CSV = 1;
    private static final int REQUEST_PERMISSION_READ_EXTERNAL_STORAGE = 100;

    private TableLayout tableData;
    private Button btnLoadCsv;
    private TextView tvFileName;
    private TextView tvMinMaxDifference;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CatalogApiClient catalogApiClient = new CatalogApiClient();
    private AppSettings appSettings;

    // Кэш в рамках сессии активности
    private List<CatalogModels.Manufacturer> cachedManufacturers = null;
    private final java.util.Map<Integer, List<CatalogModels.Wing>> cachedWingsByManufacturer = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_table);

        appSettings = new AppSettings(this);

        // Настройка Toolbar с кнопкой назад
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Таблица данных");
        }

        tableData = findViewById(R.id.tableData);
        btnLoadCsv = findViewById(R.id.btnLoadCsv);
        tvFileName = findViewById(R.id.tvFileName);
        tvMinMaxDifference = findViewById(R.id.tvMinMaxDifference);

        btnLoadCsv.setOnClickListener(v -> showSourceDialog());

        // Проверяем, есть ли уже загруженные данные
        displayExistingData();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ==================== ВЫБОР ИСТОЧНИКА ====================

    /**
     * Диалог выбора источника данных: из файла или из каталога.
     */
    private void showSourceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Источник данных");
        builder.setItems(new CharSequence[]{"Из файла", "Из каталога"},
                (dialog, which) -> {
                    if (which == 0) {
                        loadCsvFile();
                    } else {
                        startCatalogLoading();
                    }
                });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    // ==================== ЗАГРУЗКА ИЗ ФАЙЛА ====================

    private void loadCsvFile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_PERMISSION_READ_EXTERNAL_STORAGE);
                return;
            }
        }

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        String[] mimeTypes = {
                "text/csv",
                "text/plain",
                "text/comma-separated-values",
                "application/csv",
                "application/excel",
                "application/vnd.ms-excel",
                "application/vnd.msexcel"
        };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        }

        try {
            startActivityForResult(Intent.createChooser(intent, "Выберите CSV файл"), REQUEST_CODE_PICK_CSV);
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка открытия файлового менеджера: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadCsvFile();
            } else {
                Toast.makeText(this, "Для загрузки файла требуется разрешение на чтение хранилища", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_CSV && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                String fileName = getFileName(uri);

                if (!isValidCsvFile(fileName)) {
                    Toast.makeText(this, "Пожалуйста, выберите файл с расширением .csv", Toast.LENGTH_LONG).show();
                    return;
                }

                Toast.makeText(this, "Загрузка файла: " + fileName, Toast.LENGTH_SHORT).show();
                parseCsvFile(uri, fileName);
            }
        }
    }

    private String getFileName(Uri uri) {
        String fileName = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (fileName == null) {
            fileName = uri.getPath();
            if (fileName != null) {
                int lastSlash = fileName.lastIndexOf('/');
                if (lastSlash != -1) {
                    fileName = fileName.substring(lastSlash + 1);
                }
            }
        }
        return fileName;
    }

    private boolean isValidCsvFile(String fileName) {
        if (fileName == null) return false;
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".csv") ||
                lowerName.endsWith(".txt") ||
                lowerName.endsWith(".tsv") ||
                lowerName.endsWith(".dat");
    }

    private void parseCsvFile(Uri uri, String fileName) {
        List<String[]> rows = new ArrayList<>();
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                String[] row = parseCsvLine(line);
                rows.add(row);
            }

            if (rows.isEmpty()) {
                Toast.makeText(this, "Файл пуст или не содержит данных", Toast.LENGTH_SHORT).show();
                DataManager.getInstance().clearCsvData();
                showEmptyTable();
                return;
            }

            // Сохраняем данные в DataManager
            DataManager.getInstance().setCsvData(rows, fileName, uri);

            // Обновляем имя файла в UI
            if (tvFileName != null) {
                tvFileName.setText("📄 " + fileName);
                tvFileName.setVisibility(View.VISIBLE);
            }

            // Отображаем таблицу
            displayTable(rows);

            Toast.makeText(this, "Загружено " + rows.size() + " записей из " + fileName, Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка чтения файла: " + e.getMessage(), Toast.LENGTH_LONG).show();
            DataManager.getInstance().clearCsvData();
            showEmptyTable();
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char separator = detectSeparator(line);

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == separator && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());

        return values.toArray(new String[0]);
    }

    private char detectSeparator(String line) {
        int commaCount = 0;
        int semicolonCount = 0;
        int tabCount = 0;
        int spaceCount = 0;
        int pipeCount = 0;
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes) {
                if (c == ',') commaCount++;
                else if (c == ';') semicolonCount++;
                else if (c == '\t') tabCount++;
                else if (c == ' ') spaceCount++;
                else if (c == '|') pipeCount++;
            }
        }

        int maxCount = Math.max(Math.max(Math.max(Math.max(commaCount, semicolonCount),
                Math.max(tabCount, spaceCount)), pipeCount), 0);

        if (maxCount == 0) {
            return ',';
        }

        if (commaCount == maxCount) return ',';
        if (semicolonCount == maxCount) return ';';
        if (tabCount == maxCount) return '\t';
        if (spaceCount == maxCount) return ' ';
        if (pipeCount == maxCount) return '|';

        return ',';
    }

    // ==================== ЗАГРУЗКА ИЗ КАТАЛОГА ====================

    private void startCatalogLoading() {
        if (cachedManufacturers != null) {
            showManufacturerDialog(cachedManufacturers);
            return;
        }

        ProgressDialog progress = createProgressDialog("Загрузка производителей…");
        progress.show();

        new Thread(() -> {
            try {
                String baseUrl = appSettings.getCatalogBaseUrl();
                List<CatalogModels.Manufacturer> manufacturers =
                        catalogApiClient.fetchManufacturers(baseUrl);
                handler.post(() -> {
                    progress.dismiss();
                    if (manufacturers.isEmpty()) {
                        Toast.makeText(DataTableActivity.this,
                                "Не могу загрузить данные", Toast.LENGTH_LONG).show();
                        return;
                    }
                    cachedManufacturers = manufacturers;
                    showManufacturerDialog(manufacturers);
                });
            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> {
                    progress.dismiss();
                    Toast.makeText(DataTableActivity.this,
                            "Не могу загрузить данные", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showManufacturerDialog(List<CatalogModels.Manufacturer> manufacturers) {
        if (manufacturers == null || manufacturers.isEmpty()) {
            Toast.makeText(this, "Не могу загрузить данные", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[manufacturers.size()];
        for (int i = 0; i < manufacturers.size(); i++) {
            names[i] = manufacturers.get(i).name != null
                    ? manufacturers.get(i).name
                    : ("Производитель #" + manufacturers.get(i).id);
        }

        new AlertDialog.Builder(this)
                .setTitle("Выберите производителя")
                .setItems(names, (dialog, which) -> {
                    CatalogModels.Manufacturer selected = manufacturers.get(which);
                    startWingLoading(selected);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void startWingLoading(CatalogModels.Manufacturer manufacturer) {
        if (manufacturer == null) {
            return;
        }
        List<CatalogModels.Wing> cached = cachedWingsByManufacturer.get(manufacturer.id);
        if (cached != null) {
            showWingDialog(manufacturer, cached);
            return;
        }

        ProgressDialog progress = createProgressDialog("Загрузка крыльев…");
        progress.show();

        new Thread(() -> {
            try {
                String baseUrl = appSettings.getCatalogBaseUrl();
                List<CatalogModels.Wing> wings =
                        catalogApiClient.fetchWings(baseUrl, manufacturer.id);
                handler.post(() -> {
                    progress.dismiss();
                    if (wings.isEmpty()) {
                        Toast.makeText(DataTableActivity.this,
                                "Не могу загрузить данные", Toast.LENGTH_LONG).show();
                        return;
                    }
                    cachedWingsByManufacturer.put(manufacturer.id, wings);
                    showWingDialog(manufacturer, wings);
                });
            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> {
                    progress.dismiss();
                    Toast.makeText(DataTableActivity.this,
                            "Не могу загрузить данные", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showWingDialog(CatalogModels.Manufacturer manufacturer, List<CatalogModels.Wing> wings) {
        if (wings == null || wings.isEmpty()) {
            Toast.makeText(this, "Не могу загрузить данные", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[wings.size()];
        for (int i = 0; i < wings.size(); i++) {
            CatalogModels.Wing w = wings.get(i);
            String display = w.full_name;
            if (display == null || display.isEmpty()) {
                display = (w.model != null ? w.model : "Крыло") +
                        (w.size != null ? " " + w.size : "");
            }
            names[i] = display;
        }

        new AlertDialog.Builder(this)
                .setTitle("Выберите крыло")
                .setItems(names, (dialog, which) -> {
                    CatalogModels.Wing selected = wings.get(which);
                    startLinesLoading(selected);
                })
                .setPositiveButton("Назад", (dialog, which) -> showManufacturerDialog(cachedManufacturers))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void startLinesLoading(CatalogModels.Wing wing) {
        if (wing == null) {
            return;
        }

        ProgressDialog progress = createProgressDialog("Загрузка строп…");
        progress.show();

        new Thread(() -> {
            try {
                String baseUrl = appSettings.getCatalogBaseUrl();
                CatalogModels.LinesData linesData =
                        catalogApiClient.fetchLines(baseUrl, wing.id);
                CatalogApiClient.BuildResult buildResult =
                        catalogApiClient.buildCsvRows(linesData);
                handler.post(() -> {
                    progress.dismiss();
                    onCatalogLoaded(wing, buildResult);
                });
            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> {
                    progress.dismiss();
                    Toast.makeText(DataTableActivity.this,
                            "Не могу загрузить данные", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * Обрабатывает успешную загрузку каталога: сохраняет CSV в DataManager,
     * отображает таблицу, показывает итоговые сообщения.
     */
    private void onCatalogLoaded(CatalogModels.Wing wing, CatalogApiClient.BuildResult buildResult) {
        if (buildResult == null || buildResult.rows.isEmpty()) {
            Toast.makeText(this, "Не могу загрузить данные", Toast.LENGTH_LONG).show();
            return;
        }

        String fullName = wing.full_name;
        if (fullName == null || fullName.trim().isEmpty()) {
            fullName = (wing.model != null ? wing.model : "wing");
            if (wing.size != null && !wing.size.isEmpty()) {
                fullName += " " + wing.size;
            }
        }
        String fileName = fullName + ".csv";

        // Сохраняем данные в DataManager, без Uri (источник — каталог)
        DataManager.getInstance().setCsvData(buildResult.rows, fileName, null);

        if (tvFileName != null) {
            tvFileName.setText("📄 " + fileName);
            tvFileName.setVisibility(View.VISIBLE);
        }

        displayTable(buildResult.rows);

        Toast.makeText(this,
                "Загружено " + buildResult.rows.size() + " записей из " + fileName,
                Toast.LENGTH_LONG).show();

        if (buildResult.skippedNonLatinGroups > 0) {
            Toast.makeText(this,
                    "Пропущено " + buildResult.skippedNonLatinGroups
                            + " строп с нелатинским именем",
                    Toast.LENGTH_LONG).show();
        }
    }

    private ProgressDialog createProgressDialog(String message) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage(message);
        progress.setCancelable(false);
        progress.setCanceledOnTouchOutside(false);
        return progress;
    }

    // ==================== ОТОБРАЖЕНИЕ ТАБЛИЦЫ ====================

    private void displayExistingData() {
        DataManager dataManager = DataManager.getInstance();

        if (dataManager.isCsvLoaded()) {
            List<String[]> data = dataManager.getCsvData();
            String fileName = dataManager.getCurrentFileName();

            if (data != null && !data.isEmpty()) {
                if (tvFileName != null) {
                    tvFileName.setText("📄 " + fileName);
                    tvFileName.setVisibility(View.VISIBLE);
                }
                displayTable(data);
                return;
            }
        }

        if (tvFileName != null) {
            tvFileName.setText("📄 Данные не загружены");
            tvFileName.setVisibility(View.VISIBLE);
        }

        showEmptyTable();
    }

    private void showEmptyTable() {
        tableData.removeAllViews();
        String[] emptyRow = {"Загрузите CSV файл для отображения данных"};
        addTableRow(emptyRow, false, null, null);
        updateMinMaxDifference(null, null);
    }

    private void updateMinMaxDifference(Double minValue, Double maxValue) {
        if (tvMinMaxDifference == null) {
            return;
        }

        if (minValue == null || maxValue == null) {
            tvMinMaxDifference.setText("");
            tvMinMaxDifference.setVisibility(View.GONE);
            return;
        }

        double difference = maxValue - minValue;
        String diffText = String.format("Разница между min и max: %.2f", difference);
        tvMinMaxDifference.setText(diffText);
        tvMinMaxDifference.setVisibility(View.VISIBLE);
    }

    private void displayTable(List<String[]> rows) {
        tableData.removeAllViews();

        if (rows.isEmpty()) {
            showEmptyTable();
            return;
        }

        int maxColumns = 0;
        for (String[] row : rows) {
            if (row.length > maxColumns) {
                maxColumns = row.length;
            }
        }

        if (maxColumns == 0) {
            showEmptyTable();
            return;
        }

        Double minValue = null;
        Double maxValue = null;

        for (String[] row : rows) {
            for (String cell : row) {
                if (cell == null || cell.trim().isEmpty()) {
                    continue;
                }
                try {
                    String numericPart = cell.trim().replace(',', '.');
                    if (numericPart.contains("/")) {
                        numericPart = numericPart.split("/")[0];
                    }
                    double value = Double.parseDouble(numericPart);
                    if (minValue == null || value < minValue) {
                        minValue = value;
                    }
                    if (maxValue == null || value > maxValue) {
                        maxValue = value;
                    }
                } catch (NumberFormatException e) {
                    // Игнорируем нечисловые значения
                }
            }
        }

        updateMinMaxDifference(minValue, maxValue);

        String[] headers = generateColumnHeaders(maxColumns);
        addTableRow(headers, true, null, null);

        for (String[] row : rows) {
            if (row.length < maxColumns) {
                String[] newRow = new String[maxColumns];
                System.arraycopy(row, 0, newRow, 0, row.length);
                for (int j = row.length; j < maxColumns; j++) {
                    newRow[j] = "";
                }
                row = newRow;
            }
            addTableRow(row, false, minValue, maxValue);
        }
    }

    private String[] generateColumnHeaders(int count) {
        String[] headers = new String[count];
        for (int i = 0; i < count; i++) {
            headers[i] = getColumnLetter(i);
        }
        return headers;
    }

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

    private void addTableRow(String[] columns, boolean isHeader, Double minValue, Double maxValue) {
        TableRow tableRow = new TableRow(this);
        tableRow.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));

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
                boolean isMin = false;
                boolean isMax = false;
                if (minValue != null && maxValue != null && column != null && !column.isEmpty()) {
                    try {
                        String numericPart = column.trim().replace(',', '.');
                        if (numericPart.contains("/")) {
                            numericPart = numericPart.split("/")[0];
                        }
                        double value = Double.parseDouble(numericPart);
                        if (Math.abs(value - minValue) < 0.0001) {
                            isMin = true;
                        }
                        if (Math.abs(value - maxValue) < 0.0001) {
                            isMax = true;
                        }
                    } catch (NumberFormatException e) {
                        // Игнорируем нечисловые значения
                    }
                }

                if (isMin && isMax) {
                    textView.setBackgroundColor(0xFF9C27B0);
                } else if (isMin) {
                    textView.setBackgroundColor(0xFFCCCCFF);
                } else if (isMax) {
                    textView.setBackgroundColor(0xFFFFCCCC);
                } else {
                    int position = tableData.getChildCount();
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

        tableData.addView(tableRow);
    }
}