/*
 * Copyright (C) 2026 RunnerUp
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
package org.runnerup.hr;

import java.util.Locale;

/**
 * Decides which BLE peripherals should be offered as heart-rate monitors.
 *
 * <p>Amazfit Helio Band/Strap broadcasts the standard Heart Rate service (0x180D) after Heart Rate
 * Push is enabled in the Zepp app. Android's UUID-filtered LE scan often misses that advertisement,
 * so this filter inspects the raw scan record and also recognizes Helio/Huami names.
 */
public final class BleHrAdvertisement {
  static final int HR_SERVICE_UUID_16 = 0x180D;

  /** BLE 128-bit form of 0x180D, little-endian. */
  private static final byte[] HR_SERVICE_UUID_128 = {
    0x0D,
    0x18,
    0x00,
    0x00,
    0x00,
    0x00,
    0x10,
    0x00,
    (byte) 0x80,
    0x00,
    0x00,
    (byte) 0x80,
    0x5F,
    (byte) 0x9B,
    0x34,
    (byte) 0xFB
  };

  private BleHrAdvertisement() {}

  public static boolean includeScanResult(String name, byte[] scanRecord) {
    if (advertisesHeartRateService(scanRecord)) {
      return true;
    }
    return isHuamiFamilyName(name);
  }

  public static boolean includeBondedDevice(
      String name, boolean leCapable, boolean includeAllPairedBle) {
    if (leCapable) {
      return true;
    }
    return isHuamiFamilyName(name);
  }

  public static boolean isLeCapableDeviceType(int bluetoothDeviceType) {
    // BluetoothDevice.DEVICE_TYPE_LE = 2, DEVICE_TYPE_DUAL = 3
    return bluetoothDeviceType == 2 || bluetoothDeviceType == 3;
  }

  public static boolean isHelioName(String name) {
    return containsIgnoreCase(name, "helio");
  }

  public static boolean isHuamiFamilyName(String name) {
    if (name == null) {
      return false;
    }
    String n = name.toLowerCase(Locale.ROOT);
    return n.contains("helio")
        || n.contains("amazfit")
        || n.contains("huami")
        || n.contains("mi band")
        || n.contains("miband");
  }

  public static boolean advertisesHeartRateService(byte[] scanRecord) {
    if (scanRecord == null || scanRecord.length == 0) {
      return false;
    }

    int offset = 0;
    while (offset < scanRecord.length) {
      int length = scanRecord[offset] & 0xFF;
      if (length == 0) {
        break;
      }
      if (offset + length >= scanRecord.length) {
        break;
      }
      int type = scanRecord[offset + 1] & 0xFF;
      int dataStart = offset + 2;
      int dataEnd = offset + 1 + length;
      if (type == 0x02 || type == 0x03 || type == 0x14) {
        if (contains16BitUuid(scanRecord, dataStart, dataEnd, HR_SERVICE_UUID_16)) {
          return true;
        }
      } else if (type == 0x16 && dataEnd - dataStart >= 2) {
        if (read16BitUuid(scanRecord, dataStart) == HR_SERVICE_UUID_16) {
          return true;
        }
      } else if (type == 0x06 || type == 0x07 || type == 0x15) {
        if (contains128BitUuid(scanRecord, dataStart, dataEnd, HR_SERVICE_UUID_128)) {
          return true;
        }
      }
      offset += length + 1;
    }
    return false;
  }

  private static boolean contains16BitUuid(byte[] record, int start, int end, int uuid16) {
    for (int i = start; i + 1 < end; i += 2) {
      if (read16BitUuid(record, i) == uuid16) {
        return true;
      }
    }
    return false;
  }

  private static int read16BitUuid(byte[] record, int offset) {
    return (record[offset] & 0xFF) | ((record[offset + 1] & 0xFF) << 8);
  }

  private static boolean contains128BitUuid(byte[] record, int start, int end, byte[] uuid) {
    for (int i = start; i + uuid.length <= end; i += uuid.length) {
      boolean match = true;
      for (int j = 0; j < uuid.length; j++) {
        if (record[i + j] != uuid[j]) {
          match = false;
          break;
        }
      }
      if (match) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsIgnoreCase(String value, String token) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(token);
  }
}
