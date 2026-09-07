// app/src/main/java/vmaya/para/measuringpss/DifferencesFragment.java
package vmaya.para.measuringpss;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DifferencesFragment extends Fragment {

    private TableLayout tableDifferences;
    private TextView tvTotalDifferencesGroups;
    private TextView tvTotalDifferencesRecords;
    private ScrollView differencesScrollView;
    private Button btnExportDifferencesCsv;

    private final List<DifferenceStat> differenceStats = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_differences_tab, container, false);

        tableDifferences = view.findViewById(R.id.tableDifferences);
        tvTotalDifferencesGroups = view.findViewById(R.id.tvTotalDifferencesGroups);
        tvTotalDifferencesRecords = view.findViewById(R.id.tvTotalDifferencesRecords);
        differencesScrollView = view.findViewById(R.id.differencesScrollView);
        btnExportDifferencesCsv = view.findViewById(R.id.btnExportDifferencesCsv);

        btnExportDifferencesCsv.setOnClickListener(v -> exportDifferencesStatistics());

        calculateAndDisplayDifferences();

        return view;
    }

    private void calculateAndDisplayDifferences() {
        List<DataSample> samples = getDataSamplesFromManager();

        if (samples.isEmpty()) {
            Toast.makeText(getContext(), "Нет данных для анализа перепадов", Toast.LENGTH_SHORT).show();
            showEmptyState();
            return;
        }

        // Группируем данные по rowIndex + side (номер стропы + сторона)
        // Для каждой стропы храним: colIndex -> список значений
        Map<String, Map<Integer, List<Double>>> measuredData = new HashMap<>();
        Map<String, Map<Integer, List<Double>>> targetData = new HashMap<>();

        for (DataSample sample : samples) {
            if (sample.getTargetWeight() == 0 || sample.getDistance() == 0) {
                continue;
            }

            // Ключ: rowIndex + side (например: "3l", "4r")
            String key = sample.getRowIndex() + sample.getSide();
            int colIndex = sample.getCol();

            measuredData.computeIfAbsent(key, k -> new HashMap<>())
                    .computeIfAbsent(colIndex, k -> new ArrayList<>())
                    .add((double) sample.getDistance());

            targetData.computeIfAbsent(key, k -> new HashMap<>())
                    .computeIfAbsent(colIndex, k -> new ArrayList<>())
                    .add((double) sample.getTargetWeight());
        }

        if (measuredData.isEmpty() || targetData.isEmpty()) {
            Toast.makeText(getContext(), "Нет данных для расчета перепадов", Toast.LENGTH_LONG).show();
            showEmptyState();
            return;
        }

        // Сортируем ключи (номера строп + стороны)
        List<String> sortedKeys = new ArrayList<>(measuredData.keySet());
        Collections.sort(sortedKeys, (a, b) -> {
            try {
                int numA = Integer.parseInt(a.replaceAll("[^0-9]", ""));
                int numB = Integer.parseInt(b.replaceAll("[^0-9]", ""));
                if (numA != numB) {
                    return Integer.compare(numA, numB);
                }
                String sideA = a.replaceAll("[0-9]", "");
                String sideB = b.replaceAll("[0-9]", "");
                return sideA.compareTo(sideB);
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });

        // Определяем пары колонок для перепадов: (0,1)=AB, (1,2)=BC, (2,3)=CD, (3,4)=DE
        int[][] colPairs = {{0, 1}, {1, 2}, {2, 3}, {3, 4}};
        String[] pairNames = {"ab", "bc", "cd", "de"};

        differenceStats.clear();
        int totalRecords = 0;

        for (String key : sortedKeys) {
            Map<Integer, List<Double>> measuredCols = measuredData.get(key);
            Map<Integer, List<Double>> targetCols = targetData.get(key);

            // Вычисляем средние значения для каждой колонки
            Map<Integer, Double> measuredAvg = new HashMap<>();
            Map<Integer, Double> targetAvg = new HashMap<>();

            for (int col : measuredCols.keySet()) {
                measuredAvg.put(col, calculateAverage(measuredCols.get(col)));
            }
            for (int col : targetCols.keySet()) {
                targetAvg.put(col, calculateAverage(targetCols.get(col)));
            }

            // Парсим номер стропы и сторону из ключа
            int rowIndex;
            String side;
            try {
                rowIndex = Integer.parseInt(key.replaceAll("[^0-9]", ""));
                side = key.replaceAll("[0-9]", "");
            } catch (NumberFormatException e) {
                continue;
            }

            DifferenceStat diffStat = new DifferenceStat(rowIndex);
            diffStat.setSide(side);

            // Рассчитываем перепады для каждой пары колонок
            for (int i = 0; i < colPairs.length; i++) {
                int col1 = colPairs[i][0];
                int col2 = colPairs[i][1];
                String pairName = pairNames[i];

                // Проверяем, есть ли данные для обеих колонок
                if (!measuredAvg.containsKey(col1) || !measuredAvg.containsKey(col2) ||
                        !targetAvg.containsKey(col1) || !targetAvg.containsKey(col2)) {
                    continue;
                }

                // Требуемый перепад (из таблицы)
                double targetDiff = targetAvg.get(col1) - targetAvg.get(col2);

                // Измеренный перепад
                double measuredDiff = measuredAvg.get(col1) - measuredAvg.get(col2);

                // Расхождение
                double deviation = measuredDiff - targetDiff;

                diffStat.addPair(pairName, targetDiff, measuredDiff, deviation);
            }

            // Добавляем только если есть хотя бы один перепад
            if (diffStat.hasData()) {
                differenceStats.add(diffStat);
                totalRecords++;
            }
        }

        if (differenceStats.isEmpty()) {
            String message = "Недостаточно данных для расчета перепадов.\n" +
                    "Требуется минимум 2 колонки в CSV (например, A и B).";
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            showEmptyStateWithMessage(message);
            return;
        }

        // Отображаем таблицу перепадов
        displayDifferencesTable();

        // Обновляем общую информацию
        tvTotalDifferencesGroups.setText("Всего строп: " + differenceStats.size());
        tvTotalDifferencesRecords.setText("Всего записей: " + totalRecords);
    }

    private void displayDifferencesTable() {
        tableDifferences.removeAllViews();

        if (differenceStats.isEmpty()) {
            String[] emptyRow = {"Нет данных"};
            addTableRow(emptyRow, false);
            return;
        }

        // Определяем, какие перепады есть в данных
        Set<String> availablePairs = new HashSet<>();
        for (DifferenceStat stat : differenceStats) {
            availablePairs.addAll(stat.pairDataMap.keySet());
        }

        // Сортируем перепады: ab, bc, cd, de
        List<String> sortedPairs = new ArrayList<>(availablePairs);
        Collections.sort(sortedPairs);

        // Формируем заголовки
        List<String> headers = new ArrayList<>();
        headers.add("Стропа");
        headers.addAll(sortedPairs);

        // Отображаем заголовки
        addTableRow(headers.toArray(new String[0]), true);

        // Получаем порог разницы из настроек
        AppSettings appSettings = new AppSettings(requireContext());
        int diffThreshold = appSettings.getDiffThreshold();

        // Отображаем данные для каждой стропы
        for (DifferenceStat stat : differenceStats) {
            // Формируем индекс стропы: номер + сторона (например, "3l", "4r")
            String index = (stat.rowIndex + 1) + stat.getSide();
            List<String> rowData = new ArrayList<>();
            rowData.add(index);

            // Добавляем значения расхождений для каждого перепада
            for (String pair : sortedPairs) {
                DifferenceStat.PairData pairData = stat.getPairData(pair);
                if (pairData != null) {
                    // Показываем только расхождение (deviation)
                    String deviation = String.format(Locale.US, "%.0f", pairData.deviation);
                    rowData.add(deviation);
                } else {
                    rowData.add("-");
                }
            }

            // Находим максимальное расхождение для подсветки
            double maxDeviation = stat.getMaxDeviation();
            addTableRow(rowData.toArray(new String[0]), false, maxDeviation, diffThreshold);
        }

        differencesScrollView.post(() -> {
            differencesScrollView.fullScroll(View.FOCUS_DOWN);
        });
    }

    private void showEmptyState() {
        tableDifferences.removeAllViews();
        String[] emptyRow = {"Нет данных для отображения"};
        addTableRow(emptyRow, false);
        tvTotalDifferencesGroups.setText("Всего строп: 0");
        tvTotalDifferencesRecords.setText("Всего записей: 0");
    }

    private void showEmptyStateWithMessage(String message) {
        tableDifferences.removeAllViews();
        String[] emptyRow = {message};
        addTableRow(emptyRow, false);
        tvTotalDifferencesGroups.setText("Всего строп: 0");
        tvTotalDifferencesRecords.setText("Всего записей: 0");
    }

    private List<DataSample> getDataSamplesFromManager() {
        return StatisticsDataHolder.getInstance().getDataSamples();
    }

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

    private void addTableRow(String[] columns, boolean isHeader) {
        addTableRow(columns, isHeader, 0.0, 0);
    }

    private void addTableRow(String[] columns, boolean isHeader, double diffValue, int diffThreshold) {
        TableRow tableRow = new TableRow(requireContext());
        tableRow.setLayoutParams(new TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
        ));

        boolean highlightDiff = !isHeader && Math.abs(diffValue) > diffThreshold;

        for (int i = 0; i < columns.length; i++) {
            TextView textView = new TextView(requireContext());
            String column = columns[i] != null ? columns[i] : "";
            textView.setText(column);
            textView.setPadding(16, 12, 16, 12);
            textView.setMaxLines(1);
            textView.setEllipsize(android.text.TextUtils.TruncateAt.END);

            if (isHeader) {
                textView.setBackgroundColor(requireContext().getResources().getColor(android.R.color.holo_blue_dark));
                textView.setTextColor(requireContext().getResources().getColor(android.R.color.white));
                textView.setTextSize(16);
                textView.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                // Подсвечиваем все ячейки, если расхождение превышает порог
                if (highlightDiff) {
                    textView.setBackgroundColor(0xFFFFC0CB); // Светло-розовый
                } else {
                    int position = tableDifferences.getChildCount();
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
            View divider = new View(requireContext());
            divider.setLayoutParams(new TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    1
            ));
            divider.setBackgroundColor(0xFFE0E0E0);
            tableRow.addView(divider);
        }

        tableDifferences.addView(tableRow);
    }

    private void exportDifferencesStatistics() {
        if (differenceStats.isEmpty()) {
            Toast.makeText(getContext(), "Нет данных для экспорта перепадов", Toast.LENGTH_SHORT).show();
            return;
        }

        DataManager dataManager = DataManager.getInstance();
        String originalFileName = dataManager.getCurrentFileName();

        String newFileName;
        if (originalFileName != null && !originalFileName.isEmpty() && originalFileName.contains(".")) {
            int dotIndex = originalFileName.lastIndexOf('.');
            newFileName = originalFileName.substring(0, dotIndex) + "_differences.csv";
        } else {
            newFileName = "differences.csv";
        }

        StringBuilder csvContent = new StringBuilder();

        // Определяем, какие перепады есть в данных
        Set<String> availablePairs = new HashSet<>();
        for (DifferenceStat stat : differenceStats) {
            availablePairs.addAll(stat.pairDataMap.keySet());
        }

        List<String> sortedPairs = new ArrayList<>(availablePairs);
        Collections.sort(sortedPairs);

        // Заголовки
        csvContent.append("Стропа");
        for (String pair : sortedPairs) {
            csvContent.append(",").append(pair);
        }
        csvContent.append("\n");

        // Данные
        for (DifferenceStat stat : differenceStats) {
            String index = (stat.rowIndex + 1) + stat.getSide();
            csvContent.append(index);
            for (String pair : sortedPairs) {
                DifferenceStat.PairData pairData = stat.getPairData(pair);
                if (pairData != null) {
                    csvContent.append(",").append(String.format(Locale.US, "%.0f", pairData.deviation));
                } else {
                    csvContent.append(",");
                }
            }
            csvContent.append("\n");
        }

        boolean success = saveFile(newFileName, csvContent.toString());

        if (success) {
            Toast.makeText(getContext(), "Файл сохранен: " + newFileName, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), "Ошибка при сохранении файла.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Сохраняет файл в папку Downloads
     */
    private boolean saveFile(String fileName, String content) {
        ContentResolver resolver = requireContext().getContentResolver();
        ContentValues contentValues = new ContentValues();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        } else {
            try {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloadsDir, fileName);
                if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
                    return false;
                }
                try (FileOutputStream fos = new FileOutputStream(file)) {
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

    private static class DifferenceStat {
        int rowIndex;
        String side = "";
        Map<String, PairData> pairDataMap = new HashMap<>();

        DifferenceStat(int rowIndex) {
            this.rowIndex = rowIndex;
        }

        void setSide(String side) {
            this.side = side != null ? side : "";
        }

        String getSide() {
            return side;
        }

        void addPair(String pairName, double targetDiff, double measuredDiff, double deviation) {
            pairDataMap.put(pairName, new PairData(targetDiff, measuredDiff, deviation));
        }

        PairData getPairData(String pairName) {
            return pairDataMap.get(pairName);
        }

        boolean hasData() {
            return !pairDataMap.isEmpty();
        }

        double getMaxDeviation() {
            double maxDev = 0.0;
            for (PairData data : pairDataMap.values()) {
                if (Math.abs(data.deviation) > Math.abs(maxDev)) {
                    maxDev = data.deviation;
                }
            }
            return maxDev;
        }

        static class PairData {
            double targetDiff;
            double measuredDiff;
            double deviation;

            PairData(double targetDiff, double measuredDiff, double deviation) {
                this.targetDiff = targetDiff;
                this.measuredDiff = measuredDiff;
                this.deviation = deviation;
            }
        }
    }
}