package vmaya.para.measuringpss;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatisticsActivity extends AppCompatActivity {

    private TableLayout tableStatistics;
    private TextView tvTotalGroups;
    private TextView tvTotalRecords;
    private ScrollView statisticsScrollView;
    private Button btnExportCsv;

    private final List<GroupStat> groupStats = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);

        // Настройка Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Статистика по группам");
        }

        tableStatistics = findViewById(R.id.tableStatistics);
        tvTotalGroups = findViewById(R.id.tvTotalGroups);
        tvTotalRecords = findViewById(R.id.tvTotalRecords);
        statisticsScrollView = findViewById(R.id.statisticsScrollView);
        btnExportCsv = findViewById(R.id.btnExportCsv);

        btnExportCsv.setOnClickListener(v -> exportStatistics());

        // Расчет и отображение статистики
        calculateAndDisplayStatistics();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Вычисляет статистику по группам и отображает таблицу
     */
    private void calculateAndDisplayStatistics() {
        List<DataSample> samples = getDataSamplesFromManager();

        if (samples.isEmpty()) {
            Toast.makeText(this, "Нет данных для анализа", Toast.LENGTH_SHORT).show();
            showEmptyState();
            return;
        }

        // Группировка по Col + "n" + LowerTierNumber
        Map<String, List<Double>> groups = new HashMap<>();

        int skippedRecords = 0;
        for (DataSample sample : samples) {
            // Пропускаем записи без номера нижней стропы
            if (sample.getLowerTierNumber() == 0) {
                skippedRecords++;
                continue;
            }

            String groupKey = sample.getColString() + "n" + sample.getLowerTierNumber();
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>())
                    .add(sample.getDiff());
        }

        if (groups.isEmpty()) {
            String message = skippedRecords > 0
                    ? "Нет данных с номерами нижних строп. Пропущено записей: " + skippedRecords
                    : "Нет данных для группировки";
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            showEmptyState();
            return;
        }

        // Сортируем группы: сначала по букве (A, B, C...), затем по номеру
        List<String> sortedKeys = new ArrayList<>(groups.keySet());
        Collections.sort(sortedKeys, (a, b) -> {
            try {
                // Сравниваем по букве
                char charA = a.charAt(0);
                char charB = b.charAt(0);
                if (charA != charB) {
                    return Character.compare(charA, charB);
                }
                // Сравниваем по номеру
                int numA = Integer.parseInt(a.substring(1));
                int numB = Integer.parseInt(b.substring(1));
                return Integer.compare(numA, numB);
            } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                // Если ключ имеет неожиданный формат, сравниваем как строки
                return a.compareTo(b);
            }
        });

        // Заполняем список статистики
        groupStats.clear();
        int totalRecords = 0;

        for (String key : sortedKeys) {
            List<Double> diffs = groups.get(key);
            double avg = calculateAverage(diffs);
            groupStats.add(new GroupStat(key, diffs.size(), avg));
            totalRecords += diffs.size();
        }

        // Отображаем таблицу
        displayStatisticsTable();

        // Обновляем общую информацию
        String groupsInfo = "Всего групп: " + groupStats.size();
        String recordsInfo = "Всего записей: " + totalRecords;
        if (skippedRecords > 0) {
            recordsInfo += " (пропущено без номера нижней стропы: " + skippedRecords + ")";
        }
        tvTotalGroups.setText(groupsInfo);
        tvTotalRecords.setText(recordsInfo);

        // Прокручиваем вниз (к последней записи)
        statisticsScrollView.post(() -> {
            statisticsScrollView.fullScroll(View.FOCUS_DOWN);
        });
    }

    /**
     * Показывает пустое состояние
     */
    private void showEmptyState() {
        tableStatistics.removeAllViews();
        String[] emptyRow = {"Нет данных для отображения", "", ""};
        addTableRow(emptyRow, false);
        tvTotalGroups.setText("Всего групп: 0");
        tvTotalRecords.setText("Всего записей: 0");
    }

    /**
     * Получает список DataSample из DataManager
     */
    private List<DataSample> getDataSamplesFromManager() {
        return StatisticsDataHolder.getInstance().getDataSamples();
    }

    /**
     * Вычисляет среднее значение списка
     */
    private double calculateAverage(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double val : values) {
            sum += val;
        }
        return sum / values.size();
    }

    /**
     * Отображает таблицу статистики
     */
    private void displayStatisticsTable() {
        tableStatistics.removeAllViews();

        if (groupStats.isEmpty()) {
            String[] emptyRow = {"Нет данных", "", ""};
            addTableRow(emptyRow, false);
            return;
        }

        // Заголовки
        String[] headers = {"Группа", "Количество", "Средняя diff"};
        addTableRow(headers, true);

        // Получаем порог разницы из настроек
        AppSettings appSettings = new AppSettings(this);
        int diffThreshold = appSettings.getDiffThreshold();

        for (GroupStat stat : groupStats) {
            String diffText = String.format(Locale.US, "%.1f", stat.averageDiff);
            String[] rowData = {
                    stat.groupName,
                    String.valueOf(stat.count),
                    diffText
            };
            addTableRow(rowData, false, stat.averageDiff, diffThreshold);
        }

        statisticsScrollView.post(() -> {
            statisticsScrollView.fullScroll(View.FOCUS_DOWN);
        });
    }

    /**
     * Добавляет строку в таблицу (без подсветки)
     */
    private void addTableRow(String[] columns, boolean isHeader) {
        addTableRow(columns, isHeader, 0.0, 0);
    }

    /**
     * Добавляет строку в таблицу с возможностью подсветки
     */
    private void addTableRow(String[] columns, boolean isHeader, double diffValue, int diffThreshold) {
        TableRow tableRow = new TableRow(this);
        tableRow.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));

        boolean highlightDiff = !isHeader && Math.abs(diffValue) > diffThreshold;

        for (int i = 0; i < columns.length; i++) {
            TextView textView = new TextView(this);
            String column = columns[i] != null ? columns[i] : "";
            textView.setText(column);
            textView.setPadding(16, 12, 16, 12);
            textView.setMaxLines(1);
            textView.setEllipsize(android.text.TextUtils.TruncateAt.END);

            if (isHeader) {
                textView.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_dark));
                textView.setTextColor(getResources().getColor(android.R.color.white));
                textView.setTextSize(16);
                textView.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                // Подсвечиваем ячейку с разницей, если она превышает порог
                if (highlightDiff && i == 2) {
                    textView.setBackgroundColor(0xFFFFC0CB); // Светло-розовый
                } else {
                    int position = tableStatistics.getChildCount();
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

        tableStatistics.addView(tableRow);
    }

    /**
     * Экспортирует статистику в CSV файл
     */
    private void exportStatistics() {
        if (groupStats.isEmpty()) {
            Toast.makeText(this, "Нет данных для экспорта", Toast.LENGTH_SHORT).show();
            return;
        }

        DataManager dataManager = DataManager.getInstance();
        String originalFileName = dataManager.getCurrentFileName();

        // Формируем имя файла
        String newFileName;
        if (originalFileName != null && !originalFileName.isEmpty() && originalFileName.contains(".")) {
            int dotIndex = originalFileName.lastIndexOf('.');
            newFileName = originalFileName.substring(0, dotIndex) + "_statistics.csv";
        } else {
            newFileName = "statistics.csv";
        }

        // Подготавливаем содержимое CSV
        StringBuilder csvContent = new StringBuilder();
        csvContent.append("Группа,Количество,Средняя разница\n");
        for (GroupStat stat : groupStats) {
            String diffText = String.format(Locale.US, "%.1f", stat.averageDiff);
            csvContent.append(stat.groupName).append(",")
                    .append(stat.count).append(",")
                    .append(diffText).append("\n");
        }

        // Добавляем итоговую строку
        int totalRecords = 0;
        for (GroupStat stat : groupStats) {
            totalRecords += stat.count;
        }
        csvContent.append("\nИтого групп: ").append(groupStats.size())
                .append(", всего записей: ").append(totalRecords);

        // Сохраняем файл
        boolean success = saveFile(newFileName, csvContent.toString());

        if (success) {
            Toast.makeText(this, "Файл сохранен: " + newFileName, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Ошибка при сохранении файла.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Сохраняет строку в файл с использованием MediaStore (Android 10+)
     */
    private boolean saveFile(String fileName, String content) {
        ContentResolver resolver = getContentResolver();
        ContentValues contentValues = new ContentValues();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        } else {
            // Для старых версий записываем в папку Downloads через файловую систему
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

    /**
     * Внутренний класс для хранения статистики группы
     */
    private static class GroupStat {
        String groupName;
        int count;
        double averageDiff;

        GroupStat(String groupName, int count, double averageDiff) {
            this.groupName = groupName;
            this.count = count;
            this.averageDiff = averageDiff;
        }
    }
}