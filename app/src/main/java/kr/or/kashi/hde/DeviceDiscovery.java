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

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.lang.Math;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DeviceDiscovery implements Runnable {
    private static final String TAG = DeviceDiscovery.class.getSimpleName();
    private static final long SCAN_INTERVAL_MS = 200;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<Callback> mCallbacks = new ArrayList<>();
    private final Map<HomeAddress, HomeDevice> mDeviceMap = new ConcurrentHashMap<>();
    private final ArrayDeque<HomeDevice> mStagingQueue = new ArrayDeque<>();
    private boolean mIsRunning = false;
    private boolean mStartedEventFired = false;
    protected long mStopTime = 0;
    protected HomeAddress mLastPollAddress;
    protected long mLastPollTime = 0;

    public interface Callback {
        default void onDiscoveryStarted() {}
        default void onDiscoveryFinished() {}
        default void onDeviceDiscovered(HomeDevice device) {}
    }

    public DeviceDiscovery() { }

    public boolean discoverDevices(long timeout, List<HomeDevice> devices) {
        return startSchedule(timeout, devices);
    }

    public void stopDiscovering() {
        cleanUp();
    }

    public boolean isRunning() {
        return mIsRunning;
    }

    public boolean shouldRunning() {
        if (mStagingQueue.isEmpty() || mDeviceMap.isEmpty()) return false;
        if (mStopTime > SystemClock.uptimeMillis()) return false;
        return true;
    }

    public void addCallback(Callback callback) {
        if (callback == null) throw new IllegalArgumentException("Callback is null");
        mCallbacks.add(callback);
    }

    public void removeCallback(Callback callback) {
        if (callback == null) throw new IllegalArgumentException("Callback is null");
        mCallbacks.remove(callback);
    }

    private void onDiscoveryStarted() {
        for (final Callback cb : mCallbacks) {
            mHandler.post(() -> { cb.onDiscoveryStarted(); });
        }
    }

    private void onDiscoveryFinished() {
        for (final Callback cb : mCallbacks) {
            mHandler.post(() -> { cb.onDiscoveryFinished(); });
        }
    }

    private void onDeviceDiscovered(final HomeDevice device) {
        for (final Callback cb : mCallbacks) {
            mHandler.post(() -> { cb.onDeviceDiscovered(device); });
        }
    }

    @Override
    public void run() {
        HomeDevice device = mStagingQueue.poll();
        if (device != null) {
            boolean isDetected = (device.dc().getUpdateTime() != 0);
            if (mDeviceMap.containsKey(device.dc().getAddress()) && !isDetected) {
                pingDevice(device);

                if (mStopTime != 0) { // 0 is meaning of one loop of list.
                    mStagingQueue.add(device);
                }
            }
        }

        if (shouldRunning()) {
            reschedule();
        } else {
            // Clean up all the state of progress.
            new Handler().postDelayed(()-> {
                cleanUp();
            }, 1000);
        }
    }

    protected void pingDevice(HomeDevice device) {
        device.dc().requestUpdate();
        mLastPollAddress = device.dc().getAddress();
        mLastPollTime = SystemClock.uptimeMillis();
    }

    protected boolean startSchedule(long timeout, List<HomeDevice> devices) {
        if (devices == null || devices.isEmpty()) {
            return false;
        }

        cleanUp(); // Ensure that current state is cleaned up.

        for (HomeDevice d: devices) {
            mDeviceMap.put(d.dc().getAddress(), d);
        }

        mStagingQueue.addAll(devices);

        if (timeout > 0) {
            mStopTime = SystemClock.uptimeMillis() + timeout;
        } else {
            mStopTime = 0;
        }
        mLastPollAddress = null;

        reschedule();

        mIsRunning = true;

        return true;
    }

    public void onParsePacket(HomeAddress address, HomePacket packet) {
        final HomeDevice device = mDeviceMap.get(address);
        if (device != null) {
            int res = device.dc().parsePacket(packet);
            if (res == DeviceContextBase.PARSE_OK_PEER_DETECTED) {
                onDeviceDiscovered(device);
            }
            mDeviceMap.remove(device);
        }
    }

    private void reschedule() {
        if (!mStartedEventFired) {
            onDiscoveryStarted();
            mStartedEventFired = true;
        }

        long elapsed = Math.abs(SystemClock.uptimeMillis() - mLastPollTime);
        long delayMs = Math.min(SCAN_INTERVAL_MS - elapsed, SCAN_INTERVAL_MS);
        mHandler.postDelayed(this, delayMs);
    }

    private void cleanUp() {
        mHandler.removeCallbacks(this);
        mDeviceMap.clear();
        mStagingQueue.clear();

        if (mStartedEventFired) {
            onDiscoveryFinished();
            mStartedEventFired = false;
        }

        mIsRunning = false;
    }
}
