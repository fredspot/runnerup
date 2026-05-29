package org.runnerup.core.util

import org.runnerup.data.entities.LocationEntity

/** Lap marker info strings shared by map implementations. */
object RouteMarkerLabels {
  @JvmStatic
  fun lapMarkerInfo(
      formatter: Formatter,
      loc: LocationEntity,
      lapDistanceM: Double?,
  ): String {
    val meters = if (lapDistanceM != null) kotlin.math.round(lapDistanceM).toLong() else 0L
    return formatter.formatDistance(Formatter.Format.TXT_SHORT, meters) +
        " " +
        formatter.formatElapsedTime(
            Formatter.Format.TXT_SHORT,
            kotlin.math.round(loc.elapsed / 1000.0).toLong(),
        )
  }
}
