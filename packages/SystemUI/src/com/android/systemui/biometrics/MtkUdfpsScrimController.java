/*
 * Copyright (C) 2026 The LineageOS project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.biometrics;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

/**
 * Controller for managing the UDFPS HBM scrim on MediaTek devices lacking LHBM support.
 */
public class MtkUdfpsScrimController {
    private static MtkUdfpsScrimController sInstance;

    private final Context mContext;

    public MtkUdfpsScrimController(Context context) {
        mContext = context;
    }

    public static MtkUdfpsScrimController getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new MtkUdfpsScrimController(context);
        }
        return sInstance;
    }

    public float calculateAlpha(int brightness) {
        float alpha = 1.0f - (brightness / 255.0f);

        if (brightness < 25) {
            alpha *= 0.95f;
        }

        Log.d("MtkUdfpsScrimController", "Requested Brightness value: " + brightness);
        Log.d("MtkUdfpsScrimController", "Alpha Value: " + alpha);

        return Math.max(0.0f, Math.min(1.0f, alpha));
    }

    public int getSystemBrightness() {
        if (mContext == null) {
            return 127;
        }

        float brightFloat = Settings.System.getFloat(
                mContext.getContentResolver(),
                "screen_brightness_float",
                -1.0f);

        if (brightFloat >= 0.0f) {
            return (int) (brightFloat * 255.0f);
        }

        return Settings.System.getInt(
                mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS,
                127);
    }
}
