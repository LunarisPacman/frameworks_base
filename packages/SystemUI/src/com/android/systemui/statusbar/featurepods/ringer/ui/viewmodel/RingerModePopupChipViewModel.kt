/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.statusbar.featurepods.ringer.ui.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.VibrationEffect
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.res.R
import com.android.systemui.statusbar.VibratorHelper
import com.android.systemui.statusbar.featurepods.popups.shared.DynamicIslandFeatureSettings.RINGER_MODE
import com.android.systemui.statusbar.featurepods.popups.shared.DynamicIslandFeatureSettings.observeDynamicIslandFeatureEnabled
import com.android.systemui.statusbar.featurepods.popups.ui.model.ChipIcon
import com.android.systemui.statusbar.featurepods.popups.ui.model.ColorsModel
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupContentModel
import com.android.systemui.statusbar.featurepods.popups.ui.viewmodel.StatusBarPopupChipViewModel
import com.android.systemui.statusbar.featurepods.ringer.shared.model.RingerModePopupModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * ViewModel managing ringer mode changes (Ring / Vibrate / Silent) inside the dynamic island
 * with tailored haptic vibrations.
 */
class RingerModePopupChipViewModel
@AssistedInject
constructor(
    @Application private val context: Context,
    private val broadcastDispatcher: BroadcastDispatcher,
    private val vibratorHelper: VibratorHelper,
) : StatusBarPopupChipViewModel, ExclusiveActivatable() {

    private val hydrator = Hydrator("RingerModePopupChipViewModel.hydrator")
    private var lastMode: Int = -1
    private var dismissJob: Job? = null

    override val chip: PopupChipModel by
        hydrator.hydratedStateOf(
            traceName = "chip",
            initialValue = PopupChipModel.Hidden(PopupChipId.RingerMode),
            source =
                callbackFlow {
                    val audioManager = context.getSystemService(AudioManager::class.java)
                    lastMode = audioManager?.ringerModeInternal ?: AudioManager.RINGER_MODE_NORMAL

                    val filter =
                        IntentFilter().apply {
                            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                            addAction(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION)
                        }

                    val receiver =
                        object : BroadcastReceiver() {
                            override fun onReceive(ctx: Context, intent: Intent) {
                                val action = intent.action
                                if (
                                    action == AudioManager.RINGER_MODE_CHANGED_ACTION ||
                                        action == AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION
                                ) {
                                    val newMode =
                                        audioManager?.ringerModeInternal
                                            ?: intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)
                                    if (newMode >= 0 && newMode != lastMode) {
                                        lastMode = newMode
                                        triggerRingerHaptic(newMode)
                                        trySend(newMode)

                                        dismissJob?.cancel()
                                        dismissJob =
                                            launch {
                                                delay(2800L)
                                                trySend(null)
                                            }
                                    }
                                }
                            }
                        }

                    broadcastDispatcher.registerReceiver(receiver, filter)

                    awaitClose {
                        broadcastDispatcher.unregisterReceiver(receiver)
                        dismissJob?.cancel()
                    }
                }
                .combine(observeDynamicIslandFeatureEnabled(context, RINGER_MODE)) { mode, enabled ->
                    if (enabled && mode != null) {
                        toPopupChipModel(mode)
                    } else {
                        PopupChipModel.Hidden(PopupChipId.RingerMode)
                    }
                }
                .distinctUntilChanged(),
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun triggerRingerHaptic(mode: Int) {
        if (!vibratorHelper.hasVibrator()) return
        when (mode) {
            AudioManager.RINGER_MODE_SILENT -> {
                // Soft muted thud
                val effect =
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 20),
                        intArrayOf(0, 80),
                        -1,
                    )
                vibratorHelper.vibrate(effect)
            }
            AudioManager.RINGER_MODE_VIBRATE -> {
                // Dynamic double buzz
                val effect =
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 45, 40, 55),
                        intArrayOf(0, 200, 0, 240),
                        -1,
                    )
                vibratorHelper.vibrate(effect)
            }
            AudioManager.RINGER_MODE_NORMAL -> {
                // Sharp cheerful click
                vibratorHelper.vibrate(
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                )
            }
        }
    }

    private fun toPopupChipModel(mode: Int): PopupChipModel {
        val (iconRes, text) =
            when (mode) {
                AudioManager.RINGER_MODE_SILENT -> R.drawable.ic_volume_ringer_mute to "Silent"
                AudioManager.RINGER_MODE_VIBRATE -> R.drawable.ic_volume_ringer_vibrate to "Vibrate"
                else -> R.drawable.ic_volume_ringer to "Ring"
            }

        val popupModel = RingerModePopupModel(mode = mode, label = text)

        return PopupChipModel.Shown(
            chipId = PopupChipId.RingerMode,
            icons =
                listOf(
                    ChipIcon(
                        icon =
                            Icon.Resource(
                                resId = iconRes,
                                contentDescription = ContentDescription.Loaded(text),
                            )
                    )
                ),
            chipText = text,
            colors = ColorsModel.SystemTheme,
            popupContent = PopupContentModel.RingerMode(popupModel),
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(): RingerModePopupChipViewModel
    }
}
