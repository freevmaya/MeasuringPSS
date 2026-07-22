// app/src/main/java/vmaya/para/measuringpss/DataSample.java
package vmaya.para.measuringpss;

public class DataSample {
    private int num;          // Глобальный номер записи (счетчик)
    private int row;          // Ряд (1 = A, 2 = B, 3 = C, 4 = D, 5 = E)
    private int rowIndex;     // Ном. стропы (номер в пределах ряда, начинается с 1)
    private int weight;       // Превышение веса (correctedWeight - weightLimit)
    private int targetWeight; // Значение из CSV (потребная длина)
    private int distance;     // Измеренная длина
    private double diff;      // Разница (correctedDistance - csvValue)

    public DataSample() {
        this.num = 0;
        this.row = 0;
        this.rowIndex = 0;
        this.weight = 0;
        this.targetWeight = 0;
        this.distance = 0;
        this.diff = 0.0;
    }

    public DataSample(int num, int row, int rowIndex, int weight, int targetWeight, int distance) {
        this.num = num;
        this.row = row;
        this.rowIndex = rowIndex;
        this.weight = weight;
        this.targetWeight = targetWeight;
        this.distance = distance;
        this.diff = 0.0;
    }

    public DataSample(int num, int row, int rowIndex, int weight, int targetWeight, int distance, double diff) {
        this.num = num;
        this.row = row;
        this.rowIndex = rowIndex;
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
        String rowLetter;
        switch (row) {
            case 1: rowLetter = "A"; break;
            case 2: rowLetter = "B"; break;
            case 3: rowLetter = "C"; break;
            case 4: rowLetter = "D"; break;
            case 5: rowLetter = "E"; break;
            default: rowLetter = "?";
        }
        if (diff != 0)
            return String.format("%s, %d, %d г, %d -%d, разница: %.0f мм",
                    rowLetter, rowIndex, weight, targetWeight, distance, diff);
        else return String.format("%s, %d, %d г, %d мм",
                rowLetter, rowIndex, weight, distance);
    }
}