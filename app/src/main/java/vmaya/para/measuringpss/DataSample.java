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
    private String side;      // НОВОЕ ПОЛЕ: "l" - левая, "r" - правая

    public DataSample() {
        this.col = 0;
        this.rowIndex = 0;
        this.weight = 0;
        this.targetWeight = 0;
        this.rawDistance = 0;
        this.distance = 0;
        this.diff = 0.0;
        this.lowerTierNumber = 0;
        this.side = "";
    }

    public DataSample(int col, int rowIndex, int weight, int targetWeight, int rawDistance, int distance) {
        this.col = col;
        this.rowIndex = rowIndex;
        this.weight = weight;
        this.targetWeight = targetWeight;
        this.rawDistance = rawDistance;
        this.distance = distance;
        this.diff = 0.0;
        this.side = "";
    }

    public DataSample(int col, int rowIndex, int weight, int targetWeight, int rawDistance, int distance, double diff) {
        this.col = col;
        this.rowIndex = rowIndex;
        this.weight = weight;
        this.targetWeight = targetWeight;
        this.rawDistance = rawDistance;
        this.distance = distance;
        this.diff = diff;
        this.side = "";
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
        this.side = "";
    }

    // НОВЫЙ КОНСТРУКТОР с параметром side
    public DataSample(int col, int rowIndex, int weight, int targetWeight, int rawDistance, int distance, double diff, int lowerTierNumber, String side) {
        this.col = col;
        this.rowIndex = rowIndex;
        this.weight = weight;
        this.targetWeight = targetWeight;
        this.rawDistance = rawDistance;
        this.distance = distance;
        this.diff = diff;
        this.lowerTierNumber = lowerTierNumber;
        this.side = (side != null && !side.isEmpty()) ? side : "";
    }

    // Геттеры и сеттеры
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

    // НОВЫЙ ГЕТТЕР
    public String getSide() {
        return side != null ? side : "";
    }

    // НОВЫЙ СЕТТЕР
    public void setSide(String side) {
        this.side = (side != null && !side.isEmpty()) ? side : "";
    }

    /**
     * Получить полный индекс стропы верхнего яруса (например: "a4l", "b2r")
     */
    public String getFullIndex() {
        String rowLetter = getColString().toLowerCase();
        int number = rowIndex + 1;
        String sideStr = side.isEmpty() ? "" : side;
        return rowLetter + number + sideStr;
    }

    /**
     * Получить индекс стропы нижнего яруса (например: "AL2", "BR1")
     */
    public String getLowerIndex() {
        String rowLetter = getColString(); // "A", "B", "C"
        // Если сторона не указана, просто добавляем номер стропы нижнего яруса
        // Если сторона указана, добавляем её (заглавную)
        String sideStr = side.isEmpty() ? "" : side.toUpperCase();
        // Пример: A1, AL1, A2, AR2, B1, BL1, B2, BR2 и т.д.
        return rowLetter + sideStr + lowerTierNumber;
    }

    @Override
    public String toString() {
        String fullIndex = getFullIndex();
        if (diff != 0)
            return String.format("%s, %d г, %d -%d, разница: %.0f мм",
                    fullIndex, weight, targetWeight, distance, diff);
        else
            return String.format("%s, %d г, %d мм",
                    fullIndex, weight, distance);
    }
}