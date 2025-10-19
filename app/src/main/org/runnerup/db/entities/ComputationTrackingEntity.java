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

/** Content values wrapper for the {@code computation_tracking} table. */
public class ComputationTrackingEntity extends AbstractEntity {

  public ComputationTrackingEntity() {
    super();
  }

  public ComputationTrackingEntity(Cursor c) {
    this();
    try {
      toContentValues(c);
    } catch (Exception e) {
      Log.e(Constants.LOG, e.getMessage());
    }
  }

  /** Computation type ('best_times' or 'statistics') */
  public void setComputationType(String value) {
    values().put(Constants.DB.COMPUTATION_TRACKING.COMPUTATION_TYPE, value);
  }

  public String getComputationType() {
    if (values().containsKey(Constants.DB.COMPUTATION_TRACKING.COMPUTATION_TYPE)) {
      return values().getAsString(Constants.DB.COMPUTATION_TRACKING.COMPUTATION_TYPE);
    }
    return null;
  }

  /** Last computed time (Unix timestamp) */
  public void setLastComputedTime(Long value) {
    values().put(Constants.DB.COMPUTATION_TRACKING.LAST_COMPUTED_TIME, value);
  }

  public Long getLastComputedTime() {
    if (values().containsKey(Constants.DB.COMPUTATION_TRACKING.LAST_COMPUTED_TIME)) {
      return values().getAsLong(Constants.DB.COMPUTATION_TRACKING.LAST_COMPUTED_TIME);
    }
    return null;
  }

  /** ID of last activity when computed */
  public void setLastActivityId(Long value) {
    values().put(Constants.DB.COMPUTATION_TRACKING.LAST_ACTIVITY_ID, value);
  }

  public Long getLastActivityId() {
    if (values().containsKey(Constants.DB.COMPUTATION_TRACKING.LAST_ACTIVITY_ID)) {
      return values().getAsLong(Constants.DB.COMPUTATION_TRACKING.LAST_ACTIVITY_ID);
    }
    return null;
  }

  @Override
  protected ArrayList<String> getValidColumns() {
    ArrayList<String> columns = new ArrayList<>();
    columns.add(Constants.DB.PRIMARY_KEY);
    columns.add(Constants.DB.COMPUTATION_TRACKING.COMPUTATION_TYPE);
    columns.add(Constants.DB.COMPUTATION_TRACKING.LAST_COMPUTED_TIME);
    columns.add(Constants.DB.COMPUTATION_TRACKING.LAST_ACTIVITY_ID);
    return columns;
  }

  @Override
  protected String getTableName() {
    return Constants.DB.COMPUTATION_TRACKING.TABLE;
  }

  @Override
  protected String getNullColumnHack() {
    return null;
  }
}
