package org.runnerup.hr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleHrAdvertisementTest {

  @Test
  fun includeScanResult_keepsStandardHeartRateAdvertisement() {
    val record = advertisement(incomplete16BitServices(0x180D))
    assertTrue(BleHrAdvertisement.includeScanResult("Unknown", record))
  }

  @Test
  fun includeScanResult_keepsHelioNameWithoutHrUuid() {
    assertTrue(BleHrAdvertisement.includeScanResult("Amazfit Helio Band", ByteArray(0)))
    assertTrue(BleHrAdvertisement.includeScanResult("Helio Strap", null))
  }

  @Test
  fun includeScanResult_dropsUnrelatedPeripherals() {
    val record = advertisement(incomplete16BitServices(0x180F))
    assertFalse(BleHrAdvertisement.includeScanResult("Pixel Buds", record))
    assertTrue(BleHrAdvertisement.includeScanResult("Amazfit GTR", null))
  }

  @Test
  fun advertisesHeartRateService_readsServiceDataAnd128BitUuid() {
    val serviceData = advertisement(byteArrayOf(0x03, 0x16, 0x0D, 0x18))
    assertTrue(BleHrAdvertisement.advertisesHeartRateService(serviceData))

    val uuid128 =
        byteArrayOf(
            0x11,
            0x07,
            0x0D,
            0x18,
            0x00,
            0x00,
            0x00,
            0x00,
            0x10,
            0x00,
            0x80.toByte(),
            0x00,
            0x00,
            0x80.toByte(),
            0x5F,
            0x9B.toByte(),
            0x34,
            0xFB.toByte())
    assertTrue(BleHrAdvertisement.advertisesHeartRateService(uuid128))
  }

  @Test
  fun includeBondedDevice_alwaysKeepsHelioAndHuami() {
    assertTrue(BleHrAdvertisement.includeBondedDevice("Amazfit Helio Strap", false, false))
    assertTrue(BleHrAdvertisement.includeBondedDevice("Mi Band 6", true, false))
    assertFalse(BleHrAdvertisement.includeBondedDevice("WH-1000XM5", false, false))
  }

  @Test
  fun includeBondedDevice_includesAllPairedLeDevices() {
    assertTrue(BleHrAdvertisement.includeBondedDevice("WH-1000XM5", true, false))
    assertFalse(BleHrAdvertisement.includeBondedDevice("WH-1000XM5", false, true))
  }

  @Test
  fun isLeCapableDeviceType_matchesAndroidLeAndDual() {
    assertFalse(BleHrAdvertisement.isLeCapableDeviceType(0))
    assertFalse(BleHrAdvertisement.isLeCapableDeviceType(1))
    assertTrue(BleHrAdvertisement.isLeCapableDeviceType(2))
    assertTrue(BleHrAdvertisement.isLeCapableDeviceType(3))
  }

  private fun incomplete16BitServices(uuid16: Int): ByteArray {
    return byteArrayOf(0x03, 0x02, (uuid16 and 0xFF).toByte(), ((uuid16 shr 8) and 0xFF).toByte())
  }

  private fun advertisement(vararg parts: ByteArray): ByteArray {
    var size = 0
    for (part in parts) {
      size += part.size
    }
    val out = ByteArray(size)
    var offset = 0
    for (part in parts) {
      System.arraycopy(part, 0, out, offset, part.size)
      offset += part.size
    }
    return out
  }
}
