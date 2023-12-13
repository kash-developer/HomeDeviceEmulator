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

import kr.or.kashi.hde.base.PropertyValue;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * The map utility class that is specializing operations in getting
 * and modifying the property set.
 */
public abstract class PropertyMap {
    /** Get the value of a property */
    public <E> E get(String name, Class<E> clazz) {
        return (E) get(name).getValue();
    }

    /** Put new string value to a property */
    public void put(String name, String value) {
        put(new PropertyValue<String>(name, value));
    }

    /** Put new boolean value to a property */
    public void put(String name, boolean value) {
        put(new PropertyValue<Boolean>(name, value));
    }

    /** Put new integer value to a property */
    public void put(String name, int value) {
        put(new PropertyValue<Integer>(name, value));
    }

    /** Put new long value to a property */
    public void put(String name, long value) {
        put(new PropertyValue<Long>(name, value));
    }

    /** Put new float value to a property */
    public void put(String name, float value) {
        put(new PropertyValue<Float>(name, value));
    }

    /** Put new double value to a property */
    public void put(String name, double value) {
        put(new PropertyValue<Double>(name, value));
    }

    /** Put only bits of value in mask to a property */
    public void putBit(String name, int mask, boolean set) {
        int current = get(name, Integer.class);
        if (set) {
            current |= mask;
        } else {
            current &= ~mask;
        }
        put(name, current);
    }

    /** Put only bits of value in mask to a property */
    public void putBit(String name, long mask, boolean set) {
        long current = get(name, Long.class);
        if (set) {
            current |= mask;
        } else {
            current &= ~mask;
        }
        put(name, current);
    }

    /** Put only bits of value in mask to a property */
    public void putBits(String name, int mask, int value) {
        int current = get(name, Integer.class);
        for (int i=0; i<Integer.SIZE; i++) {
            int indexBit = (1 << i);
            if ((mask & indexBit) != 0) {
                if ((value & indexBit) != 0)
                    current |= indexBit;
                else
                    current &= ~indexBit;
            }
        }
        put(name, current);
    }

    /** Put only bits of value in mask to a property */
    public void putBits(String name, long mask, long value) {
        long current = get(name, Long.class);
        for (int i=0; i<Long.SIZE; i++) {
            long indexBit = (1 << i);
            if ((mask & indexBit) != 0) {
                if ((value & indexBit) != 0)
                    current |= indexBit;
                else
                    current &= ~indexBit;
            }
        }
        put(name, current);
    }

    /** Get all the property values as list */
    public List<PropertyValue> getAll() {
        // TODO: Consider to return cached list if not changed.
        return getAllInternal();
    }

    /** Get all the property values as map */
    public Map<String, PropertyValue> toMap() {
        final List<PropertyValue> props = getAll();
        Map<String, PropertyValue> map = new ArrayMap<>(props.size());
        for (PropertyValue prop : props) {
            map.put(prop.getName(), prop);
        }
        return map;
    }

    /** Put all the property value from other map */
    public void putAll(PropertyMap map) {
        putAll(map.getAll());
    }

    /** Put all the property value from other map */
    public void putAll(Map<String, PropertyValue> map) {
        putAll(map.values());
    }

    /** Put all the property value from other collection */
    public void putAll(Collection<PropertyValue> props) {
        for (PropertyValue prop : props) {
            put(prop);
        }
    }

    /** Get a property value */
    public PropertyValue get(String name) {
        return getInternal(name);
    }

    /** Put a property value */
    public void put(PropertyValue prop) {
        boolean changed = putInternal(prop);
        if (changed) onChanged();
    }

    private long mVersion = 1L;
    private List<WeakReference<Runnable>> mChangeRunnables = new ArrayList<>();

    /** @hide */
    public long version() {
        return mVersion;
    }

    /** @hide */
    public void addChangeRunnable(Runnable changeRunnable) {
        synchronized (mChangeRunnables) {
            mChangeRunnables.add(new WeakReference<>(changeRunnable));
        }
    }

    protected void onChanged() {
        mVersion++;

        List<WeakReference<Runnable>> runnables;
        synchronized (mChangeRunnables) {
            runnables = new ArrayList<>(mChangeRunnables);
        }

        ListIterator<WeakReference<Runnable>> iter = runnables.listIterator();
        while(iter.hasNext()){
            WeakReference<Runnable> runnable = iter.next();
            if (runnable.get() != null) {
                runnable.get().run();
            } else {
                iter.remove();
            }
        }
    }

    protected abstract PropertyValue getInternal(String name);
    protected abstract List<PropertyValue> getAllInternal();
    protected abstract boolean putInternal(PropertyValue prop);
}
