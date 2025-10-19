/*
 * Copyright (C) 2024 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.db.entities;

import android.database.Cursor;
import android.util.Log;
import java.util.ArrayList;
import org.runnerup.common.util.Constants;

/** Content values wrapper for the {@code monthly_stats} table. */
public class MonthlyStatsEntity extends AbstractEntity {

  public MonthlyStatsEntity() {
    super();
  }

  public MonthlyStatsEntity(Cursor c) {
    this();
    try {
      toContentValues(c);
    } catch (Exception e) {
      Log.e(Constants.LOG, e.getMessage());
    }
  }

  /** Year (e.g., 2024) */
  public void setYear(Integer value) {
    values().put(Constants.DB.MONTHLY_STATS.YEAR, value);
  }

  public Integer getYear() {
    if (values().containsKey(Constants.DB.MONTHLY_STATS.YEAR)) {
      return values().getAsInteger(Constants.DB.MONTHLY_STATS.YEAR);
    }
    return null;
  }

  /** Month (1-12) */
  public void setMonth(Integer value) {
    values().put(Constants.DB.MONTHLY_STATS.MONTH, value);
  }

  public Integer getMonth() {
    if (values().containsKey(Constants.DB.MONTHLY_STATS.MONTH)) {
      return values().getAsInteger(Constants.DB.MONTHLY_STATS.MONTH);
    }
    return null;
  }

  /** Total distance in meters */
  public void setTotalDistance(Double value) {
    values().put(Constants.DB.MONTHLY_STATS.TOTAL_DISTANCE, value);
  }

  public Double getTotalDistance() {
    if (values().containsKey(Constants.DB.MONTHLY_STATS.TOTAL_DISTANCE)) {
      return values().getAsDouble(Constants.DB.MONTHLY_STATS.TOTAL_DISTANCE);
    }
    return null;
  }

  /** Average pace in seconds per km */
  public void setAvgPace(Double value) {
    values().put(Constants.DB.MONTHLY_STATS.AVG_PACE, value);
  }

  public Double getAvgPace() {
    if (values().containsKey(Constants.DB.MONTHLY_STATS.AVG_PACE)) {
      return values().getAsDouble(Constants.DB.MONTHLY_STATS.AVG_PACE);
    }
    return null;
  }

  /** Average run length in meters */
  public void setAvgRunLength(Double value) {
    values().put(Constants.DB.MONTHLY_STATS.AVG_RUN_LENGTH, value);
  }

  public Double getAvgRunLength() {
    if (values().containsKey(Constants.DB.MONTHLY_STATS.AVG_RUN_LENGTH)) {
      return values().getAsDouble(Constants.DB.MONTHLY_STATS.AVG_RUN_LENGTH);
    }
    return null;
  }

  /** Number of runs */
  public void setRunCount(Integer value) {
    values().put(Constants.DB.MONTHLY_STATS.RUN_COUNT, value);
  }

  public Integer getRunCount() {
    if (values().containsKey(Constants.DB.MONTHLY_STATS.RUN_COUNT)) {
      return values().getAsInteger(Constants.DB.MONTHLY_STATS.RUN_COUNT);
    }
    return null;
  }

  @Override
  protected ArrayList<String> getValidColumns() {
    ArrayList<String> columns = new ArrayList<>();
    columns.add(Constants.DB.PRIMARY_KEY);
    columns.add(Constants.DB.MONTHLY_STATS.YEAR);
    columns.add(Constants.DB.MONTHLY_STATS.MONTH);
    columns.add(Constants.DB.MONTHLY_STATS.TOTAL_DISTANCE);
    columns.add(Constants.DB.MONTHLY_STATS.AVG_PACE);
    columns.add(Constants.DB.MONTHLY_STATS.AVG_RUN_LENGTH);
    columns.add(Constants.DB.MONTHLY_STATS.RUN_COUNT);
    return columns;
  }

  @Override
  protected String getTableName() {
    return Constants.DB.MONTHLY_STATS.TABLE;
  }

  @Override
  protected String getNullColumnHack() {
    return null;
  }
}
