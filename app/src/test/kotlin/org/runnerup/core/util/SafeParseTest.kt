package org.runnerup.core.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafeParseTest {

  @Test
  fun parseInt_usesDefaultOnBadInput() {
    assertEquals(42, SafeParse.parseInt("x", 42))
    assertEquals(7, SafeParse.parseInt("7", 42))
  }

  @Test
  fun parseSeconds_parsesHhMmSs() {
    assertEquals(3661L, SafeParse.parseSeconds("1:1:1", 0L))
    assertEquals(90L, SafeParse.parseSeconds("1:30", 0L))
  }

  @Test
  fun parseIntList_roundTrip() {
    val list = intArrayOf(63, 71, 78)
    assertEquals("63,71,78", SafeParse.storeIntList(list))
    assertArrayEquals(list, SafeParse.parseIntList("63,71,78"))
    assertNull(SafeParse.parseIntList("bad"))
  }
}
