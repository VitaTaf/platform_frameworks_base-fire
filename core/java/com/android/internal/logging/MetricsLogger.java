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
package com.android.internal.logging;


import android.content.Context;
import android.os.Build;

/**
 * Log all the things.
 *
 * @hide
 */
public class MetricsLogger implements MetricsConstants {
    // These constants are temporary, they should migrate to MetricsConstants.
    // next value is 146;

    public static final int NOTIFICATION_ZEN_MODE_SCHEDULE_RULE = 144;
    public static final int NOTIFICATION_ZEN_MODE_EXTERNAL_RULE = 145;
    public static final int VOLUME_DIALOG = 207;
    public static final int VOLUME_DIALOG_DETAILS = 208;
    public static final int ACTION_VOLUME_SLIDER = 209;
    public static final int ACTION_VOLUME_STREAM = 210;
    public static final int ACTION_VOLUME_KEY = 211;
    public static final int ACTION_VOLUME_ICON = 212;
    public static final int ACTION_RINGER_MODE = 213;

    public static void visible(Context context, int category) throws IllegalArgumentException {
        if (Build.IS_DEBUGGABLE && category == VIEW_UNKNOWN) {
            throw new IllegalArgumentException("Must define metric category");
        }
        EventLogTags.writeSysuiViewVisibility(category, 100);
    }

    public static void hidden(Context context, int category) {
        if (Build.IS_DEBUGGABLE && category == VIEW_UNKNOWN) {
            throw new IllegalArgumentException("Must define metric category");
        }
        EventLogTags.writeSysuiViewVisibility(category, 0);
    }

    public static void action(Context context, int category) {
        if (Build.IS_DEBUGGABLE && category == VIEW_UNKNOWN) {
            throw new IllegalArgumentException("Must define metric category");
        }
        EventLogTags.writeSysuiAction(category);
    }

    /** Add an integer value to the monotonically increasing counter with the given name. */
    public static void count(Context context, String name, int value) {
        EventLogTags.writeSysuiCount(name, value);
    }

    /** Increment the bucket with the integer label on the histogram with the given name. */
    public static void histogram(Context context, String name, int bucket) {
        EventLogTags.writeSysuiHistogram(name, bucket);
    }
}
