package org.runnerup.features

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.runnerup.R

data class TendonRow(
    val tendonId: Long,
    val name: String,
    val description: String?,
    val zone: Int,
    var pain: Int,
)

class InjuryTendonAdapter(
    private val rows: List<TendonRow>,
    private val onPainChanged: () -> Unit,
) : RecyclerView.Adapter<InjuryTendonAdapter.Holder>() {

  override fun getItemCount(): Int = rows.size

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
    val view = LayoutInflater.from(parent.context).inflate(R.layout.injury_tendon_row, parent, false)
    return Holder(view)
  }

  override fun onBindViewHolder(holder: Holder, position: Int) {
    val row = rows[position]
    holder.nameView.text = row.name
    if (!row.description.isNullOrEmpty()) {
      holder.descView.text = row.description
      holder.descView.visibility = View.VISIBLE
    } else {
      holder.descView.visibility = View.GONE
    }

    holder.painSeek.setOnSeekBarChangeListener(null)
    holder.painSeek.max = 10
    holder.painSeek.progress = row.pain
    updatePainText(holder.painVal, row.pain)

    holder.painSeek.setOnSeekBarChangeListener(
        object : SeekBar.OnSeekBarChangeListener {
          override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) {
              row.pain = progress
              updatePainText(holder.painVal, progress)
              onPainChanged()
            }
          }

          override fun onStartTrackingTouch(seekBar: SeekBar) {}

          override fun onStopTrackingTouch(seekBar: SeekBar) {}
        },
    )
  }

  class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val nameView: TextView = itemView.findViewById(R.id.tendon_name)
    val descView: TextView = itemView.findViewById(R.id.tendon_desc)
    val painSeek: SeekBar = itemView.findViewById(R.id.tendon_pain_seek)
    val painVal: TextView = itemView.findViewById(R.id.tendon_pain_val)
  }

  companion object {
    private fun updatePainText(tv: TextView, pain: Int) {
      tv.text = if (pain == 0) "-" else pain.toString()
    }
  }
}

class TendonSpacingDecoration(private val spacePx: Int) : RecyclerView.ItemDecoration() {
  override fun getItemOffsets(
      outRect: Rect,
      view: View,
      parent: RecyclerView,
      state: RecyclerView.State,
  ) {
    val position = parent.getChildAdapterPosition(view)
    if (position > 0) {
      outRect.top = spacePx
    }
  }
}
