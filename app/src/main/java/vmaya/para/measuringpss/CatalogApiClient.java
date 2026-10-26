// app/src/main/java/vmaya/para/measuringpss/CatalogApiClient.java
package vmaya.para.measuringpss;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Клиент для API каталога.
 * Все методы блокирующие — вызывать из фонового потока.
 */
public class CatalogApiClient {

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;

    private final Gson gson = new Gson();

    // ==================== HTTP ====================

    private String httpGet(String url) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL u = new URL(url);
            connection = (HttpURLConnection) u.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");

            int code = connection.getResponseCode();
            InputStream stream = (code >= 200 && code < 300)
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            if (stream == null) {
                throw new IOException("HTTP " + code);
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            return sb.toString();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // ==================== API методы ====================

    /**
     * GET /api/manufacturers
     */
    public List<CatalogModels.Manufacturer> fetchManufacturers(String baseUrl) throws IOException {
        String url = baseUrl + "/api/manufacturers";
        String body = httpGet(url);
        CatalogModels.ManufacturersResponse response;
        try {
            response = gson.fromJson(body, CatalogModels.ManufacturersResponse.class);
        } catch (JsonSyntaxException e) {
            throw new IOException("Некорректный JSON", e);
        }
        if (response == null || !response.success) {
            String err = (response != null && response.error != null) ? response.error : "Неизвестная ошибка";
            throw new IOException(err);
        }
        return response.data != null ? response.data : new ArrayList<>();
    }

    /**
     * GET /api/manufacturers/{id}/wings
     * Возвращает только крылья с catalog_match == true.
     */
    public List<CatalogModels.Wing> fetchWings(String baseUrl, int manufacturerId) throws IOException {
        String url = baseUrl + "/api/manufacturers/" + manufacturerId + "/wings";
        String body = httpGet(url);
        CatalogModels.WingsResponse response;
        try {
            response = gson.fromJson(body, CatalogModels.WingsResponse.class);
        } catch (JsonSyntaxException e) {
            throw new IOException("Некорректный JSON", e);
        }
        if (response == null || !response.success) {
            String err = (response != null && response.error != null) ? response.error : "Неизвестная ошибка";
            throw new IOException(err);
        }
        List<CatalogModels.Wing> result = new ArrayList<>();
        if (response.data != null && response.data.wings != null) {
            for (CatalogModels.Wing w : response.data.wings) {
                if (w != null && w.catalog_match) {
                    result.add(w);
                }
            }
        }
        return result;
    }

    /**
     * GET /api/wings/{id}/lines
     */
    public CatalogModels.LinesData fetchLines(String baseUrl, int wingId) throws IOException {
        String url = baseUrl + "/api/wings/" + wingId + "/lines";
        String body = httpGet(url);
        CatalogModels.LinesResponse response;
        try {
            response = gson.fromJson(body, CatalogModels.LinesResponse.class);
        } catch (JsonSyntaxException e) {
            throw new IOException("Некорректный JSON", e);
        }
        if (response == null || !response.success) {
            String err = (response != null && response.error != null) ? response.error : "Неизвестная ошибка";
            throw new IOException(err);
        }
        if (response.data == null) {
            throw new IOException("Пустой ответ");
        }
        return response.data;
    }

    // ==================== Построение CSV ====================

    /**
     * Результат построения CSV.
     */
    public static class BuildResult {
        public List<String[]> rows = new ArrayList<>();
        public int skippedNonLatinGroups = 0;
        public int skippedEmptyUpperGroups = 0;
        public int skippedBrakeGroups = 0;
        public int totalDataGroups = 0;
    }

    /**
     * Строит CSV из lower_lines по правилам:
     *  - is_brake == true   -> пропускаем
     *  - нет upper_lines    -> пропускаем
     *  - первая буква name не A-Z -> пропускаем, считаем в skippedNonLatinGroups
     *  - колонка = индекс первой буквы (A=0..Z=25)
     *  - группировка по полному name
     *  - порядок групп в колонке = порядок появления в JSON
     *  - внутри группы — сортировка upper_lines по sort_order
     *  - ячейка = "<int length_mm>/<число из name>"
     */
    public BuildResult buildCsvRows(CatalogModels.LinesData data) {
        BuildResult result = new BuildResult();
        if (data == null || data.lower_lines == null) {
            return result;
        }

        // Карта: columnIndex -> список групп (в порядке появления)
        Map<Integer, List<Group>> columnGroups = new LinkedHashMap<>();

        // Чтобы группировка по полному name работала даже при разбросанных записях,
        // сохраняем группы в порядке первого появления.
        Map<String, Group> groupsByName = new LinkedHashMap<>();
        // Для каждой группы запомним columnIndex, чтобы потом разложить по колонкам
        Map<String, Integer> groupColumnIndex = new LinkedHashMap<>();

        for (CatalogModels.LowerLine ll : data.lower_lines) {
            if (ll == null) {
                continue;
            }
            if (ll.is_brake) {
                result.skippedBrakeGroups++;
                continue;
            }
            if (ll.upper_lines == null || ll.upper_lines.isEmpty()) {
                result.skippedEmptyUpperGroups++;
                continue;
            }
            String name = ll.name != null ? ll.name.trim() : "";
            if (name.isEmpty()) {
                result.skippedNonLatinGroups++;
                continue;
            }
            char first = Character.toUpperCase(name.charAt(0));
            if (first < 'A' || first > 'Z') {
                result.skippedNonLatinGroups++;
                continue;
            }
            int columnIndex = first - 'A';

            int number = extractNumber(name);
            if (number < 0) {
                // Если в имени нет цифр — считаем это некорректным, пропускаем
                result.skippedNonLatinGroups++;
                continue;
            }

            Group group = groupsByName.get(name);
            if (group == null) {
                group = new Group();
                group.name = name;
                group.number = number;
                group.columnIndex = columnIndex;
                group.upperLines = new ArrayList<>(ll.upper_lines);
                groupsByName.put(name, group);
                groupColumnIndex.put(name, columnIndex);
            } else {
                // Дополняем upper_lines (если встретили ту же группу повторно)
                if (group.upperLines == null) {
                    group.upperLines = new ArrayList<>();
                }
                group.upperLines.addAll(ll.upper_lines);
            }
        }

        // Сортируем upper_lines в каждой группе по sort_order
        for (Group group : groupsByName.values()) {
            if (group.upperLines != null) {
                Collections.sort(group.upperLines, new Comparator<CatalogModels.UpperLine>() {
                    @Override
                    public int compare(CatalogModels.UpperLine a, CatalogModels.UpperLine b) {
                        int sa = a != null ? a.sort_order : 0;
                        int sb = b != null ? b.sort_order : 0;
                        return Integer.compare(sa, sb);
                    }
                });
            }
        }

        // Раскладываем группы по колонкам в порядке появления
        for (Group group : groupsByName.values()) {
            List<Group> list = columnGroups.get(group.columnIndex);
            if (list == null) {
                list = new ArrayList<>();
                columnGroups.put(group.columnIndex, list);
            }
            list.add(group);
        }

        if (columnGroups.isEmpty()) {
            return result;
        }

        // Определяем максимальную колонку (для количества столбцов)
        int maxColumn = 0;
        for (Integer col : columnGroups.keySet()) {
            if (col > maxColumn) {
                maxColumn = col;
            }
        }
        int columnCount = maxColumn + 1;

        // Формируем строки: для каждой колонки — последовательность ячеек (по группам),
        // затем дополняем пустыми до максимума.
        List<List<String>> columnCells = new ArrayList<>();
        for (int col = 0; col < columnCount; col++) {
            List<String> cells = new ArrayList<>();
            List<Group> groups = columnGroups.get(col);
            if (groups != null) {
                for (Group group : groups) {
                    for (CatalogModels.UpperLine upper : group.upperLines) {
                        if (upper == null) {
                            continue;
                        }
                        int length = upper.length_mm != null
                                ? (int) Math.round(upper.length_mm)
                                : 0;
                        cells.add(length + "/" + group.number);
                        result.totalDataGroups++;
                    }
                }
            }
            columnCells.add(cells);
        }

        int maxRows = 0;
        for (List<String> cells : columnCells) {
            if (cells.size() > maxRows) {
                maxRows = cells.size();
            }
        }

        for (int r = 0; r < maxRows; r++) {
            String[] row = new String[columnCount];
            for (int c = 0; c < columnCount; c++) {
                List<String> cells = columnCells.get(c);
                row[c] = (r < cells.size()) ? cells.get(r) : "";
            }
            result.rows.add(row);
        }

        return result;
    }

    /**
     * Возвращает число из имени, склеивая все цифры подряд.
     * AR1 -> 1, BR3 -> 3, CR1 -> 1, CR4 -> 4, A12 -> 12.
     * Если цифр нет — возвращает -1.
     */
    private int extractNumber(String name) {
        if (name == null) {
            return -1;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        if (digits.length() == 0) {
            return -1;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static class Group {
        String name;
        int number;
        int columnIndex;
        List<CatalogModels.UpperLine> upperLines = new ArrayList<>();
    }
}