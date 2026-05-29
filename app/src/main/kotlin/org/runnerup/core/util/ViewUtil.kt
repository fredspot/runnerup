/*
 * Copyright (C) 2025 jonas.oreland@gmail.com
 */

package org.runnerup.core.util

import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object ViewUtil {
  @JvmStatic
  fun Insets(rootView: View, marginLayout: Boolean) {
    ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, windowInsets ->
      val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
      val top = if (marginLayout) 0 else insets.top
      v.setPadding(insets.left, top, insets.right, insets.bottom)
      if (marginLayout) {
        val mlp = v.layoutParams as ViewGroup.MarginLayoutParams
        mlp.topMargin = insets.top
      }
      WindowInsetsCompat.CONSUMED
    }
  }
}
