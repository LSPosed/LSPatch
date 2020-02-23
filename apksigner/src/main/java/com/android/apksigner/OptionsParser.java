/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.apksigner;

import java.util.Arrays;

/**
 * Parser of command-line options/switches/flags.
 *
 * <p>Supported option formats:
 * <ul>
 * <li>{@code --name value}</li>
 * <li>{@code --name=value}</li>
 * <li>{@code -name value}</li>
 * <li>{@code --name} (boolean options only)</li>
 * </ul>
 *
 * <p>To use the parser, create an instance, providing it with the command-line parameters, then
 * iterate over options by invoking {@link #nextOption()} until it returns {@code null}.
 */
class OptionsParser {
    private final String[] mParams;
    private int mIndex;
    private int mPutBackIndex;
    private String mLastOptionValue;
    private String mPutBackLastOptionValue;
    private String mLastOptionOriginalForm;
    private String mPutBackLastOptionOriginalForm;

    /**
     * Constructs a new {@code OptionsParser} initialized with the provided command-line.
     */
    public OptionsParser(String[] params) {
        mParams = params.clone();
    }

    /**
     * Returns the name (without leading dashes) of the next option (starting with the very first
     * option) or {@code null} if there are no options left.
     *
     * <p>The value of this option can be obtained via {@link #getRequiredValue(String)},
     * {@link #getRequiredIntValue(String)}, and {@link #getOptionalBooleanValue(boolean)}.
     */
    public String nextOption() {
        if (mIndex >= mParams.length) {
            // No more parameters left
            return null;
        }
        String param = mParams[mIndex];
        if (!param.startsWith("-")) {
            // Not an option
            return null;
        }

        mPutBackIndex = mIndex;
        mIndex++;
        mPutBackLastOptionOriginalForm = mLastOptionOriginalForm;
        mLastOptionOriginalForm = param;
        mPutBackLastOptionValue = mLastOptionValue;
        mLastOptionValue = null;
        if (param.startsWith("--")) {
            // FORMAT: --name value OR --name=value
            if ("--".equals(param)) {
                // End of options marker
                return null;
            }
            int valueDelimiterIndex = param.indexOf('=');
            if (valueDelimiterIndex != -1) {
                mLastOptionValue = param.substring(valueDelimiterIndex + 1);
                mLastOptionOriginalForm = param.substring(0, valueDelimiterIndex);
                return param.substring("--".length(), valueDelimiterIndex);
            } else {
                return param.substring("--".length());
            }
        } else {
            // FORMAT: -name value
            return param.substring("-".length());
        }
    }

    /**
     * Undoes the last call to nextOption(), if one was made.  This allows callers to unwind state
     * so as not to eat up an option that is meant to be processed elsewhere.
     */
    public void putOption() {
        mIndex = mPutBackIndex;
        mLastOptionOriginalForm = mPutBackLastOptionOriginalForm;
        mLastOptionValue = mPutBackLastOptionValue;
    }
    /**
     * Returns the original form of the current option. The original form includes the leading dash
     * or dashes. This is intended to be used for referencing the option in error messages.
     */
    public String getOptionOriginalForm() {
        return mLastOptionOriginalForm;
    }

    /**
     * Returns the value of the current option, throwing an exception if the value is missing.
     */
    public String getRequiredValue(String valueDescription) throws OptionsException {
        if (mLastOptionValue != null) {
            String result = mLastOptionValue;
            mLastOptionValue = null;
            return result;
        }
        if (mIndex >= mParams.length) {
            // No more parameters left
            throw new OptionsException(
                    valueDescription + " missing after " + mLastOptionOriginalForm);
        }
        String param = mParams[mIndex];
        if ("--".equals(param)) {
            // End of options marker
            throw new OptionsException(
                    valueDescription + " missing after " + mLastOptionOriginalForm);
        }
        mIndex++;
        return param;
    }

    /**
     * Returns the value of the current numeric option, throwing an exception if the value is
     * missing or is not numeric.
     */
    public int getRequiredIntValue(String valueDescription) throws OptionsException {
        String value = getRequiredValue(valueDescription);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new OptionsException(
                    valueDescription + " (" + mLastOptionOriginalForm
                            + ") must be a decimal number: " + value);
        }
    }

    /**
     * Gets the value of the current boolean option. Boolean options are not required to have
     * explicitly specified values.
     */
    public boolean getOptionalBooleanValue(boolean defaultValue) throws OptionsException {
        if (mLastOptionValue != null) {
            // --option=value form
            String stringValue = mLastOptionValue;
            mLastOptionValue = null;
            if ("true".equals(stringValue)) {
                return true;
            } else if ("false".equals(stringValue)) {
                return false;
            }
            throw new OptionsException(
                    "Unsupported value for " + mLastOptionOriginalForm + ": " + stringValue
                            + ". Only true or false supported.");
        }

        // --option (true|false) form OR just --option
        if (mIndex >= mParams.length) {
            return defaultValue;
        }

        String stringValue = mParams[mIndex];
        if ("true".equals(stringValue)) {
            mIndex++;
            return true;
        } else if ("false".equals(stringValue)) {
            mIndex++;
            return false;
        } else {
            return defaultValue;
        }
    }

    /**
     * Returns the remaining command-line parameters. This is intended to be invoked once
     * {@link #nextOption()} returns {@code null}.
     */
    public String[] getRemainingParams() {
        if (mIndex >= mParams.length) {
            return new String[0];
        }
        String param = mParams[mIndex];
        if ("--".equals(param)) {
            // Skip end of options marker
            return Arrays.copyOfRange(mParams, mIndex + 1, mParams.length);
        } else {
            return Arrays.copyOfRange(mParams, mIndex, mParams.length);
        }
    }

    /**
     * Indicates that an error was encountered while parsing command-line options.
     */
    public static class OptionsException extends Exception {
        private static final long serialVersionUID = 1L;

        public OptionsException(String message) {
            super(message);
        }
    }
}
