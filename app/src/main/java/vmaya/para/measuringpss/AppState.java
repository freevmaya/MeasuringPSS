package vmaya.para.measuringpss;

public class AppState {
    // Определение состояний
    public enum State {
        EXPECT_DATA,    // Ожидание превышения веса
        EXPECT_RETURN   // Ожидание возврата веса
    }

    private State currentState = State.EXPECT_DATA;
    private boolean isDataRecordedForCycle = false;

    public State getCurrentState() {
        return currentState;
    }

    public void setState(State newState) {
        this.currentState = newState;
        // Сбрасываем флаг записи при смене состояния
        if (newState == State.EXPECT_DATA) {
            this.isDataRecordedForCycle = false;
        }
    }

    public boolean isDataRecordedForCycle() {
        return isDataRecordedForCycle;
    }

    public void setDataRecordedForCycle(boolean recorded) {
        isDataRecordedForCycle = recorded;
    }
}