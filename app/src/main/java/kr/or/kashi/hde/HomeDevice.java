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
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import kr.or.kashi.hde.base.BasicPropertyMap;
import kr.or.kashi.hde.base.PropertyDef;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.base.PropertyTask;
import kr.or.kashi.hde.base.PropertyValue;
import kr.or.kashi.hde.base.ReadOnlyPropertyMap;

/**
 * Base class of home devices.
 */
public class HomeDevice {
    private static final String TAG = HomeDevice.class.getSimpleName();
    private static final String PROP_PREFIX = "0.";

    /**
     * Application registers {@link HomeDevice.Callback} object to receive
     * notifications about the home network.
     */
    public interface Callback {
        /**
         * Called when property has been changed
         * @param device {@link HomeDevice} object that some state has been changed.
         * @param props {@link List} object that contains properties.
         */
        default void onPropertyChanged(HomeDevice device, PropertyMap props) {}

        /**
         * Called when an error is occurred.
         * @param device {@link HomeDevice} object where this error has been occurred.
         * @param error Error code.
         */
        default void onErrorOccurred(HomeDevice device, @Error int error) {}
    }

    /**
     * Types
     */
    public @interface Type {
        int UNKNOWN = 0;
        int LIGHT = 1;
        int DOOR_LOCK = 2;
        int VENTILATION = 3;
        int GAS_VALVE = 4;
        int HOUSE_METER = 5;
        int CURTAIN = 6;
        int THERMOSTAT = 7;
        int BATCH_SWITCH = 8;
        int SENSOR = 9;
        int AIRCONDITIONER = 10;
        int POWER_SAVER = 11;
    }

    /**
     * Areas
     */
    public @interface Area {
        int UNKNOWN = 0;
        int ENTERANCE = 1;
        int LIVING_ROOM = 2;
        int MAIN_ROOM = 3;
        int OTHER_ROOM = 4;
        int KITCHEN = 5;
    }

    /**
     * Error codes
     */
    public @interface Error {
        int NONE = 0;
        int UNKNOWN = -1;
        int CANT_CONTROL = -2;
        int NO_RESPONDING = -3;
        int NO_DEVICE = -4;
    }

    /** Property of the address */
    @PropertyDef(valueClass=String.class)
    public static final String PROP_ADDR        = PROP_PREFIX + "addr";

    /** Property of the area that the device is installed in */
    @PropertyDef(valueClass=Area.class, defValueI=Area.UNKNOWN)
    public static final String PROP_AREA        = PROP_PREFIX + "area";

    /** Property of the name of device */
    @PropertyDef(valueClass=String.class)
    public static final String PROP_NAME        = PROP_PREFIX + "name";

    /** Property of connection state */
    @PropertyDef(valueClass=Boolean.class)
    public static final String PROP_CONNECTED   = PROP_PREFIX + "connected";

    /** Property of on/off state  */
    @PropertyDef(valueClass=Boolean.class)
    public static final String PROP_ONOFF       = PROP_PREFIX + "onoff";

    /** Property of error code that is defined in {@link Error} */
    @PropertyDef(valueClass=Error.class)
    public static final String PROP_ERROR       = PROP_PREFIX + "error";

    /** Property of the numeric code of device vendor */
    @PropertyDef(valueClass=Integer.class)
    public static final String PROP_VENDOR_CODE = PROP_PREFIX + "vendor.code";

    /** Property of the name of device vendor */
    @PropertyDef(valueClass=String.class)
    public static final String PROP_VENDOR_NAME = PROP_PREFIX + "vendor.name";

    /** @hide */
    @PropertyDef(valueClass=Boolean.class)
    public static final String PROP_IS_SLAVE    = PROP_PREFIX + "is_salve";

    private static Map<Class, Map<String, PropertyValue>> sDefaultPropertyMap
            = new ConcurrentHashMap<>();

    private final DeviceContextBase mDeviceContext;

    private final Handler mHandler;
    private final Executor mHandlerExecutor;
    private final Object mLock = new Object();
    private DeviceContextListenerImpl mDeviceListener = null;
    private final List<Pair<Callback,Executor>> mCallbacks = new ArrayList<>();
    private final Map<String, PropertyValue> mStagedProperties = new ConcurrentHashMap<>();
    private final Runnable mCommitPropsRunnable = this::commitStagedProperties;

    public HomeDevice(DeviceContextBase deviceContext) {
        mDeviceContext = deviceContext;
        mHandler = new Handler(Looper.getMainLooper());
        mHandlerExecutor = mHandler::post;
    }

    /** @hide */
    public DeviceContextBase dc() {
        return mDeviceContext;
    }

    /**
     * Returns the {@link Type} of device.
     * @return The type of device.
     */
    public @Type int getType() {
        return Type.UNKNOWN;
    }

    /**
     * Returns the name of this device.
     * @return The name of device.
     */
    public String getName() {
        return getProperty(PROP_NAME, String.class);
    }

    /**
     * Change the name of this device.
     * @param name New name of device.
     */
    public void setName(String name) {
        setProperty(PROP_NAME, String.class, (name == null ? "" : name));
    }

    /**
     * Set the {@link Area} where the device is located in.
     * @param area The location of device.
     */
    public void setArea(@Area int area) {
        setProperty(PROP_AREA, Integer.class, area);
    }

    /**
     * Returns the address of device.
     * @return Address in string format of {@link InetAddress}.
     */
    public String getAddress() {
        return getProperty(PROP_ADDR, String.class);
    }

    /**
     * Whether the device is connected.
     * @return {@code true} if device is connected.
     */
    public boolean isConnected() {
        return getProperty(PROP_CONNECTED, Boolean.class);
    }

    /**
     * Whether the device is on
     * @return {@code true} if device is on.
     */
    public boolean isOn() {
        return getProperty(PROP_ONOFF, Boolean.class);
    }

    /**
     * Sets the on / off state to device
     * @param on The new state whether if device is on.
     */
    public void setOn(boolean on) {
        setProperty(PROP_ONOFF, Boolean.class, on);
    }

    /**
     * Retrives last code of {@link Error}
     */
    public @Error int getError() {
        return getProperty(PROP_ERROR, Integer.class);
    }

    /**
     * Register callback. The methods of the callback will be called when new events arrived.
     * @param callback The callback to handle event.
     */
    public void addCallback(Callback callback) {
        addCallback(callback, mHandlerExecutor);
    }

    public void addCallback(Callback callback, Executor executor) {
        synchronized (mLock) {
            if (mCallbacks.isEmpty() && mDeviceListener == null) {
                mDeviceListener = new DeviceContextListenerImpl(this);
                mDeviceContext.setListener(mDeviceListener);
            }
            mCallbacks.add(Pair.create(callback, executor));
        }
    }

    /**
     * Unregisters callback.
     * @param callback The callback to handle event.
     */
    public void removeCallback(Callback callback) {
        synchronized (mLock) {
            Iterator it = mCallbacks.iterator();
            while (it.hasNext()) {
                Pair<Callback,Executor> cb = (Pair<Callback,Executor>) it.next();
                if (cb.first == callback) {
                    it.remove();
                }
            }
            if (mCallbacks.isEmpty() && mDeviceListener != null) {
                mDeviceContext.setListener(null);
                mDeviceListener = null;
            }
        }
    }

    /**
     * Get current value of a property.
     *
     * @param propName The name of property.
     * @param valueClass Class type of property value.
     * @return Current value of property.
     */
    public <E> E getProperty(String propName, Class<E> valueClass) {
        PropertyValue<E> prop = mDeviceContext.getProperty(propName);
        return prop.getValue();
    }

    /**
     * Set value to a property.
     *
     * @param propName The name of property.
     * @param valueClass Class type of property value.
     * @param value New value to set.
     */
    public <E> void setProperty(String propName, Class<E> valueClass, E value) {
        setProperty(new PropertyValue<E>(propName, value));
    }

    public <E> E getStagedProperty(String name, Class<E> clazz) {
        if (mStagedProperties.containsKey(name)) {
            return (E) mStagedProperties.get(name).getValue();
        }
        return getProperty(name, clazz);
    }

    public long getPropertyL(String name) {
        return getProperty(name, Long.class);
    }

    public PropertyMap getReadPropertyMap() {
        return mDeviceContext.getReadPropertyMap();
    }

    public void setProperty(PropertyValue prop) {
        mStagedProperties.put(prop.getName(), prop);
        if (!mHandler.hasCallbacks(mCommitPropsRunnable)) {
            mHandler.postDelayed(mCommitPropsRunnable, 1 /* Minimum delay */);
        }
    }

    public void setPropertyNow(PropertyValue prop) {
        mStagedProperties.put(prop.getName(), prop);
        flushProperties();
    }

    public void flushProperties() {
        mHandler.removeCallbacks(mCommitPropsRunnable);
        commitStagedProperties();
    }

    private void commitStagedProperties() {
        if (mStagedProperties.size() > 0) {
            mDeviceContext.setProperty(new ArrayList<>(mStagedProperties.values()));
            mStagedProperties.clear();
        }
    }

    private static class DeviceContextListenerImpl implements DeviceContextBase.Listener {
        private final WeakReference<HomeDevice> mWeakOwner;

        DeviceContextListenerImpl(HomeDevice device) {
            mWeakOwner = new WeakReference<>(device);
        }

        public void onPropertyChanged(List<PropertyValue> props) {
            HomeDevice device = mWeakOwner.get();
            if (device != null) {
                device.onPropertyChanged(props);
            }
        }

        public void onErrorOccurred(int error) {
            HomeDevice device = mWeakOwner.get();
            if (device != null) {
                device.onErrorOccurred(error);
            }
        }
    }

    public void onPropertyChanged(List<PropertyValue> props) {
        Collection<Pair<Callback,Executor>> callbacks;
        synchronized (mLock) {
            if (mCallbacks.isEmpty()) return;
            callbacks = new ArraySet<>(mCallbacks);
        }

        PropertyMap propMap = new ReadOnlyPropertyMap(props);

        for (Pair<Callback,Executor> cb : callbacks) {
            cb.second.execute(() -> cb.first.onPropertyChanged(this, propMap));
        }
    }

    public void onErrorOccurred(int error) {
        Collection<Pair<Callback,Executor>> callbacks;
        synchronized (mLock) {
            if (mCallbacks.isEmpty()) return;
            callbacks = new ArraySet<>(mCallbacks);
        }

        for (Pair<Callback,Executor> cb : callbacks) {
            cb.second.execute(() -> cb.first.onErrorOccurred(this, error));
        }
    }

    /** @hide */
    public static String typeToString(@Type int type) {
        switch (type) {
            case Type.UNKNOWN: return "UNKNOWN";
            case Type.LIGHT: return "LIGHT";
            case Type.DOOR_LOCK: return "DOOR_LOCK";
            case Type.VENTILATION: return "VENTILATION";
            case Type.GAS_VALVE: return "GAS_VALVE";
            case Type.HOUSE_METER: return "HOUSE_METER";
            case Type.CURTAIN: return "CURTAIN";
            case Type.THERMOSTAT: return "THERMOSTAT";
            case Type.BATCH_SWITCH: return "BATCH_SWITCH";
            case Type.SENSOR: return "SENSOR";
            case Type.AIRCONDITIONER: return "AIRCONDITIONER";
            case Type.POWER_SAVER: return "POWER_SAVER";
        }
        return "UNKNOWN";
    }

    /** @hide */
    public static String areaToString(@Area int area) {
        switch (area) {
            case Area.UNKNOWN: return "UNKNOWN";
            case Area.ENTERANCE: return "ENTERANCE";
            case Area.LIVING_ROOM: return "LIVING_ROOM";
            case Area.MAIN_ROOM: return "MAIN_ROOM";
            case Area.OTHER_ROOM: return "OTHER_ROOM";
            case Area.KITCHEN: return "KITCHEN";
        }
        return "UNKNOWN";
    }

    /** @hide */
    @Override
    public String toString() {
        return "HomeDevice {" +
                " name=" + getName() +
                " type=" + typeToString(getType()) +
                " address=" + getAddress().toString() + " }";
    }
}
