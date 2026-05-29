/*
 * Copyright (C) 2014 jonas.oreland@gmail.com
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
package org.runnerup.widget

import android.graphics.Point
import android.support.wearable.view.GridPagerAdapter
import android.support.wearable.view.GridViewPager
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import org.runnerup.R

class MyDotsPageIndicator(private val layout: LinearLayout) :
    GridViewPager.OnPageChangeListener, GridViewPager.OnAdapterChangeListener {

    // margin, size
    private val unselected = Point(4, 2)
    private val selected = Point(6, 1)

    private var pager: GridViewPager? = null

    fun setPager(pager: GridViewPager) {
        this.pager = pager
        onDataSetChanged()
        onPageSelected(0, 0)
    }

    override fun onPageScrolled(
        i: Int,
        i2: Int,
        v: Float,
        v2: Float,
        i3: Int,
        i4: Int,
    ) {}

    override fun onPageSelected(row: Int, col: Int) {
        for (i in 0 until layout.childCount) {
            configDot(layout.getChildAt(i) as Button, false)
        }
        if (row < layout.childCount) {
            configDot(layout.getChildAt(row) as Button, true)
        }
    }

    override fun onPageScrollStateChanged(i: Int) {}

    override fun onAdapterChanged(
        gridPagerAdapter: GridPagerAdapter?,
        gridPagerAdapter2: GridPagerAdapter?,
    ) {}

    override fun onDataSetChanged() {
        layout.removeAllViews()

        val adapter = pager?.adapter ?: return

        /* skip dot for only 1 row */
        if (adapter.rowCount <= 1) return

        for (i in 0 until adapter.rowCount) {
            val b = Button(layout.context)
            layout.addView(configDot(b, false))
        }
    }

    private fun configDot(btn: Button, selected: Boolean): Button {
        btn.setBackgroundResource(R.drawable.dot)
        val measures = if (selected) this.selected else unselected
        val size = getPxFromDp(measures.x)
        val margin = getPxFromDp(measures.y)
        val p = LinearLayout.LayoutParams(size, size)
        if (layout.orientation == LinearLayout.VERTICAL) {
            p.gravity = Gravity.CENTER_HORIZONTAL
            p.setMargins(0, margin, 0, margin)
        } else {
            p.gravity = Gravity.CENTER_VERTICAL
            p.setMargins(margin, 0, margin, 0)
        }
        btn.layoutParams = p
        return btn
    }

    private fun getPxFromDp(dp: Int): Int =
        TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp.toFloat(),
                layout.resources.displayMetrics,
            )
            .toInt()
}
