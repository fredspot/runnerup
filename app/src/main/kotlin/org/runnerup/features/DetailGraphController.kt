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

package org.runnerup.features

import android.database.sqlite.SQLiteDatabase
import android.widget.LinearLayout
import org.runnerup.core.util.Formatter
import org.runnerup.core.util.GraphWrapper
import org.runnerup.core.workout.Sport

/** Graph tab lifecycle for [DetailActivity]. */
internal class DetailGraphController {

  private var graphWrapper: GraphWrapper? = null

  fun attach(
      activity: DetailActivity,
      graphTabLayout: LinearLayout,
      hrzonesBarLayout: LinearLayout,
      formatter: Formatter,
      db: SQLiteDatabase,
      activityId: Long,
      sportValue: Int,
  ) {
    val useDistanceAsX = !Sport.isWithoutGps(sportValue)
    graphWrapper =
        GraphWrapper(
            activity,
            graphTabLayout,
            hrzonesBarLayout,
            formatter,
            db,
            activityId,
            useDistanceAsX,
        )
  }

  fun updateForSport(sportValue: Int) {
    graphWrapper?.setUseDistanceAsX(!Sport.isWithoutGps(sportValue))
  }

  fun getGraphWrapper(): GraphWrapper? = graphWrapper
}
