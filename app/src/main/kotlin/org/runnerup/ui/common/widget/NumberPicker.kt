/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.runnerup.ui.common.widget

import android.content.Context
import android.content.res.TypedArray
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import org.runnerup.R

class NumberPicker(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
  fun interface OnChangedListener {
    fun onChanged(picker: NumberPicker, oldVal: Int, newVal: Int)
  }

  fun interface ValueFormatter {
    fun toString(value: Int): String
  }

  private var prevValue = 0
  private var currValue = 0
  private var minValue = MIN_VAL
  private var maxValue = MAX_VAL
  private var wrapValue = true

  private val valueText: EditText
  private var listener: OnChangedListener? = null

  private lateinit var decButton: Button
  private lateinit var incButton: Button

  private var longInc = false
  private var longDec = false
  private val longHandler = Handler(Looper.getMainLooper())
  private val textSize = 25
  private var digits = DIGITS
  private var fmtString = "%0${digits}d"

  init {
    valueText = createValueText(context)
    createButton(context, '+')
    createButton(context, '-')

    setPadding(5, 5, 5, 5)
    layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    addViews()
    updateView()

    if (attrs != null) {
      val arr = context.obtainStyledAttributes(attrs, R.styleable.NumberPicker)
      processAttributes(arr)
      arr.recycle()
    }
  }

  private fun processAttributes(arr: TypedArray?) {
    if (arr == null) return

    if (arr.hasValue(R.styleable.NumberPicker_digits)) {
      setDigits(arr.getInt(R.styleable.NumberPicker_digits, digits))
    }
    if (arr.hasValue(R.styleable.NumberPicker_min_val)) {
      minValue = arr.getInt(R.styleable.NumberPicker_min_val, minValue)
    }
    if (arr.hasValue(R.styleable.NumberPicker_max_val)) {
      maxValue = arr.getInt(R.styleable.NumberPicker_max_val, maxValue)
    }
  }

  private fun addViews() {
    val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    if (orientation == VERTICAL) {
      addView(incButton, lp)
      addView(valueText, lp)
      addView(decButton, lp)
    } else {
      addView(decButton, lp)
      addView(valueText, lp)
      addView(incButton, lp)
    }
  }

  private fun createButton(context: Context, c: Char) {
    val b = Button(context)
    b.text = c.toString()
    b.textSize = textSize.toFloat()
    b.setOnClickListener(buttonClick)
    b.setOnLongClickListener(buttonLongClick)
    b.setOnTouchListener(buttonLongTouchListener)
    b.gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
    if (c == '+') incButton = b else decButton = b
  }

  private fun createValueText(context: Context): EditText {
    val editText = EditText(context)
    editText.textSize = textSize.toFloat()
    editText.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
      if (hasFocus) {
        editText.selectAll()
      } else {
        validateInput(editText)
      }
    }
    editText.inputType = InputType.TYPE_CLASS_NUMBER
    editText.gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
    return editText
  }

  private val longPressUpdater =
      object : Runnable {
        override fun run() {
          if (longInc) {
            setValueImpl(currValue + 1)
          } else if (longDec) {
            setValueImpl(currValue - 1)
          } else {
            return
          }
          val longSpeed = 50L
          longHandler.postDelayed(this, longSpeed)
        }
      }

  private fun setValueImpl(newValue: Int) {
    var value = newValue
    if (value < minValue) {
      value =
          if (wrapValue) {
            maxValue
          } else {
            minValue
          }
    } else if (value > maxValue) {
      value =
          if (wrapValue) {
            minValue
          } else {
            maxValue
          }
    }
    val save = prevValue
    prevValue = currValue
    currValue = value
    listener?.onChanged(this, save, value)
    updateView()
  }

  private fun updateView() {
    valueText.setText(formatter.toString(currValue))
    valueText.selectAll()
  }

  private val buttonClick =
      OnClickListener { v ->
        validateInput(valueText)
        if (!valueText.hasFocus()) {
          valueText.requestFocus()
        }
        val diff = if (v === incButton) 1 else -1
        setValueImpl(currValue + diff)
      }

  private fun buttonLongClick(i: Int) {
    valueText.clearFocus()
    when {
      i < 0 -> longDec = true
      i > 0 -> longInc = true
      else -> {
        longInc = false
        longDec = false
        return
      }
    }

    longHandler.post(longPressUpdater)
  }

  private val buttonLongClick =
      OnLongClickListener { v ->
        if (v === incButton) buttonLongClick(+1) else buttonLongClick(-1)
        true
      }

  private val buttonLongTouchListener =
      OnTouchListener { v, event ->
        if (event.action == MotionEvent.ACTION_UP &&
            ((longInc && v === incButton) || (longDec && v === decButton))) {
          buttonLongClick(0)
          true
        } else {
          false
        }
      }

  private fun validateInput(tv: EditText) {
    val str = tv.text.toString()
    if ("" == str) {
      updateView()
    } else {
      try {
        val l = str.toInt()
        setValueImpl(l)
      } catch (_: NumberFormatException) {
      }
    }
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    incButton.isEnabled = enabled
    decButton.isEnabled = enabled
    valueText.isEnabled = enabled
    if (!enabled) {
      longInc = false
      longDec = false
    }
  }

  override fun setOrientation(orientation: Int) {
    if (getOrientation() != orientation) {
      super.setOrientation(orientation)
      readd()
    }
  }

  private val formatter =
      object : ValueFormatter {
        private val builder = StringBuilder()
        private val fmt = java.util.Formatter(builder)
        private val args = arrayOfNulls<Any>(1)

        override fun toString(value: Int): String {
          args[0] = value
          builder.delete(0, builder.length)
          fmt.format(fmtString, *args)
          return fmt.toString()
        }
      }

  fun setRange(min: Int, max: Int, wrap: Boolean) {
    minValue = min
    maxValue = max
    wrapValue = wrap
  }

  fun setDigits(digits: Int) {
    this.digits = digits
    fmtString = "%0${digits}d"
    updateView()
    readd()
  }

  var value: Int
    get() {
      validateInput(valueText)
      return currValue
    }
    set(newValue) {
      setValueImpl(newValue)
    }

  private fun readd() {
    removeView(incButton)
    removeView(decButton)
    removeView(valueText)
    addViews()
  }

  companion object {
    private const val DIGITS = 2
    private const val MIN_VAL = 0
    private const val MAX_VAL = 59
  }
}
