package vmaya.para.measuringpss;

public class DataModel {
    private double Weight;
    private int Distance;
    // Добавлено поле для отслеживания записи в текущем цикле
    private boolean isRecorded;

    public double getWeight() {
        return Weight;
    }

    public void setWeight(double weight) {
        Weight = weight;
    }

    public int getDistance() {
        return Distance;
    }

    public void setDistance(int distance) {
        Distance = distance;
    }

    public boolean isRecorded() {
        return isRecorded;
    }

    public void setRecorded(boolean recorded) {
        isRecorded = recorded;
    }
}