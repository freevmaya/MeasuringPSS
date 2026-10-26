// app/src/main/java/vmaya/para/measuringpss/CatalogModels.java
package vmaya.para.measuringpss;

import java.util.List;

/**
 * POJO-модели для ответов API каталога.
 * Формат ответов см. в Список запросов API.txt.
 */
public class CatalogModels {

    // ==================== /api/manufacturers ====================

    public static class ManufacturersResponse {
        public boolean success;
        public String error;
        public List<Manufacturer> data;
    }

    public static class Manufacturer {
        public int id;
        public String name;
        public String website;
        public String layout_method;
        public Integer wings_count;
    }

    // ==================== /api/manufacturers/{id}/wings ====================

    public static class WingsResponse {
        public boolean success;
        public String error;
        public WingsData data;
    }

    public static class WingsData {
        public Manufacturer manufacturer;
        public List<Wing> wings;
    }

    public static class Wing {
        public int id;
        public int source_id;
        public String model;
        public Integer model_year;
        public String size;
        public String full_name;
        public String source_url;
        public boolean catalog_match;
        public String parsed_at;
    }

    // ==================== /api/wings/{id}/lines ====================

    public static class LinesResponse {
        public boolean success;
        public String error;
        public LinesData data;
    }

    public static class LinesData {
        public Wing wing;
        public Manufacturer manufacturer;
        public List<FileUsed> files_used;
        public List<LowerLine> lower_lines;
    }

    public static class FileUsed {
        public int id;
        public String name;
        public String url;
        public String file_date;
        public int sort_order;
    }

    public static class LowerLine {
        public int id;
        public int group_index;
        public String name;
        public Double length_mm;
        public String riser_note;
        public String raw_value;
        public boolean is_brake;
        public List<UpperLine> upper_lines;
    }

    public static class UpperLine {
        public int id;
        public String name;
        public Double length_mm;
        public int sort_order;
    }
}