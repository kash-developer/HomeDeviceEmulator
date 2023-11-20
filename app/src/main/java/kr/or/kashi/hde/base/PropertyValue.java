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

import android.os.Parcel;
import android.os.Parcelable;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * Stores value of a property.
 */
public final class PropertyValue<T> implements Parcelable {
    private final String mName;
    private final T mValue;
    private final byte[] mExtraData;

    /**
     * Creates an instance of {@link PropertyValue}.
     *
     * @param name      Property name
     * @param value     Value of Property
     * @hide
     */
    public PropertyValue(String name, T value) {
        mName = name;
        mValue = value;
        mExtraData = null;
    }

    /** @hide */
    public PropertyValue(String name, T value, byte[] extraData) {
        mName = name;
        mValue = value;
        mExtraData = extraData;
    }

    /**
     * Creates an instance of PropertyValue.
     *
     * @param in Parcel to read
     * @hide
     */
    @SuppressWarnings("unchecked")
    public PropertyValue(Parcel in) {
        mName = in.readString();
        String valueClassName = in.readString();
        Class<?> valueClass;
        try {
            valueClass = Class.forName(valueClassName);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class not found: " + valueClassName);
        }

        if (String.class.equals(valueClass)) {
            byte[] bytes = in.readBlob();
            mValue = (T) new String(bytes, StandardCharsets.UTF_8);
        } else if (byte[].class.equals(valueClass)) {
            mValue = (T) in.readBlob();
        } else {
            mValue = (T) in.readValue(valueClass.getClassLoader());
        }

        mExtraData = in.readBlob();
    }

    /** @hide */
    public static final Creator<PropertyValue> CREATOR = new Creator<PropertyValue>() {
        @Override
        public PropertyValue createFromParcel(Parcel in) {
            return new PropertyValue(in);
        }

        @Override
        public PropertyValue[] newArray(int size) {
            return new PropertyValue[size];
        }
    };

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    /** @hide */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mName);

        Class<?> valueClass = mValue == null ? null : mValue.getClass();
        dest.writeString(valueClass == null ? null : valueClass.getName());

        // Special handling for String and byte[] to mitigate transaction buffer limitations.
        if (String.class.equals(valueClass)) {
            dest.writeBlob(((String) mValue).getBytes(StandardCharsets.UTF_8));
        } else if (byte[].class.equals(valueClass)) {
            dest.writeBlob((byte[]) mValue);
        } else {
            dest.writeValue(mValue);
        }

        dest.writeBlob(mExtraData);
    }

    /**
     * Get the name of property.
     */
    public String getName() {
        return mName;
    }

    /**
     * Get the value of property.
     */
    public T getValue() {
        return mValue;
    }

    /** @hide */
    public byte[] getExtraData() {
        return mExtraData;
    }

    /** @hide */
    public <E> E convertValueTo(Class<E> toClass) {
        final Class<T> fromClass = getValueClass();
        if (fromClass.equals(toClass)) {
            return (E) mValue;
        }

        String strValue = String.valueOf(mValue);
        if (String.class.equals(toClass)) {
            return (E) strValue;
        }

        return newValueObject(toClass, strValue);
    }

    /* @hide */
    public static PropertyValue create(String name, Object value) {
        if (name == null || value == null) return null;
        Class<?> valueClass = value.getClass();
        if (String.class.equals(valueClass))
            return new PropertyValue<String>(name, (String)value);
        else if (Boolean.class.equals(valueClass) || boolean.class.equals(valueClass))
            return new PropertyValue<Boolean>(name, (Boolean)value);
        else if (Integer.class.equals(valueClass) || int.class.equals(valueClass))
            return new PropertyValue<Integer>(name, (Integer)value);
        else if (Long.class.equals(valueClass) || long.class.equals(valueClass))
            return new PropertyValue<Long>(name, (Long)value);
        else if (Float.class.equals(valueClass) || float.class.equals(valueClass))
            return new PropertyValue<Float>(name, (Float)value);
        else if (Double.class.equals(valueClass) || double.class.equals(valueClass))
            return new PropertyValue<Double>(name, (Double)value);
        return null;
    }

    /** @hide */
    public static <E> E newValueObject(Class<E> clazz, String strValue) {
        if (String.class.equals(clazz)) return (E) strValue;
        if (Boolean.class.equals(clazz)) {
            if ("1".equals(strValue)) strValue = "true";
            if ("0".equals(strValue)) strValue = "false";
        } else {
            if ("true".equalsIgnoreCase(strValue)) strValue = "1";
            if ("false".equalsIgnoreCase(strValue)) strValue = "0";
        }
        try {
            if (Boolean.class.equals(clazz)) return (E) Boolean.valueOf(strValue);
            else if (Integer.class.equals(clazz)) return (E) Integer.valueOf(strValue);
            else if (Long.class.equals(clazz)) return (E) Long.valueOf(strValue);
            else if (Float.class.equals(clazz)) return (E) Float.valueOf(strValue);
            else if (Double.class.equals(clazz)) return (E) Double.valueOf(strValue);
            else throw new RuntimeException("Unsupported value type " + clazz);
        } catch (Exception e) {
            if (Boolean.class.equals(clazz)) return (E) Boolean.valueOf(false);
            else if (Integer.class.equals(clazz)) return (E) Integer.valueOf(0);
            else if (Long.class.equals(clazz)) return (E) Long.valueOf(0L);
            else if (Float.class.equals(clazz)) return (E) Float.valueOf(0.0f);
            else if (Double.class.equals(clazz)) return (E) Double.valueOf(0.0);
            else throw new RuntimeException("Unsupported value type " + clazz);
        }
    }

    /** @hide */
    public Class<T> getValueClass() {
        return (Class<T>) mValue.getClass();
    }

    /** @hide */
    public boolean isSameClassAs(Class<?> otherClass) {
        return getValueClass().equals(otherClass);
    }

    /** @hide */
    public boolean isCompatibleClassAs(Class<?> otherClass) {
        return toPrimitive(getValueClass()).equals(toPrimitive(otherClass));
    }

    /** @hide */
    public static Class<?> toPrimitive(Class<?> c) {
        if (c == Boolean.class) return boolean.class;
        else if (c == Integer.class) return int.class;
        else if (c == Long.class) return long.class;
        else if (c == Float.class) return float.class;
        else if (c == Double.class) return double.class;
        return c;
    }

    /** @hide */
    public PropertyValue cloneOf(String newValue) {
        Class<T> valueClass = getValueClass();
        if (String.class.equals(valueClass)) {
            return new PropertyValue(getName(), newValue);
        }
        return new PropertyValue(getName(), newValueObject(valueClass, newValue));
    }

    /** @hide */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PropertyValue)) return false;

        PropertyValue other = (PropertyValue)obj;
        if (!other.getName().equals(mName)) return false;
        if (other.getValue() == null) return false;

        // TODO: Should compare extra data or not?

        return mValue.equals(other.getValue());
    }

    @Override
    public String toString() {
        return "PropertyValue{" + "mName=" + mName + ", mValue=" + mValue + '}';
    }
}
