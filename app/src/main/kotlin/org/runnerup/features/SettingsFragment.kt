package org.runnerup.features

import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.ListView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import org.runnerup.R

open class SettingsFragment : PreferenceFragmentCompat() {

  private var listSpacingDecoration: RecyclerView.ItemDecoration? = null

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    setPreferencesFromResource(R.xml.settings, rootKey)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    stylePreferenceList(view)
  }

  protected fun stylePreferenceList(view: View) {
    val bg = ContextCompat.getColor(requireContext(), R.color.backgroundPrimary)
    view.setBackgroundColor(bg)

    val spacingPx = (16 * resources.displayMetrics.density).toInt()

    val recyclerView = view.findViewById<RecyclerView>(androidx.preference.R.id.recycler_view)
    if (recyclerView != null) {
      recyclerView.setBackgroundColor(bg)
      recyclerView.clipToPadding = true
      if (listSpacingDecoration == null) {
        listSpacingDecoration = PreferenceListSpacingDecoration(spacingPx)
        recyclerView.addItemDecoration(listSpacingDecoration!!)
      }
      return
    }

    val listView = view.findViewById<View>(android.R.id.list)
    if (listView is ListView) {
      listView.setBackgroundColor(bg)
      listView.cacheColorHint = Color.TRANSPARENT
      listView.divider =
          ContextCompat.getDrawable(requireContext(), android.R.color.transparent)
      listView.dividerHeight = spacingPx
    }
  }

  private class PreferenceListSpacingDecoration(private val spacingPx: Int) :
      RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
      val position = parent.getChildAdapterPosition(view)
      if (position == RecyclerView.NO_POSITION) return
      if (position < state.itemCount - 1) {
        outRect.bottom = spacingPx
      }
    }
  }
}
