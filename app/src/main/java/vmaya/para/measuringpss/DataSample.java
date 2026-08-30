// app/src/main/java/vmaya/para/measuringpss/DataSample.java
package vmaya.para.measuringpss;

public class DataSample {
    private int col;          // Ряд (1 = A, 2 = B, 3 = C, 4 = D, 5 = E)
    private int rowIndex;     // Ном. стропы (номер в пределах ряда, начинается с 1)
    private int weight;       // Превышение веса (correctedWeight - weightLimit)
    private int targetWeight; // Значение из CSV (потребная длина)
    private int lowerTierNumber; // Номер стропы нижнего яруса (из CSV)
    private int rawDistance;  // Сырое расстояние БЕЗ коррекции (с датчика)
    private int distance;     // Измеренная длина С коррекцией
    private double diff;      // Разница (correctedDistance - csvValue)

    public DataSample() {
        this.col = 0;
        this.rowIndex = 0;
        this.weight = 0;
        this.targetWeight = 0;
        this.rawDistance = 0;
        this.distance = 0;
        this.diff = 0.0;
        this.lowerTierNumber = 0;
    }

    public DataSample(int col, int rowIndex, int weight, int targetWeight, int rawDistance, int distance) {
        this.col = col;
        this.rowIndex = rowIndex;
        this.weight = weight;
        this.targetWeight = targetWeight;
        this.rawDistance = rawDistance;
        this.distance = distance;
        this.diff = 0.0;
    }

    public DataSample(int col, int rowIndex, int weight, int targetWeight, int rawDistance, int distance, double diff) {
        this.col = col;
        this.rowIndex = rowIndex;
        this.weight = weight;
        this.targetWeight = targetWeight;
        this.rawDistance = rawDistance;
        this.distance = distance;
        this.diff = diff;
    }

    public DataSample(int col, int rowIndex, int weight, int targetWeight, int rawDistance, int distance, double diff, int lowerTierNumber) {
        this.col = col;
        this.rowIndex = rowIndex;
        this.weight = weight;
        this.targetWeight = targetWeight;
        this.rawDistance = rawDistance;
        this.distance = distance;
        this.diff = diff;
        this.lowerTierNumber = lowerTierNumber;
    }

    public int getCol() {
        return col;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public String getColString() {
        String rowLetter;
        switch (col) {
            case 0: rowLetter = "A"; break;
            case 1: rowLetter = "B"; break;
            case 2: rowLetter = "C"; break;
            case 3: rowLetter = "D"; break;
            case 4: rowLetter = "E"; break;
            default: rowLetter = "?";
        }

        return rowLetter;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
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

    public int getRawDistance() {
        return rawDistance;
    }

    public void setRawDistance(int rawDistance) {
        this.rawDistance = rawDistance;
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

    public int getLowerTierNumber() {
        return lowerTierNumber;
    }

    public void setLowerTierNumber(int lowerTierNumber) {
        this.lowerTierNumber = lowerTierNumber;
    }

    @Override
    public String toString() {
        String rowLetter = getColString();
        if (diff != 0)
            return String.format("%s, %d, %d г, %d -%d, разница: %.0f мм",
                    rowLetter, rowIndex, weight, targetWeight, distance, diff);
        else return String.format("%s, %d, %d г, %d мм",
                rowLetter, rowIndex, weight, distance);
    }
}