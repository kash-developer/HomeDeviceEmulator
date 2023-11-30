/*
 * Copyright (C) 2023 Korea Association of AI Smart Home.
 * Copyright (C) 2023 KyungDong Navien Co, Ltd.
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

package kr.or.kashi.hde.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

public class LocalPreferences {
    private static final int CURRENT_VERSION = 1;

    public class Pref {
        public static final String VERSION = "version";
        public static final String LAST_RUNNING = "last_running";
        public static final String PORT_INDEX = "port_index";
        public static final String PROTOCOL_INDEX = "protocol_index";
        public static final String MODE_INDEX = "mode_index";
        public static final String SELECTED_DEVICE_TYPES = "selected_device_types";
        public static final String RANGE_GROUP_CHECKED = "range_group_checked";
        public static final String RANGE_GROUP_LAST_ID = "range_group_last_id";
        public static final String RANGE_GROUP_FULL_CHECKED = "range_group_full_CHECKED";
        public static final String RANGE_SINGLE_CHECKED = "range_single_checked";
        public static final String RANGE_SINGLE_LAST_ID = "range_single_last_id";
        public static final String RANGE_SINGLE_FULL_CHECKED = "range_single_full_checked";
        public static final String DEBUG_LOG_EVENT_ENABLED = "debug_log_event_enabled";
        public static final String DEBUG_LOG_TXRX_ENABLED = "debug_log_txrx_enabled";
        public static final String POLLING_INTERVAL_INDEX = "polling_interval_index";
    };

    private static SharedPreferences sSharedPreferences = null;
    private static SharedPreferences.Editor sCurrentEditor = null;
    private static Handler sAutoCommitHandler = new Handler(Looper.getMainLooper());
    private static Runnable sOnCommitRunnalble = () -> {
        if (sCurrentEditor != null) {
            sCurrentEditor.commit();
        }
    };

    public static void init(Context context) {
        sSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (sSharedPreferences.getInt(Pref.VERSION, 0) != CURRENT_VERSION) {
            sSharedPreferences.edit().clear().commit();
            sSharedPreferences.edit().putInt(Pref.VERSION, CURRENT_VERSION).commit();
        }
    }

    private static SharedPreferences prefs() {
        return sSharedPreferences;
    }

    private static SharedPreferences.Editor edit() {
        if (sCurrentEditor == null) {
            sCurrentEditor = prefs().edit();
        }
        if (sAutoCommitHandler.hasCallbacks(sOnCommitRunnalble) == false) {
            sAutoCommitHandler.postDelayed(sOnCommitRunnalble, 10);
        }
        return sCurrentEditor;
    }

    public static boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public static boolean getBoolean(String key, boolean defValue) {
        return prefs().getBoolean(key, defValue);
    }

    public static void putBoolean(String key, boolean value) {
        edit().putBoolean(key, value);
    }

    public static int getInt(String key) {
        return getInt(key, 0);
    }

    public static int getInt(String key, int defValue) {
        return prefs().getInt(key, defValue);
    }

    public static void putInt(String key, int value) {
        edit().putInt(key, value);
    }

    public static Set<String> getSelectedDeviceTypes() {
        return new HashSet<>(prefs().getStringSet(Pref.SELECTED_DEVICE_TYPES, new HashSet<>()));
    }

    public static void putSelectedDeviceTypes(Set<String> types) {
        edit().putStringSet(Pref.SELECTED_DEVICE_TYPES, types);
    }
}
