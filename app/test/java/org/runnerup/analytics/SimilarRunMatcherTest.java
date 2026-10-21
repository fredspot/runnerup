package org.runnerup.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.runnerup.data.RunningActivityReader.ActivityRow;

public class SimilarRunMatcherTest {

  @Test
  public void paceSecPerKm_fromDistanceAndTime() {
    assertEquals(300.0, SimilarRunMatcher.paceSecPerKm(5000, 1500), 0.01);
    assertEquals(0.0, SimilarRunMatcher.paceSecPerKm(0, 1500), 0.01);
  }

  @Test
  public void isSimilar_acceptsWithinTenPercent() {
    assertTrue(SimilarRunMatcher.isSimilar(10000, 3000, 10500, 3150));
  }

  @Test
  public void isSimilar_rejectsDistantRun() {
    assertFalse(SimilarRunMatcher.isSimilar(10000, 3000, 15000, 4500));
  }

  @Test
  public void isSimilar_rejectsDifferentPace() {
    assertFalse(SimilarRunMatcher.isSimilar(10000, 3000, 10000, 4200));
  }

  @Test
  public void rankSimilar_ordersClosestFirstAndCapsResults() {
    ActivityRow current = row(1, 10000, 3000, 150);
    List<ActivityRow> candidates =
        Arrays.asList(
            row(2, 10100, 3030, 148),
            row(3, 10900, 3270, 160),
            row(1, 10000, 3000, 150),
            row(4, 5000, 1500, 140));
    List<ActivityRow> ranked = SimilarRunMatcher.rankSimilar(current, candidates);
    assertEquals(2, ranked.size());
    assertEquals(2, ranked.get(0).activityId);
    assertEquals(3, ranked.get(1).activityId);
  }

  @Test
  public void findSimilar_keepsEveryMatch_rankSimilar_capsList() {
    ActivityRow current = row(1, 10000, 3000, 150);
    ActivityRow[] extra = new ActivityRow[12];
    for (int i = 0; i < extra.length; i++) {
      extra[i] = row(10 + i, 10000 + i * 20, 3000 + i * 6, 150);
    }
    List<ActivityRow> candidates = Arrays.asList(extra);
    List<ActivityRow> found = SimilarRunMatcher.findSimilar(current, candidates);
    assertEquals(12, found.size());
    assertEquals(SimilarRunMatcher.MAX_RESULTS, SimilarRunMatcher.rankSimilar(current, candidates).size());
  }

  private static ActivityRow row(long id, double distance, long time, int hr) {
    return new ActivityRow(id, 0, distance, time, hr, hr + 10);
  }
}
