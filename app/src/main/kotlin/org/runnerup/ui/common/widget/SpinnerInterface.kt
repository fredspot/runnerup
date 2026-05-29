package org.runnerup.ui.common.widget

import android.view.View
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.SpinnerAdapter

/** @author Miroslav Mazel */
interface SpinnerInterface {
  fun setViewPrompt(charSequence: CharSequence?)

  fun setViewLabel(charSequence: CharSequence?)

  fun setViewValue(itemId: Int)

  fun setViewText(charSequence: CharSequence?)

  fun getViewValueText(): CharSequence?

  fun setViewOnClickListener(onClickListener: View.OnClickListener?)

  fun setViewAdapter(adapter: BaseAdapter)

  fun getViewAdapter(): SpinnerAdapter?

  fun setViewSelection(value: Int)

  fun viewOnClose(listener: OnCloseDialogListener?, b: Boolean)

  fun getViewOnItemSelectedListener(): AdapterView.OnItemSelectedListener?

  fun setViewOnItemSelectedListener(listener: AdapterView.OnItemSelectedListener?)

  fun setOnClickSpinnerOpen()

  fun interface OnCloseDialogListener {
    fun onClose(spinner: SpinnerInterface, ok: Boolean)
  }

  interface OnSetValueListener {
    /** @throws IllegalArgumentException */
    @Throws(IllegalArgumentException::class)
    fun preSetValue(newValue: String): String

    /** @throws IllegalArgumentException */
    @Throws(IllegalArgumentException::class)
    fun preSetValue(newValue: Int): Int
  }
}
