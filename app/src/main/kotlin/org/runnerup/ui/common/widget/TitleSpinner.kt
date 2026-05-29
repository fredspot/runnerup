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
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.SpinnerAdapter
import android.widget.TextView
import org.runnerup.R

class TitleSpinner(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs),
    SpinnerInterface {
  internal val mPresenter: SpinnerPresenter
  private val mLayout: LinearLayout
  private val mLabel: TextView
  private val mValue: TextView
  private val mSpinner: Spinner

  init {
    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    inflater.inflate(R.layout.title_spinner, this)

    mLayout = findViewById(R.id.title_spinner_layout)
    mLabel = findViewById(R.id.title)
    mValue = findViewById(R.id.value)
    mSpinner = findViewById(R.id.spinner)
    mSpinner.isSaveEnabled = false

    mPresenter = SpinnerPresenter(context, attrs, this)
  }

  override fun setOnClickSpinnerOpen() {
    setViewOnClickListener { mSpinner.performClick() }
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    mLayout.isEnabled = enabled
    mSpinner.isEnabled = enabled
  }

  override fun setViewPrompt(charSequence: CharSequence?) {
    mSpinner.prompt = charSequence
  }

  override fun setViewLabel(label: CharSequence?) {
    mLabel.text = label
  }

  override fun setViewValue(itemId: Int) {
    val `val` = mSpinner.adapter.getItem(itemId)
    if (`val` != null) setViewText(`val`.toString()) else setViewText("")
  }

  override fun setViewText(charSequence: CharSequence?) {
    mValue.text = charSequence
  }

  override fun getViewValueText(): CharSequence? = mValue.text

  override fun setViewOnClickListener(onClickListener: OnClickListener?) {
    mLayout.setOnClickListener(onClickListener)
  }

  override fun setViewAdapter(adapter: BaseAdapter) {
    mSpinner.adapter = adapter
  }

  override fun getViewAdapter(): SpinnerAdapter? = mSpinner.adapter

  override fun setViewSelection(value: Int) {
    mSpinner.setSelection(value)
  }

  override fun viewOnClose(listener: SpinnerInterface.OnCloseDialogListener?, b: Boolean) {
    listener!!.onClose(this, b)
  }

  override fun setViewOnItemSelectedListener(listener: AdapterView.OnItemSelectedListener?) {
    mSpinner.onItemSelectedListener = listener
  }

  override fun getViewOnItemSelectedListener(): AdapterView.OnItemSelectedListener? =
      mSpinner.onItemSelectedListener

  fun setAdapter(adapter: SpinnerAdapter?) {
    mSpinner.adapter = adapter
    mPresenter.loadValue(null)
  }

  val value: CharSequence
    get() = mPresenter.getValue()

  val valueInt: Int
    get() = mPresenter.getValueInt()

  fun setValue(value: Int) {
    mPresenter.setValue(value)
  }

  fun setValue(value: String?) {
    mPresenter.setValue(value)
  }

  fun addDisabledValue(value: Int) {
    val selection = mPresenter.getSelectionValue(value)
    (mSpinner.adapter as DisabledEntriesAdapter).addDisabled(selection)
  }

  fun clearDisabled() {
    (mSpinner.adapter as DisabledEntriesAdapter).clearDisabled()
  }

  fun clear() {
    mPresenter.clear()
  }

  fun setOnSetValueListener(listener: SpinnerInterface.OnSetValueListener?) {
    mPresenter.setOnSetValueListener(listener)
  }

  fun setOnCloseDialogListener(listener: SpinnerInterface.OnCloseDialogListener?) {
    mPresenter.setOnCloseDialogListener(listener)
  }

  // Instead of android:entries="@array/anArray"
  fun setArrayEntries(entries: Array<out String?>) {
    val adapter =
        ArrayAdapter(context, android.R.layout.simple_spinner_item, entries)
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    mSpinner.adapter = adapter
  }
}
