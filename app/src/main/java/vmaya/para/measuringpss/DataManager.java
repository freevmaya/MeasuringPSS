// app/src/main/java/vmaya/para/measuringpss/DataManager.java
package vmaya.para.measuringpss;

import java.util.ArrayList;
import java.util.List;

public class DataManager {
    private static DataManager instance;
    private List<String[]> csvData = new ArrayList<>();
    private boolean isCsvLoaded = false;
    private String currentFileName = "";

    private DataManager() {}

    public static synchronized DataManager getInstance() {
        if (instance == null) {
            instance = new DataManager();
        }
        return instance;
    }

    public void setCsvData(List<String[]> data, String fileName) {
        this.csvData = new ArrayList<>(data);
        this.isCsvLoaded = true;
        this.currentFileName = fileName;
    }

    public List<String[]> getCsvData() {
        return csvData;
    }

    public boolean isCsvLoaded() {
        return isCsvLoaded;
    }

    public String getCurrentFileName() {
        return currentFileName;
    }

    public void clearCsvData() {
        csvData.clear();
        isCsvLoaded = false;
        currentFileName = "";
    }

    /**
     * Получить значение из CSV таблицы по индексам строки и колонки
     * @param rowIndex индекс строки (0 - первая строка данных, 1 - вторая и т.д.)
     * @param columnIndex индекс колонки (0 - колонка A, 1 - колонка B и т.д.)
     * @return значение ячейки или null, если данных нет
     */
    public String getCellValue(int rowIndex, int columnIndex) {
        if (!isCsvLoaded || csvData.isEmpty()) {
            return null;
        }

        // Проверяем, что строка существует
        if (rowIndex < 0 || rowIndex >= csvData.size()) {
            return null;
        }

        String[] row = csvData.get(rowIndex);

        // Проверяем, что колонка существует
        if (columnIndex < 0 || columnIndex >= row.length) {
            return null;
        }

        String value = row[columnIndex];
        return (value != null && !value.trim().isEmpty()) ? value.trim() : null;
    }

    /**
     * Получить числовое значение из CSV таблицы
     * @param rowIndex индекс строки
     * @param columnIndex индекс колонки
     * @return число или null, если значение не число
     */
    public Double getNumericCellValue(int rowIndex, int columnIndex) {
        String value = getCellValue(rowIndex, columnIndex);
        if (value == null) {
            return null;
        }

        try {
            // Заменяем запятую на точку для парсинга
            value = value.replace(',', '.');
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Получить количество строк в CSV
     */
    public int getRowCount() {
        return isCsvLoaded ? csvData.size() : 0;
    }

    /**
     * Получить количество колонок в CSV (максимальное)
     */
    public int getColumnCount() {
        if (!isCsvLoaded || csvData.isEmpty()) {
            return 0;
        }

        int maxColumns = 0;
        for (String[] row : csvData) {
            if (row.length > maxColumns) {
                maxColumns = row.length;
            }
        }
        return maxColumns;
    }
}