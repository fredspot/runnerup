package org.runnerup.features;

import android.content.ContentValues;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import org.runnerup.R;
import org.runnerup.common.util.Constants.DB;
import org.runnerup.core.util.ActivitySummaryBinder;
import org.runnerup.core.util.Formatter;
import org.runnerup.data.WorkoutStepGrouper;
import org.runnerup.core.workout.Intensity;

/** Lap list adapter and display-entry building for {@link DetailActivity}. */
final class DetailLapListController {

  interface Host {
    String labelForIntensity(int intensity);

    Formatter getFormatter();

    boolean isLapHrPresent();

    WorkoutStepGrouper.LapDisplayEntry[] getLapDisplayEntries();

    void setLapDisplayEntries(WorkoutStepGrouper.LapDisplayEntry[] entries);
  }

  private DetailLapListController() {}

  static WorkoutStepGrouper.LapDisplayEntry[] buildDisplayEntries(
      ContentValues[] laps, Host host) {
    return WorkoutStepGrouper.buildDisplayEntries(laps, host::labelForIntensity);
  }

  static BaseAdapter createAdapter(DetailActivity activity, Host host) {
    return new LapListAdapter(activity, host);
  }

  private static class ViewHolderLapList {
    private TextView tv0;
    private TextView tv1;
    private TextView tv2;
    private TextView tv3;
    private TextView tv4;
    private TextView tvHr;
  }

  private static class LapListAdapter extends BaseAdapter {

    private final DetailActivity activity;
    private final Host host;

    LapListAdapter(DetailActivity activity, Host host) {
      this.activity = activity;
      this.host = host;
    }

    @Override
    public int getViewTypeCount() {
      return 2;
    }

    @Override
    public int getItemViewType(int position) {
      return host.getLapDisplayEntries()[position].viewType;
    }

    @Override
    public int getCount() {
      WorkoutStepGrouper.LapDisplayEntry[] entries = host.getLapDisplayEntries();
      return entries != null ? entries.length : 0;
    }

    @Override
    public Object getItem(int position) {
      WorkoutStepGrouper.LapDisplayEntry entry = host.getLapDisplayEntries()[position];
      return entry.viewType == WorkoutStepGrouper.LapDisplayEntry.VIEW_LAP ? entry.lap : entry;
    }

    @Override
    public long getItemId(int position) {
      WorkoutStepGrouper.LapDisplayEntry entry = host.getLapDisplayEntries()[position];
      if (entry.viewType == WorkoutStepGrouper.LapDisplayEntry.VIEW_LAP && entry.lap != null) {
        return entry.lap.getAsLong("_id");
      }
      return -position - 1L;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      WorkoutStepGrouper.LapDisplayEntry entry = host.getLapDisplayEntries()[position];
      if (entry.viewType == WorkoutStepGrouper.LapDisplayEntry.VIEW_STEP_SUMMARY) {
        return bindStepSummaryRow(position, convertView, parent, entry);
      }
      return bindLapRow(position, convertView, parent, entry.lap);
    }

    private View bindStepSummaryRow(
        int position, View convertView, ViewGroup parent, WorkoutStepGrouper.LapDisplayEntry entry) {
      View view = convertView;
      ViewHolderLapList viewHolder;
      if (view == null
          || getItemViewType(position) != WorkoutStepGrouper.LapDisplayEntry.VIEW_STEP_SUMMARY) {
        viewHolder = new ViewHolderLapList();
        LayoutInflater inflater = LayoutInflater.from(activity);
        view = inflater.inflate(R.layout.laplist_step_summary_row, parent, false);
        viewHolder.tv0 = view.findViewById(R.id.lap_list_type);
        viewHolder.tv1 = view.findViewById(R.id.lap_list_id);
        viewHolder.tv2 = view.findViewById(R.id.lap_list_distance);
        viewHolder.tv3 = view.findViewById(R.id.lap_list_time);
        viewHolder.tv4 = view.findViewById(R.id.lap_list_pace);
        viewHolder.tvHr = view.findViewById(R.id.lap_list_hr);
        view.setTag(viewHolder);
      } else {
        viewHolder = (ViewHolderLapList) view.getTag();
      }
      viewHolder.tv1.setText("");
      viewHolder.tv0.setText(entry.summaryLabel);
      Formatter formatter = host.getFormatter();
      ActivitySummaryBinder.bind(
          formatter,
          viewHolder.tv2,
          viewHolder.tv3,
          viewHolder.tv4,
          Formatter.Format.TXT_LONG,
          Formatter.Format.TXT_LONG,
          entry.summaryDistance,
          entry.summaryTime);
      if (entry.summaryAvgHr > 0) {
        viewHolder.tvHr.setVisibility(View.VISIBLE);
        viewHolder.tvHr.setText(
            formatter.formatHeartRate(Formatter.Format.TXT_LONG, entry.summaryAvgHr));
      } else if (host.isLapHrPresent()) {
        viewHolder.tvHr.setVisibility(View.INVISIBLE);
      } else {
        viewHolder.tvHr.setVisibility(View.GONE);
      }
      return view;
    }

    private View bindLapRow(int position, View convertView, ViewGroup parent, ContentValues lap) {
      View view = convertView;
      ViewHolderLapList viewHolder;

      if (view == null || getItemViewType(position) != WorkoutStepGrouper.LapDisplayEntry.VIEW_LAP) {
        viewHolder = new ViewHolderLapList();
        LayoutInflater inflater = LayoutInflater.from(activity);
        view = inflater.inflate(R.layout.laplist_row, parent, false);
        viewHolder.tv0 = view.findViewById(R.id.lap_list_type);
        viewHolder.tv1 = view.findViewById(R.id.lap_list_id);
        viewHolder.tv2 = view.findViewById(R.id.lap_list_distance);
        viewHolder.tv3 = view.findViewById(R.id.lap_list_time);
        viewHolder.tv4 = view.findViewById(R.id.lap_list_pace);
        viewHolder.tvHr = view.findViewById(R.id.lap_list_hr);
        view.setTag(viewHolder);
      } else {
        viewHolder = (ViewHolderLapList) view.getTag();
      }
      int i = lap.getAsInteger(DB.LAP.INTENSITY);
      Intensity intensity = Intensity.values()[i];
      switch (intensity) {
        case ACTIVE:
          viewHolder.tv0.setText("");
          break;
        case COOLDOWN:
        case RESTING:
        case RECOVERY:
        case WARMUP:
        case REPEAT:
          String lapTypeLabel;
          switch (intensity) {
            case RECOVERY:
              lapTypeLabel = "recover";
              break;
            case WARMUP:
              lapTypeLabel = "warmup";
              break;
            case COOLDOWN:
              lapTypeLabel = "cooling";
              break;
            case RESTING:
              lapTypeLabel = "rest";
              break;
            case REPEAT:
              lapTypeLabel = "repeat";
              break;
            default:
              lapTypeLabel = activity.getResources().getString(intensity.getTextId());
              break;
          }
          viewHolder.tv0.setText(lapTypeLabel);
        default:
          break;
      }
      if (intensity == Intensity.ACTIVE) {
        int activeIdx = 0;
        WorkoutStepGrouper.LapDisplayEntry[] entries = host.getLapDisplayEntries();
        for (int j = 0; j <= position; j++) {
          if (entries[j].viewType != WorkoutStepGrouper.LapDisplayEntry.VIEW_LAP) {
            continue;
          }
          Integer ji = entries[j].lap.getAsInteger(DB.LAP.INTENSITY);
          if (ji != null && ji == DB.INTENSITY.ACTIVE) {
            activeIdx++;
          }
        }
        viewHolder.tv1.setText(Integer.toString(activeIdx));
      } else {
        viewHolder.tv1.setText("");
      }
      double d = lap.containsKey(DB.LAP.DISTANCE) ? lap.getAsDouble(DB.LAP.DISTANCE) : 0;
      long t = lap.containsKey(DB.LAP.TIME) ? lap.getAsLong(DB.LAP.TIME) : 0;
      Formatter formatter = host.getFormatter();
      ActivitySummaryBinder.bind(
          formatter,
          viewHolder.tv2,
          viewHolder.tv3,
          viewHolder.tv4,
          Formatter.Format.TXT_LONG,
          Formatter.Format.TXT_LONG,
          d,
          t);
      int hr = lap.containsKey(DB.LAP.AVG_HR) ? lap.getAsInteger(DB.LAP.AVG_HR) : 0;
      if (hr > 0) {
        viewHolder.tvHr.setVisibility(View.VISIBLE);
        viewHolder.tvHr.setText(formatter.formatHeartRate(Formatter.Format.TXT_LONG, hr));
      } else if (host.isLapHrPresent()) {
        viewHolder.tvHr.setVisibility(View.INVISIBLE);
      } else {
        viewHolder.tvHr.setVisibility(View.GONE);
      }
      return view;
    }
  }
}
