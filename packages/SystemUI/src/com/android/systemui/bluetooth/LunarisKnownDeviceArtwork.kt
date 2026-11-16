/*
 * Copyright (C) 2024-2026 Lunaris AOSP
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

package com.android.systemui.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.systemui.res.R

/**
 * Registry of non-FastPair Bluetooth devices with custom bundled artwork.
 */
enum class LunarisKnownDeviceArtwork(
    @DrawableRes val drawableResId: Int,
    private val matcher: (String) -> Boolean,
) {
    SOUNDCORE_Q20I(
        R.drawable.ic_bt_device_soundcore_q20i,
        { name ->
            val lower = name.lowercase()
            lower.contains("q20i") ||
                    (lower.contains("soundcore") && lower.contains("q20")) ||
                    (lower.contains("life") && lower.contains("q20"))
        },
    ),
    ONEPLUS_NORD_BUDS_2R(
        R.drawable.ic_bt_device_oneplus_nord_buds_2r,
        { name ->
            val lower = name.lowercase()
            lower.contains("nord buds 2r") ||
                    lower.contains("nord buds 2 r") ||
                    (lower.contains("buds 2r") && (lower.contains("oneplus") || lower.contains("nord"))) ||
                    (lower.contains("nord") && lower.contains("buds") && (lower.contains("2r") || lower.contains("2 r")))
        },
    );

    fun matches(candidates: Sequence<String>): Boolean {
        return candidates.any { matcher(it) }
    }

    companion object {
        fun findMatchingArtworkRes(
            device: BluetoothDevice?,
            cachedDevice: CachedBluetoothDevice? = null,
        ): Int? {
            if (device == null && cachedDevice == null) return null
            val candidates = sequenceOf<String?>(
                cachedDevice?.name,
                runCatching { device?.alias }.getOrNull(),
                runCatching { device?.name }.getOrNull(),
                runCatching { cachedDevice?.device?.alias }.getOrNull(),
                runCatching { cachedDevice?.device?.name }.getOrNull(),
            ).filterNotNull().filter { it.isNotBlank() }

            return entries.firstOrNull { it.matches(candidates) }?.drawableResId
        }

        fun getArtwork(
            context: Context,
            device: BluetoothDevice?,
            cachedDevice: CachedBluetoothDevice? = null,
        ): Drawable? {
            val resId = findMatchingArtworkRes(device, cachedDevice) ?: return null
            return runCatching { context.getDrawable(resId) }.getOrNull()
        }
    }
}
