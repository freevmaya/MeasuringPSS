// app/src/main/java/vmaya/para/measuringpss/StatisticsPagerAdapter.java
package vmaya.para.measuringpss;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class StatisticsPagerAdapter extends FragmentStateAdapter {

    public StatisticsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new StatisticsFragment();
            case 1:
                return new DifferencesFragment();
            default:
                return new StatisticsFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}