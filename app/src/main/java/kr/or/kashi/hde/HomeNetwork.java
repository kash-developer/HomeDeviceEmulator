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
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import kr.or.kashi.hde.base.BasicPropertyMap;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.base.PropertyValue;
import kr.or.kashi.hde.base.ReadOnlyPropertyMap;
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

    private final Object mLock = new Object();
    private final ArraySet<Callback> mCallbacks = new ArraySet<>();

    private final MainContext mMainContext;
    private final StreamProcessor mStreamProcessor;
    private final DeviceDiscovery mDeviceDiscovery;
    private final DeviceStatePoller mDeviceStatePoller;
    private NetworkSession mNetworkSession;

    private int mRetryCount = 0;
    private Runnable mStreamErrorRunable = this::retryStart;


    public interface Callback {
        default void onNetworkStarted() {}
        default void onNetworkStopped(int error) {}
        default void onDeviceAdded(List<HomeDevice> devices) {}
        default void onDeviceRemoved(List<HomeDevice> devices) {}
    }

    public HomeNetwork(Context context, boolean isSlaveMode) {
        mContext = context;
        mEventHandler = new Handler(Looper.getMainLooper());
        mIsSlaveMode = isSlaveMode;

        mMainContext = createMainContext(context, isSlaveMode);
        mStreamProcessor = new StreamProcessor(context, mEventHandler, mStreamErrorRunable);
        mDeviceDiscovery = mMainContext.getDeviceDiscovery();
        mDeviceStatePoller = new DeviceStatePoller();
    }

    private MainContext createMainContext(Context context, boolean isSlaveMode) {
        // return new KSMainContext(context);     // standard (2016)
        // return new KSMainContext2(context);    // extended (2022)
        return new KDMainContext(context, isSlaveMode);     // non-standard
    }

    public boolean isRunning() {
        return mStreamProcessor.isRunning();
    }

    public boolean isSlaveMode() {
        return mIsSlaveMode;
    }

    public void addCallback(HomeNetwork.Callback callback) {
        synchronized (mLock) {
            mCallbacks.add(callback);
        }
    }

    public void removeCallback(HomeNetwork.Callback callback) {
        synchronized (mLock) {
            mCallbacks.remove(callback);
        }
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

        mDeviceStatePoller.start(true /* repeative */, polleeList);

        Log.d(TAG, " network has been started");

        dispatchNetworkStarted();

        mRetryCount = 0;

        return true;
    }

    public void stop() {
        mEventHandler.removeCallbacksAndMessages(mStreamErrorRunable);

        if (!isRunning()) {
            return;
        }

        mDeviceStatePoller.stop();
        mMainContext.detachStream();
        mStreamProcessor.stopStream();

        Log.d(TAG, " network has been stopped");

        dispatchNetworkStopped();
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

    public HomeDevice getDevice(String address) {
        return mMainContext.getDevice(address);
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

    private void addSingleDevice(HomeDevice device) {
        // Append a deivce to the main context and other pollers.
        boolean added = mMainContext.addDevice(device);
        if (added) {
            mDeviceStatePoller.addPollee(device.dc());
        }
    }

    private void removeSingleDevice(HomeDevice device) {
        // Remove a device from the main context and so on.
        mMainContext.removeDevice(device);
        mDeviceStatePoller.removePollee(device.dc());
    }

    public void addDevice(HomeDevice device) {
        addDevice(new ArrayList<>(List.of(device)));
    }

    public void addDevice(List<HomeDevice> devices) {
        for (HomeDevice device: devices) {
            addSingleDevice(device);
        }
        dispatchDeviceAdded(devices);
    }

    public void removeDevice(HomeDevice device) {
        removeDevice(new ArrayList<>(List.of(device)));
    }

    public void removeDevice(List<HomeDevice> devices) {
        for (HomeDevice device: devices) {
            removeSingleDevice(device);
        }
        dispatchDeviceRemoved(devices);
    }

    public void removeAllDevices() {
        removeDevice(getAllDevices());
    }

    public void dispatchNetworkStarted() {
        Collection<HomeNetwork.Callback> callbacks;
        synchronized (mLock) {
            if (mCallbacks.isEmpty()) return;
            callbacks = new ArraySet<>(mCallbacks);
        }
        for (HomeNetwork.Callback cb : callbacks) {
            mEventHandler.post(() -> cb.onNetworkStarted());
        }
    }

    public void dispatchNetworkStopped() {
        Collection<HomeNetwork.Callback> callbacks;
        synchronized (mLock) {
            if (mCallbacks.isEmpty()) return;
            callbacks = new ArraySet<>(mCallbacks);
        }
        for (HomeNetwork.Callback cb : callbacks) {
            mEventHandler.post(() -> cb.onNetworkStopped(0));
        }
    }

    public void dispatchDeviceAdded(final List<HomeDevice> devices) {
        Collection<HomeNetwork.Callback> callbacks;
        synchronized (mLock) {
            if (mCallbacks.isEmpty()) return;
            callbacks = new ArraySet<>(mCallbacks);
        }
        for (HomeNetwork.Callback cb : callbacks) {
            mEventHandler.post(() -> cb.onDeviceAdded(devices));
        }
    }

    public void dispatchDeviceRemoved(final List<HomeDevice> devices) {
        Collection<HomeNetwork.Callback> callbacks;
        synchronized (mLock) {
            if (mCallbacks.isEmpty()) return;
            callbacks = new ArraySet<>(mCallbacks);
        }
        for (HomeNetwork.Callback cb : callbacks) {
            mEventHandler.post(() -> cb.onDeviceRemoved(devices));
        }
    }

    public boolean loadDevicesFrom(InputStream is) {
        List<HomeDevice> devices = new ArrayList<>();

        try {
            ObjectInputStream ois = new ObjectInputStream(is);
            List<List<PropertyValue>> propsList = (List) ois.readObject();
            if (propsList == null || propsList.isEmpty()) {
                return false;
            }

            removeAllDevices();
            for (List<PropertyValue> props: propsList) {
                PropertyMap propMap = new BasicPropertyMap();
                propMap.putAll(props);
                devices.add(createDevice(propMap));
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return false;
        }

        addDevice(devices);
        return true;
    }

    public boolean saveDevicesTo(OutputStream os) {
        List<HomeDevice> devices = mMainContext.getAllDevices();
        if (devices.isEmpty()) return false;

        List<List<PropertyValue>> propsList = new ArrayList<>(devices.size());
        for (HomeDevice d: devices) {
            List<PropertyValue> props = d.dc().getReadPropertyMap().getAll();
            propsList.add(props);
        }

        try {
            ObjectOutputStream oos = new ObjectOutputStream(os);
            oos.writeObject(propsList);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public DeviceDiscovery getDeviceDiscovery() {
        return mDeviceDiscovery;
    }
}
