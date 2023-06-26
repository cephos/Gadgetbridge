/*  Copyright (C) 2015-2020 Andreas Shimokawa, Carsten Pfeiffer, Daniele
    Gobbetti, vanous, Vebryn

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.content.Context;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractFragmentPagerAdapter;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes;
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper;
import nodomain.freeyourgadget.gadgetbridge.util.LimitedQueue;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class ActivityChartsActivity extends AbstractChartsActivity {
    LimitedQueue mActivityAmountCache = new LimitedQueue(60);

    @Override
    protected AbstractFragmentPagerAdapter createFragmentPagerAdapter(final FragmentManager fragmentManager) {
        return new SectionsPagerAdapter(fragmentManager);
    }

    @Override
    protected int getRecordedDataType() {
        return RecordedDataTypes.TYPE_ACTIVITY | RecordedDataTypes.TYPE_STRESS;
    }

    @Override
    protected boolean supportsRefresh() {
        final DeviceCoordinator coordinator = DeviceHelper.getInstance().getCoordinator(getDevice());
        return coordinator.supportsActivityDataFetching();
    }

    @Override
    protected boolean allowRefresh() {
        final DeviceCoordinator coordinator = DeviceHelper.getInstance().getCoordinator(getDevice());
        return coordinator.allowFetchActivityData(getDevice()) && supportsRefresh();
    }

    @Override
    protected List<String> fillChartsTabsList() {
        return fillChartsTabsList(getDevice(), this);
    }

    private static List<String> fillChartsTabsList(final GBDevice device, final Context context) {
        final List<String> tabList;
        final Prefs prefs = new Prefs(GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()));
        final String myTabs = prefs.getString(DeviceSettingsPreferenceConst.PREFS_DEVICE_CHARTS_TABS, null);

        if (myTabs == null) {
            //make list mutable to be able to remove items later
            tabList = new ArrayList<>(Arrays.asList(context.getResources().getStringArray(R.array.pref_charts_tabs_items_default)));
        } else {
            tabList = new ArrayList<>(Arrays.asList(myTabs.split(",")));
        }
        final DeviceCoordinator coordinator = DeviceHelper.getInstance().getCoordinator(device);
        if (!coordinator.supportsRealtimeData()) {
            tabList.remove("livestats");
        }
        if (!coordinator.supportsStressMeasurement()) {
            tabList.remove("stress");
        }
        return tabList;
    }

    public static int getChartsTabIndex(final String tab, final GBDevice device, final Context context) {
        final List<String> enabledTabsList = fillChartsTabsList(device, context);
        return enabledTabsList.indexOf(tab);
    }

    /**
     * A {@link FragmentStatePagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    private class SectionsPagerAdapter extends AbstractFragmentPagerAdapter {
        SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            switch (enabledTabsList.get(position)) {
                case "activity":
                    return new ActivitySleepChartFragment();
                case "activitylist":
                    return new ActivityListingChartFragment();
                case "sleep":
                    return new SleepChartFragment();
                case "sleepweek":
                    return new WeekSleepChartFragment();
                case "stress":
                    return new StressChartFragment();
                case "stepsweek":
                    return new WeekStepsChartFragment();
                case "speedzones":
                    return new SpeedZonesFragment();
                case "livestats":
                    return new LiveActivityFragment();
            }
            return null;
        }

        @Override
        public int getCount() {
            return enabledTabsList.toArray().length;
        }

        private String getSleepTitle() {
            if (GBApplication.getPrefs().getBoolean("charts_range", true)) {
                return getString(R.string.weeksleepchart_sleep_a_month);
            } else {
                return getString(R.string.weeksleepchart_sleep_a_week);
            }
        }

        public String getStepsTitle() {
            if (GBApplication.getPrefs().getBoolean("charts_range", true)) {
                return getString(R.string.weekstepschart_steps_a_month);
            } else {
                return getString(R.string.weekstepschart_steps_a_week);
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (enabledTabsList.get(position)) {
                case "activity":
                    return getString(R.string.activity_sleepchart_activity_and_sleep);
                case "activitylist":
                    return getString(R.string.charts_activity_list);
                case "sleep":
                    return getString(R.string.sleepchart_your_sleep);
                case "sleepweek":
                    return getSleepTitle();
                case "stress":
                    return getString(R.string.menuitem_stress);
                case "stepsweek":
                    return getStepsTitle();
                case "speedzones":
                    return getString(R.string.stats_title);
                case "livestats":
                    return getString(R.string.liveactivity_live_activity);
            }
            return super.getPageTitle(position);
        }
    }
}
