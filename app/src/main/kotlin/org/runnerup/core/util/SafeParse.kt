/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */

package org.runnerup.core.util

object SafeParse {
  @JvmStatic
  fun parseInt(string: String?, defaultValue: Int): Int =
      try {
        string!!.toInt()
      } catch (_: Exception) {
        defaultValue
      }

  @JvmStatic
  fun parseLong(string: String?, defaultValue: Long): Long =
      try {
        string!!.toLong()
      } catch (_: Exception) {
        defaultValue
      }

  @JvmStatic
  fun parseDouble(string: String?, defaultValue: Double): Double =
      try {
        string!!.toDouble()
      } catch (_: Exception) {
        defaultValue
      }

  /** @param string in form "HH:MM:SS" */
  @JvmStatic
  fun parseSeconds(string: String?, defaultValue: Long): Long =
      try {
        val split = string!!.split(":")
        var mul = 1L
        var sum = 0L
        for (i in split.indices.reversed()) {
          sum += split[i].toLong() * mul
          mul *= 60
        }
        sum
      } catch (_: Exception) {
        defaultValue
      }

  @JvmStatic
  fun parseIntList(str: String?): IntArray? =
      try {
        val split = str!!.split(",")
        IntArray(split.size) { i -> split[i].toInt() }
      } catch (_: Exception) {
        null
      }

  @JvmStatic
  fun storeIntList(list: IntArray): String {
    val buf = StringBuilder().append(list[0])
    for (i in 1 until list.size) {
      buf.append(',').append(list[i])
    }
    return buf.toString()
  }
}
