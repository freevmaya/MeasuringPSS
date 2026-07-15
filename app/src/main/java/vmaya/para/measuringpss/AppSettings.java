package vmaya.para.measuringpss;

public class AppSettings {
    // Константы для значений по умолчанию
    public static final double DEFAULT_WEIGHT_CF = 1.0;
    public static final int DEFAULT_DISTANCE_ADD = 0;
    public static final double DEFAULT_WEIGHT_LIMIT = 100.0;

    // Поля настроек
    private double weightCf;
    private int distanceAdd;
    private double weightLimit;

    public AppSettings() {
        // Установка значений по умолчанию
        this.weightCf = DEFAULT_WEIGHT_CF;
        this.distanceAdd = DEFAULT_DISTANCE_ADD;
        this.weightLimit = DEFAULT_WEIGHT_LIMIT;
    }

    // Геттеры и сеттеры
    public double getWeightCf() {
        return weightCf;
    }

    public void setWeightCf(double weightCf) {
        // Разрешаем любые значения, включая отрицательные
        this.weightCf = weightCf;
    }

    public int getDistanceAdd() {
        return distanceAdd;
    }

    public void setDistanceAdd(int distanceAdd) {
        this.distanceAdd = distanceAdd;
    }

    public double getWeightLimit() {
        return weightLimit;
    }

    public void setWeightLimit(double weightLimit) {
        this.weightLimit = weightLimit;
    }
}