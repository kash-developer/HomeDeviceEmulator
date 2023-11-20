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

    private static final String PREF_VERSION = "version";
    private static final String PREF_LAST_RUNNING = "last_running";
    private static final String PREF_PORT_INDEX = "port_index";
    private static final String PREF_PROTOCOL_INDEX = "protocol_index";
    private static final String PREF_MODE_INDEX = "mode_index";
    private static final String PREF_SELECTED_DEVICE_TYPES = "selected_device_types";

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
        if (sSharedPreferences.getInt(PREF_VERSION, 0) != CURRENT_VERSION) {
            sSharedPreferences.edit().clear().commit();
            sSharedPreferences.edit().putInt(PREF_VERSION, CURRENT_VERSION).commit();
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

    public static boolean wasLastRunning() {
        return prefs().getBoolean(PREF_LAST_RUNNING, false);
    }

    public static void putLastRunning(boolean running) {
        edit().putBoolean(PREF_LAST_RUNNING, running);
    }

    public static int getPortIndex() {
        return prefs().getInt(PREF_PORT_INDEX, 0);
    }

    public static void putPortIndex(int index) {
        edit().putInt(PREF_PORT_INDEX, index);
    }

    public static int getProtocolIndex() {
        return prefs().getInt(PREF_PROTOCOL_INDEX, 0);
    }

    public static void putProtocolIndex(int index) {
        edit().putInt(PREF_PROTOCOL_INDEX, index);
    }

    public static int getModeIndex() {
        return prefs().getInt(PREF_MODE_INDEX, 0);
    }

    public static void putModeIndex(int index) {
        edit().putInt(PREF_MODE_INDEX, index);
    }

    public static Set<String> getSelectedDeviceTypes() {
        return new HashSet<>(prefs().getStringSet(PREF_SELECTED_DEVICE_TYPES, new HashSet<>()));
    }

    public static void putSelectedDeviceTypes(Set<String> types) {
        edit().putStringSet(PREF_SELECTED_DEVICE_TYPES, types);
    }
}
