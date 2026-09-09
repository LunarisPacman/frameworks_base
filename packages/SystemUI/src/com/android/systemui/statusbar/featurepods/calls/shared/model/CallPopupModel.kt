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

package com.android.systemui.statusbar.featurepods.calls.shared.model

import android.app.PendingIntent
import android.graphics.drawable.Drawable

enum class CallState {
    INCOMING,
    ONGOING,
}

data class CallPopupModel(
    val state: CallState,
    val callerName: String,
    val callerNumber: String? = null,
    val callerPhoto: Drawable? = null,
    val callStartTimeMs: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val answerIntent: PendingIntent? = null,
    val declineOrHangupIntent: PendingIntent? = null,
    val contentIntent: PendingIntent? = null,
    val answer: () -> Unit = {},
    val declineOrHangup: () -> Unit = {},
    val toggleMute: (() -> Unit)? = null,
    val toggleSpeaker: (() -> Unit)? = null,
    val openApp: () -> Unit = {},
)
