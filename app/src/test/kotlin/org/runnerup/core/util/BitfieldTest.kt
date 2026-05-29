package org.runnerup.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BitfieldTest {

  @Test
  fun setAndTest() {
    var flags = 0L
    flags = Bitfield.set(flags, 2, true)
    assertTrue(Bitfield.test(flags, 2))
    assertFalse(Bitfield.test(flags, 1))
    flags = Bitfield.set(flags, 2, false)
    assertFalse(Bitfield.test(flags, 2))
  }
}
