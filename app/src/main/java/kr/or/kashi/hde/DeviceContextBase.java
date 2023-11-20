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

import android.annotation.IntDef;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import kr.or.kashi.hde.base.BasicPropertyMap;
import kr.or.kashi.hde.base.PropertyInflater;
import kr.or.kashi.hde.base.ReadOnlyPropertyMap;
import kr.or.kashi.hde.base.StageablePropertyMap;
import kr.or.kashi.hde.base.PropertyMap;
import kr.or.kashi.hde.base.PropertyTask;
import kr.or.kashi.hde.base.PropertyValue;

/*
 * The base implementation of device context.
 */
public abstract class DeviceContextBase implements DeviceStatePollee {
    private static final String TAG = DeviceContextBase.class.getSimpleName();
    private static final boolean DBG = true;

    public static final int PARSE_ERROR_MALFORMED_PACKET = -2;
    public static final int PARSE_ERROR_UNKNOWN = -1;
    public static final int PARSE_OK_NONE = 0;
    public static final int PARSE_OK_PEER_DETECTED = 1;
    public static final int PARSE_OK_STATE_UPDATED = 2;
    public static final int PARSE_OK_ACTION_PERFORMED = 3;
    public static final int PARSE_OK_ERROR_RECEIVED = 4;

    @IntDef(prefix = {"PARSE_"}, value = {
        PARSE_ERROR_MALFORMED_PACKET,
        PARSE_ERROR_UNKNOWN,
        PARSE_OK_NONE,
        PARSE_OK_PEER_DETECTED,
        PARSE_OK_STATE_UPDATED,
        PARSE_OK_ACTION_PERFORMED,
        PARSE_OK_ERROR_RECEIVED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ParseResult {}

    public interface Listener {
        default void onPropertyChanged(List<PropertyValue> props) {}
        default void onErrorOccurred(int error) {}
    }

    private final Class<?> mDeviceClass;
    private final Handler mHandler;
    private Runnable mUpdateReqRunnable;
    private Listener mListener;

    protected final PropertyMap mBasePropertyMap = new BasicPropertyMap();
    protected final StageablePropertyMap mRxPropertyMap;
    private final Map<String, PropertyTask> mPropTaskMap = new ConcurrentHashMap<>();

    private boolean mIsSlave = false;
    private HomeAddress mAddress;
    private String mLogPrefix;

    protected int mPollPhase = DeviceStatePollee.Phase.INITIAL;
    protected long mPollInterval = 0L;
    private long mLastUpdateTime = 0L;

    protected DeviceContextBase mParent;
    private DeviceContextBase[] mChildren;
    private int mChildrenCount;

    public DeviceContextBase(MainContext mainContext, Map defaultProps, Class<?> deviceClass) {
        mDeviceClass = deviceClass;
        mHandler = new Handler(Looper.getMainLooper());
        mRxPropertyMap = new StageablePropertyMap(mBasePropertyMap);
        mRxPropertyMap.putAll(PropertyInflater.inflate(deviceClass));       // Put all default properties as base
        mRxPropertyMap.putAll((Map<String, PropertyValue>)defaultProps);    // Overwrite initial properties
        mRxPropertyMap.commit();

        if (DBG) Log.d(TAG, mDeviceClass.getSimpleName() + ", default properties: " + mRxPropertyMap.toString());

        mIsSlave = mBasePropertyMap.get(HomeDevice.PROP_IS_SLAVE, Boolean.class);

        final String deviceAddress = mBasePropertyMap.get(HomeDevice.PROP_ADDR, String.class);
        mAddress = createAddress(deviceAddress);
        mLogPrefix = "[" + mAddress.getDeviceAddress() + "]";

        if (mIsSlave) {
            // In salve mode, register default property tasks to reflect all property's changes by default.
            for (String propName: mBasePropertyMap.toMap().keySet()) {
                setPropertyTask(propName, new PropertyTask.Reflection(propName));
            }
        } else {
            // Register default property tasks to reflect some property's changes by default.
            setPropertyTask(HomeDevice.PROP_AREA, new PropertyTask.Reflection(HomeDevice.PROP_AREA));
            setPropertyTask(HomeDevice.PROP_NAME, new PropertyTask.Reflection(HomeDevice.PROP_NAME));
        }
    }

    public boolean isSlave() {
        return mIsSlave;
    }

    public DeviceContextBase getParent() {
        return mParent;
    }

    public int getChildCount() {
        return mChildrenCount;
    }

    public int indexOfChild(DeviceContextBase child) {
        final int count = mChildrenCount;
        final DeviceContextBase[] children = mChildren;
        for (int i = 0; i < count; i++) {
            if (children[i] == child) {
                return i;
            }
        }
        return -1;
    }

    public void addChild(DeviceContextBase child) {
        setChildAt(mChildrenCount, child);
    }

    public void removeChild(DeviceContextBase child) {
        final int index = indexOfChild(child);
        final DeviceContextBase[] children = mChildren;
        final int count = mChildrenCount;
        if (index == count - 1) {
            children[--mChildrenCount] = null;
            child.mParent = null;
        } else if (index >= 0 && index < count) {
            System.arraycopy(children, index + 1, children, index, count - index - 1);
            children[--mChildrenCount] = null;
            child.mParent = null;
        }
    }

    public void removeAllChildren() {
        final int count = mChildrenCount;
        if (count <= 0) {
            return;
        }
        final DeviceContextBase[] children = mChildren;
        mChildrenCount = 0;
        for (int i = count - 1; i >= 0; i--) {
            children[i].mParent = null;
            children[i] = null;
        }
    }

    public @Nullable DeviceContextBase getChildAt(int index) {
        if (index < 0 || index >= mChildrenCount) {
            return null;
        }
        return mChildren[index];
    }

    public void setChildAt(int index, @Nullable DeviceContextBase child) {
        DeviceContextBase[] children = mChildren;
        final int count = mChildrenCount;
        final int size = children.length;
        if (index == count) {
            if (size == count) {
                mChildren = new DeviceContextBase[size + 8];
                System.arraycopy(children, 0, mChildren, 0, size);
                children = mChildren;
            }
            children[mChildrenCount++] = child;
            if (child != null) child.mParent = this;
        } else if (index < count) {
            if (size == count) {
                mChildren = new DeviceContextBase[size + 8];
                System.arraycopy(children, 0, mChildren, 0, index);
                System.arraycopy(children, index, mChildren, index + 1, count - index);
                children = mChildren;
            } else {
                System.arraycopy(children, index, children, index + 1, count - index);
            }
            children[index] = child;
            mChildrenCount++;
            if (child != null) child.mParent = this;
        } else {
            throw new IndexOutOfBoundsException("index=" + index + " count=" + count);
        }
    }

    public Class<?> getDeviceClass() {
        return mDeviceClass;
    }

    public String getClassName() {
        return mDeviceClass.getName();
    }

    public HomeAddress getAddress() {
        return mAddress;
    }

    public PropertyValue getProperty(String name) {
        return mBasePropertyMap.get(name);
    }

    public PropertyMap getReadPropertyMap() {
        return new ReadOnlyPropertyMap(mBasePropertyMap, true);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public boolean setProperty(List<PropertyValue> props) {
        if (DBG) {
            final String address = getAddress().getDeviceAddress();
            for (PropertyValue p: props) {
                if (p.getExtraData() != null) {
                    Log.d(TAG, mLogPrefix + " set prop :: " + p.getName() + "=" + p.getValue() + ", extra:" + p.getExtraData().length);
                } else {
                    Log.d(TAG, mLogPrefix + " set prop :: " + p.getName() + "=" + p.getValue());
                }
            }
        }

        List<PropertyTask> currentTasks = new ArrayList();

        // Make copy of the property map and update with new properties
        StageablePropertyMap tempMap = new StageablePropertyMap(mBasePropertyMap, true /* allow same */);
        tempMap.putAll(props);

        for (PropertyValue prop: props) {
            PropertyTask task = mPropTaskMap.get(prop.getName());
            if (task != null && !currentTasks.contains(task)) {
                currentTasks.add(task);
            }
        }

        for (PropertyTask task: currentTasks) {
            task.execTask(tempMap, mRxPropertyMap);
        }

        tempMap.clearStaged();

        commitPropertyChanges(mRxPropertyMap);

        return true; // TODO: Confirm the purpose of this return value, see DeviceContext
    }

    public boolean updateProperties(List<PropertyValue> props) {
        mRxPropertyMap.putAll(props);
        commitPropertyChanges(mRxPropertyMap);
        return true;
    }

    public void onAttachedToStream() {
        // Override it
    }

    public void onDetachedFromStream() {
        // Override it
    }

    @Override
    public void setPollPhase(@DeviceStatePollee.Phase int phase, long interval) {
        if (DBG) {
            final String address = getAddress().getDeviceAddress();
            final String phaseStr = phaseToString(phase);
            Log.d(TAG, mLogPrefix + " new poll phase: " + phaseStr + ", " + interval);
        }

        mPollPhase = phase;
        mPollInterval = interval;

        requestUpdate();
    }

    @Override
    public void requestUpdate() {
        if (mUpdateReqRunnable == null) {
            mUpdateReqRunnable = () -> requestUpdate(mRxPropertyMap); // TODO: Use map as readonly
        }
        if (!mHandler.hasCallbacks(mUpdateReqRunnable)) {
            mHandler.postDelayed(mUpdateReqRunnable, 10);
        }
    }

    @Override
    public long getUpdateTime() {
        return mLastUpdateTime;
    }

    protected void setPropertyTask(String propName, PropertyTask task) {
        mPropTaskMap.put(propName, task);
    }

    protected void clearPropertyTask(String propName) {
        mPropTaskMap.remove(propName);
    }

    public @ParseResult int parsePacket(HomePacket packet) {
        mLastUpdateTime = SystemClock.uptimeMillis();

        int res = parsePayload(packet, mRxPropertyMap);
        if (res <= PARSE_OK_NONE) {
            mRxPropertyMap.clearStaged();
            return res;
        }

        commitPropertyChanges(mRxPropertyMap);

        return res;
    }

    protected void commitPropertyChanges(StageablePropertyMap propMap) {
        if (propMap.isStaging()) {
            final List<PropertyValue> originalValues = new ArrayList<>();
            final List<PropertyValue> stagingValues= new ArrayList<>();
            propMap.getStaging(originalValues, stagingValues);
            propMap.commit();

            if (mListener != null) {
                for (PropertyValue prop: stagingValues) {
                    Log.d(TAG, mLogPrefix + " prop changed :: " + prop.getName() + "=" + prop.getValue());
                }
                mListener.onPropertyChanged(stagingValues);
            }
        }
    }

    // TODO: Call this according to the result of parsePacket()
    protected void onErrorOccurred(@HomeDevice.Error int errorCode) {
        // TODO: Set error code to PROP_ERROR too.
        if (mListener != null) mListener.onErrorOccurred(errorCode);
    }

    protected int clamp(String name, int value, int min, int max) {
        if (value < min) {
            if (DBG) Log.w(TAG, "warning: " + name + " is smaller than " + min);
            value = min;
        }
        if (value > max) {
            if (DBG) Log.w(TAG, "warning: " + name + " is larger than " + max);
            value = max;
        }
        return value;
    }

    protected boolean check(String name, int value, int min, int max) {
        int res = clamp(name, value, min, max);
        if (res != value) Log.w(TAG, "error: " + name + "(" + value +") is out of range (" + min + "~" + max + ")");
        return (res == value);
    }

    protected void assert_(String name, int value, int min, int max) {
        if (!check(name, value, min, max)) {
            throw new RuntimeException(name + " is out of range!");
        }
    }

    public abstract void requestUpdate(PropertyMap props);
    public abstract HomeAddress createAddress(String deviceAddress);
    public abstract @ParseResult int parsePayload(HomePacket packet, PropertyMap outProps);
}
