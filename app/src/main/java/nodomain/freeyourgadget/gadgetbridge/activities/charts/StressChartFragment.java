/*  Copyright (C) 2023 José Rebelo

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
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.LegendEntry;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.StressSample;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class StressChartFragment extends AbstractChartFragment<StressChartFragment.StressChartsData> {
    protected static final Logger LOG = LoggerFactory.getLogger(ActivitySleepChartFragment.class);

    private LineChart mStressChart;
    private PieChart mStressLevelsPieChart;

    private int BACKGROUND_COLOR;
    private int DESCRIPTION_COLOR;
    private int CHART_TEXT_COLOR;
    private int LEGEND_TEXT_COLOR;

    private String STRESS_AVERAGE_LABEL;

    private final Prefs prefs = GBApplication.getPrefs();

    private final boolean CHARTS_SLEEP_RANGE_24H = prefs.getBoolean("chart_sleep_range_24h", false);
    private final boolean SHOW_CHARTS_AVERAGE = prefs.getBoolean("charts_show_average", true);

    @Override
    protected void init() {
        BACKGROUND_COLOR = GBApplication.getBackgroundColor(requireContext());
        LEGEND_TEXT_COLOR = DESCRIPTION_COLOR = GBApplication.getTextColor(requireContext());
        CHART_TEXT_COLOR = ContextCompat.getColor(requireContext(), R.color.secondarytext);

        STRESS_AVERAGE_LABEL = requireContext().getString(R.string.charts_legend_stress_average);
    }

    @Override
    protected StressChartsData refreshInBackground(final ChartsHost chartsHost, final DBHandler db, final GBDevice device) {
        final List<? extends StressSample> samples = getSamples(db, device);

        LOG.info("Got {} stress samples", samples.size());

        ensureStartAndEndSamples((List<StressSample>) samples);

        return new StressChartsDataBuilder(samples).build();
    }

    protected LineDataSet createDataSet(final StressType stressType, final List<Entry> values) {
        final LineDataSet lineDataSet = new LineDataSet(values, stressType.getLabel(requireContext()));
        lineDataSet.setColor(stressType.getColor(requireContext()));
        lineDataSet.setDrawFilled(true);
        lineDataSet.setDrawCircles(false);
        lineDataSet.setFillColor(stressType.getColor(requireContext()));
        lineDataSet.setFillAlpha(255);
        lineDataSet.setDrawValues(false);
        lineDataSet.setValueTextColor(CHART_TEXT_COLOR);
        lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        return lineDataSet;
    }

    @Override
    protected void updateChartsnUIThread(final StressChartsData stressData) {
        final PieData pieData = stressData.getPieData();
        if (stressData.getAverage() > 0) {
            mStressLevelsPieChart.setCenterText(requireContext().getString(R.string.average, String.valueOf(stressData.getAverage())));
        } else {
            mStressLevelsPieChart.setCenterText(requireContext().getString(R.string.no_data));
        }
        mStressLevelsPieChart.setData(pieData);

        final DefaultChartsData<LineData> chartsData = stressData.getChartsData();
        mStressChart.setData(null); // workaround for https://github.com/PhilJay/MPAndroidChart/issues/2317
        mStressChart.getXAxis().setValueFormatter(chartsData.getXValueFormatter());
        mStressChart.setData(chartsData.getData());
        mStressChart.getAxisRight().removeAllLimitLines();

        if (stressData.getAverage() > 0) {
            final LimitLine averageLine = new LimitLine(stressData.getAverage());
            averageLine.setLineColor(Color.RED);
            averageLine.setLineWidth(0.1f);
            mStressChart.getAxisRight().addLimitLine(averageLine);
        }
    }

    @Override
    public String getTitle() {
        return getString(R.string.menuitem_stress);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater,
                             final ViewGroup container,
                             final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_stresschart, container, false);

        mStressChart = rootView.findViewById(R.id.stress_line_chart);
        mStressLevelsPieChart = rootView.findViewById(R.id.stress_pie_chart);

        setupLineChart();
        setupPieChart();

        // refresh immediately instead of use refreshIfVisible(), for perceived performance
        refresh();

        return rootView;
    }

    private void setupPieChart() {
        mStressLevelsPieChart.setBackgroundColor(BACKGROUND_COLOR);
        mStressLevelsPieChart.getDescription().setTextColor(DESCRIPTION_COLOR);
        mStressLevelsPieChart.setEntryLabelColor(DESCRIPTION_COLOR);
        mStressLevelsPieChart.getDescription().setText("");
        mStressLevelsPieChart.setNoDataText("");
        mStressLevelsPieChart.getLegend().setEnabled(false);
    }

    private void setupLineChart() {
        mStressChart.setBackgroundColor(BACKGROUND_COLOR);
        mStressChart.getDescription().setTextColor(DESCRIPTION_COLOR);
        configureBarLineChartDefaults(mStressChart);

        final XAxis x = mStressChart.getXAxis();
        x.setDrawLabels(true);
        x.setDrawGridLines(false);
        x.setEnabled(true);
        x.setTextColor(CHART_TEXT_COLOR);
        x.setDrawLimitLinesBehindData(true);

        final YAxis yAxisLeft = mStressChart.getAxisLeft();
        yAxisLeft.setDrawGridLines(true);
        yAxisLeft.setAxisMaximum(100f);
        yAxisLeft.setAxisMinimum(0);
        yAxisLeft.setDrawTopYLabelEntry(false);
        yAxisLeft.setTextColor(CHART_TEXT_COLOR);
        yAxisLeft.setEnabled(true);

        final YAxis yAxisRight = mStressChart.getAxisRight();
        yAxisRight.setDrawGridLines(false);
        yAxisRight.setEnabled(true);
        yAxisRight.setDrawLabels(false);
        yAxisRight.setDrawTopYLabelEntry(true);
        yAxisRight.setTextColor(CHART_TEXT_COLOR);
        yAxisRight.setAxisMaximum(100f);
        yAxisRight.setAxisMinimum(0);
    }

    @Override
    protected void setupLegend(final Chart<?> chart) {
        final List<LegendEntry> legendEntries = new ArrayList<>(StressType.values().length + 1);

        for (final StressType stressType : StressType.values()) {
            final LegendEntry entry = new LegendEntry();
            entry.label = stressType.getLabel(requireContext());
            entry.formColor = stressType.getColor(requireContext());
            legendEntries.add(entry);
        }

        if (!CHARTS_SLEEP_RANGE_24H && SHOW_CHARTS_AVERAGE) {
            final LegendEntry averageEntry = new LegendEntry();
            averageEntry.label = STRESS_AVERAGE_LABEL;
            averageEntry.formColor = Color.RED;
            legendEntries.add(averageEntry);
        }

        chart.getLegend().setCustom(legendEntries);
        chart.getLegend().setTextColor(LEGEND_TEXT_COLOR);
    }

    @Override
    protected void renderCharts() {
        mStressChart.animateX(ANIM_TIME, Easing.EaseInOutQuart);
        mStressLevelsPieChart.invalidate();
    }

    private List<? extends StressSample> getSamples(final DBHandler db, final GBDevice device) {
        final int tsStart = getTSStart();
        final int tsEnd = getTSEnd();
        final DeviceCoordinator coordinator = DeviceHelper.getInstance().getCoordinator(device);
        final TimeSampleProvider<? extends StressSample> sampleProvider = coordinator.getStressSampleProvider(device, db.getDaoSession());
        return sampleProvider.getAllSamples(tsStart * 1000L, tsEnd * 1000L);
    }

    protected void ensureStartAndEndSamples(final List<StressSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return;
        }

        final long tsEndMillis = getTSEnd() * 1000L;
        final long tsStartMillis = getTSStart() * 1000L;

        final StressSample lastSample = samples.get(samples.size() - 1);
        if (lastSample.getTimestamp() < tsEndMillis) {
            samples.add(new EmptyStressSample(tsEndMillis));
        }

        final StressSample firstSample = samples.get(0);
        if (firstSample.getTimestamp() > tsStartMillis) {
            samples.add(0, new EmptyStressSample(tsStartMillis));
        }
    }

    protected static final class EmptyStressSample implements StressSample {
        private final long ts;

        public EmptyStressSample(final long ts) {
            this.ts = ts;
        }

        @Override
        public Type getType() {
            return Type.AUTOMATIC;
        }

        @Override
        public int getStress() {
            return -1;
        }

        @Override
        public long getTimestamp() {
            return ts;
        }
    }

    protected class StressChartsDataBuilder {
        private static final int UNKNOWN_VAL = 2;

        private final List<? extends StressSample> samples;

        private final TimestampTranslation tsTranslation = new TimestampTranslation();

        private final Map<StressType, List<Entry>> lineEntriesPerLevel = new HashMap<>();
        private final Map<StressType, Integer> accumulator = new HashMap<>();

        int previousTs;
        int currentTypeStartTs;
        StressType previousStressType;
        long averageSum;
        long averageNumSamples;

        public StressChartsDataBuilder(final List<? extends StressSample> samples) {
            this.samples = samples;
        }

        private void reset() {
            tsTranslation.reset();
            lineEntriesPerLevel.clear();
            accumulator.clear();
            for (final StressType stressType : StressType.values()) {
                lineEntriesPerLevel.put(stressType, new ArrayList<>());
                accumulator.put(stressType, 0);
            }
            previousTs = 0;
            currentTypeStartTs = 0;
            previousStressType = StressType.UNKNOWN;
        }

        private void processSamples() {
            reset();

            for (final StressSample sample : samples) {
                processSample(sample);
            }

            // Add the last block, if any
            if (currentTypeStartTs != previousTs) {
                set(previousTs, previousStressType, samples.get(samples.size() - 1).getStress());
            }
        }

        private void processSample(final StressSample sample) {
            //LOG.debug("Processing sample {} {}", sdf.format(new Date(sample.getTimestamp())), sample.getStress());

            final StressType stressType = StressType.fromStress(sample.getStress());
            final int ts = tsTranslation.shorten((int) (sample.getTimestamp() / 1000L));

            if (ts == 0) {
                // First sample
                previousTs = ts;
                currentTypeStartTs = ts;
                previousStressType = stressType;
                set(ts, stressType, sample.getStress());
                return;
            }

            if (ts - previousTs > 60 * 10) {
                // More than 15 minutes since last sample
                // Set to unknown right after the last sample we got until the current time
                int lastEndTs = Math.min(previousTs + 60 * 5, ts - 1);
                set(lastEndTs, StressType.UNKNOWN, UNKNOWN_VAL);
                set(ts - 1, StressType.UNKNOWN, UNKNOWN_VAL);
            }

            if (!stressType.equals(previousStressType)) {
                currentTypeStartTs = ts;
            }

            set(ts, stressType, sample.getStress());

            accumulator.put(stressType, accumulator.get(stressType) + 60);

            if (stressType != StressType.UNKNOWN) {
                averageSum += sample.getStress();
                averageNumSamples++;
            }

            previousStressType = stressType;
            previousTs = ts;
        }

        private void set(final int ts, final StressType stressType, final int stress) {
            for (final Map.Entry<StressType, List<Entry>> stressTypeListEntry : lineEntriesPerLevel.entrySet()) {
                if (stressTypeListEntry.getKey() == stressType) {
                    stressTypeListEntry.getValue().add(new Entry(ts, stress));
                } else {
                    stressTypeListEntry.getValue().add(new Entry(ts, 0));
                }
            }
        }

        public StressChartsData build() {
            processSamples();

            final List<ILineDataSet> lineDataSets = new ArrayList<>();
            final List<PieEntry> pieEntries = new ArrayList<>();
            final List<Integer> pieColors = new ArrayList<>();

            for (final StressType stressType : StressType.values()) {
                final List<Entry> stressEntries = lineEntriesPerLevel.get(stressType);
                lineDataSets.add(createDataSet(stressType, stressEntries));

                final Integer stressTime = accumulator.get(stressType);
                if (stressType != StressType.UNKNOWN && stressTime != null && stressTime != 0) {
                    pieEntries.add(new PieEntry(stressTime, stressType.getLabel(requireContext())));
                    pieColors.add(stressType.getColor(requireContext()));
                }
            }

            final PieDataSet pieDataSet = new PieDataSet(pieEntries, "");
            pieDataSet.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return DateTimeUtils.formatDurationHoursMinutes((long) value, TimeUnit.SECONDS);
                }
            });
            pieDataSet.setColors(pieColors);
            pieDataSet.setValueTextColor(DESCRIPTION_COLOR);
            pieDataSet.setValueTextSize(13f);
            pieDataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
            pieDataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
            final PieData pieData = new PieData(pieDataSet);

            final LineData lineData = new LineData(lineDataSets);
            final ValueFormatter xValueFormatter = new SampleXLabelFormatter(tsTranslation);
            final DefaultChartsData<LineData> chartsData = new DefaultChartsData<>(lineData, xValueFormatter);
            return new StressChartsData(pieData, chartsData, Math.round((float) averageSum / averageNumSamples));
        }
    }

    protected static class StressChartsData extends ChartsData {
        private final PieData pieData;
        private final DefaultChartsData<LineData> chartsData;
        private final int average;

        public StressChartsData(final PieData pieData, final DefaultChartsData<LineData> chartsData, final int average) {
            this.pieData = pieData;
            this.chartsData = chartsData;
            this.average = average;
        }

        public PieData getPieData() {
            return pieData;
        }

        public DefaultChartsData<LineData> getChartsData() {
            return chartsData;
        }

        public int getAverage() {
            return average;
        }
    }

    protected enum StressType {
        UNKNOWN(R.string.unknown, R.color.chart_not_worn_light),
        RELAXED(R.string.stress_relaxed, R.color.chart_rem_sleep_dark),
        MILD(R.string.stress_mild, R.color.chart_activity_dark),
        MODERATE(R.string.stress_moderate, R.color.chart_heartrate),
        HIGH(R.string.stress_high, R.color.chart_heartrate_alternative),
        ;

        private final int labelId;
        private final int colorId;

        StressType(final int labelId, final int colorId) {
            this.labelId = labelId;
            this.colorId = colorId;
        }

        public String getLabel(final Context context) {
            return context.getString(labelId);
        }

        public int getColor(final Context context) {
            return ContextCompat.getColor(context, colorId);
        }

        public static StressType fromStress(final int stress) {
            if (stress < 0) {
                return StressType.UNKNOWN;
            } else if (stress < 40) {
                return StressType.RELAXED;
            } else if (stress < 60) {
                return StressType.MILD;
            } else if (stress < 80) {
                return StressType.MODERATE;
            } else {
                return StressType.HIGH;
            }
        }
    }
}
