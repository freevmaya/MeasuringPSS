// app/src/main/java/vmaya/para/measuringpss/DataTableActivity.java
package vmaya.para.measuringpss;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_table);

        tableData = findViewById(R.id.tableData);
        btnLoadCsv = findViewById(R.id.btnLoadCsv);

        btnLoadCsv.setOnClickListener(v -> loadCsvFile());
    }

    private void loadCsvFile() {
        // Проверяем разрешения для Android 10 и ниже
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_PERMISSION_READ_EXTERNAL_STORAGE);
                return;
            }
        }

        // Открываем файловый менеджер для выбора CSV файла
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Добавляем MIME типы для CSV
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

        // Для Android 4.4+ используем ACTION_OPEN_DOCUMENT
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
                parseCsvFile(uri);
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

    private void parseCsvFile(Uri uri) {
        List<String[]> rows = new ArrayList<>();
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String line;
            while ((line = reader.readLine()) != null) {
                // Пропускаем пустые строки
                if (line.trim().isEmpty()) {
                    continue;
                }

                // Разделяем строку по запятой, учитывая возможные кавычки
                String[] row = parseCsvLine(line);
                rows.add(row);
            }

            if (rows.isEmpty()) {
                Toast.makeText(this, "Файл пуст или не содержит данных", Toast.LENGTH_SHORT).show();
                return;
            }

            displayTable(rows);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка чтения файла: " + e.getMessage(), Toast.LENGTH_LONG).show();
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

        // Находим максимальное количество
        int maxCount = Math.max(Math.max(Math.max(Math.max(commaCount, semicolonCount),
                Math.max(tabCount, spaceCount)), pipeCount), 0);

        // Если все счетчики равны 0, возвращаем запятую как разделитель по умолчанию
        if (maxCount == 0) {
            return ',';
        }

        // Возвращаем разделитель с максимальным количеством вхождений
        if (commaCount == maxCount) return ',';
        if (semicolonCount == maxCount) return ';';
        if (tabCount == maxCount) return '\t';
        if (spaceCount == maxCount) return ' ';
        if (pipeCount == maxCount) return '|';

        // Если ничего не подошло, возвращаем запятую по умолчанию
        return ',';
    }

    private void displayTable(List<String[]> rows) {
        tableData.removeAllViews();

        if (rows.isEmpty()) {
            Toast.makeText(this, "Нет данных для отображения", Toast.LENGTH_SHORT).show();
            return;
        }

        // Определяем максимальное количество колонок
        int maxColumns = 0;
        for (String[] row : rows) {
            if (row.length > maxColumns) {
                maxColumns = row.length;
            }
        }

        if (maxColumns == 0) {
            Toast.makeText(this, "Нет данных для отображения", Toast.LENGTH_SHORT).show();
            return;
        }

        // Создаем заголовки с буквами (A, B, C, D, E, ...)
        String[] headers = generateColumnHeaders(maxColumns);
        addTableRow(headers, true);

        // Добавляем строки данных (все строки из файла, включая первую)
        for (String[] row : rows) {
            if (row.length < maxColumns) {
                String[] newRow = new String[maxColumns];
                System.arraycopy(row, 0, newRow, 0, row.length);
                for (int j = row.length; j < maxColumns; j++) {
                    newRow[j] = "";
                }
                row = newRow;
            }
            addTableRow(row, false);
        }

        Toast.makeText(this, "Загружено " + rows.size() + " записей", Toast.LENGTH_SHORT).show();
    }

    /**
     * Генерирует заголовки колонок в формате A, B, C, D, E, ...
     * @param count Количество колонок
     * @return Массив заголовков
     */
    private String[] generateColumnHeaders(int count) {
        String[] headers = new String[count];
        for (int i = 0; i < count; i++) {
            headers[i] = getColumnLetter(i);
        }
        return headers;
    }

    /**
     * Преобразует номер колонки в буквенное обозначение (0 -> A, 25 -> Z, 26 -> AA, 27 -> AB, ...)
     * @param index Индекс колонки (начиная с 0)
     * @return Буквенное обозначение колонки
     */
    private String getColumnLetter(int index) {
        StringBuilder sb = new StringBuilder();
        index++; // Сдвигаем, чтобы 0 -> A, 1 -> B, ...

        while (index > 0) {
            index--;
            char letter = (char) ('A' + (index % 26));
            sb.insert(0, letter);
            index = index / 26;
        }

        return sb.toString();
    }

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

            // Настройка ширины текста - автоматическое обрезание длинного текста
            textView.setMaxLines(1);
            textView.setEllipsize(android.text.TextUtils.TruncateAt.END);

            if (isHeader) {
                // Заголовки: темный фон, белый текст, жирный шрифт
                textView.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
                textView.setTextColor(getResources().getColor(android.R.color.white));
                textView.setTextSize(16);
                textView.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                // Строки данных: чередование цветов для лучшей читаемости
                int position = tableData.getChildCount();
                if (position % 2 == 1) {
                    textView.setBackgroundColor(0xFFF5F5F5); // Светло-серый
                } else {
                    textView.setBackgroundColor(0xFFFFFFFF); // Белый
                }
                // Убираем черную полосу между строками
                // Не добавляем никаких разделителей
            }

            tableRow.addView(textView);
        }

        // Добавляем тонкую серую линию под каждой строкой для разделения (опционально)
        if (!isHeader) {
            View divider = new View(this);
            divider.setLayoutParams(new TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    1 // Высота 1px
            ));
            divider.setBackgroundColor(0xFFE0E0E0); // Светло-серый цвет
            tableRow.addView(divider);
        }

        tableData.addView(tableRow);
    }
}