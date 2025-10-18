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
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.runnerup.common.util.Constants;

/** Content values wrapper for the {@code best_times} table. */
public class BestTimesEntity extends AbstractEntity {

  public BestTimesEntity() {
    super();
  }

  public BestTimesEntity(Cursor c) {
    this();
    try {
      toContentValues(c);
    } catch (Exception e) {
      Log.e(Constants.LOG, e.getMessage());
    }
  }

  /** Target distance in meters */
  public void setDistance(Integer value) {
    values().put(Constants.DB.BEST_TIMES.DISTANCE, value);
  }

  public Integer getDistance() {
    if (values().containsKey(Constants.DB.BEST_TIMES.DISTANCE)) {
      return values().getAsInteger(Constants.DB.BEST_TIMES.DISTANCE);
    }
    return null;
  }

  /** Time in milliseconds */
  public void setTime(Long value) {
    values().put(Constants.DB.BEST_TIMES.TIME, value);
  }

  public Long getTime() {
    if (values().containsKey(Constants.DB.BEST_TIMES.TIME)) {
      return values().getAsLong(Constants.DB.BEST_TIMES.TIME);
    }
    return null;
  }

  /** Pace in seconds per km */
  public void setPace(Double value) {
    values().put(Constants.DB.BEST_TIMES.PACE, value);
  }

  public Double getPace() {
    if (values().containsKey(Constants.DB.BEST_TIMES.PACE)) {
      return values().getAsDouble(Constants.DB.BEST_TIMES.PACE);
    }
    return null;
  }

  /** Reference to activity */
  public void setActivityId(Long value) {
    values().put(Constants.DB.BEST_TIMES.ACTIVITY_ID, value);
  }

  public Long getActivityId() {
    if (values().containsKey(Constants.DB.BEST_TIMES.ACTIVITY_ID)) {
      return values().getAsLong(Constants.DB.BEST_TIMES.ACTIVITY_ID);
    }
    return null;
  }

  /** When this record was achieved (in seconds since epoch) */
  public void setStartTime(Long value) {
    values().put(Constants.DB.BEST_TIMES.START_TIME, value);
  }

  public void setStartTime(Date date) {
    setStartTime(TimeUnit.MILLISECONDS.toSeconds(date.getTime()));
  }

  public Long getStartTime() {
    if (values().containsKey(Constants.DB.BEST_TIMES.START_TIME)) {
      return values().getAsLong(Constants.DB.BEST_TIMES.START_TIME);
    }
    return null;
  }

  /** Average heart rate */
  public void setAvgHr(Integer value) {
    values().put(Constants.DB.BEST_TIMES.AVG_HR, value);
  }

  public Integer getAvgHr() {
    if (values().containsKey(Constants.DB.BEST_TIMES.AVG_HR)) {
      return values().getAsInteger(Constants.DB.BEST_TIMES.AVG_HR);
    }
    return null;
  }

  /** Rank (1, 2, or 3 for top 3) */
  public void setRank(Integer value) {
    values().put(Constants.DB.BEST_TIMES.RANK, value);
  }

  public Integer getRank() {
    if (values().containsKey(Constants.DB.BEST_TIMES.RANK)) {
      return values().getAsInteger(Constants.DB.BEST_TIMES.RANK);
    }
    return null;
  }

  @Override
  protected ArrayList<String> getValidColumns() {
    ArrayList<String> columns = new ArrayList<>();
    columns.add(Constants.DB.PRIMARY_KEY);
    columns.add(Constants.DB.BEST_TIMES.DISTANCE);
    columns.add(Constants.DB.BEST_TIMES.TIME);
    columns.add(Constants.DB.BEST_TIMES.PACE);
    columns.add(Constants.DB.BEST_TIMES.ACTIVITY_ID);
    columns.add(Constants.DB.BEST_TIMES.START_TIME);
    columns.add(Constants.DB.BEST_TIMES.AVG_HR);
    columns.add(Constants.DB.BEST_TIMES.RANK);
    return columns;
  }

  @Override
  protected String getTableName() {
    return Constants.DB.BEST_TIMES.TABLE;
  }

  @Override
  protected String getNullColumnHack() {
    return null;
  }
}
