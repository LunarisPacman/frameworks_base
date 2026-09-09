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

package com.android.systemui.statusbar.featurepods.bluetooth.ui.viewmodel

import android.bluetooth.BluetoothClass
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.VibrationEffect
import android.provider.Settings
import androidx.compose.runtime.getValue
import com.android.settingslib.bluetooth.CachedBluetoothDevice
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.statusbar.VibratorHelper
import com.android.systemui.statusbar.featurepods.bluetooth.shared.model.AudioDeviceKind
import com.android.systemui.statusbar.featurepods.bluetooth.shared.model.BluetoothAudioPopupModel
import com.android.systemui.statusbar.featurepods.popups.shared.DynamicIslandFeatureSettings.BLUETOOTH
import com.android.systemui.statusbar.featurepods.popups.shared.DynamicIslandFeatureSettings.observeDynamicIslandFeatureEnabled
import com.android.systemui.statusbar.featurepods.popups.ui.model.ChipIcon
import com.android.systemui.statusbar.featurepods.popups.ui.model.ColorsModel
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipId
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupChipModel
import com.android.systemui.statusbar.featurepods.popups.ui.model.PopupContentModel
import com.android.systemui.statusbar.featurepods.popups.ui.viewmodel.StatusBarPopupChipViewModel
import com.android.systemui.statusbar.policy.BluetoothController
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
 * ViewModel for Bluetooth & audio accessory connection island with earphone vs headphone detection,
 * animated bud events, and rich haptics.
 */
class BluetoothAudioPopupChipViewModel
@AssistedInject
constructor(
    @Application private val context: Context,
    private val bluetoothController: BluetoothController,
    private val vibratorHelper: VibratorHelper,
    private val activityStarter: ActivityStarter,
) : StatusBarPopupChipViewModel, ExclusiveActivatable() {

    private val hydrator = Hydrator("BluetoothAudioPopupChipViewModel.hydrator")

    private val previousAddresses = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var dismissJob: Job? = null

    override val chip: PopupChipModel by
        hydrator.hydratedStateOf(
            traceName = "chip",
            initialValue = PopupChipModel.Hidden(PopupChipId.BluetoothAudio),
            source =
                callbackFlow {
                    val btCallback =
                        object : BluetoothController.Callback {
                            override fun onBluetoothStateChange(enabled: Boolean) {
                                if (!enabled) {
                                    previousAddresses.clear()
                                    trySend(null)
                                }
                            }

                            override fun onBluetoothDevicesChanged() {
                                val devices = bluetoothController.connectedDevices
                                val currentAddresses = devices.map { it.address }.toSet()
                                val newlyConnected =
                                    devices.filter { it.address !in previousAddresses }
                                previousAddresses.clear()
                                previousAddresses.addAll(currentAddresses)

                                if (newlyConnected.isNotEmpty()) {
                                    val connectedDevice = newlyConnected.first()
                                    triggerConnectHaptic()
                                    val info = resolveDeviceInfo(connectedDevice)
                                    trySend(info)

                                    dismissJob?.cancel()
                                    dismissJob =
                                        launch {
                                            delay(4500L)
                                            trySend(null)
                                        }
                                } else {
                                    val anyAudioConnected =
                                        devices.firstOrNull { isAudioDevice(it) }
                                    if (anyAudioConnected == null && previousAddresses.isEmpty()) {
                                        trySend(null)
                                    }
                                }
                            }
                        }

                    val headsetReceiver =
                        object : BroadcastReceiver() {
                            override fun onReceive(ctx: Context, intent: Intent) {
                                if (intent.action == AudioManager.ACTION_HEADSET_PLUG) {
                                    val state = intent.getIntExtra("state", 0)
                                    if (state == 1) {
                                        val hasMicrophone = intent.getIntExtra("microphone", 0) == 1
                                        val name = intent.getStringExtra("name") ?: "Wired Audio"
                                        triggerConnectHaptic()
                                        val info =
                                            AudioDeviceInfo(
                                                deviceName = name,
                                                deviceKind =
                                                    if (hasMicrophone) AudioDeviceKind.EARPHONES
                                                    else AudioDeviceKind.HEADPHONES,
                                                batteryLevel = null,
                                                leftBattery = null,
                                                rightBattery = null,
                                                caseBattery = null,
                                            )
                                        trySend(info)
                                        dismissJob?.cancel()
                                        dismissJob =
                                            launch {
                                                delay(4500L)
                                                trySend(null)
                                            }
                                    } else {
                                        trySend(null)
                                    }
                                }
                            }
                        }

                    bluetoothController.addCallback(btCallback)
                    context.registerReceiver(
                        headsetReceiver,
                        IntentFilter(AudioManager.ACTION_HEADSET_PLUG),
                        Context.RECEIVER_NOT_EXPORTED,
                    )

                    awaitClose {
                        bluetoothController.removeCallback(btCallback)
                        context.unregisterReceiver(headsetReceiver)
                        dismissJob?.cancel()
                    }
                }
                .combine(observeDynamicIslandFeatureEnabled(context, BLUETOOTH)) { info, enabled ->
                    if (enabled && info != null) {
                        toPopupChipModel(info)
                    } else {
                        PopupChipModel.Hidden(PopupChipId.BluetoothAudio)
                    }
                }
                .distinctUntilChanged(),
        )

    override suspend fun onActivated(): Nothing {
        hydrator.activate()
    }

    private fun triggerConnectHaptic() {
        if (!vibratorHelper.hasVibrator()) return
        val effect =
            VibrationEffect.createWaveform(
                longArrayOf(0, 25, 35, 45),
                intArrayOf(0, 160, 0, 240),
                -1,
            )
        vibratorHelper.vibrate(effect)
    }

    private fun isAudioDevice(device: CachedBluetoothDevice): Boolean {
        val btClass = device.device?.bluetoothClass ?: return false
        val deviceClass = btClass.deviceClass
        return deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
            deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
            deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
            deviceClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER ||
            btClass.hasService(BluetoothClass.Service.AUDIO)
    }

    private fun resolveDeviceInfo(cachedDevice: CachedBluetoothDevice): AudioDeviceInfo {
        val name = cachedDevice.name ?: "Bluetooth Device"
        val btClass = cachedDevice.device?.bluetoothClass
        val deviceClass = btClass?.deviceClass ?: 0

        val lowerName = name.lowercase()
        val kind =
            when {
                deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ->
                    AudioDeviceKind.HEADPHONES
                deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ->
                    AudioDeviceKind.EARPHONES
                deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> AudioDeviceKind.CAR
                deviceClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER ->
                    AudioDeviceKind.SPEAKER
                lowerName.containsAny("bud", "pod", "ear", "tws", "airpod", "freebud") ->
                    AudioDeviceKind.EARPHONES
                lowerName.containsAny(
                    "headphone",
                    "wh-",
                    "qc",
                    "quietcomfort",
                    "max",
                    "studio",
                    "major",
                    "monitor"
                ) -> AudioDeviceKind.HEADPHONES
                else -> AudioDeviceKind.EARPHONES
            }

        val battery = cachedDevice.batteryLevel.takeIf { it >= 0 }
        val left = getMetaInt(cachedDevice, 10)
        val right = getMetaInt(cachedDevice, 11)
        val case = getMetaInt(cachedDevice, 12)

        return AudioDeviceInfo(
            deviceName = name,
            deviceKind = kind,
            batteryLevel = battery,
            leftBattery = left,
            rightBattery = right,
            caseBattery = case,
            cachedDevice = cachedDevice,
        )
    }

    private fun getMetaInt(cachedDevice: CachedBluetoothDevice, key: Int): Int? {
        return try {
            val bytes = cachedDevice.device.getMetadata(key)
            if (bytes != null && bytes.isNotEmpty()) {
                String(bytes).toIntOrNull()?.takeIf { it in 0..100 }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun String.containsAny(vararg terms: String): Boolean {
        return terms.any { this.contains(it) }
    }

    private fun toPopupChipModel(info: AudioDeviceInfo): PopupChipModel {
        val iconRes =
            when (info.deviceKind) {
                AudioDeviceKind.HEADPHONES -> com.android.settingslib.R.drawable.ic_headphone
                AudioDeviceKind.EARPHONES ->
                    com.android.settingslib.R.drawable.ic_bt_untethered_earbuds
                AudioDeviceKind.SPEAKER ->
                    com.android.settingslib.R.drawable.ic_bt_le_audio_speakers
                AudioDeviceKind.CAR -> com.android.systemui.res.R.drawable.ic_bluetooth_connected
                AudioDeviceKind.OTHER -> com.android.settingslib.R.drawable.ic_headphone
            }

        val chipText =
            when {
                info.batteryLevel != null -> "${info.batteryLevel}%"
                info.leftBattery != null -> "${info.leftBattery}%"
                else -> "Connected"
            }

        val popupModel =
            BluetoothAudioPopupModel(
                deviceName = info.deviceName,
                deviceKind = info.deviceKind,
                batteryLevel = info.batteryLevel,
                leftBattery = info.leftBattery,
                rightBattery = info.rightBattery,
                caseBattery = info.caseBattery,
                openSettings = {
                    activityStarter.postStartActivityDismissingKeyguard(
                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
                        0,
                    )
                },
                disconnect = {
                    info.cachedDevice?.disconnect()
                },
            )

        return PopupChipModel.Shown(
            chipId = PopupChipId.BluetoothAudio,
            icons =
                listOf(
                    ChipIcon(
                        icon =
                            Icon.Resource(
                                resId = iconRes,
                                contentDescription = ContentDescription.Loaded(info.deviceName),
                            )
                    )
                ),
            chipText = chipText,
            colors = ColorsModel.SystemTheme,
            popupContent = PopupContentModel.BluetoothAudio(popupModel),
        )
    }

    private data class AudioDeviceInfo(
        val deviceName: String,
        val deviceKind: AudioDeviceKind,
        val batteryLevel: Int?,
        val leftBattery: Int?,
        val rightBattery: Int?,
        val caseBattery: Int?,
        val cachedDevice: CachedBluetoothDevice? = null,
    )

    @AssistedFactory
    interface Factory {
        fun create(): BluetoothAudioPopupChipViewModel
    }
}
