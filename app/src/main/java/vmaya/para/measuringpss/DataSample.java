package vmaya.para.measuringpss;

public class DataSample {
    private int num;
    private int row;
    private double weight;
    private int distance;

    public DataSample() {
        this.num = 0;
        this.row = 0;
        this.weight = 0.0;
        this.distance = 0;
    }

    public DataSample(int num, int row, double weight, int distance) {
        this.num = num;
        this.row = row;
        this.weight = weight;
        this.distance = distance;
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

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
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
        return String.format("[%d] Ряд: %s, Вес: %.2f г, Расст: %d мм",
                num, rowLetter, weight, distance);
    }
}