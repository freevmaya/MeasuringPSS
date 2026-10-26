package vmaya.para.measuringpss;

public class AppState {
    // Определение состояний
    public enum State {
        EXPECT_DATA,    // Ожидание превышения веса
        EXPECT_RETURN   // Ожидание возврата веса
    }

    private State currentState = State.EXPECT_DATA;
    private boolean isDataRecordedForCycle = false;
    // НОВЫЙ ФЛАГ: предупреждение о слишком большом превышении веса уже показано в текущем цикле
    private boolean isOverWeightWarningShown = false;

    public State getCurrentState() {
        return currentState;
    }

    public void setState(State newState) {
        this.currentState = newState;
        // Сбрасываем флаг записи и флаг предупреждения при смене состояния
        if (newState == State.EXPECT_DATA) {
            this.isDataRecordedForCycle = false;
            this.isOverWeightWarningShown = false;
        }
    }

    public boolean isDataRecordedForCycle() {
        return isDataRecordedForCycle;
    }

    public void setDataRecordedForCycle(boolean recorded) {
        isDataRecordedForCycle = recorded;
    }

    public boolean isOverWeightWarningShown() {
        return isOverWeightWarningShown;
    }

    public void setOverWeightWarningShown(boolean shown) {
        isOverWeightWarningShown = shown;
    }
}