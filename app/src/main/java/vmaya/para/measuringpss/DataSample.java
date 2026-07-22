// app/src/main/java/vmaya/para/measuringpss/DataSample.java
package vmaya.para.measuringpss;

public class DataSample {
    private int num;
    private int row;
    private int weight; // Разница с пределом веса (correctedWeight - weightLimit) - целое число
    private int targetWeight; // Значение из CSV таблицы (потребная длина) - целое число
    private int distance;
    private double diff; // Разница с таблицей CSV (correctedWeight - csvValue) - может быть дробной

    public DataSample() {
        this.num = 0;
        this.row = 0;
        this.weight = 0;
        this.targetWeight = 0;
        this.distance = 0;
        this.diff = 0.0;
    }

    public DataSample(int num, int row, int weight, int targetWeight, int distance) {
        this.num = num;
        this.row = row;
        this.weight = weight;
        this.targetWeight = targetWeight;
        this.distance = distance;
        this.diff = 0.0;
    }

    public DataSample(int num, int row, int weight, int targetWeight, int distance, double diff) {
        this.num = num;
        this.row = row;
        this.weight = weight;
        this.targetWeight = targetWeight;
        this.distance = distance;
        this.diff = diff;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getTargetWeight() {
        return targetWeight;
    }

    public void setTargetWeight(int targetWeight) {
        this.targetWeight = targetWeight;
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public double getDiff() {
        return diff;
    }

    public void setDiff(double diff) {
        this.diff = diff;
    }

    @Override
    public String toString() {
        // Преобразуем числовое значение row в букву (A, B, C, D, E)
        String rowLetter;
        switch (row) {
            case 1: rowLetter = "A"; break;
            case 2: rowLetter = "B"; break;
            case 3: rowLetter = "C"; break;
            case 4: rowLetter = "D"; break;
            case 5: rowLetter = "E"; break;
            default: rowLetter = "?"; break;
        }
        // Формат: A, 5, 25г, 100 -150, разница: 25.50 мм
        // weight и targetWeight - целые числа, diff - дробное
        return String.format("%s, %d, %dг, %d -%d, разница: %.0f мм",
                rowLetter, num, weight, targetWeight, distance, diff);
    }
}