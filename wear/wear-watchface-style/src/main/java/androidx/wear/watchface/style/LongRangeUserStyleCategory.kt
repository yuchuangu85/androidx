/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.wear.watchface.style

import android.graphics.drawable.Icon
import android.os.Bundle

/**
 * A LongRangeUserStyleCategory represents a category with a {@link Long} value in the range
 * [minimumValue .. maximumValue].
 */
class LongRangeUserStyleCategory : UserStyleCategory {

    internal companion object {
        internal const val CATEGORY_TYPE = "LongRangeUserStyleCategory"
        internal const val OPTION_TYPE = "LongRangeOption"
    }

    constructor (
        /** Identifier for the element, must be unique. */
        id: String,

        /** Localized human readable name for the element, used in the userStyle selection UI. */
        displayName: String,

        /** Localized description string displayed under the displayName. */
        description: String,

        /** Icon for use in the userStyle selection UI. */
        icon: Icon?,

        /** Minimum value (inclusive). */
        minimumValue: Long,

        /** Maximum value (inclusive). */
        maximumValue: Long,

        /** The default value for this LongRangeUserStyleCategory. */
        defaultValue: Long
    ) : super(
        id,
        displayName,
        description,
        icon,
        listOf(LongRangeOption(minimumValue), LongRangeOption(maximumValue)),
        LongRangeOption(defaultValue)
    )

    internal constructor(bundle: Bundle) : super(bundle)

    /**
     * Represents an option a {@link Long} in the range [minimumValue .. maximumValue].
     */
    class LongRangeOption : Option {
        /* The value for this option. Must be within the range [minimumValue..maximumValue]. */
        val value: Long

        constructor(value: Long) : super(value.toString()) {
            this.value = value
        }

        internal companion object {
            internal const val KEY_LONG_VALUE = "KEY_LONG_VALUE"
        }

        internal constructor(bundle: Bundle) : super(bundle) {
            value = bundle.getLong(KEY_LONG_VALUE)
        }

        override fun writeToBundle(bundle: Bundle) {
            super.writeToBundle(bundle)
            bundle.putLong(KEY_LONG_VALUE, value)
        }

        override fun getOptionType() = OPTION_TYPE
    }

    override fun getCategoryType() = CATEGORY_TYPE

    /**
     * Returns the minimum value.
     */
    fun getMinimumValue() = (options.first() as LongRangeOption).value

    /**
     * Returns the maximum value.
     */
    fun getMaximumValue() = (options.last() as LongRangeOption).value

    /**
     * Returns the default value.
     */
    fun getDefaultValue() = (defaultOption as LongRangeOption).value

    /**
     * We support all values in the range [min ... max] not just min & max.
     */
    override fun getOptionForId(optionId: String) =
        options.find { it.id == optionId } ?: checkedOptionForId(optionId)

    private fun checkedOptionForId(optionId: String): LongRangeOption {
        return try {
            val value = optionId.toLong()
            if (value < getMinimumValue() || value > getMaximumValue()) {
                defaultOption as LongRangeOption
            } else {
                LongRangeOption(value)
            }
        } catch (e: NumberFormatException) {
            defaultOption as LongRangeOption
        }
    }
}
