package org.runnerup.analytics;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import org.runnerup.data.RunningActivityReader.ActivityRow;

/**
 * Interpretable heart-performance numbers versus similar distance/pace runs. Lower average HR at
 * the same effort is treated as a stronger aerobic engine.
 */
public final class RunEngineStats {

  public static final int MIN_SIMILAR_FOR_PERCENTILE = 3;

  public static final class Snapshot {
    public final int similarWithHrAll;
    public final int similarWithHrYear;
    public final Integer percentileAll;
    public final Integer percentileYear;
    public final Integer meanHrAll;
    public final Integer meanHrYear;
    public final Integer deltaVsAll;
    public final Integer deltaVsYear;

    Snapshot(
        int similarWithHrAll,
        int similarWithHrYear,
        Integer percentileAll,
        Integer percentileYear,
        Integer meanHrAll,
        Integer meanHrYear,
        Integer deltaVsAll,
        Integer deltaVsYear) {
      this.similarWithHrAll = similarWithHrAll;
      this.similarWithHrYear = similarWithHrYear;
      this.percentileAll = percentileAll;
      this.percentileYear = percentileYear;
      this.meanHrAll = meanHrAll;
      this.meanHrYear = meanHrYear;
      this.deltaVsAll = deltaVsAll;
      this.deltaVsYear = deltaVsYear;
    }

    public boolean hasHeadline() {
      return percentileAll != null || meanHrAll != null;
    }
  }

  private RunEngineStats() {}

  public static Snapshot compute(ActivityRow current, List<ActivityRow> similar) {
    if (current == null || current.avgHr <= 0 || similar == null) {
      return empty();
    }
    int year = yearFromEpochSeconds(current.startTime);
    List<Integer> allHr = new ArrayList<>();
    List<Integer> yearHr = new ArrayList<>();
    for (ActivityRow row : similar) {
      if (row == null || row.avgHr <= 0 || row.activityId == current.activityId) {
        continue;
      }
      allHr.add(row.avgHr);
      if (yearFromEpochSeconds(row.startTime) == year) {
        yearHr.add(row.avgHr);
      }
    }
    Integer meanAll = mean(allHr);
    Integer meanYear = mean(yearHr);
    return new Snapshot(
        allHr.size(),
        yearHr.size(),
        percentileLowerIsBetter(current.avgHr, allHr),
        percentileLowerIsBetter(current.avgHr, yearHr),
        meanAll,
        meanYear,
        meanAll == null ? null : current.avgHr - meanAll,
        meanYear == null ? null : current.avgHr - meanYear);
  }

  /** Share of peers with a higher (worse) HR, 0–100. Null when the sample is too small. */
  public static Integer percentileLowerIsBetter(int thisValue, List<Integer> peers) {
    if (peers == null || peers.size() < MIN_SIMILAR_FOR_PERCENTILE) {
      return null;
    }
    int betterThan = 0;
    for (int peer : peers) {
      if (peer > thisValue) {
        betterThan++;
      }
    }
    return Math.round(100f * betterThan / peers.size());
  }

  public static Integer mean(List<Integer> values) {
    if (values == null || values.isEmpty()) {
      return null;
    }
    long sum = 0;
    for (int v : values) {
      sum += v;
    }
    return (int) Math.round(sum / (double) values.size());
  }

  public static int yearFromEpochSeconds(long startTimeSec) {
    Calendar cal = Calendar.getInstance(Locale.US);
    cal.setTimeInMillis(startTimeSec * 1000L);
    return cal.get(Calendar.YEAR);
  }

  private static Snapshot empty() {
    return new Snapshot(0, 0, null, null, null, null, null, null);
  }
}
