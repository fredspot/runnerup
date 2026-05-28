package org.runnerup.analytics;

import java.util.List;
import org.runnerup.data.RunningActivityReader;
import org.runnerup.data.RunningPaceBounds;

/** Package-visible sliding-window best-segment search over lap rows. */
final class BestTimesSegmentFinder {

  private BestTimesSegmentFinder() {}

  static BestTimesCalculator.BestTimeResult findFastestSegment(
      List<RunningActivityReader.LapRow> laps,
      int targetDistance,
      RunningActivityReader.ActivityRow activityInfo) {
    BestTimesCalculator.BestTimeResult bestResult = null;
    double bestTimeSec = Double.MAX_VALUE;

    int n = laps.size();
    for (int startIdx = 0; startIdx < n; startIdx++) {
      double accumDist = 0;
      double accumTime = 0;
      int accumHrSum = 0;
      int accumHrCount = 0;

      for (int j = startIdx; j < n; j++) {
        RunningActivityReader.LapRow lap = laps.get(j);
        if (lap.timeSeconds <= 0) {
          continue;
        }

        double remaining = targetDistance - accumDist;
        if (lap.distanceM < remaining) {
          accumDist += lap.distanceM;
          accumTime += lap.timeSeconds;
          if (lap.avgHr > 0) {
            accumHrSum += lap.avgHr;
            accumHrCount++;
          }
          continue;
        }

        double partialTime = lap.timeSeconds * (remaining / lap.distanceM);
        double segmentTime = accumTime + partialTime;
        if (lap.avgHr > 0) {
          accumHrSum += (int) Math.round(lap.avgHr);
          accumHrCount++;
        }

        double pacePerKm = segmentTime / (targetDistance / 1000.0);
        if (pacePerKm >= RunningPaceBounds.BEST_TIMES_MIN_SEC_PER_KM
            && pacePerKm <= RunningPaceBounds.BEST_TIMES_MAX_SEC_PER_KM) {
          if (segmentTime < bestTimeSec) {
            bestTimeSec = segmentTime;
            bestResult = new BestTimesCalculator.BestTimeResult();
            bestResult.activityId = activityInfo.activityId;
            bestResult.startTime = activityInfo.startTime;
            bestResult.timeMs = Math.round(segmentTime * 1000.0);
            bestResult.pacePerKm = pacePerKm;
            bestResult.avgHr =
                accumHrCount > 0 ? accumHrSum / accumHrCount : activityInfo.avgHr;
            bestResult.maxHr = activityInfo.maxHr > 0 ? activityInfo.maxHr : null;
          }
        }
        break;
      }
    }

    return bestResult;
  }
}
