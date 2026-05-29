/*
 * Copyright (C) 2012 - 2013 jonas.oreland@gmail.com
 */

package org.runnerup.core.util

object Bitfield {
  @JvmStatic
  fun test(flags: Long, bit: Int): Boolean {
    val value = 1L shl bit
    return flags and value == value
  }

  @JvmStatic
  fun set(flags: Long, bit: Int, value: Boolean): Long =
      if (value) set(flags, bit) else clear(flags, bit)

  private fun set(flags: Long, bit: Int): Long {
    val value = 1L shl bit
    return flags or value
  }

  private fun clear(flags: Long, bit: Int): Long {
    val value = 1L shl bit
    return flags and value.inv()
  }
}
