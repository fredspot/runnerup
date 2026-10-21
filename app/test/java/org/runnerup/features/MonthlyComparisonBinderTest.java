package org.runnerup.features;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.database.Cursor;
import org.junit.Test;
import org.runnerup.common.util.Constants;

public class MonthlyComparisonBinderTest {

  @Test
  public void needsZonePaceRecompute_trueWhenTableEmpty() {
    Cursor cursor = mock(Cursor.class);
    when(cursor.getCount()).thenReturn(0);
    assertTrue(MonthlyComparisonBinder.needsZonePaceRecompute(cursor));
  }

  @Test
  public void needsZonePaceRecompute_trueWhenZoneColumnsMissing() {
    Cursor cursor = mock(Cursor.class);
    when(cursor.getCount()).thenReturn(1);
    when(cursor.getColumnIndex(Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE_ZONE_1))
        .thenReturn(-1);
    assertTrue(MonthlyComparisonBinder.needsZonePaceRecompute(cursor));
  }

  @Test
  public void needsZonePaceRecompute_falseWhenRowExistsEvenIfZonePacesEmpty() {
    Cursor cursor = mock(Cursor.class);
    when(cursor.getCount()).thenReturn(1);
    when(cursor.getColumnIndex(Constants.DB.MONTHLY_COMPARISON.CURRENT_AVG_PACE_ZONE_1))
        .thenReturn(4);
    assertFalse(MonthlyComparisonBinder.needsZonePaceRecompute(cursor));
  }

  @Test
  public void hasMeaningfulData_trueWhenCurrentKmPresent() {
    MonthlyComparisonBinder.Data data = new MonthlyComparisonBinder.Data();
    data.currentTotalKm = 12.4;
    assertTrue(MonthlyComparisonBinder.hasMeaningfulData(data));
  }

  @Test
  public void hasMeaningfulData_falseWhenAllKmEmpty() {
    MonthlyComparisonBinder.Data data = new MonthlyComparisonBinder.Data();
    assertFalse(MonthlyComparisonBinder.hasMeaningfulData(data));
  }

  @Test
  public void resolveContentState_loadingHidesTableAndEmpty() {
    MonthlyComparisonBinder.ContentState state =
        MonthlyComparisonBinder.resolveContentState(true, true);
    assertTrue(state.showLoading);
    assertFalse(state.showTable);
    assertFalse(state.showEmpty);
  }

  @Test
  public void resolveContentState_afterLoadShowsTableWhenDataPresent() {
    MonthlyComparisonBinder.ContentState state =
        MonthlyComparisonBinder.resolveContentState(false, true);
    assertFalse(state.showLoading);
    assertTrue(state.showTable);
    assertFalse(state.showEmpty);
  }

  @Test
  public void parseHistoryMonth_readsIsoAndShortLabels() {
    int[] iso = MonthlyComparisonBinder.parseHistoryMonth("2025-01");
    assertEquals(2025, iso[0]);
    assertEquals(0, iso[1]);
    int[] shortYear = MonthlyComparisonBinder.parseHistoryMonth("Sep24");
    assertEquals(2024, shortYear[0]);
    assertEquals(8, shortYear[1]);
    int[] longYear = MonthlyComparisonBinder.parseHistoryMonth("Sep2024");
    assertEquals(2024, longYear[0]);
    assertEquals(8, longYear[1]);
    assertNull(MonthlyComparisonBinder.parseHistoryMonth(""));
    assertNull(MonthlyComparisonBinder.parseHistoryMonth("not-a-month"));
  }

  @Test
  public void resolveContentState_afterLoadShowsEmptyWhenNoData() {
    MonthlyComparisonBinder.ContentState state =
        MonthlyComparisonBinder.resolveContentState(false, false);
    assertFalse(state.showLoading);
    assertFalse(state.showTable);
    assertTrue(state.showEmpty);
  }
}
