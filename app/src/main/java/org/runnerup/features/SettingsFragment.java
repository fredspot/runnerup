package org.runnerup.features;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceFragmentCompat;
import org.runnerup.R;

public class SettingsFragment extends PreferenceFragmentCompat {

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    setPreferencesFromResource(R.xml.settings, rootKey);
  }

  @Override
  public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    view.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.backgroundPrimary));

    View listView = view.findViewById(android.R.id.list);
    if (listView instanceof ListView lv) {
      lv.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.backgroundPrimary));
      lv.setCacheColorHint(Color.TRANSPARENT);
      lv.setDivider(ContextCompat.getDrawable(requireContext(), android.R.color.transparent));
      lv.setDividerHeight(16);
    }
  }
}
