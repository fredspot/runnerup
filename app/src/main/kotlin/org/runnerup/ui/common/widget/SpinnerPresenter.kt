/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
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
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.DatePicker
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SpinnerAdapter
import android.widget.TimePicker
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.runnerup.R
import org.runnerup.core.util.SafeParse

/** @author Miroslav Mazel */
internal class SpinnerPresenter(
    private val mContext: Context,
    attrs: AttributeSet?,
    private val mSpin: SpinnerInterface,
) {
  private enum class Type {
    TS_SPINNER,
    TS_SPINNER_TXT,
    TS_EDITTEXT,
    TS_DATEPICKER,
    TS_TIMEPICKER,
    TS_DURATIONPICKER,
    TS_DISTANCEPICKER,
    TS_NUMBERPICKER,
  }

  private var mKey: String? = null
  private var mInputType = 0
  private var mSetValueListener: SpinnerInterface.OnSetValueListener? = null
  private val mType: Type
  private var mFirstSetValue = true
  private var values: IntArray? = null
  private var mCurrValue: Long = -1
  private val mLabel: CharSequence?

  init {
    mSpin.setViewOnItemSelectedListener(
        object : AdapterView.OnItemSelectedListener {
          override fun onItemSelected(arg0: AdapterView<*>?, arg1: View?, arg2: Int, arg3: Long) {
            onItemSelected(arg2)
          }

          override fun onNothingSelected(arg0: AdapterView<*>?) {}
        })

    val arr = mContext.obtainStyledAttributes(attrs, R.styleable.TitleSpinner)
    mLabel = arr.getString(R.styleable.TitleSpinner_android_text)
    if (mLabel != null) {
      mSpin.setViewLabel(mLabel)
    }

    val type = arr.getString(R.styleable.TitleSpinner_type)
    val defaultValue = arr.getString(R.styleable.TitleSpinner_android_defaultValue)

    mType =
        when {
          type == null || "spinner".contentEquals(type) -> {
            setupSpinner(mContext, arr)
            Type.TS_SPINNER
          }
          "spinner_txt".contentEquals(type) -> {
            setupSpinner(mContext, arr)
            Type.TS_SPINNER_TXT
          }
          "edittext".contentEquals(type) -> {
            setupEditText(mContext, attrs, arr, defaultValue)
            Type.TS_EDITTEXT
          }
          "datepicker".contentEquals(type) -> {
            setupDatePicker(mContext, attrs, defaultValue)
            Type.TS_DATEPICKER
          }
          "timepicker".contentEquals(type) -> {
            setupTimePicker(mContext, attrs, defaultValue)
            Type.TS_TIMEPICKER
          }
          "durationpicker".contentEquals(type) -> {
            setupDurationPicker(mContext, attrs, defaultValue)
            Type.TS_DURATIONPICKER
          }
          "distancepicker".contentEquals(type) -> {
            setupDistancePicker(mContext, attrs, defaultValue)
            Type.TS_DISTANCEPICKER
          }
          "numberpicker".contentEquals(type) -> {
            setupNumberPicker(mContext, attrs, defaultValue)
            Type.TS_NUMBERPICKER
          }
          else -> throw IllegalArgumentException("unknown type")
        }

    val key = arr.getString(R.styleable.TitleSpinner_android_key)
    if (key != null) {
      mKey = key
      loadValue(defaultValue)
    }

    arr.recycle()
  }

  private fun setupEditText(
      context: Context,
      attrs: AttributeSet?,
      arr: TypedArray,
      defaultValue: CharSequence?,
  ) {
    mInputType =
        arr.getInt(
            R.styleable.TitleSpinner_android_inputType,
            EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_FLAG_DECIMAL)
    setValueWithoutSave(defaultValue)

    val edit = EditText(context, attrs)
    mSpin.setViewOnClickListener {
      edit.setText(mSpin.getViewValueText())
      edit.inputType = mInputType
      edit.minimumHeight = 48
      edit.minimumWidth = 148
      if (edit.parent != null) {
        (edit.parent as LinearLayout).removeView(edit)
      }

      val layout = createLayout(context)
      layout.addView(edit)

      AlertDialog.Builder(context)
          .setTitle(mLabel)
          .setView(layout)
          .setPositiveButton(org.runnerup.common.R.string.OK) { dialog, _ ->
            setValue(edit.text.toString())
            dialog.dismiss()
            layout.removeView(edit)
            onClose(true)
          }
          .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ ->
            dialog.dismiss()
            layout.removeView(edit)
            onClose(false)
          }
          .show()
    }
  }

  private fun setupSpinner(context: Context, arr: TypedArray) {
    val defaultValue = arr.getString(R.styleable.TitleSpinner_android_defaultValue)
    val entriesId = arr.getResourceId(R.styleable.TitleSpinner_android_entries, 0)
    val valuesId = arr.getResourceId(R.styleable.TitleSpinner_values, 0)
    if (valuesId != 0) {
      values = context.resources.getIntArray(valuesId)
    }
    if (entriesId != 0) {
      val adapter = DisabledEntriesAdapter(context, entriesId)
      mSpin.setViewAdapter(adapter)
      var value = 0
      if (defaultValue != null) {
        value = SafeParse.parseInt(defaultValue, 0)
      }
      setValue(value)
    }
    mSpin.setOnClickSpinnerOpen()
    mSpin.setViewPrompt(mLabel)
  }

  private fun onItemSelected(item: Int) {
    if (mType == Type.TS_SPINNER_TXT) {
      if (mSpin.getViewAdapter() != null) {
        setValue(mSpin.getViewAdapter()!!.getItem(item).toString())
      }
    } else {
      setValue(getRealValue(item))
    }
    if (!mFirstSetValue) {
      onClose(true)
    }
    mFirstSetValue = false
  }

  private fun setupDatePicker(context: Context, attrs: AttributeSet?, defaultValue: CharSequence?) {
    var resolvedDefault = defaultValue
    if (resolvedDefault != null && "today".contentEquals(resolvedDefault)) {
      val df = android.text.format.DateFormat.getDateFormat(context)
      resolvedDefault = df.format(Date())
    }
    setValueWithoutSave(resolvedDefault)

    val datePicker = DatePicker(context, attrs)

    mSpin.setViewOnClickListener {
      if (datePicker.parent != null) {
        (datePicker.parent as LinearLayout).removeView(datePicker)
      }

      val layout = createLayout(context)
      layout.addView(datePicker)

      AlertDialog.Builder(context)
          .setTitle(mLabel)
          .setView(layout)
          .setPositiveButton(
              org.runnerup.common.R.string.OK,
              DialogInterface.OnClickListener { dialog, _ ->
                setValue(getValue(datePicker))
                dialog.dismiss()
                layout.removeView(datePicker)
                onClose(true)
              })
          .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ ->
            dialog.dismiss()
            layout.removeView(datePicker)
            onClose(false)
          }
          .show()
    }
  }

  private fun setupTimePicker(context: Context, attrs: AttributeSet?, defaultValue: CharSequence?) {
    var resolvedDefault = defaultValue
    if (resolvedDefault != null && "now".contentEquals(resolvedDefault)) {
      val df = android.text.format.DateFormat.getTimeFormat(context)
      resolvedDefault = df.format(Date())
    }
    setValueWithoutSave(resolvedDefault)

    val timePicker = TimePicker(context, attrs)

    mSpin.setViewOnClickListener {
      timePicker.setIs24HourView(true)
      if (timePicker.parent != null) {
        (timePicker.parent as LinearLayout).removeView(timePicker)
      }

      val layout = createLayout(context)
      layout.addView(timePicker)

      AlertDialog.Builder(context)
          .setTitle(mLabel)
          .setView(layout)
          .setPositiveButton(
              org.runnerup.common.R.string.OK,
              DialogInterface.OnClickListener { dialog, _ ->
                setValue(getValue(timePicker, mContext))
                dialog.dismiss()
                layout.removeView(timePicker)
                onClose(true)
              })
          .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ ->
            dialog.dismiss()
            layout.removeView(timePicker)
            onClose(false)
          }
          .show()
    }
  }

  private fun setupDurationPicker(
      context: Context,
      attrs: AttributeSet?,
      defaultValue: CharSequence?,
  ) {
    setValueWithoutSave(defaultValue)

    mSpin.setViewOnClickListener {
      val picker = DurationPicker(context, attrs)
      picker.setEpochTime(mCurrValue)
      if (picker.parent != null) {
        (picker.parent as LinearLayout).removeView(picker)
      }

      val layout = createLayout(context)
      layout.addView(picker)

      val alertDialog =
          AlertDialog.Builder(context, R.style.AlertDialogTheme)
              .setTitle(mLabel)
              .setView(layout)
              .setPositiveButton(
                  org.runnerup.common.R.string.OK,
                  DialogInterface.OnClickListener { dialog, _ ->
                    setValue(getPickerValue(picker))
                    dialog.dismiss()
                    layout.removeView(picker)
                    onClose(true)
                  })
              .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ ->
                dialog.dismiss()
                layout.removeView(picker)
                onClose(false)
              }
              .create()
      alertDialog.show()
    }
  }

  private fun setupDistancePicker(
      context: Context,
      attrs: AttributeSet?,
      defaultValue: CharSequence?,
  ) {
    setValueWithoutSave(defaultValue)

    val distancePicker = DistancePicker(context, attrs)

    mSpin.setViewOnClickListener {
      distancePicker.setDistance(mCurrValue)
      if (distancePicker.parent != null) {
        (distancePicker.parent as LinearLayout).removeView(distancePicker)
      }

      val layout = createLayout(context)
      layout.addView(distancePicker)

      val alertDialog =
          AlertDialog.Builder(context, R.style.AlertDialogTheme)
              .setTitle(mLabel)
              .setView(layout)
              .setPositiveButton(
                  org.runnerup.common.R.string.OK,
                  DialogInterface.OnClickListener { dialog, _ ->
                    setValue(getValue(distancePicker))
                    dialog.dismiss()
                    layout.removeView(distancePicker)
                    onClose(true)
                  })
              .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ ->
                dialog.dismiss()
                layout.removeView(distancePicker)
                onClose(false)
              }
              .create()
      alertDialog.show()
    }
  }

  private fun setupNumberPicker(
      context: Context,
      attrs: AttributeSet?,
      defaultValue: CharSequence?,
  ) {
    setValueWithoutSave(defaultValue)

    val numberPicker = NumberPicker(context, attrs)
    numberPicker.orientation = LinearLayout.VERTICAL

    mSpin.setViewOnClickListener {
      numberPicker.value = mCurrValue.toInt()
      if (numberPicker.parent != null) {
        (numberPicker.parent as LinearLayout).removeView(numberPicker)
      }

      val layout = createLayout(context)
      layout.addView(numberPicker)

      AlertDialog.Builder(context)
          .setTitle(mLabel)
          .setView(layout)
          .setPositiveButton(
              org.runnerup.common.R.string.OK,
              DialogInterface.OnClickListener { dialog, _ ->
                setValue(getValue(numberPicker))
                dialog.dismiss()
                layout.removeView(numberPicker)
                onClose(true)
              })
          .setNegativeButton(org.runnerup.common.R.string.Cancel) { dialog, _ ->
            dialog.dismiss()
            layout.removeView(numberPicker)
            onClose(false)
          }
          .show()
    }
  }

  fun setOnSetValueListener(listener: SpinnerInterface.OnSetValueListener?) {
    mSetValueListener = listener
  }

  private fun onClose(b: Boolean) {
    if (mCloseDialogListener != null) {
      mSpin.viewOnClose(mCloseDialogListener, b)
    }
  }

  fun loadValue(defaultValue: String?) {
    val pref = PreferenceManager.getDefaultSharedPreferences(mContext)
    when (mType) {
      Type.TS_SPINNER -> {
        var def = 0
        if (defaultValue != null) {
          def = SafeParse.parseInt(defaultValue, 0)
        }
        setValue(pref.getInt(mKey, def))
      }
      Type.TS_SPINNER_TXT,
      Type.TS_EDITTEXT,
      Type.TS_DURATIONPICKER,
      Type.TS_DISTANCEPICKER,
      Type.TS_NUMBERPICKER,
      Type.TS_DATEPICKER,
      Type.TS_TIMEPICKER -> {
        val `val` = pref.getString(mKey, defaultValue ?: "")
        setValue(`val`)
      }
    }
  }

  fun setValue(value: Int) {
    var newValue = value
    if (mSetValueListener != null) {
      try {
        newValue = mSetValueListener!!.preSetValue(newValue)
      } catch (_: IllegalArgumentException) {
        if (mCurrValue.toInt() != -1) {
          mSpin.setViewSelection(mCurrValue.toInt())
        }
        return
      }
    }
    mCurrValue = newValue.toLong()
    val selectionValue = getSelectionValue(newValue)
    mSpin.setViewSelection(selectionValue)
    if (mSpin.getViewAdapter() != null) {
      mSpin.setViewValue(selectionValue)
    }
    if (mKey == null) return
    val pref: SharedPreferences.Editor =
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
    pref.putInt(mKey, newValue)
    pref.apply()
  }

  fun setValue(value: String?) {
    setValue(value, true)
  }

  private fun setValueWithoutSave(value: CharSequence?) {
    val str = value?.toString() ?: ""
    setValue(str, false)
  }

  private fun setValue(value: String?, savePreferences: Boolean) {
    var newValue = value
    if (mSetValueListener != null) {
      try {
        newValue = mSetValueListener!!.preSetValue(newValue ?: "")
      } catch (_: IllegalArgumentException) {
        if (mSpin.getViewAdapter() != null) {
          mSpin.setViewSelection(mCurrValue.toInt())
        }
        return
      }
    }

    mCurrValue =
        when {
          newValue == null -> 0
          mType == Type.TS_DURATIONPICKER -> SafeParse.parseSeconds(newValue, 0).toLong()
          mType != Type.TS_TIMEPICKER -> SafeParse.parseDouble(newValue, 0.0).toLong()
          else -> mCurrValue
        }
    if (mType == Type.TS_DISTANCEPICKER && !TextUtils.isEmpty(newValue)) {
      mSpin.setViewText(
          String.format(
              "%s %s",
              newValue,
              mContext.resources.getString(org.runnerup.common.R.string.metrics_distance_m)))
    } else {
      mSpin.setViewText(newValue)
    }
    if (mType == Type.TS_SPINNER_TXT) {
      if (mSpin.getViewAdapter() != null) {
        val intVal = find(mSpin.getViewAdapter()!!, newValue!!)
        mCurrValue = intVal.toLong()
        mSpin.setViewSelection(intVal)
      }
    }

    if (mKey == null || !savePreferences) return
    val pref = PreferenceManager.getDefaultSharedPreferences(mContext).edit()
    pref.putString(mKey, newValue)
    pref.apply()
  }

  private fun find(adapter: SpinnerAdapter, value: String): Int {
    for (i in 0 until adapter.count) {
      if (value.contentEquals(adapter.getItem(i).toString())) {
        return i
      }
    }
    return 0
  }

  fun getSelectionValue(value: Int): Int {
    val valuesArray = values ?: return value
    var p = 0
    for (v in valuesArray) {
      if (v == value) return p
      p++
    }
    return 0
  }

  private fun getRealValue(value: Int): Int {
    val valuesArray = values ?: return value
    return if (value >= 0 && value < valuesArray.size) valuesArray[value] else valuesArray[0]
  }

  fun getValue(): CharSequence {
    when (mType) {
      Type.TS_SPINNER_TXT,
      Type.TS_EDITTEXT,
      Type.TS_DATEPICKER,
      Type.TS_TIMEPICKER -> return mSpin.getViewValueText()!!
      Type.TS_DURATIONPICKER,
      Type.TS_DISTANCEPICKER,
      Type.TS_NUMBERPICKER,
      Type.TS_SPINNER -> {}
    }
    return String.format(Locale.getDefault(), "%d", mCurrValue)
  }

  fun getValueInt(): Int = mCurrValue.toInt()

  fun clear() {
    if (mKey != null) {
      PreferenceManager.getDefaultSharedPreferences(mContext).edit().remove(mKey).apply()
    }
  }

  private var mCloseDialogListener: SpinnerInterface.OnCloseDialogListener? = null

  fun setOnCloseDialogListener(listener: SpinnerInterface.OnCloseDialogListener?) {
    mCloseDialogListener = listener
  }

  companion object {
    private fun createLayout(context: Context): LinearLayout {
      val layout = LinearLayout(context)
      layout.orientation = LinearLayout.HORIZONTAL
      layout.layoutParams =
          LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
      layout.gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
      return layout
    }

    private fun getValue(datePicker: DatePicker): String {
      val c = Calendar.getInstance()
      c.set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
      val df = android.text.format.DateFormat.getDateFormat(datePicker.context)
      return df.format(c.time)
    }

    private fun getValue(timePicker: TimePicker, context: Context): String {
      val c = Calendar.getInstance()
      c.set(2000, 1, 1, timePicker.currentHour, timePicker.currentMinute)
      val df = android.text.format.DateFormat.getTimeFormat(context)
      return df.format(c.time)
    }

    private fun getPickerValue(picker: DurationPicker): String =
        DateUtils.formatElapsedTime(picker.getEpochTime())

    private fun getValue(distancePicker: DistancePicker): String =
        distancePicker.getDistance().toString()

    private fun getValue(numberPicker: NumberPicker): String = numberPicker.value.toString()
  }
}
