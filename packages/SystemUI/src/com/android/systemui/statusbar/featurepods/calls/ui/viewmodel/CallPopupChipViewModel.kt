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

package com.android.systemui.statusbar.featurepods.calls.ui.viewmodel

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.telecom.TelecomManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.statusbar.VibratorHelper
import com.android.systemui.statusbar.featurepods.calls.shared.model.CallPopupModel
import com.android.systemui.statusbar.featurepods.calls.shared.model.CallState
import com.android.systemui.statusbar.featurepods.popups.shared.DynamicIslandFeatureSettings.CALLS
import com.android.systemui.statusbar.featurepods.popups.shared.DynamicIslandFeatureSettings.observeDynamicIslandFeatureEnabled
import com.android.systemui.statusbar.featurepods.popups.ui.model.ChipIcon
import com.android.systemui.statusbar.featurepods.popups.ui.model.ColorsModel
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupContentModel
import com.android.systemui.statusbar.featurepods.popups.ui.viewmodel.StatusBarPopupChipViewModel
import com.android.systemui.statusbar.notification.domain.interactor.ActiveNotificationsInteractor
import com.android.systemui.statusbar.notification.shared.CallType
import com.android.systemui.statusbar.phone.ongoingcall.domain.interactor.OngoingCallInteractor
import com.android.systemui.statusbar.phone.ongoingcall.shared.model.OngoingCallModel
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel managing incoming and active phone calls inside the dynamic island with haptic pulses.
 */
class CallPopupChipViewModel
@AssistedInject
constructor(
    @Application private val context: Context,
    @Application private val applicationScope: CoroutineScope,
    private val ongoingCallInteractor: OngoingCallInteractor,
    private val activeNotificationsInteractor: ActiveNotificationsInteractor,
    private val vibratorHelper: VibratorHelper,
    private val activityStarter: ActivityStarter,
) : StatusBarPopupChipViewModel, ExclusiveActivatable() {

    private val hydrator = Hydrator("CallPopupChipViewModel.hydrator")
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val telecomManager = context.getSystemService(TelecomManager::class.java)
    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)

    private var incomingHapticJob: Job? = null

    override val chip: PopupChipModel by
        hydrator.hydratedStateOf(
            traceName = "chip",
            initialValue = PopupChipModel.Hidden(PopupChipId.Call),
            source =
                combine(
                    ongoingCallInteractor.ongoingCallState,
                    activeNotificationsInteractor.topLevelRepresentativeNotifications,
                    observeDynamicIslandFeatureEnabled(context, CALLS),
                ) { ongoingModel, notifications, enabled ->
                    if (!enabled) {
                        stopIncomingHaptics()
                        return@combine PopupChipModel.Hidden(PopupChipId.Call)
                    }

                    // Check for incoming call first (highest priority)
                    val incomingCallNotif =
                        notifications.firstOrNull { it.callType == CallType.Incoming }

                    if (incomingCallNotif != null) {
                        startIncomingHaptics()
                        return@combine toIncomingCallChipModel(
                            callerName = incomingCallNotif.appName.ifBlank { "Incoming Call" },
                            callerNumber = null,
                        )
                    }

                    stopIncomingHaptics()

                    // Check ongoing call
                    if (ongoingModel is OngoingCallModel.InCall) {
                        return@combine toOngoingCallChipModel(ongoingModel)
                    }

                    PopupChipModel.Hidden(PopupChipId.Call)
                }
                .distinctUntilChanged(),
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun startIncomingHaptics() {
        if (incomingHapticJob?.isActive == true || !vibratorHelper.hasVibrator()) return
        incomingHapticJob =
            applicationScope.launch {
                val wave =
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 120, 100, 120, 400),
                        intArrayOf(0, 180, 0, 240, 0),
                        -1,
                    )
                while (isActive) {
                    vibratorHelper.vibrate(wave)
                    delay(1200L)
                }
            }
    }

    private fun stopIncomingHaptics() {
        incomingHapticJob?.cancel()
        incomingHapticJob = null
    }

    private fun triggerConfirmHaptic() {
        if (!vibratorHelper.hasVibrator()) return
        vibratorHelper.vibrate(
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        )
    }

    private fun triggerDismissHaptic() {
        if (!vibratorHelper.hasVibrator()) return
        vibratorHelper.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 40, 30, 30),
                intArrayOf(0, 140, 0, 80),
                -1,
            )
        )
    }

    private fun toIncomingCallChipModel(
        callerName: String,
        callerNumber: String?,
    ): PopupChipModel {
        val openCallApp = {
            try {
                telecomManager?.showInCallScreen(false)
            } catch (_: Exception) {}
            Unit
        }

        return PopupChipModel.Shown(
            chipId = PopupChipId.Call,
            icons =
                listOf(
                    ChipIcon(
                        icon =
                            Icon.Resource(
                                resId = R.drawable.ic_call,
                                contentDescription = ContentDescription.Loaded("Incoming Call"),
                            ),
                        onClick = openCallApp,
                    )
                ),
            chipText = callerName,
            colors = ColorsModel.SystemTheme,
            popupContent = PopupContentModel.None,
            isPopupShown = false,
            showPopup = openCallApp,
            hidePopup = {},
        )
    }

    private fun toOngoingCallChipModel(inCall: OngoingCallModel.InCall): PopupChipModel {
        val openApp = {
            inCall.intent?.let { activityStarter.postStartActivityDismissingKeyguard(it, null) }
                ?: try {
                    telecomManager?.showInCallScreen(false)
                } catch (_: Exception) {}
            Unit
        }

        return PopupChipModel.Shown(
            chipId = PopupChipId.Call,
            icons =
                listOf(
                    ChipIcon(
                        icon =
                            Icon.Resource(
                                resId = R.drawable.ic_call,
                                contentDescription = ContentDescription.Loaded("Ongoing Call"),
                            ),
                        onClick = openApp,
                    )
                ),
            chipText = null, // Will use chronometer in chip
            colors = ColorsModel.SystemTheme,
            popupContent = PopupContentModel.None,
            isPopupShown = false,
            showPopup = openApp,
            hidePopup = {},
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(): CallPopupChipViewModel
    }
}
