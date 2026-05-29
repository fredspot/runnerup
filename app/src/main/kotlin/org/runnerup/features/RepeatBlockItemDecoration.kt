/*
 * Copyright (C) 2024 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.content.Context
import android.view.View
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.runnerup.R

/** Draws a shared rounded background behind each repeat block's rows. */
class RepeatBlockItemDecoration(context: Context) : RecyclerView.ItemDecoration() {

  private val fillPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.subsectionBg)
        style = Paint.Style.FILL
      }
  private val strokePaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.stepRepeat)
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 1.5f
      }
  private val cornerRadius = context.resources.displayMetrics.density * 8f
  private val verticalPad = context.resources.displayMetrics.density * 6f
  private val horizontalPad = context.resources.displayMetrics.density * 4f
  private val blockSpacingPx = (14 * context.resources.displayMetrics.density + 0.5f).toInt()

  private var ranges: List<IntRange> = emptyList()
  private var rows: List<WorkoutEditorRow> = emptyList()

  fun setRows(newRows: List<WorkoutEditorRow>) {
    rows = newRows
    val out = ArrayList<IntRange>()
    var blockStart = -1
    for (i in newRows.indices) {
      when (newRows[i]) {
        is WorkoutEditorRow.RepeatHeader -> blockStart = i
        is WorkoutEditorRow.RepeatChild -> {}
        else -> {
          if (blockStart >= 0) {
            out.add(blockStart until i)
            blockStart = -1
          }
        }
      }
    }
    if (blockStart >= 0) {
      out.add(blockStart until newRows.size)
    }
    ranges = out
  }

  override fun getItemOffsets(
      outRect: Rect,
      view: View,
      parent: RecyclerView,
      state: RecyclerView.State,
  ) {
    val pos = parent.getChildAdapterPosition(view)
    if (pos == RecyclerView.NO_POSITION || pos >= rows.size) {
      return
    }
    val row = rows[pos]
    if (pos > 0 && isBlockStart(row)) {
      outRect.top = blockSpacingPx
    }
    if (isBlockEnd(pos)) {
      outRect.bottom = blockSpacingPx
    }
  }

  private fun isBlockStart(row: WorkoutEditorRow): Boolean =
      row is WorkoutEditorRow.TopStep || row is WorkoutEditorRow.RepeatHeader

  private fun isBlockEnd(pos: Int): Boolean {
    val row = rows[pos]
    val next = rows.getOrNull(pos + 1)
    return when (row) {
      is WorkoutEditorRow.TopStep,
      is WorkoutEditorRow.RepeatChild,
      is WorkoutEditorRow.RepeatHeader,
      -> next == null || isBlockStart(next)
      else -> false
    }
  }

  override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
    for (range in ranges) {
      var top = Float.MAX_VALUE
      var bottom = Float.MIN_VALUE
      for (pos in range) {
        val child = parent.findViewHolderForAdapterPosition(pos)?.itemView ?: continue
        top = minOf(top, child.top.toFloat())
        bottom = maxOf(bottom, child.bottom.toFloat())
      }
      if (top == Float.MAX_VALUE) continue
      val left = parent.paddingLeft.toFloat() + horizontalPad
      val right = parent.width - parent.paddingRight.toFloat() - horizontalPad
      val rect = RectF(left, top - verticalPad, right, bottom + verticalPad)
      c.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
      c.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
    }
  }
}
