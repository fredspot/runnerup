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
import org.runnerup.common.util.Constants;

/**
 * Entity class for best times summary data.
 * Contains average time and count for a specific distance.
 */
public class BestTimesSummaryEntity implements Constants {

  private int distance;
  private long averageTime; // Average time in milliseconds
  private int count; // Number of runs

  public BestTimesSummaryEntity(Cursor cursor) {
    this.distance = cursor.getInt(cursor.getColumnIndexOrThrow(Constants.DB.BEST_TIMES.DISTANCE));
    this.averageTime = cursor.getLong(cursor.getColumnIndexOrThrow("avg_time"));
    this.count = cursor.getInt(cursor.getColumnIndexOrThrow("count"));
  }

  public BestTimesSummaryEntity(int distance, long averageTime, int count) {
    this.distance = distance;
    this.averageTime = averageTime;
    this.count = count;
  }

  public int getDistance() {
    return distance;
  }

  public void setDistance(int distance) {
    this.distance = distance;
  }

  public long getAverageTime() {
    return averageTime;
  }

  public void setAverageTime(long averageTime) {
    this.averageTime = averageTime;
  }

  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }
}
