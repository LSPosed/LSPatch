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

package com.android.tools.build.apkzlib.zip;

import com.google.common.base.Preconditions;

/** Factory for instances of {@link AlignmentRule}. */
public final class AlignmentRules {

  private AlignmentRules() {}

  /**
   * A rule that defines a constant alignment for all files.
   *
   * @param alignment the alignment
   * @return the rule
   */
  public static AlignmentRule constant(int alignment) {
    Preconditions.checkArgument(alignment > 0, "alignment <= 0");

    return (String path) -> alignment;
  }

  /**
   * A rule that defines constant alignment for all files with a certain suffix, placing no
   * restrictions on other files.
   *
   * @param suffix the suffix
   * @param alignment the alignment for paths that match the provided suffix
   * @return the rule
   */
  public static AlignmentRule constantForSuffix(String suffix, int alignment) {
    Preconditions.checkArgument(!suffix.isEmpty(), "suffix.isEmpty()");
    Preconditions.checkArgument(alignment > 0, "alignment <= 0");

    return (String path) -> path.endsWith(suffix) ? alignment : AlignmentRule.NO_ALIGNMENT;
  }

  /**
   * A rule that applies other rules in order.
   *
   * @param rules all rules to be tried; the first rule that does not return {@link
   *     AlignmentRule#NO_ALIGNMENT} will define the alignment for a path; if there are no rules
   *     that return a value different from {@link AlignmentRule#NO_ALIGNMENT}, then {@link
   *     AlignmentRule#NO_ALIGNMENT} is returned
   * @return the composition rule
   */
  public static AlignmentRule compose(AlignmentRule... rules) {
    return (String path) -> {
      for (AlignmentRule r : rules) {
        int align = r.alignment(path);
        if (align != AlignmentRule.NO_ALIGNMENT) {
          return align;
        }
      }

      return AlignmentRule.NO_ALIGNMENT;
    };
  }
}
