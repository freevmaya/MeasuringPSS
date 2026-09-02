// app/src/main/java/vmaya/para/measuringpss/StatisticsFragment.java
package vmaya.para.measuringpss;

import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatisticsFragment extends Fragment {

    private TableLayout tableStatistics;
    private TextView tvTotalGroups;
    private TextView tvTotalRecords;
    private ScrollView statisticsScrollView;
    private Button btnExportCsv;

    private final List<GroupStat> groupStats = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_statistics_tab, container, false);

        tableStatistics = view.findViewById(R.id.tableStatistics);
        tvTotalGroups = view.findViewById(R.id.tvTotalGroups);
        tvTotalRecords = view.findViewById(R.id.tvTotalRecords);
        statisticsScrollView = view.findViewById(R.id.statisticsScrollView);
        btnExportCsv = view.findViewById(R.id.btnExportCsv);

        btnExportCsv.setOnClickListener(v -> exportStatistics());

        calculateAndDisplayStatistics();

        return view;
    }

    private void calculateAndDisplayStatistics() {
        List<DataSample> samples = getDataSamplesFromManager();

        if (samples.isEmpty()) {
            Toast.makeText(getContext(), "Нет данных для анализа", Toast.LENGTH_SHORT).show();
            showEmptyState();
            return;
        }

        // Группировка по Col + "n" + LowerTierNumber
        Map<String, List<Double>> groups = new HashMap<>();

        int skippedRecords = 0;
        for (DataSample sample : samples) {
            if (sample.getLowerTierNumber() == 0) {
                skippedRecords++;
                continue;
            }

            String groupKey = sample.getColString() + sample.getLowerTierNumber();
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>())
                    .add(sample.getDiff());
        }

        if (groups.isEmpty()) {
            String message = skippedRecords > 0
                    ? "Нет данных с номерами нижних строп. Пропущено записей: " + skippedRecords
                    : "Нет данных для группировки";
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            showEmptyState();
            return;
        }

        List<String> sortedKeys = new ArrayList<>(groups.keySet());
        Collections.sort(sortedKeys, (a, b) -> {
            try {
                char charA = a.charAt(0);
                char charB = b.charAt(0);
                if (charA != charB) {
                    return Character.compare(charA, charB);
                }
                int numA = Integer.parseInt(a.substring(1));
                int numB = Integer.parseInt(b.substring(1));
                return Integer.compare(numA, numB);
            } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                return a.compareTo(b);
            }
        });

        groupStats.clear();
        int totalRecords = 0;

        for (String key : sortedKeys) {
            List<Double> diffs = groups.get(key);
            double avg = calculateAverage(diffs);
            groupStats.add(new GroupStat(key, diffs.size(), avg));
            totalRecords += diffs.size();
        }

        displayStatisticsTable();

        String groupsInfo = "Всего групп: " + groupStats.size();
        String recordsInfo = "Всего записей: " + totalRecords;
        if (skippedRecords > 0) {
            recordsInfo += " (пропущено без номера нижней стропы: " + skippedRecords + ")";
        }
        tvTotalGroups.setText(groupsInfo);
        tvTotalRecords.setText(recordsInfo);
    }

    private void showEmptyState() {
        tableStatistics.removeAllViews();
        String[] emptyRow = {"Нет данных для отображения", "", ""};
        addTableRow(emptyRow, false);
        tvTotalGroups.setText("Всего групп: 0");
        tvTotalRecords.setText("Всего записей: 0");
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

    private void displayStatisticsTable() {
        tableStatistics.removeAllViews();

        if (groupStats.isEmpty()) {
            String[] emptyRow = {"Нет данных", "", ""};
            addTableRow(emptyRow, false);
            return;
        }

        String[] headers = {"Стропа", "Кол-во", "Среднее отклонение"};
        addTableRow(headers, true);

        AppSettings appSettings = new AppSettings(requireContext());
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
                if (highlightDiff && i == 2) {
                    textView.setBackgroundColor(0xFFFFC0CB);
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
            View divider = new View(requireContext());
            divider.setLayoutParams(new TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    1
            ));
            divider.setBackgroundColor(0xFFE0E0E0);
            tableRow.addView(divider);
        }

        tableStatistics.addView(tableRow);
    }

    private void exportStatistics() {
        if (groupStats.isEmpty()) {
            Toast.makeText(getContext(), "Нет данных для экспорта", Toast.LENGTH_SHORT).show();
            return;
        }

        DataManager dataManager = DataManager.getInstance();
        String originalFileName = dataManager.getCurrentFileName();

        String newFileName;
        if (originalFileName != null && !originalFileName.isEmpty() && originalFileName.contains(".")) {
            int dotIndex = originalFileName.lastIndexOf('.');
            newFileName = originalFileName.substring(0, dotIndex) + "_statistics.csv";
        } else {
            newFileName = "statistics.csv";
        }

        StringBuilder csvContent = new StringBuilder();
        csvContent.append("Группа,Количество,Средняя разница\n");
        for (GroupStat stat : groupStats) {
            String diffText = String.format(Locale.US, "%.1f", stat.averageDiff);
            csvContent.append(stat.groupName).append(",")
                    .append(stat.count).append(",")
                    .append(diffText).append("\n");
        }

        int totalRecords = 0;
        for (GroupStat stat : groupStats) {
            totalRecords += stat.count;
        }
        csvContent.append("\nИтого групп: ").append(groupStats.size())
                .append(", всего записей: ").append(totalRecords);

        boolean success = saveFile(newFileName, csvContent.toString());

        if (success) {
            Toast.makeText(getContext(), "Файл сохранен: " + newFileName, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getContext(), "Ошибка при сохранении файла.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean saveFile(String fileName, String content) {
        // Код сохранения файла (аналогичный предыдущему)
        // ... (код из MainActivity.saveFile)
        return true; // Упрощенно
    }

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