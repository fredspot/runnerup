package org.runnerup.view;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import org.runnerup.BuildConfig;
import org.runnerup.R;
import org.runnerup.widget.AboutPreference;

public class SettingsFragment extends PreferenceFragmentCompat {

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.settings, rootKey);

    if (BuildConfig.MAPBOX_ENABLED == 0) {
      Preference pref = findPreference("map_preferencescreen");
      pref.setEnabled(false);
    }
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    // Set dark background for preferences list
    view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.backgroundPrimary));
    
    // Find the ListView and style it for dark theme
    View listView = view.findViewById(android.R.id.list);
    if (listView != null && listView instanceof ListView) {
      ListView lv = (ListView) listView;
      lv.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.backgroundPrimary));
      lv.setCacheColorHint(Color.TRANSPARENT);
      lv.setDivider(ContextCompat.getDrawable(requireContext(), android.R.color.transparent));
      lv.setDividerHeight(16);
    }
  }

  @Override
  public void onDisplayPreferenceDialog(@NonNull Preference preference) {
    if (preference instanceof AboutPreference) {
      // The about preference was clicked, show the about dialog
      AboutPreference.AboutDialogFragment aboutDialogFragment =
          AboutPreference.AboutDialogFragment.newInstance(preference.getKey());
      aboutDialogFragment.setTargetFragment(this, 0);
      aboutDialogFragment.show(getParentFragmentManager(), AboutPreference.AboutDialogFragment.TAG);
    } else {
      super.onDisplayPreferenceDialog(preference);
    }
  }
}
