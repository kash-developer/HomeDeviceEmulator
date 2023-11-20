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

import kr.or.kashi.hde.base.BasicPropertyMap;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.session.NetworkSession;
import kr.or.kashi.hde.ksx4506_kd.KDMainContext;
import kr.or.kashi.hde.stream.StreamProcessor;

public class HomeNetwork {
    private static final String TAG = HomeNetwork.class.getSimpleName();
    private static final int MAX_RETRY = 3;
    private static final long RETRY_DELAY_MS = 1000L;

    private final Context mContext;
    private final Handler mEventHandler;
    private final boolean mIsSlaveMode;

    private final MainContext mMainContext;
    private final StreamProcessor mStreamProcessor;
    private final DeviceDiscovery mDeviceDiscovery;
    private final DeviceStatePoller mDeviceStatePoller;
    private NetworkSession mNetworkSession;

    private int mRetryCount = 0;
    private Runnable mStreamErrorRunable = this::retryStart;

    public HomeNetwork(Context context, boolean isSlaveMode) {
        mContext = context;
        mEventHandler = new Handler(Looper.getMainLooper());
        mIsSlaveMode = isSlaveMode;

        mMainContext = createMainContext(context);
        mStreamProcessor = new StreamProcessor(context, mEventHandler, mStreamErrorRunable);
        mDeviceDiscovery = mMainContext.getDeviceDiscovery();
        mDeviceStatePoller = new DeviceStatePoller();
    }

    private MainContext createMainContext(Context context) {
        // return new KSMainContext(context);     // standard (2016)
        // return new KSMainContext2(context);    // extended (2022)
        return new KDMainContext(context);     // non-standard
    }

    public boolean isRunning() {
        return mStreamProcessor.isRunning();
    }

    public DeviceStatePoller getDeviceStatePoller() {
        return mDeviceStatePoller;
    }

    public boolean start(NetworkSession session) {
        if (session == null) {
            return false;
        }

        if (isRunning()) {
            stop();
        }
        mNetworkSession = session;

        Log.d(TAG, " network is starting");

        boolean res = mStreamProcessor.startStream(session);
        if (!res) {
            return false;
        }

        mMainContext.attachStream(mStreamProcessor);

        final List<DeviceStatePollee> polleeList = new ArrayList<>();
        for (HomeDevice device : mMainContext.getAllDevices()) {
            polleeList.add(device.dc());
        }

        if (!mIsSlaveMode) {
            mDeviceStatePoller.start(true /* repeative */, polleeList);
        }

        Log.d(TAG, " network has been started");

        mRetryCount = 0;

        return true;
    }

    public void stop() {
        mEventHandler.removeCallbacksAndMessages(mStreamErrorRunable);

        if (!isRunning()) {
            return;
        }

        if (!mIsSlaveMode) {
            mDeviceStatePoller.stop();
        }
        mMainContext.detachStream();
        mStreamProcessor.stopStream();

        Log.d(TAG, " network has been stopped");
    }

    public void retryStart() {
        stop();

        if (mRetryCount++ >= MAX_RETRY) {
            Log.e(TAG, " retry count reached over max");
            return;
        }

        if (!start(mNetworkSession)) {
            Log.d(TAG, " restart scheduled! (" + mRetryCount + ")");
            mEventHandler.postDelayed(mStreamErrorRunable, RETRY_DELAY_MS);
        }
    }

    public List<HomeDevice> getAllDevices() {
        return new ArrayList(mMainContext.getAllDevices());
    }

    public <E> E createDevice(String address, int area, String name) {
        PropertyMap defaultProps = new BasicPropertyMap();
        defaultProps.put(HomeDevice.PROP_ADDR, address.toUpperCase());
        defaultProps.put(HomeDevice.PROP_AREA, area);
        defaultProps.put(HomeDevice.PROP_NAME, name);
        return (E) createDevice(defaultProps);
    }

    public HomeDevice createDevice(String address) {
        PropertyMap defaultProps = new BasicPropertyMap();
        defaultProps.put(HomeDevice.PROP_ADDR, address.toUpperCase());
        return createDevice(defaultProps);
    }

    public HomeDevice createDevice(PropertyMap defaultProps) {
        defaultProps.put(HomeDevice.PROP_IS_SLAVE, mIsSlaveMode);
        return mMainContext.createDevice(defaultProps.toMap());
    }

    public void addDevice(HomeDevice device) {
        // Append a deivce to the main context and other pollers.
        boolean added = mMainContext.addDevice(device);
        if (added && !mIsSlaveMode) {
            mDeviceStatePoller.addPollee(device.dc());
        }
    }

    public void removeDevice(HomeDevice device) {
        // Remove a device from the main context and so on.
        mMainContext.removeDevice(device);
        if (!mIsSlaveMode) {
            mDeviceStatePoller.removePollee(device.dc());
        }
    }

    public DeviceDiscovery getDeviceDiscovery() {
        return mDeviceDiscovery;
    }
}
