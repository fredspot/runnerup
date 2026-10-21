/*
 * Copyright (C) 2026 RunnerUp
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

/** Lets the detail map pan, and leaves tab swipes to a thin band at the screen edge. */
internal object DetailMapTouchHelper {

  private const val EDGE_DP = 24f

  fun wire(mapView: View) {
    val edgePx = EDGE_DP * mapView.resources.displayMetrics.density
    mapView.setOnTouchListener { view, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN,
        MotionEvent.ACTION_MOVE,
        MotionEvent.ACTION_POINTER_DOWN -> {
          val atEdge = event.rawX <= edgePx || event.rawX >= view.rootView.width - edgePx
          setDisallowIntercept(view, !atEdge)
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setDisallowIntercept(view, false)
      }
      false
    }
  }

  private fun setDisallowIntercept(view: View, disallow: Boolean) {
    var parent: ViewParent? = view.parent
    while (parent != null) {
      parent.requestDisallowInterceptTouchEvent(disallow)
      parent = parent.parent
    }
  }
}
