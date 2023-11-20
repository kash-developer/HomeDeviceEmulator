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

package kr.or.kashi.hde.base;

import android.util.ArrayMap;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** @hide */
public class StageablePropertyMap extends PropertyMap {
    private static final String TAG = StageablePropertyMap.class.getSimpleName();

    private final Object mLock = new Object();
    private final PropertyMap mBaseMap;
    private final Map<String, PropertyValue> mStagingMap = new ArrayMap<>();
    private final boolean mAllowSame;

    public StageablePropertyMap(PropertyMap baseMap) {
        this(baseMap, false);
    }

    public StageablePropertyMap(PropertyMap baseMap, boolean allowSame) {
        mBaseMap = baseMap;
        mBaseMap.addChangeRunnable(this::onChanged);
        mAllowSame = allowSame;
    }

    @Override
    protected PropertyValue getInternal(String name) {
        synchronized (mLock) {
            PropertyValue prop = mStagingMap.get(name);
            if (prop != null) return prop;
            return mBaseMap.get(name);
        }
    }

    @Override
    protected List<PropertyValue> getAllInternal() {
        synchronized (mLock) {
            List<PropertyValue> baseList = mBaseMap.getAll();
            if (mStagingMap.size() > 0) {
                PropertyMap tempMap = new BasicPropertyMap();
                tempMap.putAll(baseList);
                tempMap.putAll(mStagingMap);
                return tempMap.getAll();
            } else {
                return baseList;
            }
        }
    }

    @Override
    protected boolean putInternal(PropertyValue prop) {
        PropertyValue curProp = get(prop.getName());

        if (curProp != null && !prop.getValueClass().equals(curProp.getValueClass())) {
            Log.e(TAG,  "new value class(" + prop.getValueClass() + ") is differ from " +
                        "current (" + curProp.getValueClass() + ")");
            return false;
        }

        if (mAllowSame || !prop.equals(curProp)) {
            synchronized (mLock) {
                mStagingMap.put(prop.getName(), prop);
            }
            return true;
        }

        return false;
    }

    public boolean isStaging() {
        synchronized (mLock) {
            return mStagingMap.size() > 0;
        }
    }

    public List<PropertyValue> getStaging() {
        synchronized (mLock) {
            return new ArrayList<PropertyValue>(mStagingMap.values());
        }
    }

    public void getStaging(List<PropertyValue> outOriginalValues, List<PropertyValue> outStagingValues) {
        outOriginalValues.clear();
        outStagingValues.clear();

        synchronized (mLock) {
            for (PropertyValue value: mStagingMap.values()) {
                outOriginalValues.add(mBaseMap.get(value.getName()));
                outStagingValues.add(value);
            }
        }
    }

    public void commit() {
        synchronized (mLock) {
            mBaseMap.putAll(mStagingMap);
            mStagingMap.clear();
        }
    }

    public void clearStaged() {
        synchronized (mLock) {
            mStagingMap.clear();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        synchronized (mLock) {
            final List<PropertyValue> basePros = mBaseMap.getAll();
            if (basePros.size() > 0) {
                sb.append("base[");
                for (PropertyValue propValue: basePros) {
                    sb.append(propValue.getName() + "=" + propValue.getValue());
                    sb.append(",");
                }
                sb.append("]");
            }

            if (mStagingMap.size() > 0) {
                sb.append("staging[");
                for (PropertyValue propValue: mStagingMap.values()) {
                    sb.append(propValue.getName() + "=" + propValue.getValue());
                    sb.append(",");
                }
                sb.append("]");
            }
        }

        return sb.toString();
    }
}
