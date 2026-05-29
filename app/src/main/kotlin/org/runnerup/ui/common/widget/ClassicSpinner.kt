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
import android.os.Build
import android.util.AttributeSet
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.SpinnerAdapter
import androidx.appcompat.widget.AppCompatSpinner

class ClassicSpinner(context: Context, attrs: AttributeSet?) :
    AppCompatSpinner(context, attrs), SpinnerInterface {
  internal val mPresenter: SpinnerPresenter

  init {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      defaultFocusHighlightEnabled = false
    }
    mPresenter = SpinnerPresenter(context, attrs, this)
  }

  override fun setViewPrompt(charSequence: CharSequence?) {
    prompt = charSequence
  }

  override fun setViewLabel(label: CharSequence?) {
    contentDescription = label
  }

  override fun setViewValue(itemId: Int) {
    setSelection(itemId)
  }

  override fun setViewText(charSequence: CharSequence?) {}

  override fun getViewValueText(): CharSequence? = selectedItem.toString()

  override fun setViewOnClickListener(onClickListener: OnClickListener?) {
    setOnClickListener(onClickListener)
  }

  override fun setOnClickSpinnerOpen() {}

  override fun setViewAdapter(adapter: BaseAdapter) {
    setAdapter(adapter)
  }

  override fun getViewAdapter(): SpinnerAdapter? = adapter

  override fun setViewSelection(value: Int) {
    setSelection(value)
  }

  override fun viewOnClose(listener: SpinnerInterface.OnCloseDialogListener?, b: Boolean) {
    listener!!.onClose(this, b)
  }

  override fun setViewOnItemSelectedListener(listener: AdapterView.OnItemSelectedListener?) {
    onItemSelectedListener = listener
  }

  override fun getViewOnItemSelectedListener(): AdapterView.OnItemSelectedListener? =
      onItemSelectedListener
}
