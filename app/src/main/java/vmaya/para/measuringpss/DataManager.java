// app/src/main/java/vmaya/para/measuringpss/DataManager.java
package vmaya.para.measuringpss;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

public class DataManager {
    private static DataManager instance;
    private List<String[]> csvData = new ArrayList<>();
    private boolean isCsvLoaded = false;
    private String currentFileName = "";
    private Uri currentFileUri = null; // Добавлено поле для хранения URI

    private DataManager() {}

    public static synchronized DataManager getInstance() {
        if (instance == null) {
            instance = new DataManager();
        }
        return instance;
    }

    // Обновленный метод с Uri
    public void setCsvData(List<String[]> data, String fileName, Uri fileUri) {
        this.csvData = new ArrayList<>(data);
        this.isCsvLoaded = true;
        this.currentFileName = fileName;
        this.currentFileUri = fileUri;
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

    public Uri getCurrentFileUri() {
        return currentFileUri;
    }

    public void clearCsvData() {
        csvData.clear();
        isCsvLoaded = false;
        currentFileName = "";
        currentFileUri = null; // Очищаем URI
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

    /**
     * Парсит значение ячейки CSV, чтобы получить целевой вес (первое число).
     * Поддерживает форматы:
     * - "число" (например, "6340")
     * - "число/номер" (например, "6340/1")
     * @param cellValue сырое значение из ячейки
     * @return первое число как Double, или null, если парсинг не удался
     */
    public Double parseTargetWeightFromCell(String cellValue) {
        if (cellValue == null || cellValue.trim().isEmpty()) {
            return null;
        }

        String trimmedValue = cellValue.trim();
        // Разделяем по '/'
        String[] parts = trimmedValue.split("/");
        String numericPart = parts[0]; // Берем первую часть

        try {
            // Заменяем запятую на точку и парсим как Double
            numericPart = numericPart.replace(',', '.');
            return Double.parseDouble(numericPart);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}