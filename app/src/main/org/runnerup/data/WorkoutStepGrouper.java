package org.runnerup.data;

import android.content.ContentValues;
import java.util.ArrayList;
import java.util.List;
import org.runnerup.common.util.Constants;

/** Groups consecutive laps that share a workout step and intensity. */
public final class WorkoutStepGrouper {

  public static final class LapDisplayEntry {
    public static final int VIEW_LAP = 0;
    public static final int VIEW_STEP_SUMMARY = 1;

    public int viewType = VIEW_LAP;
    public ContentValues lap;
    public double summaryDistance;
    public long summaryTime;
    public int summaryAvgHr;
    public String summaryLabel;
  }

  private WorkoutStepGrouper() {}

  public static LapDisplayEntry[] buildDisplayEntries(
      ContentValues[] laps, StepSummaryLabelProvider labelProvider) {
    if (laps == null || laps.length == 0) {
      return new LapDisplayEntry[0];
    }
    ArrayList<LapDisplayEntry> rows = new ArrayList<>();
    int i = 0;
    while (i < laps.length) {
      Long stepId = getLapStepId(laps[i]);
      int intensity = laps[i].getAsInteger(Constants.DB.LAP.INTENSITY);
      int groupStart = i;
      int j = i + 1;
      while (j < laps.length && sameStepGroup(laps[j], stepId, intensity)) {
        j++;
      }
      for (int k = groupStart; k < j; k++) {
        LapDisplayEntry lapRow = new LapDisplayEntry();
        lapRow.lap = laps[k];
        rows.add(lapRow);
      }
      if (stepId != null && j - groupStart > 1 && shouldShowStepSummary(intensity)) {
        LapDisplayEntry summary = new LapDisplayEntry();
        summary.viewType = LapDisplayEntry.VIEW_STEP_SUMMARY;
        summary.summaryLabel = labelProvider.labelForIntensity(intensity);
        fillStepSummary(laps, groupStart, j, summary);
        rows.add(summary);
      }
      i = j;
    }
    return rows.toArray(new LapDisplayEntry[0]);
  }

  public interface StepSummaryLabelProvider {
    String labelForIntensity(int intensity);
  }

  public static Long getLapStepId(ContentValues lap) {
    if (lap == null || !lap.containsKey(Constants.DB.LAP.STEP) || lap.get(Constants.DB.LAP.STEP) == null) {
      return null;
    }
    long stepId = lap.getAsLong(Constants.DB.LAP.STEP);
    return stepId > 0 ? stepId : null;
  }

  public static boolean sameStepGroup(ContentValues lap, Long stepId, int intensity) {
    if (stepId == null) {
      return false;
    }
    Long otherStepId = getLapStepId(lap);
    if (otherStepId == null || !otherStepId.equals(stepId)) {
      return false;
    }
    Integer otherIntensity = lap.getAsInteger(Constants.DB.LAP.INTENSITY);
    return otherIntensity != null && otherIntensity == intensity;
  }

  public static boolean shouldShowStepSummary(int intensity) {
    return intensity == Constants.DB.INTENSITY.ACTIVE
        || intensity == Constants.DB.INTENSITY.WARMUP
        || intensity == Constants.DB.INTENSITY.COOLDOWN;
  }

  public static void fillStepSummary(
      ContentValues[] laps, int startInclusive, int endExclusive, LapDisplayEntry summary) {
    double distance = 0;
    long time = 0;
    double hrWeighted = 0;
    long hrTime = 0;
    for (int i = startInclusive; i < endExclusive; i++) {
      if (laps[i].containsKey(Constants.DB.LAP.DISTANCE)) {
        distance += laps[i].getAsDouble(Constants.DB.LAP.DISTANCE);
      }
      long lapTime = laps[i].containsKey(Constants.DB.LAP.TIME) ? laps[i].getAsLong(Constants.DB.LAP.TIME) : 0;
      time += lapTime;
      if (laps[i].containsKey(Constants.DB.LAP.AVG_HR)) {
        int hr = laps[i].getAsInteger(Constants.DB.LAP.AVG_HR);
        if (hr > 0 && lapTime > 0) {
          hrWeighted += hr * lapTime;
          hrTime += lapTime;
        }
      }
    }
    summary.summaryDistance = distance;
    summary.summaryTime = time;
    summary.summaryAvgHr = hrTime > 0 ? (int) Math.round(hrWeighted / hrTime) : 0;
  }
}
