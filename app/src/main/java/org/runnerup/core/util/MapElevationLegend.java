package org.runnerup.core.util;

import static org.runnerup.core.util.Formatter.Format.TXT_SHORT;

import android.view.View;
import android.widget.TextView;
import org.runnerup.R;

/** Binds the map overlay that explains the elevation color ramp. */
public final class MapElevationLegend {

  private MapElevationLegend() {}

  public static void bind(View mapView, Formatter formatter, double minM, double maxM) {
    if (mapView == null) {
      return;
    }
    View parent = (View) mapView.getParent();
    if (parent == null) {
      return;
    }
    View legend = parent.findViewById(R.id.map_elevation_legend);
    if (legend == null) {
      return;
    }
    if (!MapElevationColor.hasRange(minM, maxM)) {
      legend.setVisibility(View.GONE);
      return;
    }
    legend.setVisibility(View.VISIBLE);
    String lowText = formatter.formatElevation(TXT_SHORT, minM);
    String highText = formatter.formatElevation(TXT_SHORT, maxM);
    TextView low = parent.findViewById(R.id.map_elevation_legend_low);
    TextView high = parent.findViewById(R.id.map_elevation_legend_high);
    if (low != null) {
      low.setText(lowText);
    }
    if (high != null) {
      high.setText(highText);
    }
    legend.setContentDescription(
        mapView.getContext().getString(R.string.map_elevation_legend_cd, lowText, highText));
  }
}
