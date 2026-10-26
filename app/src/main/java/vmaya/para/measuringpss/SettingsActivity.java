// app/src/main/java/vmaya/para/measuringpss/SettingsActivity.java
package vmaya.para.measuringpss;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            setupCorrectionModePreference();
            updateSummaries();
        }

        @Override
        public void onResume() {
            super.onResume();
            // Перестраиваем список при каждом открытии — CSV мог измениться
            setupCorrectionModePreference();
            updateSummaries();
        }

        /**
         * Настраивает ListPreference "Режим коррекции".
         * Если CSV загружен — оставляем только те ряды, для которых есть данные,
         * плюс всегда "Общая коррекция" и "По рядам".
         * Если CSV не загружен — используем полный статический список A–E.
         */
        private void setupCorrectionModePreference() {
            ListPreference correctionModePref = findPreference(AppSettings.KEY_CORRECTION_MODE);
            if (correctionModePref == null) {
                return;
            }

            DataManager dataManager = DataManager.getInstance();

            List<CharSequence> entries = new ArrayList<>();
            List<CharSequence> entryValues = new ArrayList<>();

            // Всегда добавляем "Общая коррекция" и "По рядам"
            entries.add("Общая коррекция");
            entryValues.add(AppSettings.CORRECTION_MODE_GLOBAL);
            entries.add("По рядам");
            entryValues.add(AppSettings.CORRECTION_MODE_PER_ROW);

            if (dataManager.isCsvLoaded()) {
                // Показываем только те ряды, для которых есть данные
                int columnCount = dataManager.getColumnCount();
                for (int col = 0; col < columnCount; col++) {
                    int rowCount = dataManager.getRowCount(col);
                    if (rowCount > 0) {
                        String letter = columnIndexToLetter(col);
                        entries.add("По " + letter + " ряду");
                        entryValues.add(AppSettings.CORRECTION_MODE_ROW_PREFIX + col);
                    }
                }
            } else {
                // CSV не загружен — показываем все A–E
                String[] staticLetters = {"A", "B", "C", "D", "E"};
                for (int col = 0; col < staticLetters.length; col++) {
                    entries.add("По " + staticLetters[col] + " ряду");
                    entryValues.add(AppSettings.CORRECTION_MODE_ROW_PREFIX + col);
                }
            }

            correctionModePref.setEntries(entries.toArray(new CharSequence[0]));
            correctionModePref.setEntryValues(entryValues.toArray(new CharSequence[0]));

            // Проверяем валидность сохранённого значения
            AppSettings appSettings = new AppSettings(requireContext());
            String currentMode = appSettings.getCorrectionMode();
            if (!entryValues.contains(currentMode)) {
                // Сохранённый режим ссылается на ряд, которого нет в новом CSV —
                // автоматически переключаем на "global"
                appSettings.setCorrectionMode(AppSettings.CORRECTION_MODE_GLOBAL);
                correctionModePref.setValue(AppSettings.CORRECTION_MODE_GLOBAL);
                Toast.makeText(requireContext(),
                        "Выбранный ранее режим коррекции недоступен для этого CSV. Установлен режим «Общая коррекция».",
                        Toast.LENGTH_LONG).show();
            } else {
                correctionModePref.setValue(currentMode);
            }

            // Настраиваем summary и слушателя
            updateCorrectionModeSummary(correctionModePref);
            correctionModePref.setOnPreferenceChangeListener((preference, newValue) -> {
                String newValueStr = newValue.toString();
                CharSequence[] currentEntries = correctionModePref.getEntries();
                CharSequence[] currentValues = correctionModePref.getEntryValues();
                for (int i = 0; i < currentValues.length; i++) {
                    if (currentValues[i].toString().equals(newValueStr)) {
                        correctionModePref.setSummary("Текущий режим: " + currentEntries[i]);
                        break;
                    }
                }
                return true;
            });
        }

        private void updateCorrectionModeSummary(ListPreference pref) {
            String value = pref.getValue();
            if (value == null) {
                value = AppSettings.DEFAULT_CORRECTION_MODE;
            }
            CharSequence[] entries = pref.getEntries();
            CharSequence[] entryValues = pref.getEntryValues();
            String entryText = value;
            for (int i = 0; i < entryValues.length; i++) {
                if (entryValues[i].toString().equals(value)) {
                    entryText = entries[i].toString();
                    break;
                }
            }
            pref.setSummary("Текущий режим: " + entryText);
        }

        /**
         * Преобразует индекс колонки в букву (0 → A, 1 → B, ...)
         */
        private String columnIndexToLetter(int index) {
            StringBuilder sb = new StringBuilder();
            index++;
            while (index > 0) {
                index--;
                char letter = (char) ('A' + (index % 26));
                sb.insert(0, letter);
                index = index / 26;
            }
            return sb.toString();
        }

        private void updateSummaries() {
            // Вес
            EditTextPreference weightCfPref = findPreference(AppSettings.KEY_WEIGHT_CF);
            if (weightCfPref != null) {
                String text = weightCfPref.getText();
                if (text == null) {
                    text = String.valueOf(AppSettings.DEFAULT_WEIGHT_CF);
                }
                weightCfPref.setSummary("Текущее значение: " + text);
                weightCfPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    weightCfPref.setSummary("Текущее значение: " + newValue.toString());
                    return true;
                });
            }

            // Коррекция
            EditTextPreference distanceAddPref = findPreference(AppSettings.KEY_DISTANCE_ADD);
            if (distanceAddPref != null) {
                String text = distanceAddPref.getText();
                if (text == null) {
                    text = String.valueOf(AppSettings.DEFAULT_DISTANCE_ADD);
                }
                distanceAddPref.setSummary("Текущее значение: " + text + " (0 - авто)");
                distanceAddPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    distanceAddPref.setSummary("Текущее значение: " + newValue.toString() + " (0 - авто)");
                    return true;
                });
            }

            // Предел
            EditTextPreference weightLimitPref = findPreference(AppSettings.KEY_WEIGHT_LIMIT);
            if (weightLimitPref != null) {
                String text = weightLimitPref.getText();
                if (text == null) {
                    text = String.valueOf(AppSettings.DEFAULT_WEIGHT_LIMIT);
                }
                weightLimitPref.setSummary("Текущее значение: " + text);
                weightLimitPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    weightLimitPref.setSummary("Текущее значение: " + newValue.toString());
                    return true;
                });
            }

            // Методика замера
            ListPreference measurementMethodPref = findPreference(AppSettings.KEY_MEASUREMENT_METHOD);
            if (measurementMethodPref != null) {
                String value = measurementMethodPref.getValue();
                if (value == null) {
                    value = AppSettings.DEFAULT_MEASUREMENT_METHOD;
                }
                String entry = measurementMethodPref.getEntry() != null
                        ? measurementMethodPref.getEntry().toString()
                        : "1 замер";
                measurementMethodPref.setSummary("Текущая методика: " + entry);
                measurementMethodPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String newValueStr = newValue.toString();
                    CharSequence[] entries = measurementMethodPref.getEntries();
                    CharSequence[] entryValues = measurementMethodPref.getEntryValues();
                    for (int i = 0; i < entryValues.length; i++) {
                        if (entryValues[i].equals(newValueStr)) {
                            measurementMethodPref.setSummary("Текущая методика: " + entries[i]);
                            break;
                        }
                    }
                    return true;
                });
            }

            // Порог разницы
            EditTextPreference diffThresholdPref = findPreference(AppSettings.KEY_DIFF_THRESHOLD);
            if (diffThresholdPref != null) {
                String text = diffThresholdPref.getText();
                if (text == null) {
                    text = String.valueOf(AppSettings.DEFAULT_DIFF_THRESHOLD);
                }
                diffThresholdPref.setSummary("Текущее значение: " + text + " мм (от 10 до 200)");
                diffThresholdPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    try {
                        int value = Integer.parseInt(newValue.toString());
                        if (value < 10 || value > 200) {
                            return false;
                        }
                        diffThresholdPref.setSummary("Текущее значение: " + value + " мм (от 10 до 200)");
                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
            }

            // Режим измерения
            ListPreference measurementModePref = findPreference(AppSettings.KEY_MEASUREMENT_MODE);
            if (measurementModePref != null) {
                String value = measurementModePref.getValue();
                if (value == null) {
                    value = AppSettings.DEFAULT_MEASUREMENT_MODE;
                }
                String entry = measurementModePref.getEntry() != null
                        ? measurementModePref.getEntry().toString()
                        : "Одна консоль";
                measurementModePref.setSummary("Текущий режим: " + entry);
                measurementModePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String newValueStr = newValue.toString();
                    CharSequence[] entries = measurementModePref.getEntries();
                    CharSequence[] entryValues = measurementModePref.getEntryValues();
                    for (int i = 0; i < entryValues.length; i++) {
                        if (entryValues[i].equals(newValueStr)) {
                            measurementModePref.setSummary("Текущий режим: " + entries[i]);
                            break;
                        }
                    }
                    return true;
                });
            }

            // Экран
            SwitchPreferenceCompat keepScreenOnPref = findPreference(AppSettings.KEY_KEEP_SCREEN_ON);
            if (keepScreenOnPref != null) {
                keepScreenOnPref.setSummary(keepScreenOnPref.isChecked() ? "Экран не будет гаснуть" : "Экран может гаснуть");
                keepScreenOnPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean isChecked = (boolean) newValue;
                    keepScreenOnPref.setSummary(isChecked ? "Экран не будет гаснуть" : "Экран может гаснуть");
                    return true;
                });
            }
        }
    }
}