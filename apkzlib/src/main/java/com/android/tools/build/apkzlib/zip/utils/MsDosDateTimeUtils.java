/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.build.apkzlib.zip.utils;

import com.google.common.base.Verify;
import java.util.Calendar;
import java.util.Date;

/** Yes. This actually refers to MS-DOS in 2015. That's all I have to say about legacy stuff. */
public class MsDosDateTimeUtils {
  /** Utility class: no constructor. */
  private MsDosDateTimeUtils() {}

  /**
   * Packs java time value into an MS-DOS time value.
   *
   * @param time the time value
   * @return the MS-DOS packed time
   */
  public static int packTime(long time) {
    Calendar c = Calendar.getInstance();
    c.setTime(new Date(time));

    int seconds = c.get(Calendar.SECOND);
    int minutes = c.get(Calendar.MINUTE);
    int hours = c.get(Calendar.HOUR_OF_DAY);

    /*
     * Here is how MS-DOS packs a time value:
     * 0-4: seconds (divided by 2 because we only have 5 bits = 32 different numbers)
     * 5-10: minutes (6 bits = 64 possible values)
     * 11-15: hours (5 bits = 32 possible values)
     *
     * source: https://msdn.microsoft.com/en-us/library/windows/desktop/ms724247(v=vs.85).aspx
     */
    return (hours << 11) | (minutes << 5) | (seconds / 2);
  }

  /**
   * Packs the current time value into an MS-DOS time value.
   *
   * @return the MS-DOS packed time
   */
  public static int packCurrentTime() {
    return packTime(new Date().getTime());
  }

  /**
   * Packs java time value into an MS-DOS date value.
   *
   * @param time the time value
   * @return the MS-DOS packed date
   */
  public static int packDate(long time) {
    Calendar c = Calendar.getInstance();
    c.setTime(new Date(time));

    /*
     * Even MS-DOS used 1 for January. Someone wasn't really thinking when they decided on Java
     * it would start at 0...
     */
    int day = c.get(Calendar.DAY_OF_MONTH);
    int month = c.get(Calendar.MONTH) + 1;

    /*
     * MS-DOS counts years starting from 1980. Since its launch date was in 81, it was obviously
     * not necessary to talk about dates earlier than that.
     */
    int year = c.get(Calendar.YEAR) - 1980;
    Verify.verify(year >= 0 && year < 128);

    /*
     * Here is how MS-DOS packs a date value:
     * 0-4: day (5 bits = 32 values)
     * 5-8: month (4 bits = 16 values)
     * 9-15: year (7 bits = 128 values)
     *
     * source: https://msdn.microsoft.com/en-us/library/windows/desktop/ms724247(v=vs.85).aspx
     */
    return (year << 9) | (month << 5) | day;
  }

  /**
   * Packs the current time value into an MS-DOS date value.
   *
   * @return the MS-DOS packed date
   */
  public static int packCurrentDate() {
    return packDate(new Date().getTime());
  }
}
