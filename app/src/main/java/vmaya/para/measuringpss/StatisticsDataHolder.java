package vmaya.para.measuringpss;

import java.util.ArrayList;
import java.util.List;

/**
 * Временное хранилище для данных DataSample между активностями
 * В реальном проекте данные можно хранить в DataManager
 */
public class StatisticsDataHolder {
    private static StatisticsDataHolder instance;
    private List<DataSample> dataSamples = new ArrayList<>();

    private StatisticsDataHolder() {}

    public static synchronized StatisticsDataHolder getInstance() {
        if (instance == null) {
            instance = new StatisticsDataHolder();
        }
        return instance;
    }

    public List<DataSample> getDataSamples() {
        return dataSamples;
    }

    public void setDataSamples(List<DataSample> samples) {
        this.dataSamples = new ArrayList<>(samples);
    }

    public void clear() {
        dataSamples.clear();
    }
}