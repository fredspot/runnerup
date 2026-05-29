/*
 * Copyright (C) 2024 RunnerUp
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.features

import android.view.View

internal object WorkoutEditorLongPress {

  fun bind(view: View, onLongPress: Runnable) {
    view.isLongClickable = true
    view.setOnLongClickListener {
      onLongPress.run()
      true
    }
  }
}
