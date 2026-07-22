// app/src/main/java/vmaya/para/measuringpss/SettingsActivity.java
package vmaya.para.measuringpss;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

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
            updateSummaries();
        }

        @Override
        public void onResume() {
            super.onResume();
            updateSummaries();
        }

        private void updateSummaries() {
            // Находим Preference по ключу и обновляем его summary
            EditTextPreference weightCfPref = findPreference(AppSettings.KEY_WEIGHT_CF);
            if (weightCfPref != null) {
                String text = weightCfPref.getText();
                if (text == null) {
                    text = String.valueOf(AppSettings.DEFAULT_WEIGHT_CF);
                }
                weightCfPref.setSummary("Текущее значение: " + text);
                // Устанавливаем слушатель для обновления summary при изменении
                weightCfPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    weightCfPref.setSummary("Текущее значение: " + newValue.toString());
                    return true; // Возвращаем true, чтобы сохранить новое значение
                });
            }

            EditTextPreference distanceAddPref = findPreference(AppSettings.KEY_DISTANCE_ADD);
            if (distanceAddPref != null) {
                String text = distanceAddPref.getText();
                if (text == null) {
                    text = String.valueOf(AppSettings.DEFAULT_DISTANCE_ADD);
                }
                distanceAddPref.setSummary("Текущее значение: " + text);
                distanceAddPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    distanceAddPref.setSummary("Текущее значение: " + newValue.toString());
                    return true;
                });
            }

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
        }
    }
}