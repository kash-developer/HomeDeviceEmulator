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

package kr.or.kashi.hde;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DeviceManager {
    private static final String TAG = DeviceManager.class.getSimpleName();
    private static final long STAGE_DURATION_MS = 100;

    private static final int STAGE_IDLE     = 0;
    private static final int STAGE_ADDING   = 1;
    private static final int STAGE_REMOVING = 2;

    public interface Callback {
        void onDeviceAdded(List<HomeDevice> devices);
        void onDeviceRemoved(List<HomeDevice> devices);
    }

    private final Context mContext;
    private final Callback mCallback;
    private final Handler mEventHandler;
    private final Map<String, HomeDevice> mDeviceMap;
    private final List<HomeDevice> mCallbackStagedDevices = new ArrayList<>();
    private int mStage = STAGE_IDLE;

    public DeviceManager(Context context, Callback callback) {
        mContext = context;
        mCallback = callback;
        mEventHandler = new Handler(Looper.getMainLooper());
        mDeviceMap = new ConcurrentHashMap<>();
    }

    public boolean addDevice(HomeDevice device) {
        String address = device.getAddress();
        if (!mDeviceMap.containsKey(address)) {
            mDeviceMap.put(address, device);
            scheduleCallback(STAGE_ADDING, device);
            return true;
        }
        return false;
    }

    public void addDevices(List<HomeDevice> devices) {
        for (HomeDevice d: devices) {
            addDevice(d);
        }
    }

    public void removeDevice(HomeDevice device) {
        String address = device.getAddress();
        if (mDeviceMap.containsKey(address)) {
            mDeviceMap.remove(address);
            scheduleCallback(STAGE_REMOVING, device);
        }
    }

    public void removeDevices(List<HomeDevice> devices) {
        for (HomeDevice d: devices) {
            removeDevice(d);
        }
    }

    public void clearAllDevices() {
        mDeviceMap.clear();
    }

    public HomeDevice getDevice(String address) {
        return mDeviceMap.get(address);
    }

    public List<HomeDevice> getAllDevices() {
        return new ArrayList(mDeviceMap.values());
    }

    private void scheduleCallback(int newStage, HomeDevice device) {
        if (mCallback == null) {
            return;
        }

        if (mStage != STAGE_IDLE && newStage != mStage) {
            mEventHandler.removeCallbacks(this::onCallback);
            onCallback();
        }

        mStage = newStage;
        mCallbackStagedDevices.add(device);

        if (!mEventHandler.hasCallbacks(this::onCallback)) {
            mEventHandler.postDelayed(this::onCallback, STAGE_DURATION_MS);
        }
    }

    private void onCallback() {
        if (mCallback != null && !mCallbackStagedDevices.isEmpty()) {
            if (mStage == STAGE_ADDING) {
                mCallback.onDeviceAdded(mCallbackStagedDevices);
            } else if (mStage == STAGE_REMOVING) {
                mCallback.onDeviceRemoved(mCallbackStagedDevices);
            }
        }
        mStage = STAGE_IDLE;
        mCallbackStagedDevices.clear();
    }
}
