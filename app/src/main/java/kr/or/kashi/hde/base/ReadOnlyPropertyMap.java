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

import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** @hide */
public class ReadOnlyPropertyMap extends PropertyMap {
    private static final String TAG = ReadOnlyPropertyMap.class.getSimpleName();

    private final PropertyMap mMap;

    public ReadOnlyPropertyMap(Collection<PropertyValue> props) {
        mMap = new BasicPropertyMap();
        mMap.putAll(props);
    }

    public ReadOnlyPropertyMap(PropertyMap map, boolean isReferenceMode) {
        if (isReferenceMode) {
            mMap = map;
        } else {
            mMap = new BasicPropertyMap();
            mMap.putAll(map.getAll());
        }
    }

    @Override
    protected PropertyValue getInternal(String name) {
        return mMap.get(name);
    }

    @Override
    protected List<PropertyValue> getAllInternal() {
        return mMap.getAll();
    }

    @Override
    protected boolean putInternal(PropertyValue prop) {
        // It's read only, ignore putting of value.
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append(getClass().getSimpleName());
        sb.append("[");
        for (PropertyValue propValue: mMap.getAll()) {
            sb.append(propValue.getName() + "=" + propValue.getValue());
            sb.append(",");
        }
        sb.append("]");

        return sb.toString();
    }
}
