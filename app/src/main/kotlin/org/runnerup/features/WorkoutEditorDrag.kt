/*
 * Copyright (C) 2024 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.view.MotionEvent
import android.view.View
import android.view.ViewParent
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

internal object WorkoutEditorDrag {

  fun bindHandle(
      handle: View,
      touchHelper: ItemTouchHelper,
      holder: RecyclerView.ViewHolder,
  ) {
    handle.setOnTouchListener { v, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          setDisallowIntercept(v, true)
          touchHelper.startDrag(holder)
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setDisallowIntercept(v, false)
      }
      true
    }
  }

  private fun setDisallowIntercept(view: View, disallow: Boolean) {
    var p: ViewParent? = view.parent
    while (p != null) {
      p.requestDisallowInterceptTouchEvent(disallow)
      p = p.parent
    }
  }
}
