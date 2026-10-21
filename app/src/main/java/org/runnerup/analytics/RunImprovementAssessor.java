package org.runnerup.analytics;

import java.util.List;
import java.util.Map;
import org.runnerup.data.RunningActivityReader.ActivityRow;

/**
 * Computes weather- and grade-adjusted aerobic improvement versus comparable runs so the user gets
 * a clear, honest verdict on whether fitness improved.
 */
public final class RunImprovementAssessor {

  private RunImprovementAssessor() {}

  public static final class Verdict {
    /** Positive means faster weather-adjusted pace (seconds per km saved vs peer average). */
    public final Double adjustedPaceSavedSecPerKm;
    /**
     * Positive means better aerobic economy (fewer heartbeats per weather-adjusted km, as a
     * percentage improvement vs peer average).
     */
    public final Double aerobicGainPct;
    public final int peerCount;

    public Verdict(
        Double adjustedPaceSavedSecPerKm, Double aerobicGainPct, int peerCount) {
      this.adjustedPaceSavedSecPerKm = adjustedPaceSavedSecPerKm;
      this.aerobicGainPct = aerobicGainPct;
      this.peerCount = peerCount;
    }

    public boolean hasData() {
      return peerCount > 0 && (adjustedPaceSavedSecPerKm != null || aerobicGainPct != null);
    }
  }

  /**
   * Heartbeats per weather/grade-adjusted kilometre: {@code avgHr * (adjustedPaceSecPerKm / 60)}.
   * Lower means the runner covered an equivalent flat/mild kilometre using fewer heartbeats.
   */
  public static double beatsPerAdjustedKm(int avgHr, double adjustedPaceSecPerKm) {
    if (avgHr <= 0 || adjustedPaceSecPerKm <= 0) return 0;
    return avgHr * (adjustedPaceSecPerKm / 60.0);
  }

  public static Verdict assess(
      ActivityRow current,
      List<ActivityRow> peers,
      Map<Long, RunConditions> conditions) {
    if (current == null || peers == null || peers.isEmpty()) {
      return new Verdict(null, null, 0);
    }
    RunConditions curCond =
        conditions != null && conditions.containsKey(current.activityId)
            ? conditions.get(current.activityId)
            : RunConditions.EMPTY;
    double curAdjPace = curCond.adjustedPaceSecPerKm(current.totalDistance, current.totalTime);
    if (curAdjPace <= 0) {
      return new Verdict(null, null, 0);
    }
    double curBeatsPerKm = beatsPerAdjustedKm(current.avgHr, curAdjPace);

    double peerAdjPaceSum = 0;
    int peerAdjPaceCount = 0;
    double peerBeatsSum = 0;
    int peerBeatsCount = 0;

    for (ActivityRow peer : peers) {
      if (peer == null || peer.activityId == current.activityId) continue;
      RunConditions pCond =
          conditions != null && conditions.containsKey(peer.activityId)
              ? conditions.get(peer.activityId)
              : RunConditions.EMPTY;
      double pAdj = pCond.adjustedPaceSecPerKm(peer.totalDistance, peer.totalTime);
      if (pAdj <= 0) continue;
      peerAdjPaceSum += pAdj;
      peerAdjPaceCount++;
      double pBeats = beatsPerAdjustedKm(peer.avgHr, pAdj);
      if (pBeats > 0) {
        peerBeatsSum += pBeats;
        peerBeatsCount++;
      }
    }

    Double paceSaved =
        peerAdjPaceCount > 0 ? (peerAdjPaceSum / peerAdjPaceCount) - curAdjPace : null;
    Double aeroGainPct = null;
    if (curBeatsPerKm > 0 && peerBeatsCount > 0) {
      double peerMeanBeats = peerBeatsSum / peerBeatsCount;
      if (peerMeanBeats > 0) {
        aeroGainPct = ((peerMeanBeats - curBeatsPerKm) / peerMeanBeats) * 100.0;
      }
    }
    return new Verdict(paceSaved, aeroGainPct, peerAdjPaceCount);
  }
}
