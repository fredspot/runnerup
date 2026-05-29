/*
 * Copyright (C) 2026 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.core.util

import android.graphics.drawable.RippleDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.runnerup.R

/** Consistent press handling for rounded card rows in RecyclerViews. */
object CardPressHelper {

  @JvmStatic
  fun applyCardStyle(view: View) {
    view.setBackgroundResource(R.drawable.card_background)
    view.foreground = view.context.getDrawable(R.drawable.card_ripple_overlay)
    view.defaultFocusHighlightEnabled = false
    view.stateListAnimator = null
    view.clipToOutline = true
    (view as? ViewGroup)?.clipChildren = true
    attachCenteredRippleHotspot(view)
    (view as? ViewGroup)?.let { clearChildRipple(it) }
  }

  /** Best Times distance label: transparent idle, rounded ripple on press. */
  @JvmStatic
  fun applyChipStyle(view: View) {
    view.setBackgroundResource(android.R.color.transparent)
    view.foreground = view.context.getDrawable(R.drawable.chip_ripple_overlay)
    view.defaultFocusHighlightEnabled = false
    view.stateListAnimator = null
    attachCenteredRippleHotspot(view)
  }

  /** Call on interactive card roots after inflation (History, Best Times, etc.). */
  @JvmStatic
  fun prepareCard(view: View) {
    applyCardStyle(view)
  }

  /** Strip accidental theme ripples from non-card row hosts (RecyclerView item roots). */
  @JvmStatic
  fun prepareRowHost(view: View) {
    view.isClickable = false
    view.isFocusable = false
    view.background = null
    view.foreground = null
    view.defaultFocusHighlightEnabled = false
    view.stateListAnimator = null
  }

  @JvmStatic
  fun clearPressState(vararg views: View) {
    for (view in views) {
      view.isPressed = false
      view.isSelected = false
      view.isActivated = false
    }
  }

  /**
   * RippleDrawable defaults to the finger position, so the flash hugs one TextView. Center the
   * hotspot so the masked ripple fills the whole card/chip.
   */
  private fun attachCenteredRippleHotspot(view: View) {
    view.setOnTouchListener { v, event ->
      if (event.actionMasked == MotionEvent.ACTION_DOWN) {
        findRippleDrawable(v)?.setHotspot(v.width / 2f, v.height / 2f)
      }
      false
    }
  }

  private fun findRippleDrawable(view: View): RippleDrawable? {
    when (val fg = view.foreground) {
      is RippleDrawable -> return fg
    }
    when (val bg = view.background) {
      is RippleDrawable -> return bg
    }
    return null
  }

  /** Remove backgrounds/ripples on content so only the card root shows press feedback. */
  private fun clearChildRipple(group: ViewGroup) {
    for (i in 0 until group.childCount) {
      val child = group.getChildAt(i)
      child.background = null
      child.foreground = null
      child.isClickable = false
      child.isFocusable = false
      child.defaultFocusHighlightEnabled = false
      if (child is TextView) {
        child.highlightColor = android.graphics.Color.TRANSPARENT
      }
      if (child is ViewGroup) {
        clearChildRipple(child)
      }
    }
  }
}
